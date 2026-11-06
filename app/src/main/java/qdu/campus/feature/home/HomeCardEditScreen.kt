package qdu.campus.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.feature.web.CampusService
import qdu.campus.feature.web.icon
import qdu.campus.platform.designsystem.GroupHead
import qdu.campus.platform.designsystem.ListRow
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.UiSpacing
import qdu.campus.platform.designsystem.listBottomGuard

/**
 * 首页「常用」编辑页：调顺序、删一张、再加一张。
 * 改动即时生效并落盘，所以没有保存按钮；返回即完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCardEditScreen(
    onBack: () -> Unit,
    viewModel: HomeCardEditViewModel = hiltViewModel(),
) {
    val cards by viewModel.cards.collectAsStateWithLifecycle()
    val available = CampusService.entries.filter { it !in cards }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("常用卡片") },
                navigationIcon = { QduBackButton(onClick = onBack) },
                actions = {
                    QduButton(
                        text = "恢复默认",
                        kind = QduButtonKind.Ghost,
                        small = true,
                        onClick = viewModel::resetToDefault,
                    )
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
                .padding(horizontal = UiSpacing.PageHorizontal),
        ) {
            Text(
                text = "首页按这里的顺序显示，上下调顺序，删掉不再用的。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )

            GroupHead(text = "显示顺序", modifier = Modifier.padding(top = 6.dp))
            QduCard(padded = false) {
                cards.forEachIndexed { index, service ->
                    CardRow(
                        service = service,
                        divider = index != cards.lastIndex,
                        actions = {
                            RowAction(Icons.Outlined.ArrowUpward, "上移", index != 0,
                                onClick = { viewModel.moveUp(index) })
                            RowAction(Icons.Outlined.ArrowDownward, "下移",
                                index != cards.lastIndex,
                                onClick = { viewModel.moveDown(index) })
                            RowAction(Icons.Outlined.Close, "移除",
                                enabled = cards.size > 1,
                                tint = MaterialTheme.colorScheme.error,
                                onClick = { viewModel.remove(service) })
                        },
                    )
                }
            }

            if (available.isNotEmpty()) {
                GroupHead(text = "可添加")
                QduCard(padded = false) {
                    available.forEachIndexed { index, service ->
                        ListRow(
                            title = service.title,
                            subtitle = service.subtitle,
                            subtitleMaxLines = 1,
                            leading = service.icon,
                            divider = index != available.lastIndex,
                            chevron = false,
                            trailing = {
                                RowAction(Icons.Outlined.Add, "添加到首页", onClick = {
                                    viewModel.add(service)
                                })
                            },
                        )
                    }
                }
            }

            Text(
                text = "至少保留一张卡片。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = UiSpacing.SectionTop, bottom = listBottomGuard()),
            )
        }
    }
}

/** 已选卡片的一行：图标与文案由 ListRow 排，动作按钮并排放在尾部。 */
@Composable
private fun CardRow(
    service: CampusService,
    divider: Boolean,
    actions: @Composable RowScope.() -> Unit,
) {
    ListRow(
        title = service.title,
        subtitle = service.subtitle,
        subtitleMaxLines = 1,
        leading = service.icon,
        divider = divider,
        chevron = false,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
        },
    )
}

/**
 * 行内的小按钮。热区 34 而不是应用的 48：三个按钮并排还要留出标题，
 * 但视觉尺寸保持与列表图标一致，点起来靠的是间距而不是尺寸。
 */
@Composable
private fun RowAction(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
    }
}
