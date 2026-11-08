package qdu.campus.feature.leave

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.domain.model.XgLeaveRecord
import qdu.campus.feature.web.CampusService
import qdu.campus.feature.web.ServiceTarget
import qdu.campus.platform.common.Formats
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.designsystem.Banner
import qdu.campus.platform.designsystem.DialogButton
import qdu.campus.platform.designsystem.SessionGate
import qdu.campus.platform.designsystem.Pill
import qdu.campus.platform.designsystem.PillKind
import qdu.campus.platform.designsystem.CenteredPlaceholder
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.QduIconButton
import qdu.campus.platform.designsystem.StatusLevel
import qdu.campus.platform.designsystem.TransientToast
import qdu.campus.feature.xgcommon.XgMintPane
import qdu.campus.feature.xgcommon.XgMintWebView
import qdu.campus.feature.xgcommon.XG_MINT_OVERALL_TIMEOUT_MS
import kotlinx.coroutines.delay

/**
 * 日常请假：我的请假列表。
 *
 * 能申请时右下角给「申请请假」；有在途申请时站点自己把入口换成互斥提示，这里照印、不给入口。
 * 每条记录点开看详情，行内可撤销——撤销是终态操作，走危险色 + 二次确认，确认前禁用避免连点。
 * 缺学工会话时先挂一张隐藏内嵌页静默铸会话（替人点一次门户磁贴），补不到才摆登录引导。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveListScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenForm: () -> Unit,
    onOpenWebLogin: () -> Unit,
    viewModel: LeaveListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var revokeTarget by remember { mutableStateOf<XgLeaveRecord?>(null) }
    val syncUrl = remember { (CampusService.XG_LEAVE.target as? ServiceTarget.InternalWeb)?.url }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    // 补会话不能无限转：链可能停在某个不再加载的中间页，总时长到点就换登录引导
    LaunchedEffect(state.syncing) {
        if (state.syncing) {
            delay(XG_MINT_OVERALL_TIMEOUT_MS)
            viewModel.syncTimedOut()
        }
    }

    val page = state.page
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日常请假") },
                navigationIcon = { QduBackButton(onClick = onBack) },
                actions = {
                    QduIconButton(
                        onClick = viewModel::refresh,
                        icon = Icons.Outlined.Refresh,
                        contentDescription = "重新读取",
                    )
                },
            )
        },
        floatingActionButton = {
            if (page?.canApply == true && !state.needsLogin && !state.syncing) {
                ExtendedFloatingActionButton(
                    onClick = onOpenForm,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("申请请假") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.syncing -> XgMintPane()

                state.needsLogin -> SessionGate(
                    title = "请假需要先登录",
                    reason = "去学工系统里点一下「日常请假」，\n回来就能查看和提交请假了。",
                    onLogin = onOpenWebLogin,
                    loginLabel = "去学工系统",
                    modifier = Modifier.fillMaxSize(),
                    onRetry = viewModel::retrySession,
                )

                state.loading && page == null -> AppLoading(modifier = Modifier.fillMaxSize())

                state.error != null && page == null -> CenteredPlaceholder(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                    title = "列表没能加载出来",
                    description = state.error.orEmpty(),
                    actionLabel = "重试",
                    onAction = viewModel::load,
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val records = page?.records.orEmpty()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = state.fetchedAt?.let { "数据更新于 ${Formats.dateTime(it)}" }
                                        ?: "正在读取…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!page?.blockedMessage.isNullOrBlank()) {
                                    Banner(text = page.blockedMessage)
                                }
                                if (state.error != null) {
                                    Banner(
                                        text = state.error.orEmpty(),
                                        actionLabel = "重试",
                                        onAction = viewModel::refresh,
                                    )
                                }
                                if (state.message != null && !state.messageOk) {
                                    Banner(text = state.message.orEmpty())
                                }
                            }
                        }
                        if (records.isEmpty()) {
                            item {
                                CenteredPlaceholder(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                                    title = "还没有请假记录",
                                    description = if (page?.canApply == true) {
                                        "点右下角「申请请假」，提交后的记录会出现在这里。"
                                    } else {
                                        "申过之后记录会出现在这里。"
                                    },
                                    actionLabel = "刷新",
                                    onAction = viewModel::refresh,
                                )
                            }
                        } else {
                            items(records, key = { it.id }) { record ->
                                RecordCard(
                                    record = record,
                                    headers = page?.headers.orEmpty(),
                                    revoking = state.revokingId == record.id,
                                    onClick = { onOpenDetail(record.id) },
                                    onRevoke = { revokeTarget = record },
                                )
                            }
                            item {
                                Text(
                                    text = page?.totalRecords?.let { "共 $it 条" } ?: "共 ${records.size} 条",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (state.messageOk) {
                TransientToast(message = state.message, onDismiss = viewModel::consumeMessage)
            }

            if (state.syncing && syncUrl != null) {
                // 补会话内嵌页：跳票只能由门户页面脚本签发；满屏可见加载、上盖不透明提示挡住门户（见 XgMintWebView）
                XgMintWebView(
                    url = syncUrl,
                    script = leaveMintScript,
                    onPageFinished = viewModel::onMintPageFinished,
                    onError = { viewModel.onMintFailed() },
                )
            }
        }
    }

    val target = revokeTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { revokeTarget = null },
            title = { Text("撤销请假") },
            text = {
                Text(
                    text = target.revokeConfirm.ifEmpty { "您确定要撤销这条申请记录吗？" } +
                        " 撤销后进入终态，不能再恢复。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                DialogButton(
                    text = "确认撤销",
                    kind = QduButtonKind.Danger,
                    enabled = state.revokingId == null,
                    onClick = {
                        revokeTarget = null
                        viewModel.revoke(target)
                    },
                )
            },
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = { revokeTarget = null },
                )
            },
        )
    }
}

@Composable
private fun RecordCard(
    record: XgLeaveRecord,
    headers: List<String>,
    revoking: Boolean,
    onClick: () -> Unit,
    onRevoke: () -> Unit,
) {
    val reason = record.cells.getOrNull(cellIndex(headers, 2, "原因")).orEmpty()
    val status = record.cells.getOrNull(cellIndex(headers, 4, "审批", "状态")).orEmpty()
    val time = record.cells.getOrNull(cellIndex(headers, 0, "时间")).orEmpty()
    val duration = record.cells.getOrNull(cellIndex(headers, 1, "时长")).orEmpty()
    val location = record.cells.getOrNull(cellIndex(headers, 3, "地点")).orEmpty()
    val spoken = listOf(reason, status, time).filter { it.isNotBlank() }.joinToString("，")

    QduCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$spoken，点击查看请假详情" },
        padded = false,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = reason.ifBlank { "请假记录" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (status.isNotBlank()) {
                    Pill(text = status, kind = statusKind(status), mark = statusMark(status))
                }
            }
            if (time.isNotBlank()) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (duration.isNotBlank()) {
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (location.isNotBlank()) {
                    Text(
                        text = location,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (record.revokeButton.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    QduButton(
                        text = if (revoking) "正在撤销…" else "撤销",
                        onClick = onRevoke,
                        kind = QduButtonKind.Danger,
                        small = true,
                        enabled = !revoking,
                    )
                }
            }
        }
    }
}

/** 列名由服务端给，按关键词认列；认不出时退回契约里记录的固定列序。 */
private fun cellIndex(headers: List<String>, fallback: Int, vararg keywords: String): Int {
    val found = headers.indexOfFirst { h -> keywords.any { h.contains(it) } }
    return if (found >= 0) found else fallback
}

