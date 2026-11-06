package qdu.campus.feature.appraisal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

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
                        _state.update { it.copy(loading = false, needsLogin = true, error = null) }
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

    /** 从学工门户回来时重读一次；只在确实提示过「缺会话」时才补这一下。 */
    fun refreshOnReturn() {
        if (!_state.value.needsLogin) return
        refresh()
    }
}
