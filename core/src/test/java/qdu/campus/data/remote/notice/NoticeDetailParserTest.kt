package qdu.campus.data.remote.notice

import qdu.campus.data.remote.notice.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 详情页解析 fixture 测试。样本为 2026-11-07 抓取的 7 份真实详情页：
 * jwc 两份（正文 `<p>` 流 + 内嵌 PDF 变体）、grad 五份（附件 1~5 个、正文带图）。
 * 断言骨架不断言细节，校方改版时跑一遍就知道坏在哪一层。
 */
class NoticeDetailParserTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/notice/$name")!!.bufferedReader().readText()

    private fun parse(name: String): NoticeDetail =
        NoticeDetailParser.parse(
            fixture(name),
            when {
                name.startsWith("jwc") -> "https://jwc.qdu.edu.cn/info/1009/6704.htm"
                else -> "https://grad.qdu.edu.cn/info/1025/4661.htm"
            },
        )!!

    @Test
    fun `jwc 正文流解析出段落与附件`() {
        val d = parse("jwc-detail-content-jsp.2026-11-07.htm")
        assertTrue(d.title.contains("教学创新大赛"))
        assertEquals("2026-11-05", d.publishedDate)
        assertTrue(d.blocks.size >= 8)
        assertTrue(d.blocks.filterIsInstance<Block.Paragraph>().first().text.contains("各学院"))
        assertTrue(d.blocks.none { it is Block.EmbeddedPdf })
        assertTrue(d.attachments.isEmpty())
    }

    @Test
    fun `jwc 内嵌 PDF 正文识别为独立块`() {
        val d = parse("jwc-detail-pdf-iframe.2026-11-07.htm")
        assertTrue(d.publishedDate == "2026-11-04")
        val pdf = d.blocks.filterIsInstance<Block.EmbeddedPdf>()
        assertEquals(1, pdf.size)
        assertTrue(pdf[0].url.endsWith(".pdf"))
        assertEquals(2, d.attachments.size)
        assertTrue(d.attachments.all { it.name.contains(".xls") || it.name.contains(".doc") })
    }

    @Test
    fun `grad 附件链接文本即文件名`() {
        val d = parse("grad-detail-attach-image.2026-11-07.htm")
        assertEquals("2026-11-06", d.publishedDate)
        assertEquals(1, d.attachments.size)
        assertEquals("关于邀请参加“2026博士生国际招生面试交流会”的函.pdf", d.attachments[0].name)
        assertTrue(d.attachments[0].url.endsWith(".htm").not())
        assertTrue(d.attachments[0].url.contains("download.jsp"))
        // 正文里两张 /__local/ 图片
        assertEquals(2, d.blocks.filterIsInstance<Block.Image>().size)
        assertTrue(d.blocks.filterIsInstance<Block.Paragraph>().isNotEmpty())
    }

    @Test
    fun `grad 附件按 URL 去重且数量正确`() {
        assertEquals(1, parse("grad-detail-attach-1.2026-11-07.htm").attachments.size)
        assertEquals(1, parse("grad-detail-attach-1b.2026-11-07.htm").attachments.size)
        assertEquals(3, parse("grad-detail-attach-3.2026-11-07.htm").attachments.size)
        assertEquals(5, parse("grad-detail-attach-5.2026-11-07.htm").attachments.size)
    }

    @Test
    fun `正文图片直链转绝对地址`() {
        val d = parse("grad-detail-attach-image.2026-11-07.htm")
        for (img in d.blocks.filterIsInstance<Block.Image>()) {
            assertTrue(img.url.startsWith("https://grad.qdu.edu.cn/"))
        }
    }

    @Test
    fun `正文文本规整掉 nbsp 与连续空白`() {
        val d = parse("jwc-detail-content-jsp.2026-11-07.htm")
        for (p in d.blocks.filterIsInstance<Block.Paragraph>()) {
            assertTrue(p.text !in setOf("", "\u00A0"))
            assertEquals(p.text.trim(), p.text)
            assertTrue(!p.text.contains("\u00A0"))
        }
    }
}
