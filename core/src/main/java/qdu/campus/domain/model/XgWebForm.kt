package qdu.campus.domain.model

/**
 * 学工系统（ASP.NET WebForms）表单里的一个控件。
 *
 * 这个站点的写操作全是「整表单回发」：没有 REST 接口，提交、撤销、下拉联动都要把
 * `__VIEWSTATE` 那一套隐藏字段原样带回去，再按触发方式补一个 `__EVENTTARGET`
 * 或者被点中的 submit 按钮名值对。所以控件不只是界面要渲染的东西，它同时就是请求体本身，
 * 校验规则也长在标签属性上（`vtype` / `vlen` / `vmsg` / `vdependence` / `vcompare*`），
 * 这里一并读出来，界面与校验都不必写死字段名。
 */
enum class XgControlKind {
    HIDDEN, TEXT, TEXTAREA, PASSWORD, SELECT, RADIO, CHECKBOX, SUBMIT, BUTTON,
}

/** 下拉的一项，或者单选组里的一项。value 是回发给服务端的码，text 是给人看的原文。 */
data class XgOption(val value: String, val text: String)

/**
 * `vdependence="IsOut|1,IsCompanion|1"` 的一项：列出的控件都取到指定值，本控件才生效。
 * [field] 是服务端写的 id 后缀（`IsOut`），不是完整 name。
 */
data class XgCondition(val field: String, val value: String)

data class XgControl(
    val name: String,
    val id: String,
    val kind: XgControlKind,
    /** 当前值。单选组取选中那一项，没选中就是空串。 */
    val value: String,
    val enabled: Boolean,
    val readOnly: Boolean = false,
    val options: List<XgOption> = emptyList(),
    /** 同一行左侧标签格的原文，去掉了结尾的冒号。 */
    val label: String = "",
    /** 所属分区标题（`td.TitleTd`），服务端给几段就分几段。 */
    val section: String = "",
    val placeholder: String = "",
    val maxLength: Int? = null,
    /** `vtype` 按竖线拆开：R 必填、Compare 比较、Phone / LinkTel 电话、OrderNum 整数。 */
    val validators: List<String> = emptyList(),
    val minLength: Int? = null,
    val message: String = "",
    val dependence: List<XgCondition> = emptyList(),
    val compareOperator: String = "",
    val compareField: String = "",
    val compareMessage: String = "",
    /** 站点用 `class="time1"` 这几个类给 laydate 绑定日期时间选择器。 */
    val dateTime: Boolean = false,
) {
    val required: Boolean get() = REQUIRED in validators

    /** 界面上能不能改。隐藏字段、按钮、只读计数框都不算。 */
    val editable: Boolean
        get() = enabled && !readOnly && kind in EDITABLE

    companion object {
        private const val REQUIRED = "R"
        private val EDITABLE = setOf(
            XgControlKind.TEXT, XgControlKind.TEXTAREA, XgControlKind.PASSWORD,
            XgControlKind.SELECT, XgControlKind.RADIO, XgControlKind.CHECKBOX,
        )
    }
}

/**
 * 一张 WebForms 表单。[action] 已按页面地址解析成绝对地址。
 *
 * [controls] 保持文档顺序，单选组合并成一项；没有 name 的控件不收——浏览器本来就不发它们。
 */
data class XgForm(
    val action: String,
    val controls: List<XgControl>,
) {
    fun control(name: String): XgControl? = controls.firstOrNull { it.name == name }

    fun submit(name: String): XgControl? =
        controls.firstOrNull { it.name == name && it.kind == XgControlKind.SUBMIT }

    /** 比较校验与依赖条件都按 id 后缀指路（`LeaveBeginTime` 指的是 `ApplyInfo1_LeaveBeginTime`）。 */
    fun byIdSuffix(suffix: String): XgControl? =
        controls.firstOrNull { it.id == suffix || it.id.endsWith("_$suffix") }

    /** 当前值叠加用户改动后的取值表，校验与回发都用它。 */
    fun values(edits: Map<String, String> = emptyMap()): Map<String, String> =
        controls.associate { it.name to (edits[it.name] ?: it.value) }
}