package qdu.campus.feature.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.data.remote.xg.XgFormValidator
import qdu.campus.data.remote.xg.XgLeaveTime
import qdu.campus.domain.model.XgControl
import qdu.campus.domain.model.XgControlKind
import qdu.campus.domain.model.XgLabelledValue
import qdu.campus.domain.model.XgLeaveApproval
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.designsystem.Banner
import qdu.campus.platform.designsystem.ChipGroup
import qdu.campus.platform.designsystem.DialogButton
import qdu.campus.platform.designsystem.FieldLabel
import qdu.campus.platform.designsystem.ListRow
import qdu.campus.platform.designsystem.Pill
import qdu.campus.platform.designsystem.PillKind
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.SessionGate
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.QduDatePickerDialog
import qdu.campus.platform.designsystem.QduField
import qdu.campus.platform.designsystem.QduSelectField
import qdu.campus.platform.designsystem.SectionCard
import qdu.campus.platform.designsystem.StatusLevel
import java.time.LocalDate

/**
 * 日常请假：新建表单兼详情。`id` 为空是新建（可填 + 提交），带记录号是详情（只读 + 撤销）。
 *
 * 整页按服务端给的分区与控件清单渲染，不写死字段名：校验、必填星号、依赖显隐都取自控件元数据。
 * 「是否离校」选「是」才显示外出信息那一整段，靠 [XgFormValidator.isActive] 按 vdependence 判定，
 * 与站点 IsOutChange() 的显隐一致。省市区是回发联动，选完上级换一份新页面重绑下级选项，
 * 联动期间下级下拉禁用，避免在旧选项上乱点。
 *
 * 详情只读：修改并重新提交、附件都不在原生范围内，页脚明说去网页端办理。
 * 提交与撤销都是终态写操作，成败以服务端回话加列表记录增减为准，成功才退回列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveFormScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    onRevoked: () -> Unit,
    onOpenWebLogin: () -> Unit,
    viewModel: LeaveFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val page = state.page
    var optionSheet by remember { mutableStateOf<XgControl?>(null) }
    var revokeConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) { if (state.done) onSubmitted() }
    LaunchedEffect(state.revoked) { if (state.revoked) onRevoked() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "申请请假" else "请假详情") },
                navigationIcon = { QduBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.needsLogin -> SessionGate(
                    title = "请假需要先登录",
                    reason = "去学工系统里点一下「日常请假」，\n回来就可以提交申请了。",
                    onLogin = onOpenWebLogin,
                    modifier = Modifier.fillMaxSize(),
                    loginLabel = "去学工系统",
                    onRetry = viewModel::load,
                )

                state.loading && page == null -> AppLoading(modifier = Modifier.fillMaxSize())

                state.error != null && page == null -> CenterBlock(
                    title = "请假信息没能加载出来",
                    description = state.error.orEmpty(),
                    actionLabel = "重试",
                    onAction = viewModel::load,
                )

                state.blocked -> CenterBlock(
                    title = "暂时不能申请请假",
                    description = state.blockedMessage.ifEmpty { "当前状态不能请假或有请假未完成！" },
                    actionLabel = "重新检查",
                    onAction = viewModel::load,
                )

                page != null -> {
                    val values = page.form.values(state.edits)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.error != null && state.fieldErrors.isEmpty()) {
                            Banner(text = state.error.orEmpty())
                        }
                        if (state.message != null && !state.messageOk) {
                            Banner(text = state.message.orEmpty())
                        }
                        if (state.cascading) {
                            Text(
                                text = "正在加载下级选项…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (page.student.isNotEmpty()) {
                            SectionCard(title = "学生基本信息") {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    page.student.forEach { InfoRow(it.label, it.value) }
                                }
                            }
                        }

                        page.approval?.let { ApprovalCard(it) }

                        page.sections.forEach { section ->
                            val visible = section.fields.filter {
                                XgFormValidator.isActive(it, page.form, values)
                            }
                            if (visible.isNotEmpty() || section.notes.isNotEmpty()) {
                                SectionCard(title = section.title.ifEmpty { null }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        section.notes.forEach { NoteRow(it) }
                                        visible.forEach { control ->
                                            FieldRow(
                                                control = control,
                                                value = values[control.name] ?: control.value,
                                                editable = viewModel.isNew && control.editable,
                                                error = state.fieldErrors.firstOrNull { it.name == control.name }?.message,
                                                cascading = state.cascading,
                                                onText = { viewModel.setValue(control.name, it) },
                                                onPickOption = { optionSheet = control },
                                                onRadio = { index ->
                                                    viewModel.setValue(control.name, control.options[index].value)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (viewModel.isNew) {
                            if (state.fieldErrors.isNotEmpty()) {
                                Text(
                                    text = "有 ${state.fieldErrors.size} 项需要修改",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            QduButton(
                                text = if (state.submitting) "正在提交…" else page.submitLabel.ifEmpty { "提交申请" },
                                onClick = viewModel::submit,
                                enabled = !state.submitting && !state.cascading,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "附件与修改在途申请请在网页端办理。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (page.canRevoke) {
                            QduButton(
                                text = if (state.revoking) "正在撤销…" else "撤销申请",
                                onClick = { revokeConfirm = true },
                                kind = QduButtonKind.Danger,
                                enabled = !state.revoking,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "撤销后进入终态，不能再恢复；修改并重新提交请在网页端办理。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    val sheetControl = optionSheet
    if (sheetControl != null) {
        val current = state.edits[sheetControl.name] ?: sheetControl.value
        ModalBottomSheet(onDismissRequest = { optionSheet = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = fieldLabel(sheetControl),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                if (sheetControl.options.isEmpty()) {
                    Text(
                        text = "没有可选项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                sheetControl.options.forEach { option ->
                    ListRow(
                        title = option.text.ifEmpty { "（清空）" },
                        chevron = false,
                        trailing = if (option.value == current) {
                            { Icon(Icons.Filled.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        onClick = {
                            viewModel.selectOption(sheetControl.name, option.value)
                            optionSheet = null
                        },
                    )
                }
            }
        }
    }

    if (revokeConfirm) {
        AlertDialog(
            onDismissRequest = { revokeConfirm = false },
            title = { Text("撤销请假") },
            text = {
                Text(
                    text = (page?.revokeConfirm?.ifEmpty { "您确定要撤销这条申请记录吗？" }
                        ?: "您确定要撤销这条申请记录吗？") + " 撤销后进入终态，不能再恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                DialogButton(
                    text = "确认撤销",
                    kind = QduButtonKind.Danger,
                    enabled = !state.revoking,
                    onClick = {
                        revokeConfirm = false
                        viewModel.revoke()
                    },
                )
            },
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = { revokeConfirm = false },
                )
            },
        )
    }
}

@Composable
private fun ApprovalCard(approval: XgLeaveApproval) {
    SectionCard(
        title = "审批进度",
        trailing = if (approval.status.isBlank()) null else {
            { Pill(text = approval.status, kind = approvalKind(approval.status), mark = approvalMark(approval.status)) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            approval.steps.forEach {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            approval.records.forEach {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (approval.steps.isEmpty() && approval.records.isEmpty()) {
                Text(
                    text = "暂时没有审批记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 一个控件按类型渲染。只读（详情或禁用项）印值，可填的按 kind 给输入控件。 */
