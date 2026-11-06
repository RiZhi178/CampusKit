package qdu.campus.feature.electricity

import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import qdu.campus.data.remote.zzmp.ZzmpSession
import qdu.campus.domain.model.RoomAccount
import qdu.campus.domain.model.WebDomain
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.domain.repository.ZzmpRepository
import qdu.campus.platform.common.ZzmpAuthException
import qdu.campus.platform.web.WebSessionCoordinator
import javax.inject.Inject

/**
 * 宿舍电费首页：绑定的编号 + 服务端回的那几行账户信息。
 *
 * 数据源只有充值页自己的查询接口（`selectAndCheckOrder`），它只给当前快照，
 * 没有逐日用量端点可接（见 `docs/interfaces/zzmp.md` 的排除项），所以这一页不画用量。
 * 不落缓存：实时查、失败就如实说，不拿旧数当新数。
 *
 * 会话补齐：`ZzmpAuthException` 且本机还有统一认证票时，挂一个 1dp 隐藏页走
 * `CampusService.ELECTRICITY` 的 qdsso 桥静默换票，落地后从 storage 取
 * `siteInfo.sessionToken`；补两次仍失败才引导去网页端登录。
 */
@HiltViewModel
class DormElectricityViewModel @Inject constructor(
    private val repository: ZzmpRepository,
    private val settings: SettingsRepository,
    private val coordinator: WebSessionCoordinator,
    private val zzmpSession: ZzmpSession,
) : ViewModel() {

    data class UiState(
        val code: String = "",
        val checking: Boolean = false,
        val account: RoomAccount? = null,
        /** 本次查询成功落地的时刻（epoch millis），只活在内存里，用于「查询于 HH:MM」。 */
        val checkedAt: Long? = null,
        val error: String? = null,
        /** 隐藏页正在补 zzmp 会话，界面显示进度而不是报错。 */
        val syncing: Boolean = false,
        /** 补不上、只能重新登录：错误条给「去登录」而不是「重试」。 */
        val needsLogin: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 一轮查询内最多补两次会话，避免「补 → 查 → 还掉线 → 再补」空转。 */
    private var syncAttempts = 0

    init {
        viewModelScope.launch {
            settings.dormElectricityCode.collect { code ->
                val changed = code != _state.value.code
                // 换房间等于换数据源，旧快照与补会话进度一律作废
                _state.value = UiState(code = code)
                syncAttempts = 0
                if (code.isNotBlank() && changed) refresh()
            }
        }
    }

    fun refresh() {
        val code = _state.value.code
        if (code.isBlank() || _state.value.checking || _state.value.syncing) return
        // 冷启动时 CookieManager 可能还没回灌，先补上再判「有没有票」
        coordinator.restorePersistedCookies(force = true)
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.checkRoomCode(code) }
                .onSuccess { account ->
                    syncAttempts = 0
                    if (account.isEmpty) {
                        _state.update { it.copy(checking = false, error = "电费系统没返回这个房间的账户信息") }
                    } else {
                        _state.update {
                            it.copy(
                                checking = false,
                                account = account,
                                checkedAt = System.currentTimeMillis(),
                                error = null,
                                needsLogin = false,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (error is ZzmpAuthException) {
                        startSessionSync()
                    } else {
                        _state.update {
                            it.copy(
                                checking = false,
                                error = error.message ?: "宿舍电费信息暂时取不到",
                            )
                        }
                    }
                }
        }
    }

    /** 从网页端登录回来时重试；首次进入 `needsLogin` 为假，不会多打一次查询。 */
    fun refreshOnReturn() {
        if (!_state.value.needsLogin) return
        syncAttempts = 0
        refresh()
    }

    /** 隐藏页落在 zzmp 后调用：等页面脚本写完 storage，再取 `sessionToken` 并落 Cookie。 */
    fun onSyncPageFinished(webView: WebView) {
        if (!_state.value.syncing) return
        viewModelScope.launch {
            delay(SITE_INFO_SETTLE_MS)
            if (!_state.value.syncing) return@launch
            zzmpSession.captureSiteToken(webView)
            coordinator.saveFromWebView(listOf(WebDomain.ZZMP))
            // 不在这里判成功：Cookie 可能齐了但 sessionToken 没写进去，
            // 交给重查去撞真实接口，失败会走「再补一次 / 去登录」。
            _state.update { it.copy(syncing = false) }
            refresh()
        }
    }

    fun onSyncFailed() {
        if (!_state.value.syncing) return
        _state.update {
            it.copy(
                syncing = false,
                error = if (syncAttempts >= MAX_SYNC_ATTEMPTS) LOGIN_REQUIRED else "补登电费会话失败，请重试",
                needsLogin = syncAttempts >= MAX_SYNC_ATTEMPTS,
            )
        }
    }

    private fun startSessionSync() {
        if (!coordinator.hasCasTicket() || syncAttempts >= MAX_SYNC_ATTEMPTS) {
            _state.update {
                it.copy(
                    checking = false,
                    syncing = false,
                    error = LOGIN_REQUIRED,
                    needsLogin = true,
                )
            }
            return
        }
        syncAttempts++
        _state.update { it.copy(checking = false, syncing = true, error = null) }
    }

    private companion object {
        const val MAX_SYNC_ATTEMPTS = 2

        /** 页面 load 事件之后脚本才写 storage，等一拍再读。 */
        const val SITE_INFO_SETTLE_MS = 800L

        const val LOGIN_REQUIRED = "电费系统的登录态已失效，请在网页端重新登录一次"
    }
}
