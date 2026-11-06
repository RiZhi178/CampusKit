package qdu.campus.data.remote.jwc

import qdu.campus.domain.model.Notice
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.security.MessageDigest

/**
 * 教务处「教务通知」列表解析（`jwc.qdu.edu.cn/jwtz.htm`，2026-11-05 探测，
 * 契约见 `docs/interfaces/jwc-notice.md`）。
 *
 * 条目结构固定为 `div.notice_item > div.notice_box > span(日期) + a[title]`，
 * 九页 134 条全部命中。要点都来自实测：
 * - 页面没有 `<base>`，链接混用 `info/...`、`../info/...` 与 `content.jsp?...` 三种相对形态，
 *   必须把列表页 URL 交给 Jsoup 解析绝对地址；
 * - 标题要 trim 并压缩空白（134 条里 1 条 `title` 带尾部空格、1 条含两个连续空格），
 *   否则同一条通知两次抓出的标题不同，指纹分裂、已读标记跟着丢；
 * - 页面没有栏目元素，[Notice.column] 留空，不解析标题里的方括号前缀。
 *
 * 指纹带 `jwc_notice|` 来源前缀：研究生院按「标题+日期」算，两边同日同标题时不会互相覆盖。
 */
class JwcNoticeParser {

    fun parse(html: String, pageUrl: String): List<Notice> {
        if (html.isBlank()) return emptyList()
        val doc = runCatching { Jsoup.parse(html, pageUrl) }.getOrNull() ?: return emptyList()
        val notices = mutableListOf<Notice>()
        val seen = mutableSetOf<String>()
        for (box in doc.select("div.notice_box")) {
            val notice = toNotice(box) ?: continue
            if (seen.add(notice.fingerprint)) notices.add(notice)
        }
        return notices
    }

    private fun toNotice(box: Element): Notice? {
        val anchor = box.selectFirst("a[title]") ?: return null
        val title = collapse(anchor.attr("title"))
        if (title.length < 4) return null
        val date = box.selectFirst("span")?.text()?.trim().orEmpty()
        if (DATE_REGEX.matchEntire(date) == null) return null
        val url = anchor.absUrl("href").ifBlank { anchor.attr("href") }
        if (url.isBlank() || url.startsWith("javascript")) return null
        return Notice(
            fingerprint = fingerprint(title, date),
            title = title,
            date = date,
            column = "",
            url = url,
            source = SOURCE,
        )
    }

    private fun collapse(raw: String): String = raw.replace(WHITESPACE_REGEX, " ").trim()

    companion object {
        /** 教务处通知的来源标识，同时用作指纹前缀，与研究生院的指纹空间隔开。 */
        const val SOURCE = "jwc_notice"

        /** 栏目第一页；第 2 页起是倒序页码（`jwtz/92.htm` 是第 2 页），契约见 jwc-notice.md 第三节。 */
        const val FIRST_PAGE = "jwtz.htm"

        private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        fun fingerprint(title: String, date: String): String {
            val raw = "$SOURCE|" + title.trim() + "|" + date.trim()
            val bytes = MessageDigest.getInstance("SHA-1")
                .digest(raw.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