@Composable
private fun FieldRow(
    control: XgControl,
    value: String,
    editable: Boolean,
    error: String?,
    cascading: Boolean,
    onText: (String) -> Unit,
    onPickOption: () -> Unit,
    onRadio: (Int) -> Unit,
) {
    val label = fieldLabel(control)
    val marked = if (control.required && editable) "$label *" else label
    if (!editable) {
        ReadOnlyField(label = marked, value = displayValue(control, value))
        return
    }
    when (control.kind) {
        XgControlKind.SELECT -> QduSelectField(
            label = marked,
            value = optionText(control, value).ifBlank { "请选择" },
            enabled = !cascading,
            onClick = onPickOption,
            modifier = Modifier.fillMaxWidth(),
        )

        XgControlKind.RADIO -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(marked)
            ChipGroup(
                options = control.options.map { it.text },
                selectedIndex = control.options.indexOfFirst { it.value == value }.takeIf { it >= 0 },
                onSelect = onRadio,
            )
            if (error != null) FieldError(error)
        }

        else -> {
            if (control.dateTime) {
                DateTimeField(label = marked, value = value, onValue = onText, error = error)
            } else {
                val multiline = control.kind == XgControlKind.TEXTAREA
                QduField(
                    value = value,
                    onValueChange = onText,
                    label = marked,
                    placeholder = control.placeholder.ifEmpty { null },
                    supportingText = control.maxLength?.let { "${value.length}/$it" },
                    singleLine = !multiline,
                    keyboardOptions = keyboardFor(control, multiline),
                )
                if (error != null) FieldError(error)
            }
        }
    }
}

