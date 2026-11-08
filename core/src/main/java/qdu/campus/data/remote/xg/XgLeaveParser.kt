package qdu.campus.data.remote.xg

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import qdu.campus.domain.model.XgControl
import qdu.campus.domain.model.XgControlKind
import qdu.campus.domain.model.XgForm
import qdu.campus.domain.model.XgLabelledValue
import qdu.campus.domain.model.XgLeaveApproval
import qdu.campus.domain.model.XgLeaveFormPage
import qdu.campus.domain.model.XgLeaveListPage
import qdu.campus.domain.model.XgLeaveRecord
import qdu.campus.domain.model.XgLeaveSection
import java.net.URI

/**
 * 学工日常请假（`DailyLeave`）的三个页面：列表、新建表单、详情。
 *
 * 表单与详情是同一个 `StuApply_Edit.aspx`，带 `?Id=` 就是详情，所以 [form] 一个函数读两者。
 * 契约与实测证据见 `docs/interfaces/xg.md` 第四节。
 *
 * 这一页的表格与评奖评优那套不一样：表头是 `tr.trEven` 里的 `td`（不是 `th`），
 * 0 条时表头行整个消失，只剩一格 colspan 的「未找到相关数据」。所以列名走
 * [headerRow] 单独认，通用的 `XgGridReader` 只用来读那句「共检索到 N 条记录」。
 */
object XgLeaveParser {

    fun list(html: String, pageUrl: String): XgLeaveListPage {
        val doc = parse(html, pageUrl)
        val form = XgFormReader.read(doc)
        val grid = doc.select("table").firstOrNull {
            it.hasClass("form-table") || it.id().contains("GridView")
        }
        val rows = grid?.let(::rowsOf).orEmpty()
        val header = headerRow(rows)
        val add = doc.selectFirst("#AddDiv a[url]")
        return XgLeaveListPage(
            url = pageUrl,
            form = form,
            canApply = add != null,
            applyUrl = resolve(pageUrl, add?.attr("url").orEmpty()),
            blockedMessage = XgGridReader.normalize(doc.selectFirst("#NoLeaveDiv")?.text().orEmpty()),
            caption = caption(doc),
            headers = header?.let { cells(it) }.orEmpty(),
            records = rows.drop(if (header == null) 0 else rows.indexOf(header) + 1)
                .filterNot { XgGridReader.isPlaceholder(it) }
                .mapNotNull { record(it, pageUrl) },
            totalRecords = XgGridReader.totalRecords(doc),
        )
    }

    fun form(html: String, pageUrl: String): XgLeaveFormPage {
        val doc = parse(html, pageUrl)
        val form = XgFormReader.read(doc) ?: XgForm(action = pageUrl, controls = emptyList())
        val submit = submitLink(doc)
        return XgLeaveFormPage(
            url = pageUrl,
            form = form,
            student = student(doc),
            sections = sections(doc, form),
            approval = approval(doc),
            recordId = RECORD_ID.find(pageUrl)?.groupValues?.get(1).orEmpty(),
            submitTarget = submit?.let { POSTBACK.find(it.attr("href"))?.groupValues?.get(1) }.orEmpty(),
            submitLabel = XgGridReader.normalize(submit?.text().orEmpty()),
            canRevoke = doc.selectFirst("#Cancel a") != null,
            revokeConfirm = CONFIRM.find(html)?.groupValues?.get(1).orEmpty(),
            message = message(html),
        )
    }

    /**
     * 服务端把结果注入成 `window.onload = function(){layer.alert('操作成功！', …)}`，
     * 成功与失败都是 200 且留在原页，所以这句话是唯一的即时反馈。
     *
     * 只认 `layer.alert`：页面自己的脚本里还有 `layer.msg('该状态不能打印！')` 这种静态文案，
     * 一并扫就会把它当成服务端回话。
     */
    fun message(html: String): String =
        (ALERT_SINGLE.find(html) ?: ALERT_DOUBLE.find(html))?.groupValues?.get(1).orEmpty()

    private fun parse(html: String, pageUrl: String): Document =
        if (html.isBlank()) Document.createShell(pageUrl)
        else runCatching { Jsoup.parse(html, pageUrl) }.getOrElse { Document.createShell(pageUrl) }

    private fun record(row: Element, pageUrl: String): XgLeaveRecord? {
        val cells = cells(row)
        if (cells.isEmpty()) return null
        val link = row.selectFirst("a[url]")
        val detail = link?.attr("url").orEmpty()
        val revoke = row.selectFirst("input[type=submit]")
        val id = revoke?.attr("myid")?.takeIf { it.isNotEmpty() }
            ?: RECORD_ID.find(detail)?.groupValues?.get(1).orEmpty()
        if (id.isEmpty() && detail.isEmpty()) return null
        return XgLeaveRecord(
            id = id,
            cells = cells,
            detailUrl = resolve(pageUrl, detail),
            revokeButton = revoke?.attr("name").orEmpty(),
            revokeConfirm = row.selectFirst("a[msg]")?.attr("msg").orEmpty(),
        )
    }

