package qdu.campus.feature.baoxiu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qdu.campus.data.remote.baoxiu.BaoxiuAuthException
import qdu.campus.domain.model.RepairTicket
import qdu.campus.domain.repository.BaoxiuRepository
import qdu.campus.platform.common.RemoteDataException
import javax.inject.Inject

@HiltViewModel
class BaoxiuListViewModel @Inject constructor(
    private val repository: BaoxiuRepository,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val tickets: List<RepairTicket> = emptyList(),
        val total: Int = 0,
        val error: String? = null,
        /** 会话失效：页面给一个「去网页重新登录」的出口，不在这里静默重试。 */
        val loginRequired: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null, loginRequired = false)
        fetch()
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true, error = null, loginRequired = false)
        fetch()
    }

    private fun fetch() {
        viewModelScope.launch {
            runCatching { repository.tickets(page = 1) }
                .onSuccess { (tickets, total) ->
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        tickets = tickets,
                        total = total,
                        error = null,
                        loginRequired = false,
                    )
                }
                .onFailure { t ->
                    val auth = t is BaoxiuAuthException
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = if (auth) null else (t as? RemoteDataException)?.message ?: "报修列表获取失败",
                        loginRequired = auth,
                    )
                }
        }
    }
}
