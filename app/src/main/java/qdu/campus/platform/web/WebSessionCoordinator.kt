package qdu.campus.platform.web

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import qdu.campus.platform.network.CookieBridge
import qdu.campus.platform.security.CredentialStore
import qdu.campus.domain.model.WebDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 会话提取与清理的唯一入口。
 *
 * Cookie 为主：清空 sessionStorage 后仍能免登，说明凭据在 Cookie 里；
 * `sessionStorage.originalToken` 只在 Cookie 缺失时作回退。
 *
 * 只保存会话票与令牌。yjsxt 实测会把 `username`、`password`、`rememberMe` 一起
 * 下发成可读 Cookie，这类条目在收采和回灌两处都丢弃，见 [filterSessionCookies]。
 */
@Singleton
class WebSessionCoordinator @Inject constructor(
    private val credentials: CredentialStore,
    private val cookieBridge: CookieBridge,
) {

    private var restored = false

    /** 按域读取并持久化 Cookie。必须在 CookieManager.flush 之后读取。 */
    suspend fun saveFromWebView(domains: Collection<WebDomain> = WebDomain.entries) =
        withContext(Dispatchers.IO) {
            val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return@withContext
            runCatching { manager.flush() }
            domains.forEach { domain ->
                val cookie = filterSessionCookies(
                    runCatching { manager.getCookie(domain.cookieUrl) }.getOrNull(),
                )
                if (cookie != null) {
                    credentials.saveCookie(domain.host, cookie)
                    if (domain == WebDomain.YJSXT) {
                        extractToken(cookie)?.let { credentials.saveYjsxtToken(it) }
                    }
                }
            }
        }

    /**
     * 冷启动把上次保存的 Cookie 灌回 CookieManager，这是「一次登录后续不再填」的关键。
     *
     * 统一认证和校内系统下发的多是没有过期时间的 session cookie（实测 CAS 的
     * `JSESSIONID` 即为 session 级），进程被杀就丢，不回灌等于每次启动都重新登录。
     * 只在目标域当前没有 Cookie 时写入，避免用旧值覆盖本次运行刚拿到的新会话。
     *
     * 故意不是挂起函数：Application.onCreate 里同步执行，保证早于第一个 WebView 加载。
     */
    fun restorePersistedCookies(force: Boolean = false) {
        if (restored && !force) return
        restored = true
        val manager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        runCatching { manager.setAcceptCookie(true) }
        var touched = 0
        WebDomain.entries.forEach { domain ->
            val stored = credentials.cookie(domain.host) ?: return@forEach
            // 旧版本连 yjsxt 的 password 一起存了：这里不只挡住不回灌，
            // 还把本机那份副本就地改写成过滤后的值，一次冷启动即完成自愈。
            val saved = filterSessionCookies(stored)
            if (saved != stored) credentials.saveCookie(domain.host, saved)
            if (saved == null) return@forEach
            // 按名字补缺，而不是「有任意 Cookie 就整域跳过」：匿名访问 authserver
            // 也会种下 JSESSIONID，整域跳过会让真正的 CASTGC 永远回灌不进来。
            val present = currentCookie(domain)
                .orEmpty()
                .split(';')
                .map { it.substringBefore('=').trim() }
                .filter { it.isNotEmpty() }
                .toSet()
            var wrote = 0
            saved.split(';')
                .map { it.trim() }
                .filter { it.contains('=') }
                .filter { it.substringBefore('=').trim() !in present }
                .forEach { pair ->
                    // 存下来的只有 name=value，不带有效期就会退化成 session Cookie，
                    // 进程一被杀就又没了。统一认证 TGT 实测 7 天有效，按同一时长补上。
                    runCatching {
                        manager.setCookie(domain.cookieUrl, "$pair; path=/; Max-Age=$RESTORE_MAX_AGE_SECONDS")
                    }
                    wrote++
                }
            if (wrote > 0) touched++
        }
        if (touched > 0) runCatching { manager.flush() }
    }

    /** 直接读 CookieManager 的当前值，不落盘：用于判断会话是否已经建立。 */
    fun currentCookie(domain: WebDomain): String? =
        runCatching { CookieManager.getInstance().getCookie(domain.cookieUrl) }.getOrNull()

    /**
     * 是否真的建立了统一认证会话。
     *
     * 只看 TGT（`CASTGC`）：未登录时访问 authserver 也会留下 `JSESSIONID`，
     * 拿「authserver 有没有 Cookie」当已登录，门禁会被匿名会话直接放行。
     */
    fun hasCasTicket(): Boolean =
        currentCookie(WebDomain.AUTH)
            ?.split(';')
            ?.any { it.substringBefore('=').trim() == CAS_TICKET_KEY }
            ?: false

    /**
     * 从 yjsxt 的 Cookie 中取 `QZKJ-YJSBB-TOEKN`（拼写按原样）。
     * 取不到再回退到 `sessionStorage.originalToken`。
     */
    suspend fun captureYjsxtToken(webView: WebView): String? {
        saveFromWebView(listOf(WebDomain.YJSXT))
        val fromCookie = credentials.cookie(WebDomain.YJSXT.host)?.let(::extractToken)
        if (!fromCookie.isNullOrBlank()) {
            credentials.saveYjsxtToken(fromCookie)
            return fromCookie
        }
        val raw = evaluate(webView, "sessionStorage.getItem('originalToken')")
        val token = raw?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "null" }
        if (token != null) credentials.saveYjsxtToken(token)
        return token
    }

    fun extractToken(cookie: String): String? =
        cookie.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith(TOKEN_KEY) }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }

    fun clearWebStorage() {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
        cookieBridge.clearAll()
        credentials.saveYjsxtToken(null)
        credentials.saveZzmpToken(null)
    }

    private suspend fun evaluate(webView: WebView, script: String): String? =
        suspendCancellableCoroutine { continuation ->
            runCatching {
                webView.post {
                    webView.evaluateJavascript(script) { value ->
                        if (continuation.isActive) continuation.resume(value)
                    }
                }
            }.onFailure {
                if (continuation.isActive) continuation.resume(null)
            }
        }

    companion object {
        const val TOKEN_KEY = "QZKJ-YJSBB-TOEKN"
        const val CAS_TICKET_KEY = "CASTGC"

        /** 回灌时补的有效期，与统一认证 TGT 的实测存活时长一致。 */
        private const val RESTORE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60L
    }
}

/**
 * 口令与账号类 Cookie 的名字。会话票一律保留，这几类不保留：
 * 本应用只认票和令牌，学号另有 `CredentialStore.studentId`。
 * 精确名之外再按关键词兜一层，防的是同名变体（`userPass`、`login_pwd`）。
 */
internal fun isCredentialCookie(name: String): Boolean {
    val lower = name.lowercase()
    return lower in CREDENTIAL_COOKIE_NAMES ||
        CREDENTIAL_COOKIE_MARKERS.any { lower.contains(it) }
}

/**
 * 过滤 Cookie 头，只留下会话票与令牌。全部被滤掉时返回 null，
 * 调用方据此判断「这个域没什么可存的」。
 */
internal fun filterSessionCookies(raw: String?): String? =
    raw?.split(';')
        ?.map { it.trim() }
        ?.filter { it.contains('=') && it.substringAfter('=').isNotBlank() }
        ?.filterNot { isCredentialCookie(it.substringBefore('=').trim()) }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString("; ")

private val CREDENTIAL_COOKIE_NAMES = setOf(
    "username", "user", "uname", "account", "loginname", "rememberme", "remember",
)

private val CREDENTIAL_COOKIE_MARKERS = listOf("pass", "pwd", "secret", "remember")

