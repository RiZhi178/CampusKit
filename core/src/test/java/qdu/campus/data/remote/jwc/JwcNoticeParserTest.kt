package qdu.campus.data.remote.jwc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import qdu.campus.data.remote.graduate.NoticeParser

/**
 * 样本来自 2026-11-05 的真实抓取（`core/src/test/resources/jwc/`），
 * 契约与结论见 `docs/interfaces/jwc-notice.md`。
 */
class JwcNoticeParserTest {

    private fun read(name: String): String =
        javaClass.getResourceAsStream("/jwc/$name")!!
            .readBytes().toString(Charsets.UTF_8)

    private val baseUrl = "https://jwc.qdu.edu.cn/"

    @Test
    fun `第一页抽到十五条且按页面顺序排列`() {
        val notices = JwcNoticeParser().parse(read("notice-list.2026-11-05.p1.html"), "${baseUrl}jwtz.htm")
        assertEquals(15, notices.size)
        assertEquals("2026-11-05", notices[0].date)
        assertEquals("关于青岛大学第七届教师教学创新大赛有关事项的补充通知", notices[0].title)
        assertTrue(notices[0].url.startsWith(baseUrl))
        assertEquals("2026-10-15", notices.last().date)
    }

    @Test
    fun `第二三页与末页各十五条与十四条`() {
        val p2 = JwcNoticeParser().parse(read("notice-list.2026-11-05.p2.html"), "${baseUrl}jwtz/92.htm")
        val p3 = JwcNoticeParser().parse(read("notice-list.2026-11-05.p3.html"), "${baseUrl}jwtz/91.htm")
        val p93 = JwcNoticeParser().parse(read("notice-list.2026-11-05.p93.html"), "${baseUrl}jwtz/1.htm")
        assertEquals(15, p2.size)
        assertEquals(15, p3.size)
        assertEquals(14, p93.size)
        // 末页链接全部是 content.jsp 形态，日期已到 2020 年
        assertEquals("2020-11-26", p93[0].date)
    }

    @Test
    fun `标题清洗去尾空格与连续空白`() {
        // 第 1 页第 14 条的 title 属性带尾部空格，实测存在
        val notices = JwcNoticeParser().parse(read("notice-list.2026-11-05.p1.html"), "${baseUrl}jwtz.htm")
        val flagged = notices.first { it.title == "关于开展本科教学文档专项检查活动的通知" }
        assertEquals("关于开展本科教学文档专项检查活动的通知", flagged.title)
    }

    @Test
    fun `相对链接按列表页地址解析`() {
        val p2 = JwcNoticeParser().parse(read("notice-list.2026-11-05.p2.html"), "${baseUrl}jwtz/92.htm")
        // 第 2 页的链接写作 ../info/1009/6681.htm，页面没有 <base> 标签
        assertTrue(p2.any { it.url == "${baseUrl}info/1009/6681.htm" })
    }

    @Test
    fun `教务处没有栏目字段`() {
        val notices = JwcNoticeParser().parse(read("notice-list.2026-11-05.p1.html"), "${baseUrl}jwtz.htm")
        assertTrue(notices.all { it.column.isEmpty() })
        assertTrue(notices.all { it.source == "jwc_notice" })
    }

    @Test
    fun `指纹带来源前缀与研究生院隔开`() {
        val a = JwcNoticeParser.fingerprint("开题通知", "2026-10-20")
        val b = JwcNoticeParser.fingerprint(" 开题通知 ", "2026-10-20")
        assertEquals(a, b)
        // 与研究生院同名同日的通知指纹不同，两源并存不会互相覆盖
        val grad = NoticeParser.fingerprint("开题通知", "2026-10-20")
        assertNotEquals(a, grad)
    }

    @Test
    fun `空页面不抛异常`() {
        assertTrue(JwcNoticeParser().parse("", "${baseUrl}jwtz.htm").isEmpty())
    }
}
