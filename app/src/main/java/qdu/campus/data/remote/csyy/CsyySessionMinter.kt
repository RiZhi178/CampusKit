package qdu.campus.data.remote.csyy

import qdu.campus.platform.network.CookieBridge
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.web.WebSessionCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * csyy 本域会话的原生免登。
 *
 * 域会话（`JSESSIONID`）比统一认证票短得多。它过期时统一认证那边仍然是登录状态，
 * 网页端的做法是：向 `/auth/address` 要一条跳转地址，顺着 302 走到统一认证，
 * 带着 `CASTGC` 换回一张新的本域票。这里逐跳重放同一条链，用户不用再手动走一遍登录。
 *
 * 必须一跳一跳自己走，两个原因：
 * 1. OkHttp 没配 CookieJar，自动跟随重定向时中间那跳新下发的票会被直接丢掉；
 * 2. 每跳的票只属于那一跳的 host+path，记到别的域下等于没记。
 * 所以关掉 followRedirects，票按跳同步写回 CookieManager，下一跳由
 * [qdu.campus.platform.network.CookieBridge] 现读现带。
 */
@Singleton
class CsyySessionMinter @Inject constructor(
    private val http: HttpClientFactory,
    private val cookieBridge: CookieBridge,
    private val coordinator: WebSessionCoordinator,
    private val api: CsyyApi,
) {

    private val noRedirectClient by lazy {
        http.client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * true 只说明链走完了、值得原地重试一次请求；到底换没换到票，由重试的结果说话。
     * 统一认证那边没票时直接返回 false，再去敲只会拿到一张登录表单。
     */
    suspend fun mint(): Boolean {
        if (!coordinator.hasCasTicket()) return false
        val entry = runCatching { api.fetchCasEntry() }.getOrNull()?.toHttpUrlOrNull() ?: return false
        return runCatching { walk(entry) }.getOrDefault(false)
    }

    private suspend fun walk(entry: HttpUrl): Boolean = withContext(Dispatchers.IO) {
        var url = entry
        var hop = 0
        while (hop < MAX_HOPS) {
            hop++
            val response = runCatching {
                noRedirectClient.newCall(
                    Request.Builder().url(url).get().header("Accept", HTML_ACCEPT).build(),
                ).execute()
            }.getOrElse { return@withContext false }
            val next = response.use {
                cookieBridge.storeCookiesNow(url.toString(), it.headers("Set-Cookie"))
                if (it.code !in 300..399) return@withContext true
                it.header("Location")
                    ?.takeIf { value -> value.isNotBlank() }
                    ?.let(url::resolve)
                    ?: return@withContext false
            }
            url = next
        }
        false
    }

    private companion object {
        /** 实测链长 5 跳：address → toLoginPage → authserver → doAuth → token。留一点余量。 */
        const val MAX_HOPS = 8
        const val HTML_ACCEPT = "text/html,application/xhtml+xml"
    }
}
