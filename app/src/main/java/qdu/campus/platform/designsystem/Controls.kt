package qdu.campus.platform.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 药丸类型。Default 用 secondaryContainer，其余按原型四型固定。 */
enum class PillKind { Default, Ok, Warn, Err, Prim }

/**
 * 小标签。警示底色用 14% 而不是 20%，浅色下 11px 文字仍要 ≥ 4.5:1。
 * mark 非空时带状态形状，颜色之外再加形状区分。
 */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    kind: PillKind = PillKind.Default,
    mark: StatusLevel? = null,
) {
    val colors = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    val background: Color
    val foreground: Color
    when (kind) {
        PillKind.Default -> {
            background = scheme.secondaryContainer
            foreground = scheme.onSecondaryContainer
        }
        PillKind.Ok -> {
            background = colors.success.copy(alpha = 0.18f)
            foreground = colors.success
        }
        PillKind.Warn -> {
            background = colors.warning.copy(alpha = 0.14f)
            foreground = colors.warning
        }
        PillKind.Err -> {
            background = scheme.errorContainer
            foreground = scheme.onErrorContainer
        }
        PillKind.Prim -> {
            background = scheme.primaryContainer
            foreground = scheme.onPrimaryContainer
        }
    }
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(UiShapes.Pill)
            .background(background)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (mark != null) {
            StatusMark(level = mark)
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
        )
    }
}

/** 按钮四型，尺寸默认 48 高、小档视觉 40 高但热区仍补足 48。 */
enum class QduButtonKind { Primary, Tonal, Ghost, Danger }

/**
 * 弹窗专用按钮：不做右侧双钮横排，主操作与取消各占一行。
 *
 * 宽度按性质区分：Ghost（取消、知道了的陪衬）占半行，其余（确认、清除等要按下去的动作）
 * 占整行。M3 AlertDialog 的按钮槽是 FlowRow，两颗都请求宽于剩余空间时自然换行成竖排。
 */
@Composable
fun DialogButton(
    text: String,
    kind: QduButtonKind,
    onClick: () -> Unit,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    QduButton(
        text = text,
        kind = kind,
        small = small,
        enabled = enabled,
        onClick = onClick,
        fillWidth = true,
        modifier = Modifier.fillMaxWidth(if (kind == QduButtonKind.Ghost) 0.5f else 1f),
    )
}

@Composable
fun QduButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: QduButtonKind = QduButtonKind.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    fillWidth: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // 禁用态不整颗叠透明度：实色底按钮（Primary/Tonal）底与字一起褪色后互相凑近，
    // 弹窗底色上几乎读不出字（ClassPicker 未选班时实测）。改成 M3 惯例的灰容器 + 灰字：
    // 底字对比不变，只靠容器从主色换成灰来交代「按不下去」。
    val background: Color
    val content: Color
    val outline: Color?
    when (kind) {
        QduButtonKind.Primary -> {
            background = if (enabled) scheme.primary else scheme.surfaceContainerHighest
            content = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant
            outline = null
        }
        QduButtonKind.Tonal -> {
            background = if (enabled) scheme.primaryContainer else scheme.surfaceContainerHighest
            content = if (enabled) scheme.onPrimaryContainer else scheme.onSurfaceVariant
            outline = null
        }
        QduButtonKind.Ghost -> {
            background = Color.Transparent
            content = if (enabled) scheme.primary else scheme.onSurfaceVariant
            outline = scheme.outlineVariant
        }
        QduButtonKind.Danger -> {
            background = Color.Transparent
            content = if (enabled) scheme.error else scheme.onSurfaceVariant
            outline = if (enabled) scheme.error.copy(alpha = 0.45f) else scheme.outlineVariant
        }
    }
    Box(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(if (small) 40.dp else 48.dp)
                // fillWidth：让可见的药丸撑满调用方给定的宽度。只改外层 Box 不够——
                // 弹窗槽位里外层 Box 被拉宽后内容仍居中，看上去还是一颗小按钮。
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .clip(UiShapes.Button)
                .background(background)
                .then(if (outline != null) Modifier.border(1.dp, outline, UiShapes.Button) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = if (small) 16.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(if (small) 16.dp else 18.dp),
                    tint = content,
                )
            }
            Text(
                text = text,
                style = if (small) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}

/** 分段选择：容器 surfaceContainer 16 圆角，选中项 primaryContainer。 */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(UiShapes.Overlay)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(UiShapes.Card)
                    .clickable(role = Role.RadioButton) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(UiShapes.Card)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = if (selected) {
                            MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** 单选药丸组，用于楼栋这类有限候选。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(UiShapes.Card)
                    .clickable(role = Role.RadioButton) { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(UiShapes.Card)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        )
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** 输入框上方的小标签。 */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 2.dp, bottom = 6.dp),
    )
}

/**
 * 填充式输入框：48 高、surfaceContainer 底、无下划线。
 * 不用 OutlinedTextField，是因为它的 12 圆角与浮动标签会和原型的紧凑栅格错位。
 */
@Composable
fun QduField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) {
            FieldLabel(label)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(UiShapes.Card)
                .background(
                    if (enabled) {
                        MaterialTheme.colorScheme.surfaceContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                ),
                decorationBox = { innerTextField ->
                    // 占位文字必须画在输入框里面。早先的写法是「值为空时只渲染占位 Text、
                    // 不渲染 BasicTextField」，结果空字段在界面上看得到却点不进去，
                    // 登录页的学号框（初值空）和搜索框都打不了字。
                    Box(modifier = Modifier.padding(vertical = 10.dp)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

/**
 * 「选一天」的统一弹窗：M3 [DatePickerDialog] 的设计系统封装，调用方只认
 * [LocalDate]——UTC 毫秒与日历日的换算收敛在这里，不再散落到各个页面。
 * 选了日期点「确定」回调新日期；没选就点「确定」只关窗不回调。
 */
@Composable
fun QduDatePickerDialog(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            DialogButton(
                text = "确定",
                kind = QduButtonKind.Primary,
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onConfirm(
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate(),
                        )
                    }
                    onDismiss()
                },
            )
        },
        dismissButton = {
            DialogButton(
                text = "取消",
                kind = QduButtonKind.Ghost,
                onClick = onDismiss,
            )
        },
    ) {
        DatePicker(state = pickerState)
    }
}


/**
 * 「选一项」的统一字段：描边卡片 + 上标签/下当前值 + 下拉箭头，点击打开对应选择器
 * （学期、开学日期、时间、筛选项共用同一形态）。禁用态走灰字灰框，不叠透明度。
 */
@Composable
fun QduSelectField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector = Icons.Outlined.ExpandMore,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = CardDefaults.outlinedCardBorder(enabled = enabled),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (enabled) scheme.surface else scheme.surfaceContainerHighest,
            contentColor = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
