package qdu.campus.data.remote.xg

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import qdu.campus.domain.model.XgAwardBatch
import qdu.campus.domain.model.XgAwardPage

/**
 * 学工评奖评优申请页（PJPY `StuSchoolApply.aspx`）。
 *
 * 这一页最有用的是头部那张批次表：批次名称、学年、起止时间、批次状态、本批次可选奖项
 * 都直接印在页面上（2026-11-05 实测），比下面的申请列表更早回答「这个批次还开不开、
 * 我申过没有」。批次与奖项都是 `select`，切换要 postback，原生只读，所以其它批次
 * 只作为名字列出来，不承诺能看。
 *
 * 取法照 `NoticeParser`：按结构认（`EdtTitleBack` 是标签格，它的下一个 `td` 是值格），
 * 不认固定 id；认不出来就返回空对象，由上层判「这一页不是业务页」。
 */
object XgAwardParser {

    fun parse(html: String, pageUrl: String): XgAwardPage {
        if (html.isBlank()) return EMPTY
        val doc = runCatching { Jsoup.parse(html, pageUrl) }.getOrNull() ?: return EMPTY
        val labels = labelCells(doc)
        return XgAwardPage(
            batch = toBatch(labels),
            caption = caption(doc),
            totalRecords = XgGridReader.totalRecords(doc),
            grids = XgGridReader.read(doc),
        )
    }

    /** 标签格与值格在同一行成对出现：`EdtTitleBack` 后面那一格就是它的值。 */
    private fun labelCells(doc: Document): Map<String, Cell> {
        val out = LinkedHashMap<String, Cell>()
        doc.select("tr").forEach { row ->
            val cells = row.select("> td")
            var index = 0
            while (index < cells.size - 1) {
                val title = cells[index]
                if (title.hasClass("EdtTitleBack")) {
                    val label = XgGridReader.normalize(title.text())
                    if (label.isNotEmpty()) out[label] = readValue(cells[index + 1])
                    index += 2
                } else {
                    index++
                }
            }
        }
        return out
    }

    /** 有 `select` 的格子取选中项（没标 selected 就是第一项）并把全部选项留着；其余格子直接取文本。 */
    private fun readValue(cell: Element): Cell {
        val options = cell.select("select option")
            .map { XgGridReader.normalize(it.text()) }
            .filter { it.isNotEmpty() }
        if (options.isEmpty()) {
            return Cell(text = XgGridReader.normalize(cell.text()), options = emptyList())
        }
        val marked = cell.select("select option[selected]")
        val selected = if (marked.isEmpty()) options.first() else XgGridReader.normalize(marked[0].text())
        return Cell(text = selected, options = options)
    }

    private fun toBatch(labels: Map<String, Cell>): XgAwardBatch? {
        val batch = labels.findBy("批次名称") ?: return null
        val name = batch.text
        return XgAwardBatch(
            name = name,
            otherNames = batch.options.filter { it != name },
            year = labels.findBy("学年")?.text.orEmpty(),
            dateRange = labels.findBy("起止")?.text.orEmpty(),
            status = labels.findBy("状态")?.text.orEmpty(),
            awardOptions = labels.findBy("奖项")?.options.orEmpty(),
        )
    }

    /** 分区标题在标题条左半截，右半截是分页那句「共检索到 N 条记录」，不要混进来。 */
    private fun caption(doc: Document): String {
        val bar = doc.selectFirst(".DivCaption") ?: return ""
        val text = XgGridReader.normalize(bar.selectFirst("span")?.text() ?: bar.text())
        return text.substringBefore("共检索到").trim()
    }

    private fun Map<String, Cell>.findBy(key: String): Cell? =
        entries.firstOrNull { it.key.contains(key) }?.value

    private data class Cell(val text: String, val options: List<String>)

    private val EMPTY = XgAwardPage(batch = null, caption = "", totalRecords = null, grids = emptyList())
}
