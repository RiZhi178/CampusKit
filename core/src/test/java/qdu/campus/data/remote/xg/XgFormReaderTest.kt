package qdu.campus.data.remote.xg

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import qdu.campus.domain.model.XgCondition
import qdu.campus.domain.model.XgControlKind

/**
 * WebForms 表单读取按结构认，不写死字段名。这里用构造样本逐条钉住那些结构约定：
 * 分区、标签、单选组合并、元数据在外层 span、同格依赖继承、浏览器的成功控件取舍。
 */
class XgFormReaderTest {

    private fun read(body: String) =
        XgFormReader.read(Jsoup.parse("""<form action="./Edit.aspx">$body</form>""", PAGE))!!

    @Test
    fun `没有表单时返回 null`() {
        assertNull(XgFormReader.read(Jsoup.parse("<html><body>error</body></html>", PAGE)))
    }

    @Test
    fun `action 解析成绝对地址`() {
        assertEquals("$DIR/Edit.aspx", read("").action)
    }

    @Test
    fun `分区标题与左侧标签按结构认`() {
        val form = read(
            """<table>
               <tr><td class="TitleTd" colspan="2">请假信息</td></tr>
               <tr><td class="EdtTitleBack">监护人姓名：</td>
                   <td><input name="GuarderName" type="text" value="甲"></td></tr>
               </table>""",
        )
        val control = form.control("GuarderName")!!
        assertEquals("请假信息", control.section)
        assertEquals("监护人姓名", control.label)
        assertEquals("甲", control.value)
        assertEquals(XgControlKind.TEXT, control.kind)
    }

    @Test
    fun `单选组合并成一项并取选中值`() {
        val form = read(
            """<span id="IsOut" vtype="R" vmsg="请选择是否离校">
               <input type="radio" name="IsOut" id="IsOut_0" value="1"><label for="IsOut_0">是</label>
               <input type="radio" name="IsOut" id="IsOut_1" value="0" checked><label for="IsOut_1">否</label>
               </span>""",
        )
        val control = form.control("IsOut")!!
        assertEquals(XgControlKind.RADIO, control.kind)
        assertEquals("0", control.value)
        assertEquals(listOf("1", "0"), control.options.map { it.value })
        assertEquals(listOf("是", "否"), control.options.map { it.text })
        assertTrue(control.required)
        assertEquals("请选择是否离校", control.message)
    }

    @Test
    fun `没选中的单选组值为空`() {
        val form = read(
            """<input type="radio" name="R" id="R_0" value="1"><input type="radio" name="R" id="R_1" value="0">""",
        )
        assertEquals("", form.control("R")!!.value)
    }

    @Test
    fun `下拉取 selected 属性否则取首项`() {
        val selected = read(
            """<select name="S"><option value=""></option><option value="3" selected="selected">青岛市内</option></select>""",
        )
        assertEquals("3", selected.control("S")!!.value)
        val first = read("""<select name="S"><option value="a">甲</option><option value="b">乙</option></select>""")
        assertEquals("a", first.control("S")!!.value)
    }

    @Test
    fun `省市区无 selected 时从 bootstrap 按钮 title 反查 value`() {
        val form = read(
            """<select name="Province" id="ApplyInfo1_OutAddressArea_ProvinceDdl">
               <option value=""></option><option value="370000">山东省</option><option value="110000">北京市</option>
               </select>
               <button type="button" data-id="ApplyInfo1_OutAddressArea_ProvinceDdl" title="山东省">山东省</button>""",
        )
        assertEquals("370000", form.control("Province")!!.value)
    }

    @Test
    fun `按钮 title 为空或暂无选中项时回落首项`() {
        val blank = read(
            """<select name="City" id="CityDdl"><option value=""></option><option value="370200">青岛市</option></select>
               <button data-id="CityDdl" title="暂无选中项"></button>""",
        )
        assertEquals("", blank.control("City")!!.value)
    }

    @Test
    fun `有 selected 属性时优先于按钮 title`() {
        val form = read(
            """<select name="Area" id="AreaDdl">
               <option value=""></option><option value="370202" selected="selected">市南区</option><option value="370203">市北区</option>
               </select>
               <button data-id="AreaDdl" title="市北区">市北区</button>""",
        )
        assertEquals("370202", form.control("Area")!!.value)
    }