private fun statusKind(status: String): PillKind = when {
    status.contains("通过") && !status.contains("不") -> PillKind.Ok
    status.contains("撤销") -> PillKind.Default
    status.contains("不") || status.contains("拒") -> PillKind.Err
    status.contains("等待") || status.contains("审批") -> PillKind.Warn
    else -> PillKind.Default
}

/** 颜色之外再给形状，色觉障碍下也能区分进行中 / 终态。 */
private fun statusMark(status: String): StatusLevel? = when {
    status.contains("通过") && !status.contains("不") -> StatusLevel.SUCCESS
    status.contains("撤销") -> StatusLevel.BLOCKED
    status.contains("不") || status.contains("拒") -> StatusLevel.DANGER
    status.contains("等待") || status.contains("审批") -> StatusLevel.WARNING
    else -> null
}

/** 替点门户「办事大厅」里的「日常请假管理」磁贴铸会话；选择器用实测稳定属性，不靠文案猜（见 xg.md §二·补）。 */
private val leaveMintScript: String = """
(function(){
  if (window.__qduMint) return;
  window.__qduMint = 1;
  if (!window.__qduShim) {
    window.__qduShim = 1;
    window.open = function(u){ if (u) location.assign(String(u)); return null; };
  }
  var done = new RegExp('/apps/DailyLeave/', 'i');
  var tries = 0;
  var timer = setInterval(function(){
    tries++;
    if (done.test(location.href) || tries > 40) { clearInterval(timer); return; }
    var tile = document.querySelector('[data-key="App_DailyLeave"]');
    if (tile) { clearInterval(timer); tile.click(); return; }
    if (!window.__qduHall) {
      var hall = document.querySelector('a[data-id="bsdt"]');
      if (hall) { window.__qduHall = 1; hall.click(); }
    }
  }, 700);
})();
"""