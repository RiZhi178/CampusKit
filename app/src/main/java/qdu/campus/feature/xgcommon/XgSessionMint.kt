package qdu.campus.feature.xgcommon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.network.XG_DESKTOP_UA
import qdu.campus.platform.web.CampusWebView

/**
 * 学工静默铸会话的公共外壳：补会话内嵌页宿主、加载提示与超时。替点门户磁贴的注入脚本
 * 各页自带、不共用（选择器按各页实测写死）；落地后回灌 Cookie 由各页 saveFromWebView 完成
 * （机制见 docs/interfaces/xg.md §二·补）。
 */

/** 隐藏内嵌页单跳「卡住不动」的超时；每前进一格进度就重新计时。 */
const val XG_MINT_WEBVIEW_TIMEOUT_MS = 20_000L

/** 整条铸会话链的总时长上限，到点换登录引导。 */
const val XG_MINT_OVERALL_TIMEOUT_MS = 40_000L

/** 铸会话进度页：用户看到的是「自动登录」，不需要知道背后发生了什么。 */
@Composable
fun XgMintPane() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppLoading()
        Text(
            text = "正在自动登录，请稍候…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "通常只需几秒钟，不会弹出账号密码。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/**
 * 补会话内嵌页：跳票只能由门户页面脚本签发，内嵌页保持附着、注入脚本才跑得起来。
 * 内嵌页照常满屏可见地加载，上面再盖一层不透明的「正在自动登录」提示挡住门户。
 * 不能缩成 1dp 或调透明：不可见的 WebView 渲染进程会被系统以「isolated not needed」杀掉
 * （本机 ColorOS 实测，钉渲染进程优先级也压不住），学工门户重页面加载久、杀在半途补会话就失败；
 * 可见地画出来渲染进程才保得住。盖层同时吃掉触摸，免得误点门户。
 */
@Composable
fun XgMintWebView(
    url: String,
    script: String,
    onPageFinished: (String) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        CampusWebView(
            url = url,
            modifier = Modifier.fillMaxSize(),
            userAgent = XG_DESKTOP_UA,
            scriptInjection = script,
            timeoutMillis = XG_MINT_WEBVIEW_TIMEOUT_MS,
            handleBack = false,
            onPageFinished = onPageFinished,
            onError = { onError() },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            XgMintPane()
        }
    }
}
