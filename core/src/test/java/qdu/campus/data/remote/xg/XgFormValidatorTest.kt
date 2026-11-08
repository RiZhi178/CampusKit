package qdu.campus.data.remote.xg

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import qdu.campus.domain.model.XgForm

/**
 * 校验只读标签上的元数据，不写死字段名；服务端也校验同一套，这里只是省一次往返。
 * 依赖条件（vdependence）决定一条规则现在算不算数，比较校验（Compare）按日期先后判。
 */
class XgFormValidatorTest {

    private fun form(body: String): XgForm =
        XgFormReader.read(Jsoup.parse("""<form action="./Edit.aspx">$body</form>""", PAGE))!!

    private fun errors(form: XgForm, edits: Map<String, String> = emptyMap()) =
        XgFormValidator.validate(form, form.values(edits))

    @Test
    fun `必填留空回服务端那句 vmsg`() {
        val form = form("""<table><tr><td class="EdtTitleBack">监护人姓名：</td>
            <td><input name="GuarderName" type="text" vtype="R" vmsg="请填写监护人姓名"></td></tr></table>""")
        val error = errors(form).single()
        assertEquals("GuarderName", error.name)
        assertEquals("监护人姓名", error.label)
        assertEquals("请填写监护人姓名", error.message)
    }

    @Test
    fun `没有 vmsg 时按控件类型补默认提示`() {
        val text = form("""<table><tr><td class="EdtTitleBack">详细说明：</td>
            <td><input name="D" type="text" vtype="R"></td></tr></table>""")
        assertEquals("请填写详细说明", errors(text).single().message)
        val select = form("""<table><tr><td class="EdtTitleBack">范围：</td>
            <td><select name="S" vtype="R"><option value=""></option></select></td></tr></table>""")
        assertEquals("请选择范围", errors(select).single().message)
    }

    @Test
    fun `填了就不报必填`() {
        val form = form("""<input name="A" type="text" vtype="R" vmsg="必填">""")
        assertTrue(errors(form, mapOf("A" to "有值")).isEmpty())
    }

    @Test
    fun `手机号与电话格式`() {
        val phone = form("""<input name="P" type="text" vtype="R|Phone" vmsg="请填写电话">""")
        assertTrue(errors(phone, mapOf("P" to "13800138000")).isEmpty())
        assertEquals(1, errors(phone, mapOf("P" to "12345")).size)
        val tel = form("""<input name="T" type="text" vtype="LinkTel">""")
        assertTrue(errors(tel, mapOf("T" to "0532-8888888")).isEmpty())
        assertTrue(errors(tel, mapOf("T" to "13800138000")).isEmpty())
        assertEquals(1, errors(tel, mapOf("T" to "abc")).size)
    }

    @Test
    fun `非必填空值不触发格式校验`() {
        val form = form("""<input name="T" type="text" vtype="LinkTel">""")
        assertTrue(errors(form, mapOf("T" to "")).isEmpty())
    }

    @Test
    fun `整数校验`() {
        val form = form("""<input name="N" type="text" vtype="R|OrderNum">""")
        assertTrue(errors(form, mapOf("N" to "0")).isEmpty())
        assertEquals(1, errors(form, mapOf("N" to "1.5")).size)
    }

    @Test
    fun `长度区间`() {
        val form = form("""<table><tr><td class="EdtTitleBack">说明：</td>
            <td><input name="D" type="text" vtype="R" vlen="2_5"></td></tr></table>""")
        assertEquals(1, errors(form, mapOf("D" to "一")).size)
        assertEquals(1, errors(form, mapOf("D" to "一二三四五六")).size)
        assertTrue(errors(form, mapOf("D" to "一二三")).isEmpty())
    }

    @Test
    fun `结束需大于开始`() {
        val form = form(
            """<input name="Begin" id="ApplyInfo1_Begin" type="text" value="2026-11-07 22:00">
               <input name="End" id="ApplyInfo1_End" type="text" vtype="Compare"
                 vcompareoperator="&gt;" vbecompareelemid="Begin" vcomparemsg="结束需大于开始" value="">""",
        )
        assertEquals("结束需大于开始", errors(form, mapOf("End" to "2026-11-07 22:00")).single().message)
        assertTrue(errors(form, mapOf("End" to "2026-11-07 23:00")).isEmpty())
    }

    @Test
    fun `大于等于时相等也通过`() {
        val form = form(
            """<input name="Begin" id="ApplyInfo1_Begin" type="text" value="2026-11-07 22:00">
               <input name="End" id="ApplyInfo1_End" type="text" vtype="Compare"
                 vcompareoperator="&gt;=" vbecompareelemid="Begin" value="">""",
        )
        assertTrue(errors(form, mapOf("End" to "2026-11-07 22:00")).isEmpty())
        assertEquals(1, errors(form, mapOf("End" to "2026-11-07 21:00")).size)
    }

    @Test
    fun `不离校时外出字段整段不校验`() {
        val form = form(
            """<input name="IsOut" id="ApplyInfo1_IsOut" type="text" value="0">
               <input name="Street" type="text" vtype="R" vdependence="IsOut|1" vmsg="请填写外出地点">""",
        )
        assertTrue(errors(form).isEmpty())
        assertFalse(XgFormValidator.isActive(form.control("Street")!!, form, form.values()))
    }

    @Test
    fun `离校时外出字段必填生效`() {
        val form = form(
            """<input name="IsOut" id="ApplyInfo1_IsOut" type="text" value="1">
               <input name="Street" type="text" vtype="R" vdependence="IsOut|1" vmsg="请填写外出地点">""",
        )
        assertEquals("请填写外出地点", errors(form).single().message)
        assertTrue(XgFormValidator.isActive(form.control("Street")!!, form, form.values()))
        assertTrue(errors(form, mapOf("Street" to "测试地址")).isEmpty())
    }

    @Test
    fun `禁用控件不校验`() {
        val form = form("""<input name="A" type="text" vtype="R" vmsg="必填" disabled>""")
        assertTrue(errors(form).isEmpty())
    }

    private companion object {
        const val PAGE = "https://xg.qdu.edu.cn/apps/DailyLeave/Features/DailyLeave/StuApply_Edit.aspx"
    }
}