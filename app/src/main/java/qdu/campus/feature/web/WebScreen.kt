package qdu.campus.feature.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.system.ExternalLauncher
import qdu.campus.platform.web.CampusWebView
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(
    service: CampusService,
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: WebViewModel = hiltViewModel(),
) {
    val casAuthenticated by viewModel.casAuthenticated.collectAsState()
    val target = service.target as? ServiceTarget.InternalWeb
    val targetUrl = target?.url.orEmpty()
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // 从登录页返回时这一屏会重新组合，登录结果以 CookieManager 的当前值为准。
    LaunchedEffect(Unit) { viewModel.refreshCasSession() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(service.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    QduButton(
                        text = "浏览器",
                        onClick = { ExternalLauncher(context).openUrl(targetUrl) },
                        kind = QduButtonKind.Ghost,
                        small = true,
                        enabled = target != null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (target == null) {
                PlaceholderBlock(
                    title = "该入口不在 App 内打开",
                    description = "请在首页卡片中使用对应入口。",
                )
                return@Column
            }

            // 软门禁：未登录统一认证时不加载页面，但已缓存的内容与其他入口都不受影响
            if (service.needsLogin && !casAuthenticated) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PlaceholderBlock(
                        title = "需要统一认证登录",
                        description = "${service.title}由统一认证放行。" +
                            "在登录页完成一次统一身份认证后，图书馆、学工、座位与办事大厅都会自动带入。",
                        actionLabel = "去登录",
                        onAction = onOpenLogin,
                    )
                }
                return@Column
            }

            if (loading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val failure = error
            if (failure != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlaceholderBlock(
                            title = "页面没能加载出来",
                            description = failure,
                            actionLabel = "重试",
                            onAction = {
                                error = null
                                loading = true
                            },
                        )
                        QduButton(
                            text = "用系统浏览器打开",
                            onClick = { ExternalLauncher(context).openUrl(targetUrl) },
                            kind = QduButtonKind.Ghost,
                            small = true,
                        )
                    }
                }
            } else {
                CampusWebView(
                    url = targetUrl,
                    cssInjection = service.cssInjection,
                    scriptInjection = service.scriptInjection,
                    userAgent = service.userAgent,
                    onProgress = {
                        progress = it
                        if (it >= 100) loading = false
                    },
                    onPageFinished = { url ->
                        loading = false
                        viewModel.persistCookies()
                    },
                    onError = { message ->
                        loading = false
                        error = message
                    },
                )
            }
        }
    }
}
