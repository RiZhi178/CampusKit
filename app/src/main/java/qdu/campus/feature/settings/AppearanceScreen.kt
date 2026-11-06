package qdu.campus.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.platform.designsystem.GroupHead
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.Segmented
import qdu.campus.platform.designsystem.StatusLevel
import qdu.campus.platform.designsystem.StatusMark
import qdu.campus.platform.designsystem.UiShapes
import qdu.campus.platform.designsystem.UiSpacing
import qdu.campus.platform.designsystem.cardBorderColor
import qdu.campus.platform.designsystem.generateColorScheme
import qdu.campus.platform.designsystem.rowDivider
import qdu.campus.domain.model.AppearanceMode
import qdu.campus.domain.model.AppearanceScheme

/**
 * 外观页。方案、明暗、深色是否保留颜色三项独立保存，点任一方案本页立即换色。
 * 选中态用描边 + 勾选表达，不只靠颜色，色觉异常也能分辨。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val scheme by viewModel.scheme.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val darkTinted by viewModel.darkTinted.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (mode) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("外观") },
                navigationIcon = {
                    QduBackButton(onClick = onBack)
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = UiSpacing.PageHorizontal,
                    end = UiSpacing.PageHorizontal,
                    bottom = UiSpacing.ListBottomGuard,
                ),
        ) {
            Text(
                text = "方案决定色相，明暗决定亮度，两项独立保存。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )

            GroupHead(text = "配色方案")
            SchemeGrid(
                selected = scheme,
                isDark = isDark,
                tinted = darkTinted,
                onSelect = viewModel::selectScheme,
            )

            GroupHead(text = "明暗")
            Segmented(
                options = AppearanceMode.entries.map { it.label },
                selectedIndex = AppearanceMode.entries.indexOf(mode),
                onSelect = { index -> viewModel.selectMode(AppearanceMode.entries[index]) },
            )
            Text(
                text = "跟随系统时，系统切换深浅立即生效，不重启、不闪白。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
            )

            GroupHead(text = "深色选项")
            QduCard(padded = false) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.CardInner, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "保留颜色",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "深色底带上方案的主色相，不是近黑的灰。浅色下不生效。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = darkTinted,
                        onCheckedChange = viewModel::setDarkTinted,
                    )
                }
            }

            GroupHead(text = "状态色")
            QduCard(padded = false) {
                StatusLegendRow(
                    level = StatusLevel.SUCCESS,
                    title = "正常完成",
                    detail = "认证成功、同步完成、设备下线",
                    role = "固定绿\n5dp 圆点",
                )
                StatusLegendRow(
                    level = StatusLevel.WARNING,
                    title = "需要注意",
                    detail = "设备满额、电量偏低，必须带文字",
                    role = "固定黄\n8 × 5 倒三角",
                )
                StatusLegendRow(
                    level = StatusLevel.DANGER,
                    title = "失败",
                    detail = "认证失败、加载失败，原因写在正文",
                    role = "固定红\n6dp ×",
                )
                StatusLegendRow(
                    level = StatusLevel.BLOCKED,
                    title = "不可用",
                    detail = "座位不开放、仅现场预约，不是出错",
                    role = "固定灰\n8 × 1.5 短杠",
                    divider = false,
                )
            }
            Text(
                text = "状态色固定为绿、黄、红、灰四档，不随方案换色相，只用于小面积标记。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
private fun SchemeGrid(
    selected: AppearanceScheme,
    isDark: Boolean,
    tinted: Boolean,
    onSelect: (AppearanceScheme) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        AppearanceScheme.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { item ->
                    SchemeTile(
                        item = item,
                        selected = item == selected,
                        isDark = isDark,
                        tinted = tinted,
                        modifier = Modifier.weight(1f),
                        onSelect = { onSelect(item) },
                    )
                }
                if (row.size == 1) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SchemeTile(
    item: AppearanceScheme,
    selected: Boolean,
    isDark: Boolean,
    tinted: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 色条按当前明暗与「保留颜色」生成，预览与整屏结果一致
    val preview = remember(item, isDark, tinted) { generateColorScheme(item, isDark, tinted) }
    Column(
        modifier = modifier
            .clip(UiShapes.Card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .background(scheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = if (selected) scheme.primary else cardBorderColor(),
                shape = UiShapes.Card,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .rowDivider(cardBorderColor()),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(preview.primary))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(preview.primaryContainer))
            Box(modifier = Modifier.weight(0.82f).fillMaxHeight().background(preview.surface))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "已选用",
                    modifier = Modifier.size(14.dp),
                    tint = scheme.primary,
                )
            }
        }
    }
}

@Composable
private fun StatusLegendRow(
    level: StatusLevel,
    title: String,
    detail: String,
    role: String,
    divider: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.rowDivider(cardBorderColor()) else Modifier)
            .padding(horizontal = UiSpacing.CardInner, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            StatusMark(level = level)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = role,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 84.dp),
        )
    }
}
