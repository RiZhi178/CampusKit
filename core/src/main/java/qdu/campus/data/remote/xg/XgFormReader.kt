package qdu.campus.data.remote.xg

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import qdu.campus.domain.model.XgCondition
import qdu.campus.domain.model.XgControl
import qdu.campus.domain.model.XgControlKind
import qdu.campus.domain.model.XgForm
import qdu.campus.domain.model.XgOption

/**
 * 把学工的 WebForms 表单读成控件清单。
 *
 * 只收有 `name` 的控件：没有 name 的那些（附件 widget 的一堆配置隐藏字段、bootstrap-select
 * 生成的搜索框）浏览器本来就不提交，收进来只会污染请求体。
 *
 * 三件按结构认、不按字段名认的事：
 * 1. 分区标题是 `td.TitleTd`，按文档顺序走一遍就能把控件归到它下面那段；
 * 2. 字段标签是同格里左边那个 `td.EdtTitleBack`，一行有两对标签时各认各的；
 * 3. 单选组的校验元数据长在外层 `span[id]` 上，不在 input 上，所以要往上找一层。
 */
object XgFormReader {

    fun read(doc: Document): XgForm? {
        val form = doc.selectFirst("form") ?: return null
        val action = form.absUrl("action").ifEmpty { doc.baseUri() }
        val byName = LinkedHashMap<String, XgControl>()
        val cells = HashMap<String, Element>()
        var section = ""
        for (node in form.select("td.TitleTd, input[name], select[name], textarea[name]")) {
            if (node.hasClass("TitleTd")) {
                section = XgGridReader.normalize(node.text())
                continue
            }
            val control = control(node, section) ?: continue
            node.ancestor { it.tagName() == "td" }?.let { cells.putIfAbsent(control.name, it) }
            val prev = byName[control.name]
            byName[control.name] =
                if (prev != null && prev.kind == control.kind && control.kind in GROUP_KINDS) {
                    merge(prev, control)
                } else {
                    control
                }
        }
        return XgForm(action = action, controls = inheritDependence(byName, cells))
    }

    /**
     * 省市区三个下拉自己没有 `vdependence`，但与它们同格的详细地址有（`IsOut|1`）。
     * 同格的条件就是这一格的条件，按这个继承，不必把字段名写死。
     */
    private fun inheritDependence(
        byName: LinkedHashMap<String, XgControl>,
        cells: Map<String, Element>,
    ): List<XgControl> = byName.values.map { control ->
        if (control.dependence.isNotEmpty()) return@map control
        val cell = cells[control.name] ?: return@map control
        val donor = byName.values.firstOrNull {
            it.name != control.name && it.dependence.isNotEmpty() && cells[it.name] == cell
        }
        if (donor == null) control else control.copy(dependence = donor.dependence)
    }

    private fun control(el: Element, section: String): XgControl? {
        val name = el.attr("name")
        if (name.isEmpty()) return null
        val kind = kindOf(el)
        val meta = metadata(el)
        val length = meta.attr("vlen").split('_')
        val options: List<XgOption>
        val value: String
        when (kind) {
            XgControlKind.SELECT -> {
                options = el.select("option").map {
                    XgOption(it.attr("value"), XgGridReader.normalize(it.text()))
                }
                value = el.select("option[selected]").firstOrNull()?.attr("value")
                    ?: bootstrapSelected(el, options)
                    ?: options.firstOrNull()?.value.orEmpty()
            }
            XgControlKind.RADIO, XgControlKind.CHECKBOX -> {
                options = listOf(XgOption(el.attr("value"), optionLabel(el)))
                value = if (el.hasAttr("checked")) el.attr("value") else ""
            }
            XgControlKind.TEXTAREA -> {
                options = emptyList()
                value = el.wholeText()
            }
            else -> {
                options = emptyList()
                value = el.attr("value")
            }
        }
        return XgControl(
            name = name,
            id = el.id(),
            kind = kind,
            value = value,
            enabled = !el.hasAttr("disabled"),
            readOnly = el.hasAttr("readonly"),
            options = options,
            label = label(el),
            section = section,
            placeholder = el.attr("placeholder"),
            maxLength = length.getOrNull(1)?.toIntOrNull() ?: el.attr("maxlength").toIntOrNull(),
            validators = meta.attr("vtype").split('|').filter { it.isNotEmpty() },
            minLength = length.getOrNull(0)?.toIntOrNull(),
            message = meta.attr("vmsg"),
            dependence = dependence(meta.attr("vdependence")),
            compareOperator = meta.attr("vcompareoperator"),
            compareField = meta.attr("vbecompareelemid"),
            compareMessage = meta.attr("vcomparemsg"),
            dateTime = el.classNames().any { TIME_CLASS.matches(it) },
        )
    }

