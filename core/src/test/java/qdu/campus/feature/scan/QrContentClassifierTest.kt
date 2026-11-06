package qdu.campus.feature.scan

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分类器只锁判类不锁细节：断言归属类别与原文保留，不断言内容含义。
 * 真实样本：饮水机码、洗衣机码已采（2026-11-06，见 fixture），空调待采。
 */
class QrContentClassifierTest {

    @Test
    fun `微信支付码 wxp 前缀`() {
        val raw = "wxp://f2f0abcdef123456"
        assertEquals(QrContent.WeChatScheme(raw), QrContentClassifier.classify(raw))
    }

    @Test
    fun `微信开放 scheme 前缀`() {
        val raw = "weixin://dl/business/?t=abcdef"
        assertEquals(QrContent.WeChatScheme(raw), QrContentClassifier.classify(raw))
    }

    @Test
    fun `scheme 大小写与空白不敏感`() {
        val raw = "  WXP://f2f123  "
        assertEquals(
            QrContent.WeChatScheme("WXP://f2f123"),
            QrContentClassifier.classify(raw),
        )
    }

    @Test
    fun `小程序码 gh_ 开头`() {
        val raw = "gh_103d9a55c9@app"
        assertEquals(QrContent.WeChatMiniProgram(raw), QrContentClassifier.classify(raw))
    }

    @Test
    fun `小程序码 JSON 包装体也能认出`() {
        val raw = """{"app":"gh_103d9a55c9@app","id":"#"}"""
        assertEquals(QrContent.WeChatMiniProgram(raw), QrContentClassifier.classify(raw))
    }

    @Test
    fun `支付宝收款码 alipays 前缀`() {
        val raw = "alipays://platformapi/startapp?appId=20000056"
        assertEquals(QrContent.AlipayScheme(raw), QrContentClassifier.classify(raw))
    }

    @Test
    fun `支付宝扫码 alipayqr 前缀`() {
        val raw = "alipayqr://platformapi/startapp?saId=0000"
        assertEquals(QrContent.AlipayScheme(raw), QrContentClassifier.classify(raw))
    }

    @Test
    fun `https 短链归网页`() {
        val raw = "https://s.example-water.com/p/ABC123"
        assertEquals(QrContent.Web(raw), QrContentClassifier.classify(raw))
    }

    @Test
    fun `http 也归网页`() {
        val raw = "http://www.leadfair.com"
        assertEquals(QrContent.Web(raw), QrContentClassifier.classify(raw))
    }

    @Test
    fun `真实饮水机码样本归网页`() {
        // 2026-11-06 在真机上采到的样本：lefair（水管家）的 http 短链，
        // 带 rtuid 设备参数。形态是明文 http + 站内路径，不是 scheme，
        // 归「网页」类，由「去外部程序」交微信打开（UA 门禁在微信里天然满足）。
        val raw = javaClass.getResourceAsStream("/scan/water-dispenser.2026-11-06.txt")!!
            .bufferedReader()
            .readText()
            .trim()
        assertEquals(QrContent.Web(raw), QrContentClassifier.classify(raw))
    }

    @Test
    fun `真实洗衣机码样本归网页`() {
        // 2026-11-06 采到的样本：海尔洗衣机 h5.haier-ioc.com 的 https 短链，
        // N 为设备 UUID。形态与饮水机码同类（厂商站内短链），归「网页」类，
        // 由「去外部程序」交微信打开。
        val lines = javaClass.getResourceAsStream("/scan/laundry.2026-11-06.txt")!!
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertEquals(3, lines.size)
        lines.forEach { raw ->
            assertEquals(QrContent.Web(raw), QrContentClassifier.classify(raw))
        }
    }

    @Test
    fun `普通文本兜底`() {
        assertEquals(
            QrContent.PlainText("HELLO-1234"),
            QrContentClassifier.classify("HELLO-1234"),
        )
    }

    @Test
    fun `空串归兜底不抛异常`() {
        assertEquals(QrContent.PlainText(""), QrContentClassifier.classify(""))
        assertEquals(QrContent.PlainText("   "), QrContentClassifier.classify("   "))
    }
}