    /** 表头行：有 `th` 就认，没有就认第一行「全是纯文本格子」的 `trEven`。数据行里一定有链接。 */
    private fun headerRow(rows: List<Element>): Element? =
        rows.firstOrNull { it.select("th").isNotEmpty() }
            ?: rows.firstOrNull {
                it.hasClass("trEven") && it.children().size > 1 && it.select("a, input").isEmpty()
            }

    private fun rowsOf(table: Element): List<Element> =
        table.children().flatMap { child ->
            when (child.tagName()) {
                "tr" -> listOf(child)
                "thead", "tbody", "tfoot" -> child.children().filter { it.tagName() == "tr" }
                else -> emptyList()
            }
        }

    private fun cells(row: Element): List<String> =
        row.children().filter { it.tagName() in CELL_TAGS }.map { XgGridReader.normalize(it.text()) }

    private fun caption(doc: Document): String {
        val bar = doc.selectFirst(".DivCaption") ?: return ""
        val text = XgGridReader.normalize(bar.selectFirst("div")?.text() ?: bar.text())
        return text.substringBefore("共检索到").trim()
    }

    /** 学生基本信息是服务端直接印在表单上半段的，按控件 id 前缀认，标签取同格左边那一格。 */
    private fun student(doc: Document): List<XgLabelledValue> =
        doc.select("[id^=StudentInfo1_]").mapNotNull { span ->
            val cell = span.closestTd() ?: return@mapNotNull null
            val value = XgGridReader.normalize(span.text())
            XgLabelledValue(label(cell), value)
        }.filter { it.label.isNotEmpty() }

    private fun sections(doc: Document, form: XgForm): List<XgLeaveSection> {
        val fields = form.controls.filter(::isField).groupBy { it.section }
        val notes = LinkedHashMap<String, MutableList<XgLabelledValue>>()
        var section = ""
        for (cell in doc.select("td.TitleTd, td[colspan]")) {
            if (cell.hasClass("TitleTd")) {
                section = XgGridReader.normalize(cell.text())
                notes.getOrPut(section) { mutableListOf() }
                continue
            }
            if (cell.select("input[name], select[name], textarea[name], [id^=StudentInfo1_]").isNotEmpty()) continue
            val text = XgGridReader.normalize(cell.text())
            if (text.isEmpty()) continue
            notes.getOrPut(section) { mutableListOf() } += XgLabelledValue(label(cell), text)
        }
        val titles = LinkedHashSet<String>().apply {
            addAll(notes.keys)
            addAll(fields.keys)
        }
        return titles.map { title ->
            XgLeaveSection(
                title = title,
                fields = fields[title].orEmpty(),
                notes = notes[title].orEmpty(),
            )
        }.filter { it.fields.isNotEmpty() || it.notes.isNotEmpty() }
    }

    /** 隐藏底座、按钮、只读的字数计数框都不是给人填的字段。 */
    private fun isField(control: XgControl): Boolean =
        control.kind in FIELD_KINDS && !(control.readOnly && control.validators.isEmpty())

    private fun approval(doc: Document): XgLeaveApproval? {
        val status = doc.selectFirst("#LookExamInfo_NextStepMsg") ?: return null
        return XgLeaveApproval(
            status = XgGridReader.normalize(status.text()),
            steps = doc.select("#LookExamInfo_WorkflowInfo span").map { XgGridReader.normalize(it.text()) },
            records = doc.select("#LookExamInfo_ExamInfo .set_row").map { XgGridReader.normalize(it.text()) },
        )
    }

    private fun submitLink(doc: Document): Element? =
        doc.select("a[href]").firstOrNull { POSTBACK.containsMatchIn(it.attr("href")) }

    private fun label(cell: Element): String {
        var sibling = cell.previousElementSibling()
        while (sibling != null) {
            if (sibling.hasClass("EdtTitleBack")) {
                return XgGridReader.normalize(sibling.text()).trimEnd('：', ':').trim()
            }
            sibling = sibling.previousElementSibling()
        }
        return ""
    }

    private fun Element.closestTd(): Element? {
        var node: Element? = this
        while (node != null) {
            if (node.tagName() == "td") return node
            node = node.parent()
        }
        return null
    }

    /** 页面上的入口都是相对地址（`StuApply_Edit.aspx?Id=…`），按当前页地址解析成绝对地址。 */
    private fun resolve(base: String, relative: String): String {
        if (relative.isEmpty()) return ""
        return runCatching { URI(base).resolve(relative).toString() }.getOrElse { relative }
    }

    private val CELL_TAGS = setOf("th", "td")
    private val FIELD_KINDS = setOf(
        XgControlKind.TEXT, XgControlKind.TEXTAREA, XgControlKind.PASSWORD,
        XgControlKind.SELECT, XgControlKind.RADIO, XgControlKind.CHECKBOX,
    )
    private val RECORD_ID = Regex("""[?&]Id=([^&\s"']+)""")
    private val POSTBACK = Regex("""__doPostBack\(['"]([^'"]+)['"]""")
    private val CONFIRM = Regex("""layer\.confirm\(\s*"([^"]*)"""")
    private val ALERT_SINGLE = Regex("""layer\.alert\(\s*'([^']*)'""")
    private val ALERT_DOUBLE = Regex("""layer\.alert\(\s*"([^"]*)"""")
}