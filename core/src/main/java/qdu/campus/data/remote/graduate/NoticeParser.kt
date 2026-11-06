package qdu.campus.data.remote.graduate

import qdu.campus.domain.model.Notice
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.security.MessageDigest

/**
 * 研究生院通知解析（培养 / 学籍 / 学位三个栏目，2026-11-05 探测，契约见
 * `docs/interfaces/grad-notice.md`）。
 *
 * 不从首页抓：首页长标题被截成省略号，「学子风采」的人物报道结构和通知块一样，会混进结果。
 * 三个栏目页共用一个模板 `ul.wz-list > li.wz-list-li > a[title] > p.tit + span.time`，
 * 完整标题在 `title` 属性里。栏目页的 `<a>` 同时包住标题与日期，
 * 不能用链接文字取标题，否则日期会被拼进标题，同一条通知会产生两种指纹、重复入库。
 *
 * 只认带非空 `title` 属性且与列表页同域的链接。首页通知块的 `title` 是空串，
 * 「学子风采」指向微信公众号，两者都会被这两个条件挡掉——首页因此解析不出条目，这是有意为之：
 * 首页不再是数据源。栏目模板若改版到属性消失，解析结果变空，
 * 由上层按「未解析到通知」提示，不静默出错。
 */
class NoticeParser {

    fun parse(html: String, pageUrl: String): List<Notice> {
        if (html.isBlank()) return emptyList()
        val doc = runCatching { Jsoup.parse(html, pageUrl) }.getOrNull() ?: return emptyList()
        val column = columnOf(pageUrl)
        val pageHost = runCatching { java.net.URI(pageUrl).host }.getOrNull()
        val notices = mutableListOf<Notice>()
        val seen = mutableSetOf<String>()
        for (element in doc.select("li")) {
            val notice = toNotice(element, column, pageHost) ?: continue
            if (seen.add(notice.fingerprint)) notices.add(notice)
        }
        return notices
    }

    private fun toNotice(element: Element, column: String, pageHost: String?): Notice? {
        val anchor = element.select("a[href]")
            .firstOrNull { it.attr("title").trim().isNotEmpty() } ?: return null
        val title = collapse(anchor.attr("title"))
        if (title.length < 4) return null
        val date = DATE_REGEX.find(element.text())?.value ?: return null
        val url = anchor.absUrl("href").ifBlank { anchor.attr("href") }
        if (url.isBlank() || url.startsWith("javascript")) return null
        // 只收与列表页同域的链接：首页「学子风采」指向微信公众号，结构和通知块一样，
        // 没有 host 过滤就会混进通知里。真实通知都落在同一站点内。
        val linkHost = runCatching { java.net.URI(url).host }.getOrNull()
        if (pageHost != null && linkHost != pageHost) return null
        return Notice(
            fingerprint = fingerprint(title, normalizeDate(date)),
            title = title,
            date = normalizeDate(date),
            column = column,
            url = url,
        )
    }

    /**
     * 标题清洗：trim 并压缩连续空白。教务处九页 134 条里实测 1 条 `title` 带尾部空格、
     * 1 条含两个连续空格；同一份响应里标题清洗不一致，指纹就会分裂，已读标记跟着丢。
     */
    private fun collapse(raw: String): String = raw.replace(WHITESPACE_REGEX, " ").trim()

    /** 栏目名按栏目页 URL 判定，与 [COLUMNS] 同一套口径，不解析标题里的方括号前缀。 */
    private fun columnOf(pageUrl: String): String =
        COLUMNS.firstOrNull { pageUrl.contains("/${it.path.substringBefore('/')}/") }?.label.orEmpty()

    private fun normalizeDate(raw: String): String {
        val match = DATE_PARTS.find(raw) ?: return raw
        val (year, month, day) = match.destructured
        return "%04d-%02d-%02d".format(year.toInt(), month.toInt(), day.toInt())
    }

    companion object {
        private val DATE_REGEX = Regex("""\d{4}\s*[-/.年]\s*\d{1,2}\s*[-/.月]\s*\d{1,2}""")
        private val DATE_PARTS = Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})""")
        private val WHITESPACE_REGEX = Regex("""\s+""")

        /**
         * 三个通知栏目。`path` 相对研究生院域名，是各栏目的第一页；
         * 第 2 页起在路径里加倒序页码（如培养第 2 页是 `pygz/tzgg/50.htm`），
         * 总页数各栏不同（51 / 6 / 49），要从分页控件读，不能互相套用。
         */
        val COLUMNS = listOf(
            NoticeColumn("pygz/tzgg.htm", "培养工作"),
            NoticeColumn("xjgz/tzgg.htm", "学籍工作"),
            NoticeColumn("xwgz/tzgg.htm", "学位工作"),
        )

        fun fingerprint(title: String, date: String): String {
            val raw = title.trim() + "|" + date.trim()
            val bytes = MessageDigest.getInstance("SHA-1")
                .digest(raw.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/** 一个通知栏目：相对路径与页面上的栏目名。 */
data class NoticeColumn(val path: String, val label: String)
