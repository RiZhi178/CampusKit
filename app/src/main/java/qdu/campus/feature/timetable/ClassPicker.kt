package qdu.campus.feature.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import qdu.campus.data.local.db.UgClassRef
import qdu.campus.data.repository.KcapTimetableRepository
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.platform.designsystem.QduSelectField
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.DialogButton
import qdu.campus.platform.designsystem.QduButtonKind

data class ClassPickerUiState(
    val loading: Boolean = true,
    val semesterLabel: String = "",
    /** 班级目录：班名 + 归属学院（清洗阶段对照专业设置表归类；空 = 未分类）。 */
    val classes: List<UgClassRef> = emptyList(),
    val chosen: Set<String> = emptySet(),
    val query: String = "",
    /** 年级筛选（「26级」）；null = 还没选。两级选择的第一级。 */
    val yearFilter: String? = null,
    /** 学院筛选；null = 还没选。第二级，候选随年级联动。 */
    val collegeFilter: String? = null,
    /** 勾选的这些班一共排了多少节课，随勾随变，让用户确认「这就是我的课」。 */
    val courseCount: Int = 0,
    /** 噪声班名（教学班号、分项编号）的附注：学院 + 课程名，让学生知道这串编号是什么。 */
    val annotations: Map<String, String> = emptyMap(),
    /** 随包发布的表里没有这个学期：要说明，不能给一个空的勾选框。 */
    val unavailable: Boolean = false,
)

/**
 * 全校本科课程总表里挑出自己的班级。
 *
 * 这是本科课表唯一的「导入」动作：数据本来就在应用包里，勾了班就有课表，
 * 顺带把学期与学期起始日按表里写好的定下来。
 */
@HiltViewModel
class ClassPickerViewModel @Inject constructor(
    private val repository: KcapTimetableRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ClassPickerUiState())
    val state: StateFlow<ClassPickerUiState> = _state.asStateFlow()

    private var semesterId: String = ""

    init {
        viewModelScope.launch {
            val requested = settings.semesterId.first()
            val semester = repository.semesterOption(requested)
            semesterId = semester?.id ?: requested
            if (semester == null) {
                _state.value = _state.value.copy(loading = false, unavailable = true)
                return@launch
            }
            val classes = repository.classDirectory(semesterId)
            val chosen = settings.undergraduateClasses.first()
            _state.value = ClassPickerUiState(
                loading = false,
                semesterLabel = semester.label,
                classes = classes,
                chosen = chosen,
                courseCount = repository.courseCount(semesterId, chosen),
                annotations = buildAnnotations(semesterId, classes),
            )
        }
    }

    /**
     * 原始表把少数几门课的「上课班级」填成了教学班号或「1-1」这类分项编号
     * （教育实习、医学统计学等，见 docs/interfaces/jwc-kcap.md 第七节）。
     * 它们在勾选列表里像乱码，但清掉就丢课，只能原样保留——靠附注说清来历：
     * 学院 + 这个班名下排的课。
     */
    private suspend fun buildAnnotations(
        semesterId: String,
        classes: List<UgClassRef>,
    ): Map<String, String> {
        val noise = classes.filter { !it.className.any { ch -> ch in '\u4e00'..'\u9fff' } }
        if (noise.isEmpty()) return emptyMap()
        return noise.associate { ref ->
            val courses = repository.courseNamesOfClass(semesterId, ref.className)
                .joinToString("/")
            val note = listOf(ref.college.ifBlank { "未分类" }, courses)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            ref.className to note
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    /** 年级变化时联动清掉学院：学院候选是按年级算的，旧选择可能不在新候选里。 */
    fun setYearFilter(value: String?) {
        _state.value = _state.value.copy(yearFilter = value, collegeFilter = null)
    }

    fun setCollegeFilter(value: String?) {
        _state.value = _state.value.copy(collegeFilter = value)
    }

    fun toggle(name: String) {
        val current = _state.value.chosen
        update(if (name in current) current - name else current + name)
    }

    /** 当前搜索结果的班一次全勾上：一个专业往往十几个班，逐个点太慢。 */
    fun selectMatching() = update(_state.value.chosen + visible(_state.value).map { it.className })

    fun clearAll() = update(emptySet())

    fun applyToTimetable(onDone: () -> Unit) {
        val chosen = _state.value.chosen
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            repository.applyClasses(semesterId, chosen)
            onDone()
        }
    }

    private fun update(next: Set<String>) {
        _state.value = _state.value.copy(chosen = next)
        viewModelScope.launch {
            val count = repository.courseCount(semesterId, next)
            // 连点时先算完的那一个可能后落地；只在勾的还是这一份时才写。
            if (_state.value.chosen == next) {
                _state.value = _state.value.copy(courseCount = count)
            }
        }
    }
}

