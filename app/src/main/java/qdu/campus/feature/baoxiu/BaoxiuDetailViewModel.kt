package qdu.campus.feature.baoxiu

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qdu.campus.data.remote.baoxiu.BaoxiuAuthException
import qdu.campus.domain.model.RepairDetail
import qdu.campus.domain.repository.BaoxiuRepository
import qdu.campus.platform.common.RemoteDataException
import javax.inject.Inject

@HiltViewModel
class BaoxiuDetailViewModel @Inject constructor(
    private val repository: BaoxiuRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val detail: RepairDetail? = null,
        val error: String? = null,
        val loginRequired: Boolean = false,
        val cancelling: Boolean = false,
        /** 撤销完成后给一次提示，随后让调用方退回列表。 */
        val cancelled: Boolean = false,
        val actionError: String? = null,
    )

    private val ticketId: String = savedStateHandle.get<String>("ticketId").orEmpty()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (ticketId.isBlank()) {
            _state.value = _state.value.copy(loading = false, error = "缺少工单号")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null, loginRequired = false)
        viewModelScope.launch {
            runCatching { repository.detail(ticketId) }
                .onSuccess { _state.value = _state.value.copy(loading = false, detail = it) }
                .onFailure { t ->
                    val auth = t is BaoxiuAuthException
                    _state.value = _state.value.copy(
                        loading = false,
                        loginRequired = auth,
                        error = if (auth) null else (t as? RemoteDataException)?.message ?: "工单详情获取失败",
                    )
                }
        }
    }

    /** 撤销。原因由弹窗收集，空原因不发请求（与网页端一致）。 */
    fun cancel(reason: String) {
        if (ticketId.isBlank() || _state.value.cancelling) return
        if (reason.isBlank()) {
            _state.value = _state.value.copy(actionError = "请填写撤销原因")
            return
        }
        _state.value = _state.value.copy(cancelling = true, actionError = null)
        viewModelScope.launch {
            runCatching { repository.cancel(ticketId, reason) }
                .onSuccess {
                    _state.value = _state.value.copy(cancelling = false, cancelled = true)
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        cancelling = false,
                        actionError = (t as? RemoteDataException)?.message ?: "撤销失败，请稍后重试",
                    )
                }
        }
    }

    fun consumeActionError() {
        _state.value = _state.value.copy(actionError = null)
    }
}
