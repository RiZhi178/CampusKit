package qdu.campus.feature.scan

/**
 * 扫出来的码内容分类。只做字符串判定，不碰 Android API，归属与转交动作的
 * 对照表见 `docs/scan-slice.md` 第三节。
 *
 * 分类刻意保守：判不了的一律给 [PlainText]，由 UI 展示原文并复制，
 * 不猜「大概是个微信码」就往微信里送。
 */
sealed interface QrContent {
    /** 微信专用 scheme（`weixin://`、`wxp://`）。可以带包名定向转交给微信。 */
    data class WeChatScheme(val raw: String) : QrContent

    /** 微信小程序码（`gh_…` 开头）。第三方应用无法深链，只能引导回微信。 */
    data class WeChatMiniProgram(val raw: String) : QrContent

    /** 支付宝专用 scheme（`alipays://`、`alipayqr://`、`alipay://`）。 */
    data class AlipayScheme(val raw: String) : QrContent

    /** 网页链接。进应用内浏览器；若服务端有 UA 门禁，由用户复制后自行转投。 */
    data class Web(val url: String) : QrContent

    /** 判不了归属的其余内容。 */
    data class PlainText(val raw: String) : QrContent
}

/**
 * 码内容分类器。判定规则全部是前缀匹配，大小写不敏感、首尾空白忽略——
 * 真实扫码内容常带不可见字符，规则太严会把能转交的码判成 PlainText。
 */
object QrContentClassifier {

    private val weChatSchemes = listOf("weixin://", "wxp://")
    private val alipaySchemes = listOf("alipays://", "alipayqr://", "alipay://")

    fun classify(rawInput: String): QrContent {
        val raw = rawInput.trim()
        if (raw.isEmpty()) return QrContent.PlainText(rawInput)

        val lowered = raw.lowercase()
        // 先判 scheme 再判小程序前缀：weixin:// 永远优先于其它形态
        if (weChatSchemes.any { lowered.startsWith(it) }) return QrContent.WeChatScheme(raw)
        if (alipaySchemes.any { lowered.startsWith(it) }) return QrContent.AlipayScheme(raw)
        // 小程序码扫出来是 gh_ 开头的路径（如 gh_1a2b3c@app），有时带 JSON 包装，
        // 包装体里 gh_ 不在开头，取包含判定
        if (lowered.startsWith("gh_") || lowered.contains("\"gh_")) {
            return QrContent.WeChatMiniProgram(raw)
        }
        if (lowered.startsWith("http://") || lowered.startsWith("https://")) {
            return QrContent.Web(raw)
        }
        return QrContent.PlainText(raw)
    }
}
