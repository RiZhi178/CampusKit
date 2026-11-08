package qdu.campus.feature.appraisal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import qdu.campus.domain.model.WebDomain
import qdu.campus.domain.model.XgAwardPage
import qdu.campus.domain.repository.XgRepository
import qdu.campus.platform.common.XgAuthException
import qdu.campus.platform.web.WebSessionCoordinator
import javax.inject.Inject

/**
 * 评奖评优进度。
 *
 * 会话补齐按「先手点跑通」的口径做：这一页只读，读不到会话就说明白——
 * 去学工门户点一次「评奖评优管理」，回来自动重读。不挂隐藏 WebView 替人点，
 * 因为学工的跳票只由门户页面签发，模拟那一跳等于自己造一套免登链。
 */
@HiltViewModel
class AppraisalViewModel @Inject constructor(
    private val repository: XgRepository,
    private val coordinator: WebSessionCoordinator,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val page: XgAwardPage? = null,
        /** 本次读取成功的时刻（epoch millis），只活在内存里，用于「数据更新于」。 */
        val fetchedAt: Long? = null,
        val error: String? = null,
        /** 不是网络问题，是学工会话没有：提示语与「去学工系统」按钮都跟着这个走。 */
        val needsLogin: Boolean = false,
        /** 正在用隐藏内嵌页静默自动登录（替点门户磁贴），页面显示进度并把 WebView 藏起来。 */
        val syncing: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 静默自动登录的起始时刻；单跳与整链超时都从这里算。 */
    private var mintStartedAt = 0L

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        // 冷启动时 CookieManager 可能还没回灌，先把存过的 Cookie 摆回去再发请求
        coordinator.restorePersistedCookies(force = true)
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.schoolAwardApply() }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            loading = false,
                            page = page,
                            fetchedAt = System.currentTimeMillis(),
                            error = null,
                            needsLogin = false,
                        )
                    }
                }
                .onFailure { error ->
                    if (error is XgAuthException) {
                        if (coordinator.hasCasTicket()) {
                            mintStartedAt = System.currentTimeMillis()
                            _state.update { it.copy(loading = false, syncing = true, needsLogin = false, error = null) }
                        } else {
                            _state.update { it.copy(loading = false, needsLogin = true, error = null) }
                        }
                    } else {
                        _state.update {
                            it.copy(
                                loading = false,
                                error = error.message ?: "评奖评优信息暂时取不到",
                            )
                        }
                    }
                }
        }
    }

    /**
     * 隐藏内嵌页每加载完一页报一次。链上有多跳，只有落地 /apps/PJPY/ 才代表跳票
     * 消费掉、学工会话铸好；此时回灌 Cookie 再重读页面，以真的读到为独立判据。
     * 其余页只用来兜总时长。
     */
    fun onMintPageFinished(url: String) {
        if (!_state.value.syncing) return
        if (!url.contains("/apps/PJPY/")) {
            if (System.currentTimeMillis() - mintStartedAt > 40_000L) endSync()
            return
        }
        viewModelScope.launch {
            delay(800)
            if (!_state.value.syncing) return@launch
            coordinator.saveFromWebView(listOf(WebDomain.XG))
            refresh()
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

    /** 从学工门户回来时重读一次；只在确实提示过「缺会话」时才补这一下。 */
    fun refreshOnReturn() {
        if (!_state.value.needsLogin) return
        refresh()
    }
}
