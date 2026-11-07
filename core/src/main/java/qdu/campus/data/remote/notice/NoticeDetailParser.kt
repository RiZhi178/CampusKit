package qdu.campus.data.remote.notice

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * 教务处 / 研究生院通知**详情页**解析（两站同一套博达 CMS，2026-11-07 探测，
 * 契约见 `docs/interfaces/jwc-notice.md` 第四节与 `grad-notice.md` 附录）。
 *
 * 正文容器是 `div[id^=vsb_content]` 里的 `div.v_news_content`（jwc 有 `vsb_content_4`
 * 这类带数字后缀的变体，且单双引号混用，选择器按前缀匹配）；正文以 `<p>` 流为主，
 * 段内 span 只带字体样式，`&nbsp;` 是缩进——按空白规整后跳过空段。
 * 内嵌 PDF 正文（jwc 的 `showVsbpdfIframe("/__local/...pdf")`）不是 DOM 元素而是内联
 * JS 调用，正则从容器 HTML 里抠直链，作为独立块交给 UI 处理。
 *
 * 附件在正文容器**外**：`a[href*=download.jsp]`，链接文本就是完整文件名（含扩展名）。
 * 全文档扫一遍并按 URL 去重。附件下载必须带详情页 URL 作 Referer（两站同款陷阱：
 * 无 Referer 回 200 + 3805B HTML 假 200），下载侧的事，解析器只负责给 URL。
 */
/** 正文块。表格样本未出现（7 页实测 0 个 `<table>`），遇到时降级为纯文本段。 */
sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Image(val url: String) : Block
    data class EmbeddedPdf(val url: String) : Block
}

data class Attachment(
    val name: String,
    val url: String,
)

/** 一条通知的原生详情：标题 / 发布日期 / 正文块流 / 附件清单。 */
data class NoticeDetail(
    val title: String,
    val publishedDate: String,
    val blocks: List<Block>,
    val attachments: List<Attachment>,
)

object NoticeDetailParser {

    private val DATE_REGEX = Regex("""(?:发布时间|日期)[：:]\s*(\d{4}-\d{2}-\d{2})""")
    private val PDF_IFRAME_REGEX = Regex("""showVsbpdfIframe\(\s*"([^"]+)"""")

    fun parse(html: String, pageUrl: String): NoticeDetail? {
        if (html.isBlank()) return null
        val doc = runCatching { Jsoup.parse(html, pageUrl) }.getOrNull() ?: return null

        val title = doc.title().trim().ifBlank { "" }
        val bodyText = doc.body().text()
        val date = DATE_REGEX.find(bodyText)?.groupValues?.get(1).orEmpty()

        val container = doc.selectFirst("div[id^=vsb_content]")
            ?: return NoticeDetail(title, date, emptyList(), attachmentsOf(doc, pageUrl))
        val content = container.selectFirst(".v_news_content") ?: container

        val blocks = mutableListOf<Block>()
        for (el in content.select("p, img, table")) {
            when {
                el.tagName() == "img" -> addImage(blocks, el, pageUrl)
                el.tagName() == "table" -> addTableAsText(blocks, el)
                else -> {
                    // 段落里的内嵌图片先入块，再收段落文本
                    for (img in el.select("img")) addImage(blocks, img, pageUrl)
                    val text = normalize(el.text())
                    if (text.isNotEmpty()) blocks.add(Block.Paragraph(text))
                }
            }
        }

        // showVsbpdfIframe 是内联 JS，DOM 里不存在对应元素，从容器 HTML 正则抠直链
        PDF_IFRAME_REGEX.find(content.html())?.let { match ->
            val probe = Jsoup.parse("""<a href="${match.groupValues[1]}"></a>""", pageUrl)
            val pdfUrl = probe.selectFirst("a")?.absUrl("href").orEmpty()
            if (pdfUrl.isNotBlank()) blocks.add(Block.EmbeddedPdf(pdfUrl))
        }

        return NoticeDetail(title, date, blocks, attachmentsOf(doc, pageUrl))
    }

    private fun addImage(blocks: MutableList<Block>, img: Element, pageUrl: String) {
        val url = img.absUrl("src").ifBlank { img.attr("src") }
        if (url.isNotBlank() && url !in blocks.filterIsInstance<Block.Image>().map { it.url }) {
            blocks.add(Block.Image(url))
        }
    }

    private fun addTableAsText(blocks: MutableList<Block>, table: Element) {
        val rows = table.select("tr").map { row ->
            row.select("td, th").joinToString("　") { it.text().trim() }.trim()
        }.filter { it.isNotEmpty() }
        if (rows.isNotEmpty()) blocks.add(Block.Paragraph(rows.joinToString("\n")))
    }

    private fun attachmentsOf(doc: org.jsoup.nodes.Document, pageUrl: String): List<Attachment> {
        val out = mutableListOf<Attachment>()
        val seen = mutableSetOf<String>()
        for (a in doc.select("a[href*=download.jsp]")) {
            val url = a.absUrl("href").ifBlank { a.attr("href") }
            if (url.isBlank() || !seen.add(url)) continue
            val name = normalize(a.text()).ifBlank { "附件" }
            out.add(Attachment(name = name, url = url))
        }
        return out
    }

    /** 全角空格与 `&nbsp;` 都规整成普通空格，再压缩连续空白——两次抓同一页指纹要稳定。 */
    private fun normalize(text: String): String =
        text.replace('\u00A0', ' ').replace(Regex("""\s+"""), " ").trim()
}
