package qdu.campus.feature.auth

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.platform.security.CredentialStore
import qdu.campus.platform.web.WebSessionCoordinator
import qdu.campus.domain.model.SessionState
import qdu.campus.domain.model.WebDomain
import qdu.campus.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

enum class LoginStage { LOGIN, PREHEAT, DONE }

data class LoginUiState(
    val stage: LoginStage = LoginStage.LOGIN,
    val url: String = START_URL,
    /** WebView 实际加载到的地址，域名条显示它而不是 [url]，跳转账密页时才能看出变化。 */
    val pageUrl: String = START_URL,
    val message: String? = null,
    /** 加载失败的原因，与 [message] 分开：前者要红色提示条，后者只是预热进度。 */
    val error: String? = null,
    val loading: Boolean = true,
    val progress: Int = 0,
    /** 进入统一认证页后注入的脚本，用于把学号填进登录表单。 */
    val script: String? = null,
) {
    companion object {
        /**
         * 登录起点就是 authserver 自己的登录页，不经过任何业务系统的 CAS 入口。
         *
         * 直接登录 authserver 只产生一份 TGT；ehall、xg、csyy、报修与图书馆检索
         * 随后各自静默换票。研究生系统**不在**这条链上：它的 `getToken` 对合法票据
         * 也回「认证失败」，且失败后票未被消费（2026-11-05 实测，见
         * `docs/interfaces/yjsxt.md`），所以它由课表页自己的登录入口解决。
         * 招生与对分易不在统一认证链上，仍需各自登录。
         */
        val START_URL: String = "https://${WebDomain.AUTH.host}/authserver/login"
    }
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val coordinator: WebSessionCoordinator,
    private val sessionRepository: SessionRepository,
    private val credentials: CredentialStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private var webView: WebView? = null

    /** 一次登录只收一遍会话：页面会连续 finish（重定向、iframe），否则协程会叠着跑。 */
    private var capturing = false

    init {
        // 学号在引导页就写进了本机凭据，这里只负责取出来预填，不再单独问一次。
        _state.value = _state.value.copy(
            script = prefillScript(credentials.studentId()),
        )
    }

    fun onProgress(value: Int) {
        _state.value = _state.value.copy(progress = value)
    }

    /** 域名条的刷新按钮：重放当前页，不重新走一遍登录起点。 */
    fun reload() {
        val view = webView ?: return
        view.reload()
        _state.value = _state.value.copy(loading = true, progress = 0, message = null, error = null)
    }

    fun attachWebView(view: WebView) {
        webView = view
    }

    fun onPageStarted(url: String) {
        _state.value = _state.value.copy(
            loading = true,
            pageUrl = url.ifBlank { _state.value.pageUrl },
        )
    }

    fun onPageFinished(url: String) {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host
        if (url.isNotBlank()) {
            _state.value = _state.value.copy(pageUrl = url, loading = false)
        }
        if (_state.value.stage == LoginStage.DONE) return
        when {
            // 登录成功的唯一判据是 CAS 下发了 TGT，落在哪一页不重要。
            coordinator.hasCasTicket() -> captureFromCurrentPage()
            host == WebDomain.AUTH.host -> {
                val preheating = _state.value.stage == LoginStage.PREHEAT
                _state.value = _state.value.copy(
                    stage = LoginStage.LOGIN,
                    loading = false,
                    // 预热被弹回登录页 = 上次的票已经失效，先说清楚再让用户登录
                    message = if (preheating) "统一认证会话已过期，请重新登录。" else _state.value.message,
                    script = prefillScript(credentials.studentId()),
                )
            }
            // 还在从认证页往业务页跳的中间态，等下一页再看
            else -> Unit
        }
    }

    /**
     * 手动收尾：TGT 已经在本机时直接收；否则回到认证页，
     * 票还在时 authserver 会自己认出来，不需要再输一次。
     */
    fun finishNow() {
        if (_state.value.stage == LoginStage.DONE) return
        if (coordinator.hasCasTicket()) {
            captureFromCurrentPage()
        } else {
            _state.value = _state.value.copy(
                stage = LoginStage.PREHEAT,
                url = LoginUiState.START_URL,
                loading = true,
                script = prefillScript(credentials.studentId()),
                message = "正在确认统一认证会话…",
            )
        }
    }

    /** 当前页已经有 TGT：收 Cookie、校验，再进入 DONE。 */
    private fun captureFromCurrentPage() {
        if (capturing) return
        capturing = true
        viewModelScope.launch {
            coordinator.saveFromWebView()
            if (!coordinator.hasCasTicket()) {
                capturing = false
                _state.value = _state.value.copy(
                    stage = LoginStage.LOGIN,
                    loading = false,
                    message = "还没有拿到统一认证会话，请完成登录。",
                    script = prefillScript(credentials.studentId()),
                )
                return@launch
            }
            // 票已经在 CookieManager 里，登录就算成了。探测只是附带确认：
            // CAS 的登录页探针存在假阴性（换票流程还依赖会话级的 TGC Cookie），
            // 一次没换到票不能当成 TGT 失效——之前正是这里清票，
            // 造成「登录完了卡片还显示未登录」「隔一会就要重新登录」。
            val valid = sessionRepository.validate()
            sessionRepository.markValid()
            // 课表不再在这里同步：研究生系统只认自己的登录态，统一认证的票换不过去
            // （2026-11-05 实测：票据合法、CAS 也认，它的 getToken 在校验前就回「认证失败」）。
            // 课表页自己给了「去登录研究生系统」的入口。
            _state.value = _state.value.copy(
                stage = LoginStage.DONE,
                loading = false,
                script = null,
                message = if (valid != SessionState.Valid) "登录已完成，会话将在回到前台时复核" else null,
            )
        }
    }

    fun onError(message: String) {
        _state.value = _state.value.copy(loading = false, error = message)
    }

    /** 认证页停在原样重放用不了（比如被踢回登录页），从这里重新走一遍统一认证。 */
    fun resetToLogin() {
        capturing = false
        _state.value = LoginUiState(
            stage = LoginStage.LOGIN,
            script = prefillScript(credentials.studentId()),
        )
    }

    /**
     * 预填统一认证页的学号。实测登录页字段是 `#username`（占位符「输入学号」），
     * 且没有验证码；密码一律不碰，由用户自己输入。
     */
    private fun prefillScript(studentId: String?): String? {
        if (studentId.isNullOrBlank()) return null
        val escaped = studentId.replace("\\", "\\\\").replace("'", "\\'")
        return "(function(){var i=document.getElementById('username');" +
            "if(i&&!i.value){i.value='$escaped';" +
            "i.dispatchEvent(new Event('input',{bubbles:true}));}})();"
    }

}
