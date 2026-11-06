package qdu.campus.data.remote.xg

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import qdu.campus.domain.model.XgGrid

/**
 * 学工系统（ASP.NET WebForms）列表页的通用表格读取。
 *
 * 学工的每个业务页都是 `GridView` + 分页控件这套同一模板，列由服务端按批次与奖项给：
 * 有数据时才有表头行，0 条时只留一行 `colspan` 的「未找到相关数据」。
 * 所以这里按结构认表格（`id` 或 `class` 里带 grid），表头给几列就认几列，
 * 不给任何一页写死列名——校方增减一列不用改代码。
 *
 * 请假记录、测评、三助这些页共用这一个读取器。
 */
object XgGridReader {

    fun read(doc: Document): List<XgGrid> =
        doc.select("table")
            .filter { isGrid(it) }
            .map { toGrid(it) }
            .filter { it.headers.isNotEmpty() || it.rows.isNotEmpty() }

    /**
     * 列表总数取分页控件那句「共检索到 N 条记录」；找不到返回 null，让界面不报数。
     *
     * 原句里的间隔是 `&nbsp;`，正则的 `\s` 不认 U+00A0，所以整页文本先归一化再找。
     */
    fun totalRecords(doc: Document): Int? =
        TOTAL.find(normalize(doc.text()))?.groupValues?.get(1)?.toIntOrNull()

    fun normalize(text: String): String =
        text.replace(' ', ' ').replace(WHITESPACE, " ").trim()

    private fun isGrid(table: Element): Boolean =
        table.id().contains("grid", true) ||
            table.className().contains("grid", true) ||
            table.`is`(".form-table")

    private fun toGrid(table: Element): XgGrid {
        val rows = rowsOf(table)
        val headerIndex = rows.indexOfFirst { row -> row.select("th").isNotEmpty() }
        val headers = if (headerIndex >= 0) cellsOf(rows[headerIndex]).map(::normalize) else emptyList()
        val dataRows = rows
            .drop(if (headerIndex >= 0) headerIndex + 1 else 0)
            .mapNotNull { row -> cellsOf(row).takeUnless { isPlaceholder(row, it) }?.map(::normalize) }
            .filter { cells -> cells.any { it.isNotEmpty() } }
        return XgGrid(id = table.id(), headers = headers, rows = dataRows)
    }

    /** 只取这张表自己的行，嵌套子表的行不算。 */
    private fun rowsOf(table: Element): List<Element> =
        table.children().flatMap { child ->
            when (child.tagName()) {
                "tr" -> listOf(child)
                "thead", "tbody", "tfoot" -> child.children().filter { it.tagName() == "tr" }
                else -> emptyList()
            }
        }

    private fun cellsOf(row: Element): List<String> =
        row.children()
            .filter { it.tagName() in CELL_TAGS }
            .map { it.text() }

    /** 空表由服务端渲染成一格横跨整行的提示语，它不是数据行。 */
    private fun isPlaceholder(row: Element, cells: List<String>): Boolean {
        if (cells.size > 1) return false
        if (cells.firstOrNull()?.let(::normalize) in NO_DATA) return true
        val span = row.childOrNull(0)?.attr("colspan")?.toIntOrNull() ?: 0
        return span > 1
    }

    private val CELL_TAGS = setOf("th", "td")
    private val NO_DATA = setOf("未找到相关数据", "没有找到相关数据", "暂无数据")
    private val TOTAL = Regex("""共检索到\s*(\d+)\s*条记录""")
    private val WHITESPACE = Regex("""\s+""")
}

private fun Element.childOrNull(index: Int): Element? = children().getOrNull(index)
