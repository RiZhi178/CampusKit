package qdu.campus.platform.network

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import qdu.campus.domain.model.WebDomain
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学工系统只认桌面 UA。
 *
 * 它把「手机 UA + 会话已建立」两个条件同时满足的请求一律改道到已删除的移动应用
 * `/XGPhone/PhoneCas/account/Menu`，落在 IIS 404 上；桌面 UA 下门户首页与 `/userhall/`
 * 都正常（2026-11-05 在已登录状态实测）。内嵌 WebView 与原生请求必须用同一串，
 * 换一边就会把另一边的会话形态带坏，证据见 `docs/interfaces/xg.md`。
 */
const val XG_DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/** 全局 JSON 配置：服务端字段远多于客户端使用的部分，必须忽略未知键。 */
val appJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/**
 * WebView 的 CookieManager 与 OkHttp 之间的双向同步。
 *
 * 每次请求前从 CookieManager 读取当前 Cookie（各页面登录后立即生效），
 * 响应中的 Set-Cookie 回写给 CookieManager，供 WebView 内嵌页复用。
 */
@Singleton
class CookieBridge @Inject constructor() {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun cookieHeader(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

    fun storeCookies(url: String, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        mainHandler.post {
            val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return@post
            setCookieHeaders.forEach { manager.setCookie(url, it) }
            manager.flush()
        }
    }

    /**
     * 同步写入。
     *
     * 逐跳免登时下一跳立刻要读这一跳刚下发的票，[storeCookies] 那种 post 回主线程的写法
     * 排在队列后面，赶不上；这里直接在调用线程写 CookieManager 的内存仓。
     */
    fun storeCookiesNow(url: String, setCookieHeaders: List<String>) {
        if (setCookieHeaders.isEmpty()) return
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        setCookieHeaders.forEach { manager.setCookie(url, it) }
        runCatching { manager.flush() }
    }

    fun flush() {
        mainHandler.post {
            runCatching { CookieManager.getInstance().flush() }
        }
    }

    fun clearAll() {
        mainHandler.post {
            runCatching {
                val manager = CookieManager.getInstance()
                manager.removeAllCookies(null)
                manager.flush()
            }
        }
    }

    fun interceptor(): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val cookie = cookieHeader(request.url.toString())
        val outgoing = if (!cookie.isNullOrBlank()) {
            request.newBuilder().header("Cookie", cookie).build()
        } else {
            request
        }
        val response: Response = chain.proceed(outgoing)
        storeCookies(request.url.toString(), response.headers("Set-Cookie"))
        response
    }
}

/**
 * 统一的 OkHttp 客户端。
 *
 * User-Agent 与 WebView 保持一致，避免同一会话在不同 UA 下被判为异常；
 * 例外是学工，它按 UA 分流页面，见 [XG_DESKTOP_UA]。
 */
@Singleton
class HttpClientFactory @Inject constructor(
    val cookieBridge: CookieBridge,
) {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(cookieBridge.interceptor())
            .addInterceptor { chain ->
                val request = chain.request()
                val outgoing = request.newBuilder()
                    .header("User-Agent", userAgentFor(request.url.host))
                    .build()
                chain.proceed(outgoing)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun clientWithUserAgent(ua: String): OkHttpClient = client.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", ua).build())
        }
        .build()

    companion object {
        /** 由 Application 在启动时用 WebSettings.getDefaultUserAgent 覆盖。 */
        @Volatile
        var USER_AGENT: String = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /** 按域选 UA：只有学工要桌面 UA，其余仍跟随系统 WebView。 */
        fun userAgentFor(host: String?): String =
            if (WebDomain.ofHost(host) == WebDomain.XG) XG_DESKTOP_UA else USER_AGENT
    }
}