    private fun merge(prev: XgControl, next: XgControl): XgControl = prev.copy(
        value = next.value.ifEmpty { prev.value },
        options = prev.options + next.options,
        validators = prev.validators.ifEmpty { next.validators },
        message = prev.message.ifEmpty { next.message },
        dependence = prev.dependence.ifEmpty { next.dependence },
    )

    private fun kindOf(el: Element): XgControlKind = when (el.tagName()) {
        "select" -> XgControlKind.SELECT
        "textarea" -> XgControlKind.TEXTAREA
        else -> when (el.attr("type").lowercase()) {
            "hidden" -> XgControlKind.HIDDEN
            "radio" -> XgControlKind.RADIO
            "checkbox" -> XgControlKind.CHECKBOX
            "submit" -> XgControlKind.SUBMIT
            "password" -> XgControlKind.PASSWORD
            // 按钮与文件框都不产生请求项，附件这条链原生不接
            else -> if (el.attr("type").lowercase() in UNUSED_INPUTS) {
                XgControlKind.BUTTON
            } else {
                XgControlKind.TEXT
            }
        }
    }

    /** 单选组的 `vtype` / `vmsg` / `vdependence` 在外层 span 上，往上找到第一个带校验属性的祖先。 */
    private fun metadata(el: Element): Element =
        if (VALIDATOR_ATTRS.any(el::hasAttr)) el
        else el.ancestor { node -> VALIDATOR_ATTRS.any(node::hasAttr) } ?: el

    private fun label(el: Element): String {
        var cell = el.ancestor { it.tagName() == "td" }?.previousElementSibling()
        while (cell != null) {
            if (cell.hasClass("EdtTitleBack")) {
                return XgGridReader.normalize(cell.text()).trimEnd('：', ':').trim()
            }
            cell = cell.previousElementSibling()
        }
        return ""
    }

    private fun optionLabel(el: Element): String {
        val id = el.id()
        if (id.isEmpty()) return ""
        val doc = el.ownerDocument() ?: return ""
        return XgGridReader.normalize(doc.selectFirst("""label[for="$id"]""")?.text().orEmpty())
    }

    /**
     * 省市区下拉的选中项服务端不写 `option[selected]`，而是内联 JS `.val(...)` 设进去，
     * bootstrap-select 再把选中文案镜像到 `button[data-id=<select id>][title=<文案>]`。
     * 没有 JS 的读法只能靠这个 title 反查 value，否则详情/回填里省市区一律读成空。
     */
    private fun bootstrapSelected(el: Element, options: List<XgOption>): String? {
        val id = el.id()
        if (id.isEmpty()) return null
        val doc = el.ownerDocument() ?: return null
        val title = XgGridReader.normalize(doc.selectFirst("""button[data-id="$id"]""")?.attr("title").orEmpty())
        if (title.isEmpty()) return null
        return options.firstOrNull { it.text == title }?.value
    }

    private fun dependence(raw: String): List<XgCondition> =
        raw.split(',').mapNotNull { part ->
            val pair = part.split('|', limit = 2)
            if (pair.size == 2 && pair[0].isNotEmpty()) XgCondition(pair[0].trim(), pair[1].trim()) else null
        }

    private fun Element.ancestor(match: (Element) -> Boolean): Element? {
        var node = parentElement()
        while (node != null) {
            if (match(node)) return node
            node = node.parentElement()
        }
        return null
    }

    private val GROUP_KINDS = setOf(XgControlKind.RADIO, XgControlKind.CHECKBOX)
    private val UNUSED_INPUTS = setOf("button", "reset", "image", "file")
    private val TIME_CLASS = Regex("""time\d+""")
    private val VALIDATOR_ATTRS = listOf(
        "vtype", "vmsg", "vlen", "vdependence",
        "vcomparetype", "vcompareoperator", "vbecompareelemid", "vcomparemsg",
    )
}