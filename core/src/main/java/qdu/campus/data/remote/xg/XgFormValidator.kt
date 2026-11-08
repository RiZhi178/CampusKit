package qdu.campus.data.remote.xg

import qdu.campus.domain.model.XgControl
import qdu.campus.domain.model.XgControlKind
import qdu.campus.domain.model.XgFieldError
import qdu.campus.domain.model.XgForm

/**
 * 按标签上的校验元数据检查表单，不写死字段名。
 *
 * 站点自己的 `Zove_FormValidator` 读的就是这几条：`vtype` 里的 R / Compare / Phone / LinkTel /
 * OrderNum，`vlen` 的长度区间，`vmsg` 的提示语，`vdependence` 决定这条现在算不算数，
 * `vcompareoperator` + `vbecompareelemid` + `vcomparemsg` 是两个日期字段之间的比较。
 * 服务端也校验同一套（监护人留空真点提交，回的就是 `vmsg` 那句「请填写监护人姓名」），
 * 所以这里只是省一次往返，不是唯一防线。
 *
 * 页面另有一段手写 `SaveValid()`，其中「外出返程不得晚于请假结束」没有对应的标签属性，
 * 这里不凭空补规则，交给服务端回话。
 */
object XgFormValidator {

    fun validate(form: XgForm, values: Map<String, String>): List<XgFieldError> =
        form.controls.mapNotNull { control ->
            if (!checkable(control)) return@mapNotNull null
            if (!isActive(control, form, values)) return@mapNotNull null
            val value = values[control.name] ?: control.value
            firstError(control, value, form, values)?.let {
                XgFieldError(control.name, control.label, it)
            }
        }

    /** 依赖条件全部满足才生效：`IsOut|1` 说的是「是否离校」选了「是」。 */
    fun isActive(control: XgControl, form: XgForm, values: Map<String, String>): Boolean =
        control.dependence.all { condition ->
            val target = form.byIdSuffix(condition.field) ?: return@all false
            (values[target.name] ?: target.value) == condition.value
        }

    private fun checkable(control: XgControl): Boolean =
        control.enabled && control.validators.isNotEmpty() && control.kind in CHECKABLE

    private fun firstError(
        control: XgControl,
        value: String,
        form: XgForm,
        values: Map<String, String>,
    ): String? {
        if (control.required && value.isBlank()) {
            return control.message.ifEmpty {
                (if (control.options.isEmpty()) "请填写" else "请选择") + control.label
            }
        }
        if (value.isBlank()) return null
        val min = control.minLength
        val max = control.maxLength
        if (min != null && value.length < min) return rangeMessage(control, min, max)
        if (max != null && value.length > max) return rangeMessage(control, min, max)
        for (validator in control.validators) {
            val error = when (validator) {
                "Phone" -> if (MOBILE.matches(value)) null else "${control.label}应为 11 位手机号"
                "LinkTel" -> if (TELEPHONE.matches(value)) null else "${control.label}应为手机号或区号-号码"
                "OrderNum" -> if (DIGITS.matches(value)) null else "${control.label}应为整数"
                "Compare" -> compareError(control, value, form, values)
                else -> null
            }
            if (error != null) return error
        }
        return null
    }

    private fun rangeMessage(control: XgControl, min: Int?, max: Int?): String = when {
        min != null && max != null -> "${control.label}需 $min ~ $max 字"
        max != null -> "${control.label}不能超过 $max 字"
        min != null -> "${control.label}不能少于 $min 字"
        else -> "${control.label}长度不正确"
    }

    private fun compareError(
        control: XgControl,
        value: String,
        form: XgForm,
        values: Map<String, String>,
    ): String? {
        if (control.compareField.isEmpty()) return null
        val target = form.byIdSuffix(control.compareField) ?: return null
        val other = values[target.name] ?: target.value
        val left = XgLeaveTime.parse(value) ?: return null
        val right = XgLeaveTime.parse(other) ?: return null
        val cmp = left.compareTo(right)
        val failed = when (control.compareOperator) {
            ">" -> cmp <= 0
            ">=" -> cmp < 0
            "<" -> cmp >= 0
            "<=" -> cmp > 0
            else -> false
        }
        return if (failed) control.compareMessage.ifEmpty { "${control.label}与${target.label}先后顺序不正确" } else null
    }

    private val CHECKABLE = setOf(
        XgControlKind.TEXT, XgControlKind.TEXTAREA, XgControlKind.PASSWORD,
        XgControlKind.SELECT, XgControlKind.RADIO, XgControlKind.CHECKBOX,
    )
    private val MOBILE = Regex("""1[3-9]\d{9}""")
    private val TELEPHONE = Regex("""(?:1[3-9]\d{9}|0\d{2,3}-?\d{7,8})""")
    private val DIGITS = Regex("""\d+""")
}