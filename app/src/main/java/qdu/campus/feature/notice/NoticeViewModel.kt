package qdu.campus.feature.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.domain.model.Notice
import qdu.campus.domain.model.SyncState
import qdu.campus.domain.model.TimetableLevel
import qdu.campus.domain.repository.NoticeRepository
import qdu.campus.domain.repository.RefreshOutcome
import qdu.campus.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 通知页的页签。[source] 为空表示「全部」。 */
enum class NoticeTab(val source: String?, val label: String) {
    All(null, "全部"),
    Graduate("grad_notice", "研究生院"),
    Jwc("jwc_notice", "教务处"),
}

/** 通知页顶部横幅的一条消息。来源失败与关键词命中回执共用一条通道。 */
data class NoticeMessage(val text: String, val isSuccess: Boolean)

data class NoticeUiState(
    val notices: List<Notice> = emptyList(),
    /** 标题包含任一关键词才显示；空表示不筛。同一份设置也用于推送匹配，跨来源生效。 */
    val keywords: List<String> = emptyList(),
    /** 各数据源各自的同步状态，「数据更新于」按页签分别取。 */
    val syncStates: List<SyncState> = emptyList(),
    val tab: NoticeTab = NoticeTab.All,
    val isRefreshing: Boolean = false,
    val message: NoticeMessage? = null,
) {
    /** 各来源的未读数，页签上显示；「全部」是两源之和。 */
    val unreadBySource: Map<String, Int>
        get() = notices.filter { !it.read }.groupingBy { it.source }.eachCount()

    val visibleNotices: List<Notice>
        get() = notices.filter { notice ->
            (tab.source == null || notice.source == tab.source) &&
                (keywords.isEmpty() || keywords.any { notice.title.contains(it, ignoreCase = true) })
        }

    /** 当前页签按关键词过滤后为空、但库里这一来源其实有条目时，用于区分「没数据」与「没匹配」。 */
    val currentSourceCount: Int
        get() = notices.count { tab.source == null || it.source == tab.source }
}

@HiltViewModel
class NoticeViewModel @Inject constructor(
    private val repository: NoticeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NoticeUiState())
    val state: StateFlow<NoticeUiState> = _state.asStateFlow()

    /** 用户手动切过页签后，层次设置的默认值就不再覆盖它。 */
    private var tabTouched = false

    init {
        viewModelScope.launch {
            repository.observeNotices().collect {
                _state.value = _state.value.copy(notices = it)
            }
        }
        viewModelScope.launch {
            settings.notificationKeywords.collect {
                _state.value = _state.value.copy(keywords = it)
            }
        }
        viewModelScope.launch {
            repository.observeSyncStates().collect {
                _state.value = _state.value.copy(syncStates = it)
            }
        }
        // 默认页签复用课表那边的层次设置：研究生看研究生院、本科生看教务处，谁都没填就给「全部」。
        // 设置还没选过时先落在「全部」，之后选了层次也不强行改，避免用户正在看的时候页签自己跳走。
        viewModelScope.launch {
            settings.timetableLevel.collect { level ->
                if (!tabTouched) {
                    _state.value = _state.value.copy(tab = defaultTab(level))
                }
            }
        }
        if (_state.value.notices.isEmpty()) refresh()
    }

    private fun defaultTab(level: TimetableLevel?): NoticeTab = when (level) {
        TimetableLevel.Graduate -> NoticeTab.Graduate
        TimetableLevel.Undergraduate -> NoticeTab.Jwc
        null -> NoticeTab.All
    }

    fun selectTab(tab: NoticeTab) {
        tabTouched = true
        _state.value = _state.value.copy(tab = tab)
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch {
            val outcome = repository.refresh()
            val message = when (outcome) {
                is RefreshOutcome.Partial ->
                    NoticeMessage("已更新 ${outcome.itemCount} 条；${outcome.failures.joinToString("；")}", false)
                is RefreshOutcome.Failure -> NoticeMessage(outcome.message, false)
                is RefreshOutcome.Success -> null
            }
            _state.value = _state.value.copy(isRefreshing = false, message = message)
        }
    }

    /** 逗号（中英文）、顿号与空格都当分隔符；空串即清空筛选。 */
    fun setKeywords(raw: String) {
        val list = raw.split(',', '，', '、', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        viewModelScope.launch { settings.setNotificationKeywords(list) }
    }

    fun markRead(fingerprint: String) {
        viewModelScope.launch { repository.markRead(fingerprint) }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
