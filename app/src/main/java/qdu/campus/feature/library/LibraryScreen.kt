package qdu.campus.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.domain.model.LibraryHolding
import qdu.campus.domain.model.LibraryLoan
import qdu.campus.domain.model.LibraryLoanStatus
import qdu.campus.domain.model.LibraryRecord
import qdu.campus.feature.web.CampusService
import qdu.campus.feature.web.ServiceTarget
import qdu.campus.platform.common.Formats
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.designsystem.Banner
import qdu.campus.platform.designsystem.BannerKind
import qdu.campus.platform.designsystem.ErrWrap
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.QduField
import qdu.campus.platform.designsystem.Segmented
import qdu.campus.platform.designsystem.StatusLevel
import qdu.campus.platform.designsystem.StatusMark
import qdu.campus.platform.designsystem.listBottomGuard
import qdu.campus.platform.web.CampusWebView
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * 图书馆：馆藏检索与我的借阅。
 *
 * 两个标签页都要超星那对 `jwtHeader`/`jwt` 会话头（补测：缺头时检索回空结果而不是报错）。
 * 这对头由图书馆页面脚本写进 Cookie，原生造不出来，所以进页面先静默加载一次入口链补会话；
 * 补不到才挡住整页，给「去登录」走统一认证。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenPortal: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = remember { Formats.today() }
    val selected = state.selected
    val syncUrl = remember { (CampusService.LIBRARY.target as? ServiceTarget.InternalWeb)?.url }

    // 书目详情是同一页的一层覆盖，不另开路由：数据都已在内存里，路由只会多一套参数搬运
    BackHandler(enabled = selected != null) { viewModel.closeBook() }

    // 补会话不能无限转：WebView 可能停在某个不再加载的中间页，超时后换登录引导
    LaunchedEffect(state.syncing) {
        if (state.syncing) {
            delay(SYNC_TIMEOUT_MS + 5_000)
            viewModel.syncTimedOut()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.title ?: "图书馆") },
                navigationIcon = {
                    QduBackButton(onClick = if (selected == null) onBack else viewModel::closeBook)
                },
                actions = {
                    if (selected == null) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val banner = selected?.let { state.holdingsError }
                    ?: if (state.tab == LibraryTab.BORROWING) state.borrowingError else state.searchError
                banner?.let {
                    Banner(
                        text = it,
                        kind = BannerKind.Failure,
                        actionLabel = "关闭",
                        onAction = viewModel::dismissErrors,
                    )
                }

                when {
                    state.syncing -> SyncPane()

                    state.sessionMissing -> SessionGate(
                        onOpenLogin = onOpenLogin,
                        onRetry = viewModel::retrySession,
                    )

                    selected != null -> HoldingList(
                        record = selected,
                        holdings = state.holdings,
                        loading = state.holdingsLoading,
                    )

                    else -> {
                        Segmented(
                            options = listOf("馆藏检索", "我的借阅"),
                            selectedIndex = state.tab.ordinal,
                            onSelect = { viewModel.selectTab(LibraryTab.entries[it]) },
                        )
                        if (state.tab == LibraryTab.SEARCH) {
                            SearchPane(state = state, viewModel = viewModel)
                            QduButton(
                                text = "数据库导航（图书馆门户）",
                                onClick = onOpenPortal,
                                kind = QduButtonKind.Ghost,
                                small = true,
                            )
                        }
                        else BorrowingPane(
                            state = state,
                            today = today,
                            viewModel = viewModel,
                        )
                    }
                }
            }

            if (state.syncing && syncUrl != null) {
                // 1dp 透明 WebView：会话头只能由图书馆页面脚本种下；隐藏但保持附着，脚本才跑
                CampusWebView(
                    url = syncUrl,
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f),
                    timeoutMillis = SYNC_TIMEOUT_MS,
                    handleBack = false,
                    onPageFinished = { viewModel.onSyncPageFinished() },
                    onError = { viewModel.onSyncFailed() },
                )
            }
        }
    }
}

private const val SYNC_TIMEOUT_MS = 20_000L