/** 班名开头的两位年级（26口腔01班 → 26级），只认 2 开头：教学班号（441107…）不是年级，归入「其他」。 */
internal fun yearOf(className: String): String? =
    "^(2\\d)".toRegex().find(className)?.groupValues?.get(1)?.plus("级")

/** 筛选口径的年级桶：无年级前缀的统一落「其他」，与下拉里的「其他」对得上。 */
internal fun yearBucketOf(className: String): String = yearOf(className) ?: "其他"

/**
 * 超 4 个字的学院显示简称（下拉与按钮里省横向空间）；筛选与数据仍用全名。
 * 逐个人工定名——机械去「学」会闹出「口腔医院」这种歧义。
 */
private val COLLEGE_SHORT = mapOf(
    "马克思主义学院" to "马院",
    "政治与公共管理学院" to "政管院",
    "教育科学学院" to "教科院",
    "文学与新闻传播学院" to "文新院",
    "数学与统计学院" to "数统院",
    "物理科学学院" to "物理院",
    "化学化工学院" to "化工院",
    "生命科学学院" to "生科院",
    "机电工程学院" to "机电院",
    "材料科学与工程学院" to "材料院",
    "自动化学院" to "自动化院",
    "电气工程学院" to "电气院",
    "电子信息学院（微纳技术学院）" to "电子信息",
    "计算机科学技术学院" to "计算机院",
    "环境与地理科学学院" to "环地院",
    "纺织服装学院" to "纺服院",
    "青岛医学院" to "青医院",
    "口腔医学院" to "口腔院",
    "公共卫生学院" to "公卫院",
)

internal fun collegeShort(name: String): String = COLLEGE_SHORT[name] ?: name

/**
 * 学院显示顺序：马院第一（高校惯例），其余按教务处《本科专业设置一览表》的官方
 * 出现顺序（docs/interfaces/jwc-kcap.md 引用的那张表）。表外的学院排在最后按名字排。
 */
private val COLLEGE_ORDER = listOf(
    "马克思主义学院",
    "历史学院", "经济学院", "法学院", "政治与公共管理学院",
    "教育科学学院", "体育学院", "文学与新闻传播学院", "外语学院",
    "数学与统计学院", "物理科学学院", "化学化工学院", "生命科学学院",
    "机电工程学院", "材料科学与工程学院", "自动化学院", "电气工程学院",
    "电子信息学院（微纳技术学院）", "计算机科学技术学院", "环境与地理科学学院",
    "纺织服装学院", "青岛医学院", "口腔医学院", "公共卫生学院",
    "药学院", "护理学院", "商学院", "艺术学院",
)

internal fun collegeOrder(name: String): Int = COLLEGE_ORDER.indexOf(name).let {
    if (it >= 0) it else COLLEGE_ORDER.size
}

/** 两级选择框的宽度配比（一位小数）：年级名最多 3 字，学院简称最长 4 字 + 下拉箭头。 */
private const val YEAR_PICKER_WEIGHT = 0.9f
private const val COLLEGE_PICKER_WEIGHT = 1.1f

private fun visible(state: ClassPickerUiState): List<UgClassRef> {
    val query = state.query.trim()
    var list = state.classes
    if (state.yearFilter != null) list = list.filter { yearBucketOf(it.className) == state.yearFilter }
    if (state.collegeFilter != null) list = list.filter { it.college == state.collegeFilter }
    if (query.isNotEmpty()) list = list.filter { it.className.contains(query, ignoreCase = true) }
    return list
}

