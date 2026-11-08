package qdu.campus.data.remote.xg

import qdu.campus.domain.model.XgControl
import qdu.campus.domain.model.XgControlKind
import qdu.campus.domain.model.XgForm

/**
 * 把表单拼成 `application/x-www-form-urlencoded` 的有序名值对，口径照浏览器的「成功控件」：
 *
 * - 没有 name、`disabled`、没被选中的单选/多选、未被点中的 submit 都不发；
 * - `readonly` 照发（浏览器就发，服务端对 ReadOnly 的 TextBox 用自己的视图状态，不收这份值）；
 * - 空文本框也发，发空串；
 * - 被点中的那个 submit 发它自己的 name=value，这是站点三种写操作里的两种
 *   （列表行内撤销 `GridView1$ctlNN$DelBtn=撤销申请`、详情撤销 `DelSave=Button`）；
 * - 第三种是链接触发的 `__doPostBack`，靠 [eventTarget]（提交是 `Save`，省市区联动是那个 select 的 name）。
 *
 * `__VIEWSTATE` 实测约 70 万字符，必须原样回带；它就在控件清单里，不需要特殊照顾。
 */
object XgFormPost {

    fun pairs(
        form: XgForm,
        eventTarget: String = "",
        eventArgument: String = "",
        clickedSubmit: String = "",
        overrides: Map<String, String> = emptyMap(),
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(form.controls.size + 2)
        var hasTarget = false
        var hasArgument = false
        for (control in form.controls) {
            when (control.name) {
                EVENT_TARGET -> {
                    hasTarget = true
                    out += control.name to eventTarget.ifEmpty { control.value }
                }
                EVENT_ARGUMENT -> {
                    hasArgument = true
                    out += control.name to eventArgument.ifEmpty { control.value }
                }
                else -> valueOf(control, clickedSubmit, overrides)?.let { out += control.name to it }
            }
        }
        // 列表页的表单里没有这两个隐藏字段，联动与提交只发生在编辑页；真要在别的页面上
        // 触发 postback 时得自己补上，否则服务端认不出是谁触发的。
        if (eventTarget.isNotEmpty()) {
            if (!hasTarget) out.add(0, EVENT_TARGET to eventTarget)
            if (!hasArgument) out.add(minOf(1, out.size), EVENT_ARGUMENT to eventArgument)
        }
        return out
    }

    private fun valueOf(
        control: XgControl,
        clickedSubmit: String,
        overrides: Map<String, String>,
    ): String? {
        if (!control.enabled) return null
        val value = overrides[control.name] ?: control.value
        return when (control.kind) {
            XgControlKind.HIDDEN,
            XgControlKind.TEXT,
            XgControlKind.TEXTAREA,
            XgControlKind.PASSWORD,
            XgControlKind.SELECT,
            -> value
            XgControlKind.RADIO, XgControlKind.CHECKBOX -> value.takeIf { it.isNotEmpty() }
            XgControlKind.SUBMIT -> value.takeIf { control.name == clickedSubmit }
            XgControlKind.BUTTON -> null
        }
    }

    private const val EVENT_TARGET = "__EVENTTARGET"
    private const val EVENT_ARGUMENT = "__EVENTARGUMENT"
}