@Composable
private fun SyncPane() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppLoading()
        Text(
            text = "正在同步图书馆会话…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "图书馆的登录态由它的页面脚本写入本机，这里用统一认证的票静默补一次，不会弹账号密码。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun SessionGate(onOpenLogin: () -> Unit, onRetry: () -> Unit) {
    ErrWrap(
        title = "图书馆会话未同步",
        reason = "检索与借阅都依赖图书馆自己的会话头。统一认证登录后打开本页会自动补齐；" +
            "如果一直补不上，去统一认证确认一次登录再回来重试。",
        actions = {
            QduButton(text = "去登录", onClick = onOpenLogin)
            Spacer(Modifier.height(8.dp))
            QduButton(
                text = "重试",
                kind = QduButtonKind.Tonal,
                onClick = onRetry,
            )
        },
    )
}

@Composable
private fun SearchPane(state: LibraryUiState, viewModel: LibraryViewModel) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QduField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "书名 / 作者 / ISBN",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                modifier = Modifier.weight(1f),
            )
            QduButton(
                text = if (state.searching) "查询中" else "搜索",
                onClick = viewModel::search,
                enabled = !state.searching && state.query.isNotBlank(),
            )
        }

        when {
            state.searching && state.results.isEmpty() -> LoadingRow()

            state.results.isEmpty() -> PlaceholderBlock(
                title = if (state.page == 0) "还没检索" else "没有匹配的书目",
                description = state.searchHint,
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = listBottomGuard()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        text = "共 ${state.numFound} 条，已显示 ${state.results.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(state.results, key = { it.recordId }) { record ->
                    RecordRow(record = record, onOpen = { viewModel.openBook(record) })
                }
                if (state.canLoadMore) {
                    item(key = "load-more") {
                        QduButton(
                            text = "再看 10 条",
                            kind = QduButtonKind.Tonal,
                            onClick = viewModel::loadMore,
                            enabled = !state.searching,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: LibraryRecord, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
    ) {
        Text(
            text = record.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val line = listOf(record.author, record.imprint).filter { it.isNotBlank() }.joinToString(" · ")
        if (line.isNotBlank()) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = listOfNotNull(record.isbn.takeIf { it.isNotBlank() }, record.callNo.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 颜色不作为唯一载体：在架与否必须先有文字
            val count = record.onShelfCount
            when {
                count == null -> Text(
                    text = "在架数未提供",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> {
                    StatusMark(level = if (count > 0) StatusLevel.SUCCESS else StatusLevel.BLOCKED)
                    Text(
                        text = if (count > 0) "在架 $count 册" else "无可借复本",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingList(record: LibraryRecord, holdings: List<LibraryHolding>, loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val meta = listOf(record.author, record.imprint, record.isbn).filter { it.isNotBlank() }
            .joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            loading && holdings.isEmpty() -> LoadingRow()

            holdings.isEmpty() -> PlaceholderBlock(
                title = "没有查到馆藏",
                description = "这条书目可能是电子资源或校外联合馆藏，本馆没有实体复本。",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = listBottomGuard()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Text(
                        text = "${holdings.size} 册 · 借前请核对该书的借阅规则",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(holdings, key = { it.itemId }) { holding ->
                    HoldingRow(holding)
                }
            }
        }
    }
}

@Composable
private fun HoldingRow(holding: LibraryHolding) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 在架与否先说文字，标记只是重复强调
            StatusMark(level = if (holding.onShelf) StatusLevel.SUCCESS else StatusLevel.BLOCKED)
            Text(
                text = holding.status.ifBlank { "状态未提供" },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (holding.volume.isNotBlank()) {
                Text(
                    text = holding.volume,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = listOf(holding.locationDisplay, holding.callNo).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = listOfNotNull(
                holding.barcode.takeIf { it.isNotBlank() }?.let { "条码 $it" },
                holding.acquiredAt.takeIf { it.isNotBlank() }?.let { "入藏 $it" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BorrowingPane(
    state: LibraryUiState,
    today: LocalDate,
    viewModel: LibraryViewModel,
) {
    if (state.borrowingLoading && state.loans.isEmpty()) {
        LoadingRow()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = listBottomGuard()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "loan-summary") {
            val quota = state.maxLoan?.let { "/$it" }.orEmpty()
            Text(
                text = buildString {
                    append("在借 ${state.loanTotal} 册$quota")
                    state.reader?.name?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        if (state.loans.isEmpty()) {
            item(key = "loan-empty") {
                PlaceholderBlock(
                    title = "当前没有在借的书",
                    description = "借阅历史与在架查询照常可用，往下翻即可。",
                )
            }
        }
        items(state.loans) { loan ->
            LoanRow(loan = loan, today = today)
        }
        item(key = "history-head") {
            Text(
                text = "借阅历史" + if (state.historyTotal > 0) "（共 ${state.historyTotal} 条）" else "",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
        }
        if (state.history.isEmpty()) {
            item(key = "history-empty") {
                Text(
                    text = if (state.borrowingLoaded) "没有借阅历史" else "还没有读取",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.history) { loan ->
            LoanRow(loan = loan, today = today, showDue = false)
        }
        if (state.historyHidden > 0) {
            item(key = "history-more") {
                Text(
                    text = "另有 ${state.historyHidden} 条未显示，图书馆网页端可查全部",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoanRow(loan: LibraryLoan, today: LocalDate, showDue: Boolean = true) {
    val status = loan.statusOf(today)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = loan.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val line = listOf(
            loan.author,
            loan.location,
            if (loan.loanDate.isNotBlank()) "借出 ${loan.loanDate}" else "",
            if (showDue && loan.dueDate.isNotBlank()) "应还 ${loan.dueDate}" else "",
            if (!showDue && loan.returnDate.isNotBlank()) "归还 ${loan.returnDate}" else "",
        ).filter { it.isNotBlank() }.joinToString(" · ")
        if (line.isNotBlank()) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 逾期与临期必须有文字，标记不能单独承担状态
        val note = when (status) {
            LibraryLoanStatus.OVERDUE -> "已逾期 ${loan.overdueDays(today) ?: 0} 天"
            LibraryLoanStatus.DUE_SOON -> "即将到期"
            LibraryLoanStatus.NORMAL -> null
            LibraryLoanStatus.UNKNOWN -> if (showDue && loan.dueDate.isBlank()) "应还日期未提供" else null
        }
        val level = when (status) {
            LibraryLoanStatus.OVERDUE -> StatusLevel.DANGER
            LibraryLoanStatus.DUE_SOON -> StatusLevel.WARNING
            else -> StatusLevel.SUCCESS
        }
        if (note != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusMark(level = level)
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { AppLoading() }
}
