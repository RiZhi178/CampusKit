package qdu.campus.data.remote.xg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日常请假三个页面的解析，全部对着 2026-11-07 真跑后脱敏的样本断言。
 *
 * 样本是浏览器跑完脚本后的 DOM 快照：省市区三级下拉的选中值由内联脚本 `.val()` 设进 property，
 * 不落 `selected` 属性，所以 jsoup 读出来是空串——这一点与原生 OkHttp 拿到的原始响应一致
 * （服务端本就不在这三个下拉上渲染 selected，靠那段脚本回填），不是脱敏带来的失真。
 */
class XgLeaveParserTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/xg/$name")).bufferedReader().use { it.readText() }

    private val list by lazy { XgLeaveParser.list(fixture("leave-list.2026-11.html"), LIST) }
    private val empty by lazy { XgLeaveParser.list(fixture("leave-list-empty.2026-11.html"), LIST) }
    private val form by lazy { XgLeaveParser.form(fixture("leave-form.2026-11.html"), EDIT) }
    private val out by lazy { XgLeaveParser.form(fixture("leave-form-out.2026-11.html"), EDIT) }
    private val detail by lazy { XgLeaveParser.form(fixture("leave-detail.2026-11.html"), "$EDIT?Id=$ID") }

    @Test
    fun `有在途申请时列表给出门禁与一条记录`() {
        assertTrue(list.hasContent)
        assertFalse(list.canApply)
        assertEquals("当前状态不能请假或有请假未完成！", list.blockedMessage)
        assertEquals("日常请假列表", list.caption)
        assertEquals(1, list.totalRecords)
        assertEquals(1, list.records.size)
    }

    @Test
    fun `列表表头是六个 td 而不是 th`() {
        assertEquals(6, list.headers.size)
        assertEquals("请假时间", list.headers.first())
        assertEquals("审批状态", list.headers[4])
    }

    @Test
    fun `记录读出 id 详情地址与行内撤销按钮`() {
        val record = list.records.single()
        assertEquals(ID, record.id)
        assertEquals("事假", record.cells[2])
        assertEquals("等待辅导员审批", record.cells[4])
        assertEquals("$EDIT?Id=$ID", record.detailUrl)
        assertEquals("GridView1\$ctl02\$DelBtn", record.revokeButton)
        assertEquals("您确定要撤销这条申请记录吗？", record.revokeConfirm)
        assertEquals(setOf(ID), list.recordIds)
    }

    @Test
    fun `单页时上下页按钮都禁用`() {
        assertNotNull(list.prevPage)
        assertNotNull(list.nextPage)
        assertFalse(list.prevPage!!.enabled)
        assertFalse(list.nextPage!!.enabled)
    }

    @Test
    fun `零条记录是空表而不是解析失败`() {
        assertTrue(empty.hasContent)
        assertTrue(empty.canApply)
        assertEquals(EDIT, empty.applyUrl)
        assertEquals("", empty.blockedMessage)
        assertEquals(0, empty.totalRecords)
        assertTrue(empty.headers.isEmpty())
        assertTrue(empty.records.isEmpty())
    }

    @Test
    fun `新建表单认出提交目标与服务端拒绝文案`() {
        assertTrue(form.hasContent)
        assertTrue(form.isNew)
        assertFalse(form.blocked)
        assertEquals("Save", form.submitTarget)
        assertEquals("提交", form.submitLabel)
        assertEquals("请填写监护人姓名", form.message)
        assertFalse(form.canRevoke)
        assertNull(form.revokeSubmit)
    }

    @Test
    fun `监护人留空那条样本里监护人字段确实是空的`() {
        assertEquals("", form.form.control("ApplyInfo1\$GuarderName")?.value)
        assertEquals("", form.form.control("ApplyInfo1\$GuarderTel")?.value)
        assertEquals("0", form.form.control("ApplyInfo1\$IsOut")?.value)
    }

    @Test
    fun `学生基本信息与分区都读得到`() {
        assertTrue(form.student.any { it.label == "学号" })
        assertTrue(form.student.any { it.label == "姓名" })
        val titles = form.sections.map { it.title }
        assertTrue(titles.contains("请假信息"))
        assertTrue(titles.contains("外出信息"))
        assertTrue(form.sections.first { it.title == "请假信息" }.fields.isNotEmpty())
    }

    @Test
    fun `提交成功样本停在原页只多一句操作成功`() {
        assertEquals("操作成功！", out.message)
        assertTrue(out.isNew)
        assertEquals("1", out.form.control("ApplyInfo1\$IsOut")?.value)
        assertEquals("3", out.form.control("ApplyInfo1\$GoOutRange")?.value)
    }

    @Test
    fun `详情页认出记录号审批流与详情撤销按钮`() {
        assertTrue(detail.hasContent)
        assertFalse(detail.isNew)
        assertEquals(ID, detail.recordId)
        assertEquals("", detail.message)
        assertTrue(detail.canRevoke)
        assertEquals("你确认要撤销申请吗?", detail.revokeConfirm)
        val revoke = requireNotNull(detail.revokeSubmit)
        assertEquals("DelSave", revoke.name)
        assertEquals("Button", revoke.value)
    }

    @Test
    fun `审批进度按服务端原文读出`() {
        val approval = requireNotNull(detail.approval)
        assertEquals("等待辅导员审批", approval.status)
        assertEquals(listOf("学生申请", "辅导员", "学院", "结束"), approval.steps)
        assertEquals(1, approval.records.size)
    }

    @Test
    fun `只认 layer_alert 不认页面静态的 layer_msg`() {
        assertEquals("", XgLeaveParser.message("<script>layer.msg('该状态不能打印！');</script>"))
        assertEquals(
            "操作成功！",
            XgLeaveParser.message("""window.onload=function(){layer.alert("操作成功！",{icon:1})}"""),
        )
        assertEquals(
            "请填写监护人姓名",
            XgLeaveParser.message("""layer.alert('请填写监护人姓名',{icon:2,closeBtn:0},function(){})"""),
        )
    }

    @Test
    fun `没有表单的错误页与空页都判不成业务页`() {
        val noAuth = XgLeaveParser.form("<html><body>error</body></html>", EDIT)
        assertFalse(noAuth.hasContent)
        val blank = XgLeaveParser.form("", EDIT)
        assertFalse(blank.hasContent)
        assertFalse(XgLeaveParser.list("", LIST).hasContent)
    }

    private companion object {
        const val LIST = "https://xg.qdu.edu.cn/apps/DailyLeave/Features/DailyLeave/StuApply.aspx"
        const val EDIT = "https://xg.qdu.edu.cn/apps/DailyLeave/Features/DailyLeave/StuApply_Edit.aspx"
        const val ID = "00000000-0000-0000-0000-000000000001"
    }
}