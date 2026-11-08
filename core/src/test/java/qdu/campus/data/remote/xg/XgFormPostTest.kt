package qdu.campus.data.remote.xg

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import qdu.campus.domain.model.XgForm

/**
 * 回发名值对照浏览器「成功控件」口径。三种写操作触发方式都在这里钉住：
 * 链接 postback 走 [XgFormPost.pairs] 的 eventTarget，行内/详情撤销走 clickedSubmit。
 */
class XgFormPostTest {

    private fun form(body: String): XgForm =
        XgFormReader.read(Jsoup.parse("""<form action="./Edit.aspx">$body</form>""", PAGE))!!

    private fun pairs(form: XgForm, target: String = "", clicked: String = "", overrides: Map<String, String> = emptyMap()) =
        XgFormPost.pairs(form, eventTarget = target, clickedSubmit = clicked, overrides = overrides).toMap()

    @Test
    fun `隐藏底座原样回带`() {
        val form = form("""<input type="hidden" name="__VIEWSTATE" value="VS">
                           <input type="hidden" name="__EVENTVALIDATION" value="EV">""")
        val out = pairs(form)
        assertEquals("VS", out["__VIEWSTATE"])
        assertEquals("EV", out["__EVENTVALIDATION"])
    }

    @Test
    fun `禁用不发只读照发空文本发空串`() {
        val form = form(
            """<input name="Off" type="text" value="x" disabled>
               <input name="Ro" type="text" value="y" readonly>
               <input name="Empty" type="text" value="">""",
        )
        val out = pairs(form)
        assertFalse(out.containsKey("Off"))
        assertEquals("y", out["Ro"])
        assertEquals("", out["Empty"])
    }

    @Test
    fun `单选多选只发选中的`() {
        val form = form(
            """<input type="radio" name="R" value="1"><input type="radio" name="R" value="0" checked>
               <input type="checkbox" name="C" value="on">
               <input type="checkbox" name="D" value="on" checked>""",
        )
        val out = pairs(form)
        assertEquals("0", out["R"])
        assertFalse(out.containsKey("C"))
        assertEquals("on", out["D"])
    }

    @Test
    fun `只发被点中的那个 submit`() {
        val form = form(
            """<input type="submit" name="DelBtn" value="撤销申请">
               <input type="submit" name="Other" value="其它">""",
        )
        val out = pairs(form, clicked = "DelBtn")
        assertEquals("撤销申请", out["DelBtn"])
        assertFalse(out.containsKey("Other"))
        assertFalse(pairs(form).containsKey("DelBtn"))
    }

    @Test
    fun `详情撤销发 DelSave 等于 Button`() {
        val form = form("""<input type="submit" name="DelSave" value="Button">""")
        assertEquals("Button", pairs(form, clicked = "DelSave")["DelSave"])
    }

    @Test
    fun `按钮与文件框永不发`() {
        val form = form("""<input name="Up" type="button" value="上传"><input name="F" type="file">""")
        val out = pairs(form, clicked = "Up")
        assertFalse(out.containsKey("Up"))
        assertFalse(out.containsKey("F"))
    }

    @Test
    fun `编辑页里 EVENTTARGET 被覆盖`() {
        val form = form(
            """<input type="hidden" name="__EVENTTARGET" value="">
               <input type="hidden" name="__EVENTARGUMENT" value="">""",
        )
        val out = pairs(form, target = "Save")
        assertEquals("Save", out["__EVENTTARGET"])
        assertEquals("", out["__EVENTARGUMENT"])
    }

    @Test
    fun `省市区联动用下拉名当 EVENTTARGET`() {
        val form = form(
            """<input type="hidden" name="__EVENTTARGET" value="">
               <input type="hidden" name="__EVENTARGUMENT" value="">
               <select name="ApplyInfo1${'$'}OutAddressArea${'$'}ProvinceDdl"><option value="370000" selected>山东省</option></select>""",
        )
        val target = "ApplyInfo1\$OutAddressArea\$ProvinceDdl"
        val out = pairs(form, target = target)
        assertEquals(target, out["__EVENTTARGET"])
        assertEquals("370000", out[target])
    }

    @Test
    fun `列表页没有 EVENTTARGET 时补在最前`() {
        val form = form("""<input type="hidden" name="__VIEWSTATE" value="VS">
                           <input type="submit" name="GridView1${'$'}ctl02${'$'}DelBtn" value="撤销申请">""")
        val out = XgFormPost.pairs(form, eventTarget = "Save", clickedSubmit = "GridView1\$ctl02\$DelBtn")
        assertEquals("__EVENTTARGET", out.first().first)
        assertEquals("Save", out.first().second)
        assertEquals("__EVENTARGUMENT", out[1].first)
        assertEquals("撤销申请", out.toMap()["GridView1\$ctl02\$DelBtn"])
    }

    @Test
    fun `用户改动覆盖当前值`() {
        val form = form("""<input name="Reason" type="text" value="旧">""")
        assertEquals("新", pairs(form, overrides = mapOf("Reason" to "新"))["Reason"])
    }

    @Test
    fun `不发没有触发目标的空 EVENTTARGET`() {
        val form = form("""<input type="hidden" name="__VIEWSTATE" value="VS">""")
        val out = XgFormPost.pairs(form)
        assertTrue(out.none { it.first == "__EVENTTARGET" })
    }

    private companion object {
        const val PAGE = "https://xg.qdu.edu.cn/apps/DailyLeave/Features/DailyLeave/StuApply_Edit.aspx"
    }
}