package qdu.campus.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 从错误响应体里取出服务端自己写的那句话。
 *
 * 各家 Api 在非 2xx 时原本只报状态码，服务端明明给了原因也被丢掉，界面上只剩
 * 「XX系统返回 500」，无法定位。这里只负责提取，不判断语义：是不是会话失效
 * 仍由各 Api 按自己的业务码决定。
 *
 * 输出压成单行并截断，避免把整页 HTML 或超长堆栈灌进界面与日志。
 */
object ServerMessage {

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** 按各家实际用的字段名排，前面的优先。`error` 放最后：它常是机器码而非人话。 */
    private val KEYS = listOf("message", "errorMessage", "msg", "error_description", "returnMessage", "error")

    private val QUOTED = KEYS.map { Regex("\"$it\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"") }
    private val TITLE = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val WHITESPACE = Regex("\\s+")

    private const val MAX_LENGTH = 120

    fun extract(body: String): String? {
        val text = body.trim()
        if (text.isEmpty()) return null

        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (obj != null) {
            for (key in KEYS) {
                val value = runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull()
                clean(value)?.let { return it }
            }
        }

        // 响应体被截断或不是合法 JSON 时退回正则，仍比只报状态码强
        for (regex in QUOTED) {
            clean(regex.find(text)?.groupValues?.get(1)?.let(::unescape))?.let { return it }
        }

        if (text.startsWith("<")) {
            return clean(TITLE.find(text)?.groupValues?.get(1))
        }
        return null
    }

    private fun clean(raw: String?): String? {
        val text = raw?.replace(WHITESPACE, " ")?.trim().orEmpty()
        if (text.isEmpty()) return null
        return if (text.length <= MAX_LENGTH) text else text.take(MAX_LENGTH) + "…"
    }

    private fun unescape(raw: String): String = raw
        .replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\n", " ")
        .replace("\\r", " ")
        .replace("\\t", " ")
        .replace("\\\\", "\\")
}