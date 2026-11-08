package qdu.campus.feature.appraisal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.domain.model.XgAwardBatch
import qdu.campus.domain.model.XgAwardPage
import qdu.campus.domain.model.XgGrid
import qdu.campus.platform.common.Formats
import qdu.campus.platform.designsystem.Banner
import qdu.campus.platform.designsystem.ListRow
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.feature.xgcommon.XgMintPane
import qdu.campus.feature.xgcommon.XgMintWebView
import qdu.campus.feature.xgcommon.XG_MINT_OVERALL_TIMEOUT_MS
import qdu.campus.feature.web.CampusService
import qdu.campus.feature.web.ServiceTarget
import qdu.campus.platform.designsystem.SessionGate
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.listBottomGuard

/**
 * 评奖评优进度：批次窗口 + 本人在这个批次下的申请记录。
 *
 * 批次与记录都取自学工 PJPY 的学校奖助页，只读。切换批次、选奖项和真正提交申请
 * 都在网页端做——那一步要往校方系统写数据，本应用不做写入。
 * 没有学工门户会话时不猜、不空转，直接说明要点哪一次。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppraisalScreen(
    onBack: () -> Unit,
    onOpenWebLogin: () -> Unit,
    viewModel: AppraisalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncUrl = remember { (CampusService.XG_AWARD.target as? ServiceTarget.InternalWeb)?.url }
    val fetchedAt = state.fetchedAt
    val freshness = when {
        state.loading -> "正在读取…"
        fetchedAt != null -> "数据更新于 ${Formats.dateTime(fetchedAt)}"
        else -> "还没读到数据"
    }

    // 去学工门户点过一次再回来：会话刚落，自动重读，不用再手点重试
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("评奖评优") },
                navigationIcon = { QduBackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重新读取")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = freshness,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when {
                state.syncing -> XgMintPane()

                state.needsLogin -> SessionGate(
                    title = "评奖评优需要先登录",
                    reason = "去学工系统里点一下「评奖评优」，\n回来这里就能查看了。",
                    onLogin = onOpenWebLogin,
                    loginLabel = "去学工系统",
                    onRetry = viewModel::refresh,
                )

                state.loading -> AppLoading(modifier = Modifier.fillMaxSize())

                else -> {
                    val page = state.page
                    if (page != null) {
                        page.batch?.let { BatchCard(it) }
                        Records(page)
                    }
                    state.error?.let { message ->
                        Banner(text = message, actionLabel = "重试", onAction = viewModel::refresh)
                    }
                }
            }

            Spacer(Modifier.height(listBottomGuard()))
        }

        // 铸会话总时长兜底：到点换登录引导，不让页面一直转
        LaunchedEffect(state.syncing) {
            if (state.syncing) {
                delay(XG_MINT_OVERALL_TIMEOUT_MS)
                viewModel.syncTimedOut()
            }
        }

        if (state.syncing && syncUrl != null) {
            XgMintWebView(
                url = syncUrl,
                script = appraisalMintScript,
                onPageFinished = viewModel::onMintPageFinished,
                onError = { viewModel.onMintFailed() },
            )
        }
        }
    }
}

/** 批次窗口。原文照印，不改成结构化日期：站点的写法会变，改了就得跟着维护。 */
@Composable
private fun BatchCard(batch: XgAwardBatch) {
    QduCard {
        Text(text = batch.name, style = MaterialTheme.typography.titleMedium)
        if (batch.status.isNotBlank()) {
            Text(
                text = batch.status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        listOf(batch.year, batch.dateRange)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
            .let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        if (batch.awardOptions.isNotEmpty()) {
            Text(
                text = "可申奖项：${batch.awardOptions.joinToString("、")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (batch.otherNames.isNotEmpty()) {
            Text(
                text = "这个页面还有别的批次，切换要在网页端选：" +
                    batch.otherNames.joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Records(page: XgAwardPage) {
    val grids = page.records
    if (grids.isEmpty()) {
        PlaceholderBlock(
            title = "这个批次下没有查到你的申请记录",
            description = page.totalRecords?.let { "服务端报的是 $it 条记录。" }
                ?: "申过之后记录会出现在这里；批次换档与奖项换类都要在网页端点。",
        )
        return
    }
    grids.forEachIndexed { gridIndex, grid ->
        Text(
            text = recordLabel(page, grid, gridIndex),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QduCard(padded = false) {
            grid.rows.forEachIndexed { index, row ->
                ListRow(
                    title = row.firstOrNull()?.takeIf { it.isNotEmpty() } ?: "第 ${index + 1} 条",
                    subtitle = detailOf(row, grid.headers).ifBlank { null },
                    subtitleMaxLines = 3,
                    chevron = false,
                    divider = index < grid.rows.lastIndex,
                )
            }
        }
    }
}

/** 一页可能摆几张表：第一张用页面给的分区标题（实测是「学校奖学金」），其余用表格自己的 id。 */
private fun recordLabel(page: XgAwardPage, grid: XgGrid, gridIndex: Int): String {
    val head = if (gridIndex == 0) page.caption.ifBlank { grid.id } else grid.id
    val total = page.totalRecords
    return when {
        head.isBlank() && total == null -> "申请记录"
        total == null -> head
        else -> "$head · 共 $total 条"
    }
}

/** 一行的其余列拼成一句：列名由服务端给，有几列认几列，缺列名时只印值。 */
private fun detailOf(row: List<String>, headers: List<String>): String =
    row.drop(1)
        .mapIndexed { index, value ->
            if (value.isBlank()) return@mapIndexed ""
            val label = headers.getOrNull(index + 1).orEmpty()
            if (label.isEmpty()) value else "$label $value"
        }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

/** 替点门户「办事大厅」里的「评奖评优管理」磁贴铸会话；选择器用实测稳定属性，不靠文案猜（见 xg.md §二·补）。 */
private val appraisalMintScript: String = """
(function(){
  if (window.__qduMint) return;
  window.__qduMint = 1;
  if (!window.__qduShim) {
    window.__qduShim = 1;
    window.open = function(u){ if (u) location.assign(String(u)); return null; };
  }
  var done = new RegExp('/apps/PJPY/', 'i');
  var tries = 0;
  var timer = setInterval(function(){
    tries++;
    if (done.test(location.href) || tries > 40) { clearInterval(timer); return; }
    var tile = document.querySelector('[data-key="App_PJPY"]');
    if (tile) { clearInterval(timer); tile.click(); return; }
    if (!window.__qduHall) {
      var hall = document.querySelector('a[data-id="bsdt"]');
      if (hall) { window.__qduHall = 1; hall.click(); }
    }
  }, 700);
})();
"""
