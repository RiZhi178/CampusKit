package qdu.campus.feature.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import qdu.campus.domain.model.WebDomain
import qdu.campus.domain.model.XgLeaveListPage
import qdu.campus.domain.model.XgLeaveRecord
import qdu.campus.domain.repository.XgRepository
import qdu.campus.platform.common.XgAuthException
import qdu.campus.platform.web.WebSessionCoordinator
import javax.inject.Inject

/**
 * 我的请假列表。
 *
 * 会话静默补：读不到学工会话且本机有统一认证票时，挂一张隐藏内嵌页进学工门户，
 * 由注入脚本替人点一次「日常请假」磁贴签发跳票（站点自己的 JS 走签名隧道，本项目不复刻），
 * 落地 `/apps/DailyLeave/` 即铸好会话，回灌 Cookie 后重读列表；补不到才摆登录引导。
 * 撤销直接落在这一页：行内按钮名随行号前移，所以每次都用当前这份列表去撤，
 * 撤完成败以服务端重读后该记录还在不在为准（仓储已核对）。
 */
@HiltViewModel
class LeaveListViewModel @Inject constructor(
    private val repository: XgRepository,
    private val coordinator: WebSessionCoordinator,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val page: XgLeaveListPage? = null,
        val fetchedAt: Long? = null,
        val error: String? = null,
        val needsLogin: Boolean = false,
        /** 正在用隐藏内嵌页静默铸会话，页面显示一行进度并把 WebView 藏起来。 */
        val syncing: Boolean = false,
        /** 正在撤销的那条记录号，用来禁用该行按钮、避免连点。 */
        val revokingId: String? = null,
        val message: String? = null,
        val messageOk: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var booting = false
    private var syncStartedAt = 0L

    init {
        bootstrap()
    }

    /**
     * 进页面先读一次列表确认会话，不等用户点「重试」。
     *
     * 缺会话且有统一认证票就静默补；连票都没有，补会话的网页链只会停在登录表单，直接摆登录引导。
     */
    fun bootstrap() {
        if (booting || _state.value.syncing) return
        booting = true
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            coordinator.restorePersistedCookies(force = true)
            // TEMP PROBE — remove after diagnosis (names only, never values)
            runCatching {
                val cm = android.webkit.CookieManager.getInstance()
                fun names(u: String): String =
                    (cm.getCookie(u) ?: "").split(';')
                        .mapNotNull { it.substringBefore('=').trim().takeIf { n -> n.isNotEmpty() } }
                        .joinToString(",").ifEmpty { "<none>" }
                android.util.Log.d(
                    "XgProbe",
                    "root=[" + names("https://xg.qdu.edu.cn/") +
                        "] app=[" + names("https://xg.qdu.edu.cn/apps/DailyLeave/") +
                        "] full=[" + names("https://xg.qdu.edu.cn/apps/DailyLeave/Features/DailyLeave/StuApply.aspx") + "]",
                )
            }
            val outcome = runCatching { repository.leaveList() }
            android.util.Log.d("XgProbe", "leaveList=" + if (outcome.isSuccess) "OK" else outcome.exceptionOrNull()?.javaClass?.simpleName)
            booting = false
            outcome
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            syncing = false,
                            needsLogin = false,
                            page = page,
                            fetchedAt = System.currentTimeMillis(),
                            error = null,
                        )
                    }
                }
                .onFailure { t ->
                    if (t is XgAuthException) {
                        if (coordinator.hasCasTicket()) {
                            syncStartedAt = System.currentTimeMillis()
                            _state.update {
                                it.copy(loading = false, refreshing = false, syncing = true, needsLogin = false, error = null)
                            }
                        } else {
                            _state.update {
                                it.copy(loading = false, refreshing = false, syncing = false, needsLogin = true, error = null)
                            }
                        }
                    } else {
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                syncing = false,
                                error = t.message ?: "请假列表暂时取不到",
                            )
                        }
                    }
                }
        }
    }

    fun load() = bootstrap()

    fun refresh() {
        val s = _state.value
        if (s.loading || s.refreshing || s.syncing) return
        if (s.needsLogin || s.page == null) {
            bootstrap()
            return
        }
        coordinator.restorePersistedCookies(force = true)
        _state.update { it.copy(refreshing = true, error = null) }
        fetch()
    }

    private fun fetch() {
        viewModelScope.launch {
            runCatching { repository.leaveList() }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            refreshing = false,
                            page = page,
                            fetchedAt = System.currentTimeMillis(),
                            error = null,
                            needsLogin = false,
                        )
                    }
                }
                .onFailure { t ->
                    if (t is XgAuthException) {
                        _state.update { it.copy(refreshing = false, needsLogin = true, error = null) }
                    } else {
                        _state.update {
                            it.copy(refreshing = false, error = t.message ?: "请假列表暂时取不到")
                        }
                    }
                }
        }
    }

    /**
     * 隐藏内嵌页每加载完一页报一次。链上有多跳（统一认证→门户→办事大厅→磁贴→Omniselector），
     * 只有落地 `/apps/DailyLeave/` 才代表跳票消费掉、应用会话铸好；此时回灌 Cookie 再重读列表，
     * 以列表真的读到为独立判据（不拿「导航到了」当成功）。其余页只用来兜总时长。
     */
    fun onMintPageFinished(url: String) {
        if (!_state.value.syncing) return
        if (!url.contains("/apps/DailyLeave/")) {
            if (System.currentTimeMillis() - syncStartedAt > SYNC_LIMIT_MS) endSync()
            return
        }
        viewModelScope.launch {
            // 跳票消费与落地之间有一拍，等 Cookie 落定再读
            delay(800)
            if (!_state.value.syncing) return@launch
            coordinator.saveFromWebView(listOf(WebDomain.XG))
            runCatching { repository.leaveList() }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            needsLogin = false,
                            page = page,
                            fetchedAt = System.currentTimeMillis(),
                            error = null,
                        )
                    }
                }
                .onFailure { endSync() }
        }
    }

    fun onMintFailed() {
        if (_state.value.syncing) endSync()
    }

    fun syncTimedOut() {
        if (_state.value.syncing) endSync()
    }

    private fun endSync() {
        _state.update { it.copy(syncing = false, needsLogin = true) }
    }

    /** 登录引导里的「重试」：重新回灌一次 Cookie；本机有票就再静默补一次会话。 */
    fun retrySession() {
        _state.update { it.copy(needsLogin = false) }
        bootstrap()
    }

    /** 行内撤销。[record] 必须来自当前 [UiState.page]，按钮名才与行号对得上。 */
    fun revoke(record: XgLeaveRecord) {
        val page = _state.value.page ?: return
        if (_state.value.revokingId != null) return
        _state.update { it.copy(revokingId = record.id, error = null, message = null) }
        viewModelScope.launch {
            runCatching { repository.revokeLeave(record, page) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            revokingId = null,
                            page = result.list,
                            fetchedAt = System.currentTimeMillis(),
                            message = result.message.ifEmpty {
                                if (result.ok) "已撤销" else "撤销没有生效，请重试"
                            },
                            messageOk = result.ok,
                        )
                    }
                }
                .onFailure { t ->
                    if (t is XgAuthException) {
                        _state.update { it.copy(revokingId = null, needsLogin = true, error = null) }
                    } else {
                        _state.update {
                            it.copy(revokingId = null, error = t.message ?: "撤销失败，请稍后重试")
                        }
                    }
                }
        }
    }

    /** 从详情 / 表单 / 登录页回来时重读一次。首帧加载或补会话还在跑就不重复发。 */
    fun refreshOnReturn() {
        val s = _state.value
        if (s.loading || s.refreshing || s.syncing) return
        if (s.needsLogin) bootstrap() else if (s.page != null) refresh()
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private companion object {
        /** 静默铸会话的总时长上限；超过就换登录引导，不让页面一直空转。 */
        const val SYNC_LIMIT_MS = 35_000L
    }
}