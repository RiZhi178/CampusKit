package qdu.campus.data.remote.zzmp

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonPrimitive
import qdu.campus.platform.network.appJson
import qdu.campus.platform.security.CredentialStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * zzmp 的 `sessionToken` 提取。
 *
 * 会话本身是 Cookie 里的 `SESSION`，但网页端在每个 `/api/` 请求上还带
 * `sessionToken: siteInfo.sessionToken`。这个值只由页面脚本写进 storage，
 * 原生侧拿不到就发空串（前端自己也是空串兜底），所以提取失败不算错误，
 * 只影响服务端是否接受这次请求——页面会照常给出「未登录」并引导重登。
 *
 * 和图书馆的 `jwt` 一样，这一份只能由 WebView 里跑着的页面交出来；
 * 提取时机在补会话页加载完成之后。
 */
@Singleton
class ZzmpSession @Inject constructor(
    private val credentials: CredentialStore,
) {

    fun token(): String? = credentials.zzmpToken()

    suspend fun captureSiteToken(webView: WebView): String? = withContext(Dispatchers.Main) {
        val raw = suspendCancellableCoroutine<String?> { continuation ->
            runCatching {
                webView.evaluateJavascript(SCRIPT, { value ->
                    if (continuation.isActive) continuation.resume(value)
                })
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }
        // evaluateJavascript 回调的是 JSON 字面量，字符串带引号，交给 JSON 解析去掉转义
        val token = runCatching {
            appJson.parseToJsonElement(raw.orEmpty()).jsonPrimitive.content
        }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != "null" }
        credentials.saveZzmpToken(token)
        token
    }

    private companion object {
        /** 前端同时读 localStorage 与 sessionStorage，按同一顺序取。 */
        const val SCRIPT =
            "(function(){try{var raw=localStorage.getItem('siteInfo')" +
                "||sessionStorage.getItem('siteInfo');if(!raw)return '';" +
                "var info=JSON.parse(raw);return info&&info.sessionToken?" +
                "String(info.sessionToken):'';}catch(e){return '';}})()"
    }
}
