package qdu.campus.feature.electricity

import android.net.Uri
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.domain.model.WebDomain
import qdu.campus.feature.web.CampusService
import qdu.campus.feature.web.ServiceTarget
import qdu.campus.platform.common.Formats
import qdu.campus.platform.designsystem.Banner
import qdu.campus.platform.designsystem.ListRow
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.system.ExternalLauncher
import qdu.campus.platform.web.CampusWebView

/**
 * 宿舍电费。绑好房号后每次进来直接查这个房间的账户快照，不用再在网页端一步步点。
 *
 * 余额单独放大，其余键值行原样摆，不猜字段含义。
 * 充值仍然只在网页端做：这一页连复制编号都算进去，是为了少一次手打，不是要下单。
 * 会话过期时挂一个 1dp 隐藏页静默补登录态；补不上才引导去网页端登录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DormElectricityScreen(
    onBack: () -> Unit,
    onOpenBinding: () -> Unit,
    onOpenRecharge: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: DormElectricityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val syncUrl = remember { (CampusService.ELECTRICITY.target as? ServiceTarget.InternalWeb)?.url }
    var syncView by remember { mutableStateOf<WebView?>(null) }
    val checkedAt = state.checkedAt
    val freshness = when {
        state.syncing -> "正在补登登录态…"
        state.checking -> "正在查询…"
        checkedAt != null -> "查询于 ${Formats.dateTime(checkedAt)}"
        else -> "还没查到数据"
    }

    // 去网页端登录后回到这一页：票已落地，自动重查一次，不用再点重试
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("宿舍电费") },
                navigationIcon = { QduBackButton(onClick = onBack) },
                actions = {
                    if (state.code.isNotBlank()) {
                        IconButton(
                            onClick = viewModel::refresh,
                            enabled = !state.checking && !state.syncing,
                        ) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "重新查询")
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.code.isBlank()) {
                    PlaceholderBlock(
                        title = "还没有绑定宿舍",
                        description = "选好楼栋与房号、核对通过后就记在本机，以后每次进来直接查这一间的剩余电量与余额。充值仍在网页端完成。",
                        actionLabel = "绑定宿舍房号",
                        onAction = onOpenBinding,
                    )
                    return@Column
                }

                QduCard {
                    Text(
                        text = "充值编号",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = state.code, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = freshness,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                state.account?.let { account ->
                    val balanceIndex = account.lines.indexOfFirst { it.label.contains("余额") }
                    val balance = account.lines.getOrNull(balanceIndex)
                    val others = account.lines.filterIndexed { index, _ -> index != balanceIndex }

                    if (balance != null) {
                        QduCard {
                            Text(
                                text = balance.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = balance.value,
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (others.isNotEmpty()) {
                        QduCard(padded = false) {
                            others.forEachIndexed { index, line ->
                                ListRow(
                                    title = line.label,
                                    trailing = {
                                        Text(text = line.value, style = MaterialTheme.typography.bodyMedium)
                                    },
                                    chevron = false,
                                    divider = index < others.lastIndex,
                                )
                            }
                        }
                    }
                }

                state.error?.let { message ->
                    if (state.needsLogin) {
                        Banner(text = message, actionLabel = "去登录", onAction = onOpenLogin)
                    } else {
                        Banner(text = message, actionLabel = "重试", onAction = viewModel::refresh)
                    }
                }

                Spacer(Modifier.weight(1f))

                // 网页端充值要手输编号，复制和跳转合成一步，省一次来回
                QduButton(
                    text = "去网页端充值",
                    kind = QduButtonKind.Tonal,
                    onClick = {
                        ExternalLauncher(context).copyToClipboard(state.code, "充值编号")
                        onOpenRecharge()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.syncing,
                )
                QduButton(
                    text = "改绑房间",
                    kind = QduButtonKind.Ghost,
                    small = true,
                    onClick = onOpenBinding,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.syncing && syncUrl != null) {
                // 1dp 透明页：zzmp 的 sessionToken 只由页面脚本写进 storage，隐藏但保持附着才会跑
                CampusWebView(
                    url = syncUrl,
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f),
                    timeoutMillis = SYNC_TIMEOUT_MS,
                    handleBack = false,
                    onWebViewReady = { syncView = it },
                    onPageFinished = { url ->
                        // 桥上有 authserver、qdsso 好几跳，只有落在 zzmp 才有要取的东西
                        if (Uri.parse(url).host == WebDomain.ZZMP.host) {
                            syncView?.let(viewModel::onSyncPageFinished)
                        }
                    },
                    onError = { viewModel.onSyncFailed() },
                )
            }
        }
    }
}

private const val SYNC_TIMEOUT_MS = 20_000L
