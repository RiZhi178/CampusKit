package qdu.campus.feature.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.platform.common.Formats
import qdu.campus.platform.common.everyMinute
import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.NoScheduleCourse
import qdu.campus.domain.model.SemesterOption
import qdu.campus.domain.model.TimetableLevel
import qdu.campus.domain.model.WeekSchedule
import qdu.campus.domain.repository.RefreshOutcome
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.domain.repository.TimetableRepository
import qdu.campus.domain.usecase.BuildWeekScheduleUseCase
import qdu.campus.domain.usecase.WeekCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TimetableUiState(
    val selectedWeek: Int = 1,
    val currentWeek: Int = 1,
    val maxWeek: Int = 20,
    val schedule: WeekSchedule? = null,
    val noSchedule: List<NoScheduleCourse> = emptyList(),
    val updatedAt: Long? = null,
    val isRefreshing: Boolean = false,
    val message: String? = null,
    /** 同步失败是因为会话没了：只有重新登录能解决，提示条给「去登录」。 */
    val loginRequired: Boolean = false,
    val hasData: Boolean = false,
    val selectedCourse: Course? = null,
    val semesterId: String = "",
    val termStartDate: String = "",
    val baseSections: List<ClassSection> = emptyList(),
    val sectionOverrides: Map<Int, Pair<String, String>> = emptyMap(),
    /** 页面里的「今天」一律取自这里，不自己读系统时钟：跨零点没有交互时也要翻页。 */
    val today: LocalDate = Formats.today(),
    /** 此刻正在上的课程 id，非本周时恒为空。 */
    val liveCourseIds: Set<String> = emptySet(),
    /** 选中周对应的日期区间文案，「10 月 26 日 – 11 月 1 日」。 */
    val weekRange: String = "",
    /** 本周不上的课程及原因，见 [BuildWeekScheduleUseCase] 的周次过滤。 */
    val weekNotes: List<String> = emptyList(),
    /** 课表来源；null = 这台机器上还没选过，第一次进课表时问一次。 */
    val level: TimetableLevel? = null,
    /** 本科已勾选的班级数，0 表示还没选——页面据此决定要不要开勾选框。 */
    val ugClassCount: Int = 0,
    /** 当前这一路数据源给出的学期候选，「学期与节次」里的学期选择框用它。 */
    val semesterOptions: List<SemesterOption> = emptyList(),
)

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val useCase = BuildWeekScheduleUseCase()

    private val _state = MutableStateFlow(TimetableUiState())
    val state: StateFlow<TimetableUiState> = _state.asStateFlow()

    private var courses: List<Course> = emptyList()
    private var baseSections: List<ClassSection> = emptyList()
    private var sections: List<ClassSection> = emptyList()
    private var sectionOverrides: Map<Int, Pair<String, String>> = emptyMap()
    private var noSchedule: List<NoScheduleCourse> = emptyList()
    private var startDate: LocalDate? = null
    private var userPickedWeek = false
    private var semesterId: String = ""
    private var lastLevel: TimetableLevel? = null

    init {
        viewModelScope.launch {
            settings.termStartDate.collect { value ->
                startDate = value.takeIf { it.isNotBlank() }?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }
                _state.value = _state.value.copy(termStartDate = value)
                recompute()
            }
        }
        viewModelScope.launch {
            settings.sectionTimeOverrides.collect { value ->
                sectionOverrides = value
                _state.value = _state.value.copy(sectionOverrides = value)
                mergeSections()
                recompute()
            }
        }
        viewModelScope.launch {
            settings.semesterId.collectLatest { semester ->
                semesterId = semester
                _state.value = _state.value.copy(semesterId = semester)
                launch {
                    repository.observeCourses(semester).collect {
                        courses = it
                        recompute()
                    }
                }
                launch {
                    repository.observeSections(semester).collect {
                        baseSections = it
                        _state.value = _state.value.copy(baseSections = it)
                        mergeSections()
                        recompute()
                    }
                }
                launch {
                    repository.observeNoSchedule(semester).collect {
                        noSchedule = it
                        recompute()
                    }
                }
                if (courses.isEmpty()) refresh()
            }
        }
        viewModelScope.launch {
            repository.observeSyncState().collect { sync ->
                _state.value = _state.value.copy(updatedAt = sync?.lastSuccessAt)
            }
        }
        viewModelScope.launch {
            settings.timetableLevel.collect { value ->
                _state.value = _state.value.copy(level = value)
                // 换来源等于换掉一整路数据，而学期标识没变，学期那一路的收集器不会重跑。
                // 不主动刷一次，研究生那边就永远不知道该提示登录。
                if (value != null && value != lastLevel) refresh()
                lastLevel = value
            }
        }
        viewModelScope.launch {
            settings.undergraduateClasses.collect { chosen ->
                _state.value = _state.value.copy(ugClassCount = chosen.size)
            }
        }
        viewModelScope.launch {
            repository.observeSemesterOptions().collect { options ->
                _state.value = _state.value.copy(semesterOptions = options)
            }
        }
        // 只吃本机时钟：此刻上到哪节、今天翻没翻页，不该等一次刷新才知道。
        viewModelScope.launch { everyMinute { recompute() } }
    }

    fun selectWeek(week: Int) {
        val max = _state.value.maxWeek
        if (week < 1 || week > max) return
        userPickedWeek = true
        _state.value = _state.value.copy(selectedWeek = week)
        recompute()
    }

    fun backToCurrentWeek() {
        userPickedWeek = false
        recompute()
    }

    fun selectCourse(course: Course?) {
        _state.value = _state.value.copy(selectedCourse = course)
    }

    fun setSemester(value: String) {
        viewModelScope.launch { settings.setSemesterId(value.trim()) }
    }

    /**
     * 换课表来源：研究生系统还是随包发布的本科生课程总表。
     *
     * 这里只改「读哪一路」。本科的学期与学期起始日由课程总表自己带着，
     * 在勾选班级并应用时一并写入，改来源本身不动那两项。
     */
    fun setLevel(value: TimetableLevel) {
        viewModelScope.launch { settings.setTimetableLevel(value) }
    }

    fun setTermStartDate(value: String) {
        viewModelScope.launch { settings.setTermStartDate(value.trim()) }
    }

    fun setSectionOverrides(value: Map<Int, Pair<String, String>>) {
        viewModelScope.launch { settings.setSectionTimeOverrides(value) }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, loginRequired = false)
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.value = _state.value.copy(isRefreshing = true)
        viewModelScope.launch {
            val outcome = runCatching { repository.refresh() }
                .getOrElse { RefreshOutcome.Failure(it.message ?: "课表更新失败") }
            val failure = outcome as? RefreshOutcome.Failure
            _state.value = _state.value.copy(
                isRefreshing = false,
                message = failure?.message,
                loginRequired = failure?.loginRequired == true,
            )
        }
    }

    private fun mergeSections() {
        sections = baseSections.map { section ->
            sectionOverrides[section.index]?.let { (start, end) ->
                section.copy(startTime = start, endTime = end)
            } ?: section
        }
    }

    private fun recompute() {
        val today = Formats.today()
        val current = WeekCalculator.currentWeek(startDate, today)
        val max = maxOf(current, WeekCalculator.maxWeek(courses))
        val selected = when {
            userPickedWeek -> _state.value.selectedWeek.coerceIn(1, max)
            else -> current.coerceIn(1, max)
        }
        val schedule = useCase(courses, sections, selected)
        _state.value = _state.value.copy(
            selectedWeek = selected,
            currentWeek = current,
            maxWeek = max,
            schedule = schedule,
            noSchedule = noSchedule,
            hasData = courses.isNotEmpty() || noSchedule.isNotEmpty(),
            today = today,
            liveCourseIds = if (selected == current) {
                schedule.liveCourseIds(today.dayOfWeek.value, Formats.now())
            } else {
                emptySet()
            },
            weekRange = WeekCalculator.weekDates(startDate, selected)
                ?.let { (monday, sunday) ->
                    "${Formats.monthDay(monday)} – ${Formats.monthDay(sunday)}"
                }
                .orEmpty(),
            weekNotes = weekNotes(courses, schedule),
        )
    }

    /**
     * 本周说明：把因为单双周或非连续周次而看不到的课程点名说出来。
     * 不说，用户看到的只是一节课凭空消失，无从判断是没排还是这周不上。
     */
    private fun weekNotes(courses: List<Course>, schedule: WeekSchedule): List<String> {
        if (schedule.days.values.all { it.isEmpty() }) return emptyList()
        val shown = schedule.days.values.flatten().map { it.id }.toSet()
        return courses.filterNot { it.id in shown }
            .distinctBy { "${it.name}${it.weekLabel}" }
            .map { "「${it.name}」${it.weekLabel}，本周不上" }
    }
}
