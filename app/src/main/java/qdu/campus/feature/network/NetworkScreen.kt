package qdu.campus.feature.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import qdu.campus.domain.model.CampusNetworkEnvironment
import qdu.campus.domain.model.CampusWifi
import qdu.campus.platform.designsystem.InlineNotice
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.SectionCard
import qdu.campus.platform.system.CampusPortal
import qdu.campus.platform.system.ExternalLauncher
import qdu.campus.platform.system.WifiPanel
import qdu.campus.platform.system.WifiPermission

/**
 * 校园网模块只做两件事：告诉用户当前在不在校园网、地址是多少；把接入与认证的入口递出去。
 *
 * 不自己发认证请求：连上 QDU-Web 后系统会弹「需要登录 WLAN」，认证在系统的捕获门户页里完成。
 * 这里留的网页入口是兜底，且交给系统浏览器——内网 portal 是 http，应用侧不放行它的明文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(
    onOpenSelfService: (String) -> Unit,
    viewModel: NetworkViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val environment = state.environment
    val context = LocalContext.current
    val launcher = remember { ExternalLauncher(context) }

    var permissionState by remember { mutableStateOf(WifiPermission.grantStatus(context)) }
    var launchFailure by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionState = WifiPermission.grantStatus(context)
        viewModel.refresh()
    }
    // 只在第一次进入时申请，被拒绝后不再反复弹窗，改由提示条提供入口
    LaunchedEffect(Unit) {
        if (permissionState == WifiPermission.GrantState.Missing) {
            permissionLauncher.launch(WifiPermission.required)
        }
    }
    // 从系统 Wi-Fi 面板或浏览器返回时重读一次，避免停在旧地址上
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园网") },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新检测")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (launchFailure != null) {
                item {
                    InlineNotice(
                        text = launchFailure.orEmpty(),
                        icon = Icons.Filled.Info,
                        isError = true,
                        actionLabel = "知道了",
                        onAction = { launchFailure = null },
                    )
                }
            }

            if (permissionState == WifiPermission.GrantState.Missing) {
                item {
                    InlineNotice(
                        text = "读取当前 Wi-Fi 名称需要定位权限。不给也能用，只是判不出在不在校园网。",
                        icon = Icons.Filled.Info,
                        actionLabel = "授权",
                        onAction = { permissionLauncher.launch(WifiPermission.required) },
                    )
                }
            }

            item {
                SectionCard(title = "当前网络") {
                    ValueRow("Wi-Fi 名称", environment.ssid)
                    ValueRow("接入状态", accessLabel(environment))
                    ValueRow(
                        label = "本机 IPv4",
                        value = environment.localIpv4,
                        onCopy = { launcher.copyToClipboard(it, "IPv4 地址") },
                        monospace = true,
                    )
                    ValueRow(
                        label = "IPv6 全局",
                        value = environment.ipv6Global.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                        onCopy = { launcher.copyToClipboard(it, "IPv6 地址") },
                        monospace = true,
                    )
                    ValueRow(
                        label = "IPv6 本地",
                        value = environment.ipv6LinkLocal.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                        onCopy = { launcher.copyToClipboard(it, "链路本地地址") },
                        monospace = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "地址由系统分配、随连接变化。校园网可能同时下发 IPv6，" +
                            "只显示 IPv4 容易让人以为没连上，所以这里把 v6 一并列出；" +
                            "两类都没有就是这次连接确实没拿到 v6。" +
                            "「IPv6 本地」是 fe80 开头的链路本地地址，出了本链路不可用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionCard(title = "接入与认证") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = when (environment.campusWifi) {
                                CampusWifi.Qdu1x -> "QDU-1X 在无线连接面板里填一次账号密码、信任证书，" +
                                    "之后连接时由系统完成 802.1X 认证，不需要在这一页做任何事。" +
                                    "连不上时打开面板重新输入。"
                                CampusWifi.QduWeb -> "QDU-Web 连上后还要过网页认证。系统一般会直接弹出" +
                                    "「需要登录 WLAN」，在那个页面里登录一次即可；没弹时可以手动打开认证页。"
                                null -> "学校只有 QDU-1X 与 QDU-Web 两个 SSID，两个都算校园网。" +
                                    "应用不能替你连接或保存 Wi-Fi 网络（Android 10 起系统不允许），" +
                                    "能做的只是把系统面板打开。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        QduButton(
                            text = "打开系统 Wi-Fi 面板",
                            kind = QduButtonKind.Tonal,
                            onClick = {
                                if (!WifiPanel.open(context)) {
                                    launchFailure = "系统没有提供可用的 Wi-Fi 面板，请在系统设置里手动打开无线局域网。"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (environment.campusWifi == CampusWifi.QduWeb) {
                            QduButton(
                                text = "用浏览器打开认证页",
                                kind = QduButtonKind.Ghost,
                                onClick = {
                                    if (!launcher.openUrl(CampusPortal.AUTH_PAGE)) {
                                        launchFailure = "没有可用的浏览器打开认证页，请从系统的「需要登录 WLAN」通知进入。"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "认证页由系统浏览器打开，本应用不接收、不保存校园网密码，" +
                                    "也不判断认证是否成功；有没有认证上，以校内网站能否打开为准。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(title = "自助服务") {
                    Text(
                        text = "查看与下线在线设备、修改套餐。该系统需单独登录，验证码每次必填；" +
                            "设备数已满导致无法认证时，在这里下线闲置设备。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    QduButton(
                        text = "打开自助服务",
                        kind = QduButtonKind.Ghost,
                        small = true,
                        onClick = { onOpenSelfService(CampusPortal.SELF_SERVICE) },
                    )
                }
            }
        }
    }
}

/** 接入状态只按 SSID 分档，不掺认证结果——认证成没成，应用侧没有可靠判据。 */
private fun accessLabel(environment: CampusNetworkEnvironment): String = when {
    environment.campusWifi == CampusWifi.Qdu1x -> "校园网 · QDU-1X（802.1X）"
    environment.campusWifi == CampusWifi.QduWeb -> "校园网 · QDU-Web（需网页认证）"
    !environment.isWifi -> "未连接 Wi-Fi"
    environment.ssid == null -> "Wi-Fi · 读不到名称"
    else -> "非校园网 Wi-Fi"
}

/**
 * 一行「标签 + 值 + 复制」。值为空时给占位符并隐藏复制按钮，
 * 地址用等宽字体，报障时把一组字符逐位念给对方不会混。
 */
@Composable
private fun ValueRow(
    label: String,
    value: String?,
    onCopy: ((String) -> Unit)? = null,
    monospace: Boolean = false,
) {
    val text = value?.takeIf { it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = text ?: "—",
            style = if (monospace) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp),
        )
        if (onCopy != null && text != null) {
            QduButton(
                text = "复制",
                kind = QduButtonKind.Ghost,
                small = true,
                onClick = { onCopy(text) },
            )
        }
    }
}
