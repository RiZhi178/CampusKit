package qdu.campus.data.remote.graduate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 样本来自 2026-11-05 的真实抓取（`core/src/test/resources/grad/`），
 * 契约与结论见 `docs/interfaces/grad-notice.md`。
 */
class NoticeParserTest {

    private fun read(name: String): String =
        javaClass.getResourceAsStream("/grad/$name")!!
            .readBytes().toString(Charsets.UTF_8)

    private val baseUrl = "https://grad.qdu.edu.cn/"

    @Test
    fun `培养栏目第一页抽到十条且标题不带日期`() {
        val notices = NoticeParser().parse(
            read("notice-list.2026-11-05.pygz-p1.html"),
            "${baseUrl}pygz/tzgg.htm",
        )
        assertEquals(10, notices.size)
        assertEquals("2026-11-05", notices[0].date)
        assertEquals("青岛大学2027版研究生培养方案修（制）订文件资料汇编", notices[0].title)
        assertEquals("培养工作", notices[0].column)
        // 栏目页的 <a> 包住标题与日期，用链接文字取标题会把日期拼进去，指纹跟着分裂
        assertTrue(notices.none { it.title.contains(Regex("""\d{4}-\d{2}-\d{2}""")) })
        assertTrue(notices[0].url.startsWith(baseUrl))
    }

    @Test
    fun `第二页与另两个栏目同样各十条`() {
        val pygz2 = NoticeParser().parse(
            read("notice-list.2026-11-05.pygz-p2.html"),
            "${baseUrl}pygz/tzgg/50.htm",
        )
        val xjgz1 = NoticeParser().parse(
            read("notice-list.2026-11-05.xjgz-p1.html"),
            "${baseUrl}xjgz/tzgg.htm",
        )
        val xwgz1 = NoticeParser().parse(
            read("notice-list.2026-11-05.xwgz-p1.html"),
            "${baseUrl}xwgz/tzgg.htm",
        )
        val xwgz2 = NoticeParser().parse(
            read("notice-list.2026-11-05.xwgz-p2.html"),
            "${baseUrl}xwgz/tzgg/48.htm",
        )
        assertEquals(10, pygz2.size)
        assertEquals(10, xjgz1.size)
        assertEquals(10, xwgz1.size)
        assertEquals(10, xwgz2.size)
        assertEquals("学籍工作", xjgz1[0].column)
        assertEquals("学位工作", xwgz1[0].column)
        assertEquals("2026-06-23", pygz2[0].date)
    }

    @Test
    fun `相对链接按栏目页地址解析成绝对地址`() {
        val notices = NoticeParser().parse(
            read("notice-list.2026-11-05.pygz-p1.html"),
            "${baseUrl}pygz/tzgg.htm",
        )
        // 栏目页里的链接写作 ../info/1025/4657.htm，没有 <base> 标签可用
        assertTrue(notices.all { it.url.startsWith(baseUrl) })
        assertTrue(notices.any { it.url == "${baseUrl}info/1025/4657.htm" })
    }

    @Test
    fun `首页不再作为数据源`() {
        // 首页通知块 title 为空、学子风采是微信外链，都不该被抽出来
        val notices = NoticeParser().parse(read("home.2026-11-05.html"), baseUrl)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `标题清洗压缩空白避免指纹分裂`() {
        val html = """
            <html><body><ul class="wz-list">
              <li class="wz-list-li"><a title="关于   开题的通知 " href="info/1025/1.htm">
                <p class="tit">关于   开题的通知 </p><span class="time">2026-11-05</span></a></li>
            </ul></body></html>
        """.trimIndent()
        val notices = NoticeParser().parse(html, "${baseUrl}pygz/tzgg.htm")
        assertEquals(1, notices.size)
        assertEquals("关于 开题的通知", notices[0].title)
        val reference = NoticeParser.fingerprint("关于 开题的通知", "2026-11-05")
        assertEquals(reference, notices[0].fingerprint)
    }

    @Test
    fun `指纹由标题与日期决定且稳定`() {
        val a = NoticeParser.fingerprint("开题通知", "2026-10-20")
        val b = NoticeParser.fingerprint(" 开题通知 ", "2026-10-20")
        val c = NoticeParser.fingerprint("开题通知", "2026-10-21")
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `空页面不抛异常`() {
        assertTrue(NoticeParser().parse("", baseUrl).isEmpty())
    }
}
