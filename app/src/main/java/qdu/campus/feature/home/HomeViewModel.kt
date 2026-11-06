package qdu.campus.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.platform.common.Formats
import qdu.campus.platform.system.NetworkEnvironmentReader
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.Notice
import qdu.campus.domain.model.SessionState
import qdu.campus.domain.repository.NoticeRepository
import qdu.campus.domain.repository.ProfileRepository
import qdu.campus.domain.repository.SessionRepository
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.domain.repository.TimetableRepository
import qdu.campus.domain.usecase.WeekCalculator
import qdu.campus.feature.web.CampusService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val todayCourses: List<Course> = emptyList(),
    val currentCourseId: String? = null,
    val notices: List<Notice> = emptyList(),
    val unreadCount: Int = 0,
    /** 统一认证会话状态，首页顶部的状态卡直接取自它。 */
    val session: SessionState = SessionState.Unknown,
    val studentId: String? = null,
    /** 账号卡上的姓名，来自一卡通账号接口；没同步到时为 null。 */
    val userName: String? = null,
    /** 首页「常用」的卡片，已按本机设置解析过，未设置时是默认集合。 */
    val homeCards: List<CampusService> = CampusService.homeCards,
    val isCampusWifi: Boolean = false,
    val isRefreshing: Boolean = false,
    val ssid: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val noticeRepository: NoticeRepository,
    private val sessionRepository: SessionRepository,
    private val environmentReader: NetworkEnvironmentReader,
    private val settings: SettingsRepository,
    private val profile: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var courses: List<Course> = emptyList()
    private var startDate: LocalDate? = null
    private var today: LocalDate = Formats.today()
    private var nameSyncAttempted = false

    init {
        viewModelScope.launch {
            settings.termStartDate.collect {
                startDate = it.takeIf { v -> v.isNotBlank() }?.let { v ->
                    runCatching { LocalDate.parse(v) }.getOrNull()
                }
                recompute()
            }
        }
        viewModelScope.launch {
            settings.semesterId.collectLatest { semester ->
                timetableRepository.observeCourses(semester).collect {
                    courses = it
                    recompute()
                }
            }
        }
        viewModelScope.launch {
            noticeRepository.observeNotices().collect { list ->
                _state.value = _state.value.copy(
                    notices = list.take(3),
                    unreadCount = list.count { !it.read },
                )
            }
        }
        viewModelScope.launch {
            sessionRepository.state.collect { session ->
                _state.value = _state.value.copy(
                    session = session,
                    studentId = sessionRepository.studentId(),
                    userName = profile.cachedName(),
                )
                if (session == SessionState.Valid) syncName()
            }
        }
        viewModelScope.launch {
            settings.homeCards.collect { ids ->
                _state.value = _state.value.copy(homeCards = CampusService.resolveHomeCards(ids))
            }
        }
        refreshEnvironment()
    }

    /**
     * 姓名只在已登录时取一次：账号卡每次进首页都要显示，但一卡通接口不是本地数据，
     * 每个生命周期打一次不值得。失败静默——退回缓存或「已登录」，不弹错误。
     */
    private fun syncName() {
        if (nameSyncAttempted || !profile.cachedName().isNullOrBlank()) return
        nameSyncAttempted = true
        viewModelScope.launch {
            val name = profile.syncName()
            if (!name.isNullOrBlank()) {
                _state.value = _state.value.copy(userName = name)
            }
        }
    }

    fun refreshEnvironment() {
        val env = environmentReader.current()
        _state.value = _state.value.copy(
            isCampusWifi = env.onCampus,
            ssid = env.ssid,
        )
        today = Formats.today()
        recompute()
    }

    fun refreshContent() {
        if (_state.value.isRefreshing) return
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch {
            try {
                timetableRepository.refresh()
                noticeRepository.refresh()
            } finally {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }

    private fun recompute() {
        val week = WeekCalculator.currentWeek(startDate, today)
        val dayOfWeek = today.dayOfWeek.value
        val todays = courses.filter { it.day == dayOfWeek && week in it.weeks }
            .sortedBy { it.sections.firstOrNull() ?: Int.MAX_VALUE }
        _state.value = _state.value.copy(
            todayCourses = todays,
            currentCourseId = inSessionCourse(todays, week),
        )
    }

    /**
     * 「在上」只给真正落在当前时段的那一节：按起止时间判，
     * 周次不符的课程已经在上一步过滤掉，不会误标。
     */
    private fun inSessionCourse(todays: List<Course>, week: Int): String? {
        val now = Formats.now()
        return todays.firstOrNull { course ->
            val start = Formats.parseTime(course.startTime) ?: return@firstOrNull false
            val end = Formats.parseTime(course.endTime) ?: return@firstOrNull false
            course.activeOn(week) && now >= start && now < end
        }?.id
    }
}
