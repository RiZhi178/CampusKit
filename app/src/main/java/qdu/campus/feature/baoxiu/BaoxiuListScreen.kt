package qdu.campus.feature.baoxiu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import qdu.campus.domain.model.RepairTicket
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.designsystem.Pill
import qdu.campus.platform.designsystem.PillKind
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.StatusLevel

/**
 * 「我的报修」。结构按移动端惯例来：列表可下拉刷新，主操作「新建报修」用右下角
 * 悬浮按钮（Material 对主操作的位置约定），而不是塞进顶栏。
 *
 * 状态既给文字又给形状（[Pill] 的 `mark`），不单靠颜色区分，色觉障碍下也能读。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaoxiuListScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenForm: () -> Unit,
    onOpenWebLogin: () -> Unit,
    viewModel: BaoxiuListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("宿舍报修") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        floatingActionButton = {
            // 只有在能提交的时候才给入口；会话过期或加载失败时先解决登录/网络。
            if (!state.loginRequired) {
                ExtendedFloatingActionButton(
                    onClick = onOpenForm,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("新建报修") },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loginRequired -> CenterBlock(
                    title = "需要重新登录报修系统",
                    description = "统一认证会话过期后，报修接口会拒绝请求。去内嵌页登录一次即可。",
                    actionLabel = "去登录",
                    onAction = onOpenWebLogin,
                )

                state.loading -> AppLoading(modifier = Modifier.fillMaxSize())

                state.error != null -> CenterBlock(
                    title = "列表没能加载出来",
                    description = state.error.orEmpty(),
                    actionLabel = "重试",
                    onAction = { viewModel.load() },
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.tickets.isEmpty()) {
                        // 空态也要能下拉刷新，所以放进可滚动容器里。
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                CenterBlock(
                                    title = "还没有报修记录",
                                    description = "点右下角「新建报修」，提交后的工单会出现在这里。",
                                    actionLabel = "刷新",
                                    onAction = { viewModel.refresh() },
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            // 底部留出悬浮按钮的高度，最后一条不会被挡住。
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(state.tickets, key = { it.id }) { ticket ->
                                TicketCard(ticket = ticket, onClick = { onOpenDetail(ticket.id) })
                            }
                            item {
                                Text(
                                    text = "共 ${state.total} 条",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketCard(ticket: RepairTicket, onClick: () -> Unit) {
    val spoken = listOf(ticket.title, ticket.stateText, ticket.location, ticket.applyTime)
        .filter { it.isNotBlank() }
        .joinToString("，")
    QduCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$spoken，点击查看工单详情" },
        padded = false,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = ticket.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ticket.stateText.isNotBlank()) {
                    Pill(
                        text = ticket.stateText,
                        kind = stateKind(ticket.state),
                        mark = stateMark(ticket.state),
                    )
                }
                Text(
                    text = ticket.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = ticket.applyTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 20 是等待调度（进行中），12 是已撤销（终态），其余按中性显示。 */
private fun stateKind(state: String?): PillKind = when (state) {
    "20" -> PillKind.Warn
    "12" -> PillKind.Default
    else -> PillKind.Default
}

/** 与颜色并行的第二重编码：进行中给警示形，已撤销给终止形。 */
private fun stateMark(state: String?): StatusLevel? = when (state) {
    "20" -> StatusLevel.WARNING
    "12" -> StatusLevel.BLOCKED
    else -> null
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
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlaceholderBlock(
            title = title,
            description = description,
            actionLabel = actionLabel,
            onAction = onAction,
        )
    }
}
