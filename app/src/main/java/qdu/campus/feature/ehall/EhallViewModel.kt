package qdu.campus.feature.ehall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.domain.model.EhallApp
import qdu.campus.domain.model.EhallTaskCounts
import qdu.campus.domain.repository.EhallRepository
import qdu.campus.platform.common.EhallAuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EhallUiState(
    val services: List<EhallApp> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedOut: Boolean = false,
    /** 侧边栏任务计数。null 表示没取到——它只是列表上的一行提示，取不到不报错。 */
    val taskCounts: EhallTaskCounts? = null,
) {
    /**
     * 过滤后的条目按服务端给的分类顺序分组。
     * 分类为空时归到「未分类」，不丢条目——办事大厅的清单少一个就是一项办不成的事。
     */
    val grouped: List<Pair<String, List<EhallApp>>>
        get() {
            val needle = query.trim()
            val matched = if (needle.isEmpty()) {
                services
            } else {
                services.filter {
                    it.name.contains(needle, ignoreCase = true) ||
                        it.description.contains(needle, ignoreCase = true) ||
                        it.category.contains(needle, ignoreCase = true)
                }
            }
            return matched
                .groupBy { it.category.ifBlank { UNGROUPED } }
                .toList()
        }

    val shownCount: Int get() = grouped.sumOf { it.second.size }

    private companion object {
        const val UNGROUPED = "未分类"
    }
}

@HiltViewModel
class EhallViewModel @Inject constructor(
    private val repository: EhallRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EhallUiState())
    val state: StateFlow<EhallUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun refresh() = load()

    /** 从内嵌页登录回来：先清掉掉线态，否则重登成功但页面还停在「未登录」。 */
    fun retryLogin() {
        _state.update { it.copy(loggedOut = false, error = null) }
        load()
    }

    /** 磁贴去处交给服务端解析器，本层不拼第三方应用的地址。 */
    fun entryUrl(app: EhallApp): String = repository.entryUrl(app)

    private fun load() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            // 计数与清单并发取：计数慢或坏掉都不该拖住列表，它坏了就当没有计数。
            val counts = async { runCatching { repository.taskCounts() }.getOrNull() }
            val outcome = runCatching { repository.services() }
            _state.update { current ->
                current.copy(
                    loading = false,
                    services = outcome.getOrNull() ?: current.services,
                    error = outcome.exceptionOrNull()?.let { failureMessage(it) },
                    loggedOut = outcome.exceptionOrNull() is EhallAuthException,
                    // 列表都没取到就别说计数：那一定是同一份会话坏了，留着旧数字会误导
                    taskCounts = if (outcome.isSuccess) counts.await() else null,
                )
            }
        }
    }

    private fun failureMessage(error: Throwable): String = when (error) {
        is EhallAuthException -> error.message ?: "办事大厅需要重新登录"
        else -> error.message ?: "办事大厅暂时打不开"
    }
}
