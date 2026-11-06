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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.system.ExternalLauncher
import qdu.campus.platform.web.CampusWebView

/** 任意 URL 的内嵌页：通知原文、网页认证、自助服务。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlWebScreen(
    url: String,
    title: String,
    onBack: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    QduButton(
                        text = "浏览器",
                        onClick = { ExternalLauncher(context).openUrl(url) },
                        kind = QduButtonKind.Ghost,
                        small = true,
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
                                reloadKey += 1
                            },
                        )
                        QduButton(
                            text = "用系统浏览器打开",
                            onClick = { ExternalLauncher(context).openUrl(url) },
                            kind = QduButtonKind.Ghost,
                            small = true,
                        )
                    }
                }
            } else {
                key(reloadKey) {
                    CampusWebView(
                        url = url,
                        onProgress = {
                            progress = it
                            if (it >= 100) loading = false
                        },
                        onPageFinished = { loading = false },
                        onError = {
                            loading = false
                            error = it
                        },
                        timeoutMillis = 12_000,
                    )
                }
            }
        }
    }
}