    @Test
    fun `textarea 取整段原文`() {
        val form = read("""<textarea name="D">第一行
第二行</textarea>""")
        assertEquals("第一行\n第二行", form.control("D")!!.value)
        assertEquals(XgControlKind.TEXTAREA, form.control("D")!!.kind)
    }

    @Test
    fun `disabled 与 readonly 分别标记`() {
        val form = read(
            """<input name="A" type="text" disabled value="x">
               <input name="B" type="text" readonly value="y">""",
        )
        assertFalse(form.control("A")!!.enabled)
        assertFalse(form.control("A")!!.readOnly)
        assertTrue(form.control("B")!!.enabled)
        assertTrue(form.control("B")!!.readOnly)
        assertFalse(form.control("B")!!.editable)
    }

    @Test
    fun `vlen 拆成上下限 maxlength 兜底`() {
        val form = read(
            """<input name="A" type="text" vtype="R" vlen="1_30">
               <input name="B" type="text" maxlength="80">""",
        )
        assertEquals(1, form.control("A")!!.minLength)
        assertEquals(30, form.control("A")!!.maxLength)
        assertEquals(80, form.control("B")!!.maxLength)
        assertNull(form.control("B")!!.minLength)
    }

    @Test
    fun `比较校验元数据读全`() {
        val form = read(
            """<input name="End" type="text" vtype="R|Compare" vcompareoperator="&gt;"
               vbecompareelemid="Begin" vcomparemsg="结束需大于开始" vcomparetype="Date">""",
        )
        val control = form.control("End")!!
        assertEquals(listOf("R", "Compare"), control.validators)
        assertEquals(">", control.compareOperator)
        assertEquals("Begin", control.compareField)
        assertEquals("结束需大于开始", control.compareMessage)
    }

    @Test
    fun `同格的依赖条件继承给省市区下拉`() {
        val form = read(
            """<table><tr><td class="EdtTitleBack">外出地点：</td><td>
               <select name="Province"><option value=""></option></select>
               <select name="City"><option value=""></option></select>
               <input name="Street" type="text" vtype="R" vdependence="IsOut|1">
               </td></tr></table>""",
        )
        assertEquals(listOf(XgCondition("IsOut", "1")), form.control("Street")!!.dependence)
        assertEquals(listOf(XgCondition("IsOut", "1")), form.control("Province")!!.dependence)
        assertEquals(listOf(XgCondition("IsOut", "1")), form.control("City")!!.dependence)
    }

    @Test
    fun `不同格不继承依赖`() {
        val form = read(
            """<table>
               <tr><td><input name="Street" type="text" vdependence="IsOut|1"></td></tr>
               <tr><td><select name="Other"><option value=""></option></select></td></tr>
               </table>""",
        )
        assertTrue(form.control("Other")!!.dependence.isEmpty())
    }

    @Test
    fun `没有 name 的控件不收`() {
        val form = read(
            """<input type="text" value="无名">
               <input type="hidden" value="无名单隐藏">
               <input name="Named" type="text" value="有名">""",
        )
        assertEquals(listOf("Named"), form.controls.map { it.name })
    }

    @Test
    fun `日期类名标记 dateTime`() {
        val form = read("""<input name="T" type="text" class="time1"><input name="U" type="text">""")
        assertTrue(form.control("T")!!.dateTime)
        assertFalse(form.control("U")!!.dateTime)
    }

    @Test
    fun `按钮与文件框归为 BUTTON 不产生请求项`() {
        val form = read(
            """<input name="Up" type="button" value="上传"><input name="F" type="file">""",
        )
        assertEquals(XgControlKind.BUTTON, form.control("Up")!!.kind)
        assertEquals(XgControlKind.BUTTON, form.control("F")!!.kind)
    }

    private companion object {
        const val DIR = "https://xg.qdu.edu.cn/apps/DailyLeave/Features/DailyLeave"
        const val PAGE = "$DIR/StuApply_Edit.aspx"
    }
}