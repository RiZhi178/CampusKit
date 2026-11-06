package qdu.campus.data.repository

import android.util.Log
import qdu.campus.BuildConfig
import qdu.campus.platform.network.CookieBridge
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.security.CredentialStore
import qdu.campus.platform.web.WebSessionCoordinator
import qdu.campus.domain.model.SessionState
import qdu.campus.domain.model.WebDomain
import qdu.campus.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话有效性判断。
 *
 * 不按 Cookie 里有没有票预判，而是带票向 authserver 再要一次 service：
 * 直接 302 换票说明 TGT 仍然有效，被退回登录表单说明已经失效。
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val http: HttpClientFactory,
    private val cookieBridge: CookieBridge,
    private val credentials: CredentialStore,
    private val coordinator: WebSessionCoordinator,
) : SessionRepository {

    /**
     * 冷启动先按本机有没有 `CASTGC` 给出结论（见 `warmStart`），随后由业务请求与
     * 前台校验收敛；探针只改状态，票本身除非用户主动退出否则不动。
     *
     * 请求不可达（离线、校外）时不降级：那说明不了票已失效，
     * 保留上次结论比误报「已过期」更接近事实。
     */
    private val _state = MutableStateFlow(SessionState.Unknown)

    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override fun markValid() {
        _state.value = SessionState.Valid
    }

    override fun warmStart() {
        _state.value = if (coordinator.hasCasTicket()) SessionState.Valid else SessionState.Unknown
    }

    override suspend fun validate(): SessionState = withContext(Dispatchers.IO) {
        if (!hasStoredSession()) {
            _state.value = SessionState.Unknown
            return@withContext SessionState.Unknown
        }
        if (!coordinator.hasCasTicket()) {
            // WebView 的 session cookie 随进程回收掉了，用本机保存的副本回灌再试
            coordinator.restorePersistedCookies(force = true)
        }
        val probe = runCatching {
            // CAS 自己就是最准的探针：带着 TGT 再请求一次带 service 的登录地址。
            // 票有效时 CAS 直接 302 带着新票据跳走；失效时回到登录表单。
            val service = URLEncoder.encode(WebDomain.EHALL.homeUrl, "UTF-8")
            val request = Request.Builder()
                .url("https://${WebDomain.AUTH.host}/authserver/login?service=$service")
                .get()
                .header("Accept", "text/html,application/xhtml+xml")
                .build()
            noRedirectClient.newCall(request).execute().use { response ->
                val location = response.header("Location").orEmpty()
                when {
                    response.code == 401 || response.code == 403 -> Probe.Invalid
                    response.code in 300..399 && location.contains("ticket=") -> Probe.Valid
                    response.code in 300..399 -> Probe.Unreachable
                    !response.isSuccessful -> Probe.Unreachable
                    else -> {
                        // 登录表单回来了，说明 TGT 已经不被 authserver 接受
                        val body = response.body.string()
                        if (body.contains("casLoginForm") || body.contains("id=\"username\"")) {
                            Probe.Invalid
                        } else {
                            Probe.Unreachable
                        }
                    }
                }
            }
        }.getOrDefault(Probe.Unreachable)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "validate probe=$probe ticket=${coordinator.hasCasTicket()} from=${_state.value}")
        }
        _state.value = when (probe) {
            Probe.Valid -> SessionState.Valid
            // 判成过期只是状态提示，票本身不清：换票探针依赖会话级 TGC，
            // 少带一次就把刚拿到的 TGT 删掉，等于自己把登录弄丢。
            Probe.Invalid -> SessionState.Expired
            // 说明不了票已失效，维持原判，离线时不该把「在线」改成「未登录」
            Probe.Unreachable -> _state.value
        }
        _state.value
    }

    /** 业务页探测的三种结果，网络层错误不能当成「票已失效」。 */
    private enum class Probe { Valid, Invalid, Unreachable }

    /** 只认统一认证的凭据；yjsxt 是独立账号，存没存过 token 不影响 CAS 结论。 */
    private fun hasStoredSession(): Boolean =
        credentials.cookie(WebDomain.AUTH.host) != null

    /** 校验时需要看 CAS 的 302 本身，不能被 OkHttp 自动跟随掉。 */
    private val noRedirectClient by lazy {
        http.client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    override suspend fun studentId(): String? = credentials.studentId()

    override suspend fun clearSession() {
        WebDomain.entries.forEach { domain -> credentials.saveCookie(domain.host, null) }
        credentials.saveYjsxtToken(null)
        credentials.saveZzmpToken(null)
        coordinator.clearWebStorage()
        cookieBridge.flush()
        _state.value = SessionState.Unknown
    }

    override suspend fun clearAll() {
        credentials.clear()
        coordinator.clearWebStorage()
        cookieBridge.flush()
        _state.value = SessionState.Unknown
    }

    private companion object {
        const val TAG = "SessionRepository"
    }
}
