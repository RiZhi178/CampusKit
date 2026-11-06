package qdu.campus.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.platform.designsystem.Banner
import qdu.campus.platform.designsystem.HintBar
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.UrlBar
import qdu.campus.platform.web.CampusWebView

private fun String.hostOf(): String =
    runCatching { java.net.URI(this).host.orEmpty() }.getOrDefault("")

/** 域名条里主机之后剩下的路径，避免和标题下那行主机名重复成两段一样的字。 */
private fun String.displayedPath(host: String): String {
    val path = runCatching { java.net.URI(this).path.orEmpty() }.getOrDefault("")
    return if (host.isBlank() || path.isBlank()) this else "$host$path"
}

/**
 * 统一认证页（原型屏 02）。全屏推入，标题行与域名条都显示真实主机，
 * 用户能核对是不是校方页面；底部说明条常驻，明确「客户端不碰密码」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onFinished: () -> Unit,
    onBack: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.stage) {
        if (state.stage == LoginStage.DONE) onFinished()
    }

    val host = state.pageUrl.hostOf()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "统一身份认证",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (host.isNotBlank()) {
                        Text(
                            text = host,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .weight(1f, fill = false),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = { QduBackButton(onClick = onBack) },
            actions = {
                // 校方的跳转一变，自动判定就会失效；给一个手动收尾的出口，
                // 会话已经在 WebView 里，点一下就能收进本机。
                if (state.stage != LoginStage.DONE) {
                    QduButton(
                        text = "完成登录",
                        onClick = viewModel::finishNow,
                        kind = QduButtonKind.Ghost,
                        small = true,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            },
            // 外层 Column 已让出系统栏，这里不能再叠一次。
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        UrlBar(
            displayUrl = state.pageUrl.displayedPath(host),
            loadProgress = if (state.loading) state.progress / 100f else null,
            onRefresh = viewModel::reload,
        )
        state.error?.let { reason ->
            Banner(
                text = reason,
                actionLabel = "重新加载",
                onAction = viewModel::reload,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        // 成功后到导航真正发生之间还有一段落盘时间，中途卸载 WebView 会丢会话
        CampusWebView(
            url = state.url,
            modifier = Modifier.weight(1f),
            scriptInjection = state.script,
            onProgress = viewModel::onProgress,
            onPageStarted = viewModel::onPageStarted,
            onPageFinished = viewModel::onPageFinished,
            onError = viewModel::onError,
            onWebViewReady = viewModel::attachWebView,
            timeoutMillis = 20_000,
        )
        HintBar(
            icon = Icons.Outlined.Shield,
            text = when (state.stage) {
                LoginStage.PREHEAT -> state.message ?: "正在确认统一认证会话…"
                LoginStage.DONE -> state.message ?: "统一认证会话已写入本机，正在返回首页。"
                else -> state.message
                    ?: "此页由校方提供，输入内容不经过本应用。"
            },
        )
    }
}
