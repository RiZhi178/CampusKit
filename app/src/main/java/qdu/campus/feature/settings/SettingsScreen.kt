package qdu.campus.feature.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import qdu.campus.domain.model.SessionState
import qdu.campus.feature.web.CampusService
import qdu.campus.feature.web.icon
import qdu.campus.platform.common.CrashLogger
import java.net.URLEncoder
import qdu.campus.platform.designsystem.DialogButton
import qdu.campus.platform.designsystem.GroupHead
import qdu.campus.platform.designsystem.ListRow
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.TransientToast
import qdu.campus.platform.designsystem.UiSpacing
import qdu.campus.platform.designsystem.listBottomGuard
import qdu.campus.platform.system.ExternalLauncher

/**
 * 更多（原型屏 13）。分组：账户、学习空间、生活服务、外观、关于。
 *
 * 账户下只留两个入口：账号管理（退出登录与切换账号在账号二级页）与数据管理
 * （清除数据与导出日志收在同一个弹窗）。通知相关入口不在这里，筛选移到通知页本身。
 * 关于固定三项：检查更新、我要反馈、声明。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAccount: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenDormElectricity: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenService: (CampusService) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showData by remember { mutableStateOf(false) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var confirmClearAll by remember { mutableStateOf(false) }
    val version = remember(context) { appVersion(context) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("更多") }) },
        contentWindowInsets = WindowInsets.systemBars
            .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            .union(WindowInsets.ime),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = UiSpacing.PageHorizontal),
            ) {
                GroupHead(text = "账户")
                QduCard(padded = false) {
                    ListRow(
                        title = "账号管理",
                        subtitle = if (state.session == SessionState.Valid) {
                            "退出登录、切换账号"
                        } else {
                            "未登录"
                        },
                        subtitleMaxLines = 1,
                        leading = Icons.Outlined.ManageAccounts,
                        onClick = if (state.session == SessionState.Valid) onOpenAccount else onOpenLogin,
                    )
                    ListRow(
                        title = "数据管理",
                        subtitle = "清除缓存与数据、导出日志",
                        subtitleMaxLines = 1,
                        leading = Icons.Outlined.Storage,
                        divider = false,
                        onClick = { showData = true },
                    )
                }

                GroupHead(text = "学习空间")
                QduCard(padded = false) {
                    listOf(
                        CampusService.XG,
                        CampusService.XG_AWARD,
                        CampusService.LIBRARY_SEAT,
                        CampusService.LIBRARY,
                    ).forEachIndexed { index, service ->
                            ListRow(
                                title = service.title,
                                subtitle = service.subtitle,
                                subtitleMaxLines = 1,
                                leading = service.icon,
                                divider = index < 3,
                                onClick = { onOpenService(service) },
                            )
                        }
                }

                GroupHead(text = "生活服务")
                QduCard(padded = false) {
                    listOf(CampusService.PAYMENT, CampusService.REPAIR)
                        .forEach { service ->
                            ListRow(
                                title = service.title,
                                subtitle = service.subtitle,
                                subtitleMaxLines = 1,
                                leading = service.icon,
                                onClick = { onOpenService(service) },
                            )
                        }
                    ListRow(
                        title = "宿舍电费",
                        subtitle = "绑定房号，查剩余电量与余额",
                        subtitleMaxLines = 1,
                        leading = Icons.Outlined.Bolt,
                        divider = false,
                        onClick = onOpenDormElectricity,
                    )
                }

                GroupHead(text = "外观")
                QduCard(padded = false) {
                    ListRow(
                        title = "主题选择",
                        subtitle = "${state.scheme.label} · ${state.mode.label}",
                        subtitleMaxLines = 1,
                        leading = Icons.Outlined.Palette,
                        divider = false,
                        onClick = onOpenAppearance,
                    )
                }

                GroupHead(text = "关于")
                QduCard(padded = false) {
                    ListRow(
                        title = "检查更新",
                        subtitle = "当前版本 $version",
                        subtitleMaxLines = 1,
                        leading = Icons.Outlined.SystemUpdate,
                        onClick = viewModel::checkUpdate,
                    )
                    ListRow(
                        title = "我要反馈",
                        subtitle = "到 GitHub Issues 提问题或建议",
                        subtitleMaxLines = 1,
                        leading = Icons.Outlined.Feedback,
                        onClick = {
                            if (!ExternalLauncher(context).openUrl(feedbackIssueUrl(version))) {
                                viewModel.showMessage("无法打开浏览器")
                            }
                        },
                    )
                    ListRow(
                        title = "声明",
                        subtitle = "仅限个人使用，禁止外传",
                        subtitleMaxLines = 1,
                        leading = Icons.Outlined.Info,
                        divider = false,
                        onClick = { showDisclaimer = true },
                    )
                }

                Text(
                    text = "全部数据仅存本机 · 卸载即清除 · 不参与云备份",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = UiSpacing.SectionTop, bottom = listBottomGuard()),
                )
            }

            TransientToast(
                message = state.message,
                onDismiss = viewModel::dismissMessage,
                modifier = Modifier.padding(bottom = listBottomGuard()),
            )
        }
    }

    // 检查更新：无更新与失败走轻提示，有更新才弹窗（版本号 + 发布说明 + Release 页入口）。
    // 轻提示在 LaunchedEffect 里发，不在组合期改状态。
    LaunchedEffect(state.update) {
        when (state.update) {
            UpdateCheckState.Checking -> viewModel.showMessage("正在检查更新…")
            UpdateCheckState.Latest -> viewModel.showMessage("当前已是最新版本")
            UpdateCheckState.Failed -> viewModel.showMessage("检查更新失败，请检查网络后重试")
            else -> Unit
        }
        if (state.update != UpdateCheckState.Idle && state.update !is UpdateCheckState.Available) {
            viewModel.dismissUpdate()
        }
    }
    val update = state.update
    if (update is UpdateCheckState.Available) {
        UpdateDialog(
            update = update,
            onOpenRelease = {
                viewModel.dismissUpdate()
                if (!ExternalLauncher(context).openUrl(update.url)) {
                    viewModel.showMessage("无法打开浏览器")
                }
            },
            onDismiss = viewModel::dismissUpdate,
        )
    }

    if (showData) {
        AlertDialog(
            onDismissRequest = { showData = false },
            title = { Text("数据管理") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "数据与日志都只在本机；清除后无法恢复，登录凭据只随「全部」一起清掉。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DialogAction("导出日志", "仅本机，分享去向自定") {
                        val log = CrashLogger.read(context)
                        showData = false
                        if (log.isNotBlank()) {
                            ExternalLauncher(context).shareText(log, "崩溃日志")
                        } else {
                            viewModel.showMessage("暂无崩溃日志")
                        }
                    }
                    DialogAction("清除课表缓存", "课程、节次与学期记录") {
                        viewModel.clearTimetable()
                        showData = false
                    }
                    DialogAction("清除通知记录", "已抓取的研究生院通知") {
                        viewModel.clearNotices()
                        showData = false
                    }
                    DialogAction(
                        text = "清除全部数据",
                        description = "凭据、课表与通知一并清除",
                        danger = true,
                    ) {
                        showData = false
                        confirmClearAll = true
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = { showData = false },
                )
            },
        )
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            title = { Text("免责声明") },
            text = {
                Text(
                    text = "本应用是非官方的单机客户端，任何人可自行安装使用，非青岛大学官方出品。" +
                        "所有数据仅存于本机，不上传、不统计、不接入广告。" +
                        "涉及资金与不可逆操作的功能一律只做入口，不在本应用内完成。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                DialogButton(
                    text = "知道了",
                    kind = QduButtonKind.Primary,
                    onClick = { showDisclaimer = false },
                )
            },
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清除全部数据？") },
            text = {
                Text(
                    "会清除登录凭据、课表、通知、房号绑定与主题等全部本机数据与设置，" +
                        "退出后重新显示引导页，无法恢复。",
                )
            },
            confirmButton = {
                DialogButton(
                    text = "清除",
                    kind = QduButtonKind.Danger,
                    onClick = {
                        viewModel.clearAll()
                        confirmClearAll = false
                    },
                )
            },
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = { confirmClearAll = false },
                )
            },
        )
    }
}

@Composable
private fun DialogAction(
    text: String,
    description: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    // 数据操作做成带描边的整行，避免在弹窗里看起来只是一段文字。
    QduCard(modifier = Modifier.fillMaxWidth(), padded = false) {
        ListRow(
            title = text,
            subtitle = description,
            titleColor = color,
            divider = false,
            onClick = onClick,
        )
    }
}

/** 仓库公开页：检查更新看 Releases（取数走 UpdateFetcher），反馈提 Issues。 */
private const val REPO_NEW_ISSUE_URL = "https://github.com/RiZhi178/QDUCampus/issues/new"

