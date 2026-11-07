package qdu.campus.platform.security

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 凭据形态描述：只输出结构信息，绝不输出凭据值本身。
 *
 * 要回答的是「本机存的到底是什么」——界面说需要重新登录时，用它区分
 * 没存到、存的不是 JWT、还是 JWT 已经过期。这三种成因在界面上长得一样。
 *
 * 放在 core 是为了能纯 JVM 测试；不碰 Android Keystore，也不碰 CookieManager。
 */
object CredentialShape {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** 载荷字段名只报这么多：够认出令牌类型，又不会把整份 claims 摊在界面上。 */
    private const val MAX_FIELDS = 12

    /**
     * JWT 形态。非 JWT 的不透明串会得到「段数=1、载荷字段=读不出」，
     * 这正是要看的结论，不要把它当成解析失败。
     */
    fun jwt(token: String?, nowMillis: Long = System.currentTimeMillis()): String {
        if (token.isNullOrBlank()) return "无令牌"
        val parts = token.split('.')
        val payload = decodePayload(parts.getOrNull(1))
        val obj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
        val fields = obj?.keys?.take(MAX_FIELDS)?.toList().orEmpty()
        return buildString {
            append("段数=").append(parts.size)
            append("，长度=").append(token.length)
            append("，含exp=").append(obj?.containsKey("exp") == true)
            append("，载荷字段=").append(if (fields.isEmpty()) "读不出" else fields.joinToString(","))
            val exp = runCatching { obj?.get("exp")?.jsonPrimitive?.content?.toLongOrNull() }.getOrNull()
            if (exp != null) {
                val remaining = exp * 1000 - nowMillis
                append("，")
                if (remaining >= 0) {
                    append("剩余").append(remaining / 60_000).append("分钟")
                } else {
                    append("已过期").append(-remaining / 60_000).append("分钟")
                }
            }
        }
    }

    /** Cookie 串形态：只报条目数与名字。名字不是凭据，值才是。 */
    fun cookies(header: String?): String {
        if (header.isNullOrBlank()) return "无 Cookie"
        val names = header.split(';')
            .map { it.trim() }
            .filter { it.contains('=') }
            .map { it.substringBefore('=').trim() }
        return "条目数=${names.size}，名字=${names.joinToString(",")}，总长度=${header.length}"
    }

    /** 不透明令牌只报长度：它没有可解析的结构，报字段名没有意义。 */
    fun opaque(token: String?): String =
        if (token.isNullOrBlank()) "无令牌" else "长度=${token.length}（不透明，非 JWT）"

    /**
     * JWT 载荷是 base64url 且不带填充，标准解码器会拒。先换回标准字母表再补齐 `=`，
     * 用 MIME 解码器容忍换行与非法字符——这里只为看形态，解不出来就报「读不出」。
     */
    private fun decodePayload(part: String?): String {
        if (part.isNullOrBlank()) return ""
        return runCatching {
            val normalized = part.replace('-', '+').replace('_', '/')
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            String(java.util.Base64.getMimeDecoder().decode(padded), Charsets.UTF_8)
        }.getOrDefault("")
    }
}