@Composable
fun ClassPickerDialog(
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
    viewModel: ClassPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val shown = visible(state)
    // 学院候选随年级联动：先选年级，学院列表只留该年级真正有的学院，按学院代码顺序排
    val colleges = remember(state.classes, state.yearFilter) {
        state.classes
            .filter { state.yearFilter == null || yearBucketOf(it.className) == state.yearFilter }
            .map { it.college }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { collegeOrder(it) }
    }
    var yearMenu by remember { mutableStateOf(false) }
    var collegeMenu by remember { mutableStateOf(false) }
    // 年级候选：班名开头两位数字；教学班号这类无年级的归入「其他」（存在才列）
    val years = remember(state.classes) {
        val named = state.classes.mapNotNull { yearOf(it.className) }.distinct().sorted()
        if (named.size < state.classes.size) named + listOf("其他") else named
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择我的班级") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        state.loading -> "正在读课程总表…"
                        state.unavailable -> "随包发布的课程总表里没有这个学期，等下次更新应用。"
                        else -> "${state.semesterLabel} · 全校 ${state.classes.size} 个班"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.loading && !state.unavailable) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        label = { Text("搜索班级") },
                        placeholder = { Text("如：24 计算机、口腔") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 两级选择：先年级、再学院，两级都选了才列出班级——
                    // 绝不把一千多个班平铺到用户面前。触发框与课表页的学期/日期
                    // 共用同一形态（QduSelectField 描边字段）。
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(modifier = Modifier.weight(YEAR_PICKER_WEIGHT)) {
                            QduSelectField(
                                label = "年级",
                                value = state.yearFilter ?: "未选择",
                                onClick = { yearMenu = true },
                            )
                            DropdownMenu(expanded = yearMenu, onDismissRequest = { yearMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("全部年级") },
                                    onClick = {
                                        viewModel.setYearFilter(null)
                                        yearMenu = false
                                    },
                                )
                                years.forEach { year ->
                                    DropdownMenuItem(
                                        text = { Text(year) },
                                        onClick = {
                                            viewModel.setYearFilter(year)
                                            yearMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(COLLEGE_PICKER_WEIGHT)) {
                            QduSelectField(
                                label = "学院",
                                value = state.collegeFilter?.let { collegeShort(it) } ?: "未选择",
                                onClick = { collegeMenu = true },
                                enabled = state.yearFilter != null,
                            )
                            DropdownMenu(expanded = collegeMenu, onDismissRequest = { collegeMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("全部学院") },
                                    onClick = {
                                        viewModel.setCollegeFilter(null)
                                        collegeMenu = false
                                    },
                                )
                                colleges.forEach { college ->
                                    DropdownMenuItem(
                                        text = { Text(collegeShort(college)) },
                                        onClick = {
                                            viewModel.setCollegeFilter(college)
                                            collegeMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "已选 ${state.chosen.size} 个班 · ${state.courseCount} 节课",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            QduButton(
                                text = if (state.query.isBlank()) "全选" else "全选结果",
                                onClick = viewModel::selectMatching,
                                kind = QduButtonKind.Ghost,
                                small = true,
                            )
                            QduButton(
                                text = "清空",
                                onClick = viewModel::clearAll,
                                kind = QduButtonKind.Ghost,
                                small = true,
                            )
                        }
                    }
                    // 两级都没选时默认只给提示；但搜索是定向行为，有关键词就直接出结果
                    val showList = (state.yearFilter != null && state.collegeFilter != null) ||
                        state.query.isNotBlank()
                    if (showList) {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            items(shown, key = { it.className }) { ref ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Checkbox) { viewModel.toggle(ref.className) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = ref.className in state.chosen, onCheckedChange = null)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ref.className,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        state.annotations[ref.className]?.let { note ->
                                            Text(
                                                text = note,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            if (shown.isEmpty()) {
                                item {
                                    Text(
                                        text = "这个年级与学院组合下没有班级。班级名里的数字和汉字" +
                                            "都要对上，比如「24高材」。",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "先选年级，再选学院，才会列出班级。班级与学院都来自教务处" +
                                "公开数据，选错了可随时回来重选。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .height(300.dp)
                                .fillMaxWidth()
                                .wrapContentHeight(Alignment.CenterVertically),
                        )
                    }
                    Text(
                        text = "课表来自教务处公开的课程总表。学期与学期起始日随表发布，" +
                            "觉得不对可在「学期与节次」里改。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            DialogButton(
                text = "应用到课表",
                kind = QduButtonKind.Primary,
                enabled = state.chosen.isNotEmpty(),
                onClick = { viewModel.applyToTimetable(onApplied) },
            )
        },
        dismissButton = {
            DialogButton(
                text = "取消",
                onClick = onDismiss,
                kind = QduButtonKind.Ghost,
            )
        },
    )
}
