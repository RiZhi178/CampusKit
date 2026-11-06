package qdu.campus.data.remote.xg

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import qdu.campus.domain.model.XgGrid

/**
 * 学工评奖评优页的解析。
 *
 * 真实样本只到「批次开着、本人 0 条记录」这一层，表格有数据时的列名还没录到，
 * 所以表格部分用构造样本断言结构（有表头认列、没表头只认行），
 * 等第一次真跑出记录时再补一条真实样本进来。
 */
class XgAwardParserTest {

    private val realHtml: String by lazy {
        checkNotNull(javaClass.getResourceAsStream("/xg/schoolsapply.2026-11.html"))
            .bufferedReader().use { it.readText() }
    }

    private val page by lazy { XgAwardParser.parse(realHtml, PAGE) }

    @Test
    fun `真实批次表给出批次与窗口`() {
        val batch = requireNotNull(page.batch)
        assertEquals("2026年研究生新生学业奖学金(硕士 博士)", batch.name)
        assertEquals("2025-2026学年", batch.year)
        assertEquals("2026-10-27至2026-11-15", batch.dateRange)
        assertEquals("已开始,可以提交申请", batch.status)
    }

    @Test
    fun `批次下拉里的其它批次只作提示`() {
        val batch = requireNotNull(page.batch)
        assertEquals(1, batch.otherNames.size)
        assertTrue(batch.otherNames[0].contains("优秀学业奖学金"))
    }

    @Test
    fun `奖项下拉与分区标题`() {
        assertEquals(listOf("研究生新生学业奖学金"), page.batch?.awardOptions)
        assertEquals("学校奖学金", page.caption)
    }

    @Test
    fun `零条记录认得出是空表而不是解析失败`() {
        assertEquals(0, page.totalRecords)
        assertTrue(page.grids.isEmpty())
        assertTrue(page.records.isEmpty())
        assertTrue(page.hasContent)
    }

    @Test
    fun `通用表格按表头认列`() {
        val grid = grid(
            """<tr><th>奖项名称</th><th>申请时间</th><th>审核状态</th></tr>
               <tr><td>新生学业奖学金</td><td>2026-11-01</td><td>待学院审核</td></tr>
               <tr><td>单项奖学金</td><td>2026-11-02</td><td>通过</td></tr>""",
        )
        assertEquals(listOf("奖项名称", "申请时间", "审核状态"), grid.headers)
        assertEquals(2, grid.rows.size)
        assertEquals("待学院审核", grid.rows[0][2])
    }

    @Test
    fun `行宽不齐时按表头补齐`() {
        val grid = grid(
            """<tr><th>甲</th><th>乙</th><th>丙</th></tr>
               <tr><td>一</td><td>二</td></tr>""",
        )
        assertEquals(listOf(listOf("一", "二", "")), grid.normalizedRows)
    }

    @Test
    fun `空表的横跨整行提示不算记录`() {
        val doc = doc(
            """<table id="P1_GridView1"><tr><td colspan="9">未找到相关数据</td></tr></table>""",
        )
        assertTrue(XgGridReader.read(doc).isEmpty())
    }

    @Test
    fun `没有表头行时列名留空但行照收`() {
        val grid = grid("""<tr><td>甲</td><td>乙</td></tr>""")
        assertTrue(grid.headers.isEmpty())
        assertEquals(listOf("甲", "乙"), grid.rows.single())
    }

    @Test
    fun `nbsp 与换行折成单个空格`() {
        val grid = grid(
            """<tr><th>楼栋</th></tr><tr><td>&nbsp;怡山&nbsp;&nbsp;1
               号楼</td></tr>""",
        )
        assertEquals("怡山 1 号楼", grid.rows.single().single())
    }

    @Test
    fun `会话没建立时拿到的登录页判不成业务页`() {
        val login = """
            <html><body><form method="post" action="/authserver/login">
            <input type="text" name="username"/><input type="password" name="password"/>
            </form></body></html>
        """.trimIndent()
        val parsed = XgAwardParser.parse(login, "https://authserver.qdu.edu.cn/authserver/login")
        assertFalse(parsed.hasContent)
        assertNull(parsed.totalRecords)
    }

    @Test
    fun `空页面不抛异常`() {
        val parsed = XgAwardParser.parse("", PAGE)
        assertFalse(parsed.hasContent)
        assertNull(parsed.totalRecords)
    }

    /** 构造一张 `GridView`：只关心读取器认不认结构，页面外壳留给真实样本。 */
    private fun grid(rows: String): XgGrid =
        XgGridReader.read(doc("""<table id="StuApplyPage1_GridView1">$rows</table>""")).single()

    private fun doc(html: String) = Jsoup.parse(html, PAGE)

    private companion object {
        const val PAGE = "https://xg.qdu.edu.cn/apps/PJPY/Features/AppraiseAward/XSCenter/StuSchoolApply.aspx"
    }
}