/**
 * 发现新版本的弹窗：版本号 + 发布说明（原文，超长内滚）+ 打开 Release 页。
 * 不做应用内下载安装——下载与覆盖安装交给用户在浏览器里完成，本机数据不受影响。
 */
@Composable
private fun UpdateDialog(
    update: UpdateCheckState.Available,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 ${update.version}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = update.notes.ifBlank { "本次更新没有发布说明。" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            DialogButton(
                text = "打开 Release 页",
                kind = QduButtonKind.Primary,
                onClick = onOpenRelease,
            )
        },
        dismissButton = {
            DialogButton(
                text = "取消",
                kind = QduButtonKind.Ghost,
                onClick = onDismiss,
            )
        },
    )
}

/**
 * 反馈入口带模板：GitHub 支持用 `?body=` 预填新建 Issue 的正文，
 * 打开就是现成的版本与设备信息，用户只补问题描述。
 * 模板只带版本与设备型号，不带学号、Cookie 或日志内容。
 */
private fun feedbackIssueUrl(version: String): String {
    val body = "CampusKit $version\n" +
        "设备：${Build.MANUFACTURER} ${Build.MODEL}\n" +
        "系统：Android ${Build.VERSION.RELEASE}\n\n" +
        "问题描述：\n"
    return REPO_NEW_ISSUE_URL + "?body=" + URLEncoder.encode(body, "UTF-8")
}

private fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "${info.versionName} (${info.longVersionCode})"
}.getOrDefault("未知")
