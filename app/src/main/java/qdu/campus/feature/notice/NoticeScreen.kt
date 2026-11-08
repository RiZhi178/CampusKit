package qdu.campus.feature.notice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import qdu.campus.platform.common.Formats
import qdu.campus.platform.designsystem.DialogButton
import qdu.campus.platform.designsystem.InlineNotice
import qdu.campus.platform.designsystem.Pill
import qdu.campus.platform.designsystem.PillKind
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.Segmented
import qdu.campus.platform.designsystem.TransientToast
import qdu.campus.platform.designsystem.UnreadDot
import qdu.campus.platform.designsystem.listBottomGuard
import qdu.campus.domain.model.Notice
import qdu.campus.domain.model.SyncState

/** 通知数据来源的展示名与页签对应关系，列表条目和页签共用。 */
private fun sourceLabel(source: String): String =
    if (source == "jwc_notice") "教务处" else "研究生院"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeScreen(
    onOpenNotice: (Notice) -> Unit,
    viewModel: NoticeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val visible = state.visibleNotices
    var showFilter by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrolled = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
    val unread = state.unreadBySource

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知") },
                actions = {
                    if (scrolled) {
                        IconButton(
                            onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        ) {
                            Icon(
                                Icons.Outlined.VerticalAlignTop,
                                contentDescription = "回到顶部",
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            draft = state.keywords.joinToString("，")
                            showFilter = true
                        },
                    ) {
                        Icon(
                            Icons.Outlined.FilterAlt,
                            contentDescription = "关键词筛选",
                            tint = if (state.keywords.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            state.message?.let { message ->
                if (!message.isSuccess) {
                    InlineNotice(
                        text = message.text,
                        isError = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        actionLabel = "知道了",
                        onAction = viewModel::dismissMessage,
                    )
                }
            }

            // 页签：默认项按本机「研究生 / 本科」层次设置，手动切换只在这次页面会话内保留
            Segmented(
                options = NoticeTab.entries.map { tab ->
                    val count = when (tab) {
                        NoticeTab.All -> unread.values.sum()
                        else -> unread[tab.source] ?: 0
                    }
                    if (count > 0) "${tab.label} $count" else tab.label
                },
                selectedIndex = NoticeTab.entries.indexOf(state.tab),
                onSelect = { index -> viewModel.selectTab(NoticeTab.entries[index]) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            if (state.isRefreshing && state.notices.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
                return@Column
            }

            if (state.notices.isEmpty()) {
                // 空态也要能下拉刷新：清缓存后这里是唯一的刷新入口，提示写着「下拉」
                // 就必须拉得动——放进可滚动容器里，手势才传得进 PullToRefreshBox。
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        PlaceholderBlock(
                            title = "还没有通知",
                            description = "下拉刷新或点右上角刷新。",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                        )
                    }
                }
                return@Column
            }

            if (state.keywords.isNotEmpty()) {
                // 关键词只给一行预览，超长省略；命中条数单独占一块不跟着截断，「清除」按钮
                // 离屏幕边缘保持与其他内容一致的内边距（之前 end 只给了 4dp，太贴边）。
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        // 超长时由 ellipsis 收尾成「开题、中期、答辩…」，关键词少时完整显示
                        text = "筛选：${state.keywords.joinToString("、")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${visible.size}/${state.currentSourceCount} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QduButton(
                        text = "清除",
                        kind = QduButtonKind.Ghost,
                        small = true,
                        onClick = { viewModel.setKeywords("") },
                    )
                }
            }

            when {
                visible.isNotEmpty() -> NoticeList(
                    visible = visible,
                    listState = listState,
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    onItemClick = { notice ->
                        viewModel.markRead(notice.fingerprint)
                        onOpenNotice(notice)
                    },
                    syncStates = state.syncStates,
                    tab = state.tab,
                )

                state.currentSourceCount > 0 -> PlaceholderBlock(
                    title = "没有匹配的通知",
                    description = "当前只显示标题包含「${state.keywords.joinToString("、")}」的条目。换个关键词，或清空筛选看全部。",
                    actionLabel = "清空筛选",
                    onAction = { viewModel.setKeywords("") },
                    modifier = Modifier.padding(24.dp),
                )

                state.tab == NoticeTab.All -> PlaceholderBlock(
                    title = "还没有通知",
                    description = "下拉刷新或点右上角刷新。",
                    modifier = Modifier.padding(24.dp),
                )

                else -> PlaceholderBlock(
                    title = "这一栏还没有通知",
                    description = "另一个来源已有条目；切到「全部」可以一起看。",
                    actionLabel = "看全部",
                    onAction = { viewModel.selectTab(NoticeTab.All) },
                    modifier = Modifier.padding(24.dp),
                )
            }
            }

            state.message?.takeIf { it.isSuccess }?.let {
                TransientToast(
                    message = it.text,
                    onDismiss = viewModel::dismissMessage,
                    modifier = Modifier.padding(bottom = listBottomGuard()),
                )
            }
        }
    }

    if (showFilter) {
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = { Text("关键词筛选") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = { Text("关键词") },
                        placeholder = { Text("如：奖学金，选课") },
                        singleLine = true,
                    )
                    Text(
                        text = "用逗号或空格分隔；通知标题包含任一关键词才显示，两个来源都按它筛。留空显示全部。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.keywords.isNotEmpty()) {
                        QduButton(
                            text = "清除当前筛选",
                            kind = QduButtonKind.Danger,
                            small = true,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                viewModel.setKeywords("")
                                showFilter = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                DialogButton(
                    text = "保存",
                    kind = QduButtonKind.Primary,
                    onClick = {
                        viewModel.setKeywords(draft)
                        showFilter = false
                    },
                )
            },
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = { showFilter = false },
                )
            },
        )
    }
}

@Composable
private fun NoticeList(
    visible: List<Notice>,
    listState: LazyListState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onItemClick: (Notice) -> Unit,
    syncStates: List<SyncState>,
    tab: NoticeTab,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = listBottomGuard(),
            ),
        ) {
            items(visible, key = { it.fingerprint }) { notice ->
                NoticeItem(
                    notice = notice,
                    onClick = { onItemClick(notice) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item {
                SyncFootnote(syncStates = syncStates, tab = tab)
            }
        }
    }
}

/** 底部按来源分别标「数据更新于」；「全部」两行都列，不合成一个时间假装统一。 */
@Composable
private fun SyncFootnote(syncStates: List<SyncState>, tab: NoticeTab) {
    val states = syncStates.associateBy { it.source }
    val lines = when (tab) {
        NoticeTab.All -> listOf("grad_notice", "jwc_notice")
        else -> listOfNotNull(tab.source)
    }.map { source ->
        val label = sourceLabel(source)
        val time = states[source]?.lastSuccessAt
        if (time != null) "$label：数据更新于 ${Formats.dateTime(time)}" else "$label：尚未成功同步"
    }
    Text(
        text = lines.joinToString("\n"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun NoticeItem(notice: Notice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 未读只留一个小圆点，标题与日期之间不再夹一个「未读」标签
        UnreadDot(read = notice.read)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Formats.noticeTitle(notice.title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (notice.read) FontWeight.Normal else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 来源用文字标签表达，两个来源一眼可分；颜色之外还有文字
                Pill(
                    text = sourceLabel(notice.source),
                    kind = if (notice.source == "jwc_notice") PillKind.Prim else PillKind.Default,
                )
                if (notice.column.isNotBlank()) {
                    Text(
                        text = notice.column,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = notice.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
