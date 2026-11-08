package qdu.campus.feature.ehall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.domain.model.EhallApp
import qdu.campus.domain.model.EhallTaskCounts
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.designsystem.Banner
import qdu.campus.platform.designsystem.BannerKind
import qdu.campus.platform.designsystem.SessionGate
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduField
import qdu.campus.platform.designsystem.StatusLevel
import qdu.campus.platform.designsystem.StatusMark
import qdu.campus.platform.designsystem.listBottomGuard

/**
 * 办事大厅的服务清单。
 *
 * 大厅本身是聚合入口，背后挂几十个第三方系统，原生化只做「看清有哪些服务、
 * 按名称搜到它」这一层；点条目仍进内嵌 WebView，由 `appShow?appId=` 补第三方应用的登录握手。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EhallScreen(
    onBack: () -> Unit,
    onOpenService: (url: String, title: String) -> Unit,
    onOpenWebLogin: () -> Unit,
    viewModel: EhallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("办事大厅") },
                navigationIcon = { QduBackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 掉线时下面的错误块已经把话说完了，顶部再挂一条同样的横幅只是重复。
            state.error?.takeIf { !state.loggedOut }?.let { error ->
                Banner(
                    text = error,
                    kind = BannerKind.Failure,
                    actionLabel = "关闭",
                    onAction = viewModel::dismissError,
                )
            }

            when {
                state.loggedOut -> SessionGate(
                    title = "办事大厅需要先登录",
                    reason = "登录后就能使用大厅里的各项服务了。",
                    onLogin = onOpenWebLogin,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = viewModel::retryLogin,
                    retryLabel = "已登录，重试",
                )

                state.loading && state.services.isEmpty() -> AppLoading(modifier = Modifier.fillMaxSize())

                else -> {
                    if (state.services.isNotEmpty()) {
                        QduField(
                            value = state.query,
                            onValueChange = viewModel::setQuery,
                            placeholder = "搜索服务名称",
                        )
                    }
                    when {
                        state.grouped.isEmpty() -> PlaceholderBlock(
                            title = if (state.query.isBlank()) "没有可用的服务" else "没有匹配的服务",
                            description = if (state.query.isBlank()) {
                                "办事大厅返回了空清单，可能是当前角色没有开通任何服务。"
                            } else {
                                "换个关键词，或清空搜索框看全部 ${state.services.size} 项。"
                            },
                            actionLabel = "刷新",
                            onAction = viewModel::refresh,
                        )

                        else -> ServiceList(
                            groups = state.grouped,
                            total = state.services.size,
                            counts = state.taskCounts,
                            onOpen = { app -> onOpenService(viewModel.entryUrl(app), app.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceList(
    groups: List<Pair<String, List<EhallApp>>>,
    total: Int,
    counts: EhallTaskCounts?,
    onOpen: (EhallApp) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = listBottomGuard()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text(
                text = "共 ${groups.sumOf { it.second.size }} 项，全部 $total 项",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        // 计数取不到就当这行不存在，不写「0」骗人说没有待办
        if (counts != null) {
            item(key = "task-counts") {
                Text(
                    text = "待办 ${counts.pending} 项 · 流程跟踪 ${counts.tracked} 项 · 已办 ${counts.done} 项",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        groups.forEach { (category, apps) ->
            item(key = "cat-$category") {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            items(apps, key = { it.id }) { app ->
                ServiceRow(app = app, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun ServiceRow(app: EhallApp, onOpen: (EhallApp) -> Unit) {
    val note = when {
        app.maintaining -> "维护中"
        !app.hasPermission -> "无权限"
        else -> null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(app) }
            .padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 颜色不作为唯一载体：标记之外必须跟着同样的文字
            note?.let {
                StatusMark(level = if (app.maintaining) StatusLevel.BLOCKED else StatusLevel.WARNING)
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (app.description.isNotBlank()) {
            Text(
                text = app.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