/**
 * 日期时间字段。站点 laydate 只到小时，但实测 `yyyy-MM-dd HH:mm` 能提交，且同小时的
 * 结束>开始比较要用到分钟，所以这里给到分钟：先选日期再选时间，拼成 [XgLeaveTime.PATTERN]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    error: String?,
) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        QduSelectField(
            label = label,
            value = value.ifBlank { "请选择日期时间" },
            icon = Icons.Outlined.CalendarMonth,
            onClick = { showDate = true },
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) FieldError(error)
    }

    if (showDate) {
        QduDatePickerDialog(
            initialDate = XgLeaveTime.parse(value)?.toLocalDate() ?: LocalDate.now(),
            onConfirm = {
                pickedDate = it
                showTime = true
            },
            onDismiss = { showDate = false },
        )
    }

    if (showTime) {
        val initialTime = XgLeaveTime.parse(value)?.toLocalTime()
        val timeState = rememberTimePickerState(
            initialHour = initialTime?.hour ?: 8,
            initialMinute = initialTime?.minute ?: 0,
            is24Hour = true,
        )
        TimePickerDialog(
            onDismissRequest = { showTime = false },
            title = { Text("选择时间") },
            confirmButton = {
                DialogButton(
                    text = "确定",
                    kind = QduButtonKind.Primary,
                    onClick = {
                        val date = pickedDate ?: LocalDate.now()
                        onValue(XgLeaveTime.format(date.atTime(timeState.hour, timeState.minute)))
                        showTime = false
                    },
                )
            },
            dismissButton = {
                DialogButton(text = "取消", kind = QduButtonKind.Ghost, onClick = { showTime = false })
            },
        ) {
            TimePicker(state = timeState)
        }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FieldLabel(label)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NoteRow(note: XgLabelledValue) {
    if (note.value.isBlank()) return
    if (note.label.isBlank()) {
        Text(
            text = note.value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FieldLabel(note.label)
            Text(
                text = note.value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FieldError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp),
    )
}

@Composable
private fun CenterBlock(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlaceholderBlock(title = title, description = description, actionLabel = actionLabel, onAction = onAction)
    }
}

private fun fieldLabel(control: XgControl): String =
    control.label.ifEmpty { control.id.substringAfterLast('_') }

private fun optionText(control: XgControl, value: String): String =
    control.options.firstOrNull { it.value == value }?.text?.takeIf { it.isNotEmpty() } ?: value

private fun displayValue(control: XgControl, value: String): String = when (control.kind) {
    XgControlKind.SELECT, XgControlKind.RADIO, XgControlKind.CHECKBOX -> optionText(control, value).ifEmpty { "—" }
    else -> value.ifEmpty { "—" }
}

private fun keyboardFor(control: XgControl, multiline: Boolean): KeyboardOptions {
    val type = when {
        control.validators.any { it == "Phone" || it == "LinkTel" } -> KeyboardType.Phone
        control.validators.contains("OrderNum") -> KeyboardType.Number
        else -> KeyboardType.Text
    }
    return KeyboardOptions(keyboardType = type, imeAction = if (multiline) ImeAction.Default else ImeAction.Next)
}

private fun approvalKind(status: String): PillKind = when {
    status.contains("通过") && !status.contains("不") -> PillKind.Ok
    status.contains("撤销") -> PillKind.Default
    status.contains("不") || status.contains("拒") -> PillKind.Err
    status.contains("等待") || status.contains("审批") -> PillKind.Warn
    else -> PillKind.Default
}

private fun approvalMark(status: String): StatusLevel? = when {
    status.contains("通过") && !status.contains("不") -> StatusLevel.SUCCESS
    status.contains("撤销") -> StatusLevel.BLOCKED
    status.contains("不") || status.contains("拒") -> StatusLevel.DANGER
    status.contains("等待") || status.contains("审批") -> StatusLevel.WARNING
    else -> null
}