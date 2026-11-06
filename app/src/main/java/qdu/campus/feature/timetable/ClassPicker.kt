package qdu.campus.feature.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import qdu.campus.data.repository.KcapTimetableRepository
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.DialogButton
import qdu.campus.platform.designsystem.QduButtonKind

data class ClassPickerUiState(
    val loading: Boolean = true,
    val semesterLabel: String = "",
    val classes: List<String> = emptyList(),
    val chosen: Set<String> = emptySet(),
    val query: String = "",
    /** 勾选的这些班一共排了多少节课，随勾随变，让用户确认「这就是我的课」。 */
    val courseCount: Int = 0,
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
            _state.value = ClassPickerUiState(
                loading = false,
                semesterLabel = semester.label,
                classes = repository.classNames(semesterId),
                chosen = settings.undergraduateClasses.first(),
                courseCount = repository.courseCount(semesterId, settings.undergraduateClasses.first()),
            )
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun toggle(name: String) {
        val current = _state.value.chosen
        update(if (name in current) current - name else current + name)
    }

    /** 当前搜索结果的班一次全勾上：一个专业往往十几个班，逐个点太慢。 */
    fun selectMatching() = update(_state.value.chosen + visible(_state.value))

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

private fun visible(state: ClassPickerUiState): List<String> {
    val query = state.query.trim()
    if (query.isEmpty()) return state.classes
    return state.classes.filter { it.contains(query, ignoreCase = true) }
}

@Composable
fun ClassPickerDialog(
    onDismiss: () -> Unit,
    onApplied: () -> Unit,
    viewModel: ClassPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val shown = visible(state)

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
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(shown) { name ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Checkbox) { viewModel.toggle(name) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = name in state.chosen, onCheckedChange = null)
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (shown.isEmpty()) {
                            item {
                                Text(
                                    text = "没有匹配的班级。班级名里的数字和汉字都要对上，比如「24高材」。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                            }
                        }
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
