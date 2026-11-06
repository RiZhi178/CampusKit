package qdu.campus.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.feature.account.accountCardState
import qdu.campus.platform.common.Formats
import qdu.campus.platform.designsystem.AccountCard
import qdu.campus.platform.designsystem.LocalStatusColors
import qdu.campus.platform.designsystem.Pill
import qdu.campus.platform.designsystem.PillKind
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.SectionHead
import qdu.campus.platform.designsystem.StatusLevel
import qdu.campus.platform.designsystem.UiShapes
import qdu.campus.platform.designsystem.UiSpacing
import qdu.campus.platform.designsystem.UnreadDot
import qdu.campus.platform.designsystem.cardBorderColor
import qdu.campus.platform.designsystem.listBottomGuard
import qdu.campus.platform.designsystem.rowDivider
import qdu.campus.platform.system.ExternalLauncher
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.Notice
import qdu.campus.domain.model.SessionState
import qdu.campus.feature.web.CampusService
import qdu.campus.feature.web.EntryIconTint
import qdu.campus.feature.web.ServiceTarget
import qdu.campus.feature.web.icon
import qdu.campus.feature.web.iconTint
import kotlinx.coroutines.launch

/**
 * 首页（原型屏 03 / 15）。五段：校园网状态条、账号卡、今日课、常用入口、
 * 研究生院通知。账号卡未登录时给「立即登录」，已登录时点卡进账号二级页。
 */
@Composable
fun HomeScreen(
    onOpenService: (CampusService) -> Unit,
    onOpenScan: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenNotice: (String) -> Unit,
    onOpenNoticeList: () -> Unit,
    onOpenTimetable: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenCardEdit: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = remember { ExternalLauncher(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        // 底部已由外层的图标底栏让出，这里只处理状态栏与左右。
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = {
                viewModel.refreshEnvironment()
                viewModel.refreshContent()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = UiSpacing.PageHorizontal,
                    end = UiSpacing.PageHorizontal,
                    top = 0.dp,
                    bottom = listBottomGuard(),
                ),
            ) {
                item {
                    HomeTop(
                        ssid = state.ssid,
                        isCampusWifi = state.isCampusWifi,
                        onOpenNetwork = onOpenNetwork,
                    )
                }

                item {
                    AccountCard(
                        state = accountCardState(state.session, state.studentId, state.userName),
                        compact = true,
                        onClick = if (state.session == SessionState.Valid) onOpenAccount else null,
                        onSignIn = onOpenLogin,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                item {
                    SectionHead(
                        title = "今日",
                        more = when {
                            state.todayCourses.isNotEmpty() -> "${state.todayCourses.size} 节"
                            state.session != SessionState.Valid -> "离线"
                            else -> "课表"
                        },
                        onMore = onOpenTimetable,
                        modifier = Modifier.padding(top = 15.dp, bottom = 8.dp),
                    )
                }

                item {
                    QduCard(padded = false) {
                        if (state.todayCourses.isEmpty()) {
                            Text(
                                text = "今天没有课",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            )
                        } else {
                            state.todayCourses.forEachIndexed { index, course ->
                                TodayRow(
                                    course = course,
                                    inSession = course.id == state.currentCourseId,
                                    divider = index != state.todayCourses.lastIndex,
                                )
                            }
                        }
                    }
                }

                item {
                    SectionHead(
                        title = "常用",
                        more = "编辑",
                        onMore = onOpenCardEdit,
                        modifier = Modifier.padding(top = 15.dp, bottom = 8.dp),
                    )
                }

                // 通栏扫码大卡：取代原先打水、开空调、洗衣服三张卡（PRD F2-6）。
                // 三类设备都靠扫机身上的码使用，识别转交见 docs/scan-slice.md。
                item {
                    ScanCard(
                        onClick = onOpenScan,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                itemsIndexed(state.homeCards.chunked(2)) { index, row ->
                    // 同一行的卡片按内容最高者取齐，不写死高度：换设备、换字号都成立。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                            // 行与行之间留 8.dp；首行不叠加，SectionHead 的下边距已经给了第一段。
                            .padding(top = if (index == 0) 0.dp else 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { service ->
                            EntryCard(
                                service = service,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                onClick = {
                                    handleService(
                                        service = service,
                                        launcher = launcher,
                                        onOpenService = onOpenService,
                                        onMessage = { message ->
                                            scope.launch { snackbar.showSnackbar(message) }
                                        },
                                    )
                                },
                            )
                        }
                        // 奇数个时末行单卡靠左，与原型一致
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                item {
                    SectionHead(
                        title = "校内通知",
                        more = "全部",
                        onMore = onOpenNoticeList,
                        modifier = Modifier.padding(top = 15.dp, bottom = 8.dp),
                    )
                }

                item {
                    QduCard(padded = false) {
                        if (state.notices.isEmpty()) {
                            Text(
                                text = "暂无通知，下拉刷新可重新抓取",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            )
                        } else {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                                state.notices.forEachIndexed { index, notice ->
                                    NoticeRow(
                                        notice = notice,
                                        divider = index != state.notices.lastIndex,
                                        onClick = { onOpenNotice(notice.url) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶栏：只留「首页」二字，行高 64 与其余页面的 TopAppBar 对齐；
 * 右侧校园网徽章缩成单行小胶囊，与标题共用一条中线。
 *
 * 学期、周次、日期不再占这一行：前两项在课表页才有完整上文，
 * 日期与「今天是第几周」又依赖课表数据，摆在首页顶部反而容易看成准的。
 */
@Composable
private fun HomeTop(
    ssid: String?,
    isCampusWifi: Boolean,
    onOpenNetwork: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "首页",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        NetChip(
            ssid = ssid ?: "校园网",
            isCampusWifi = isCampusWifi,
            onClick = onOpenNetwork,
        )
    }
}

/**
 * 校园网徽章。视觉高 30，触控热区仍按 48 给——外圈 Box 撑开热区，不靠视觉高度凑。
 * 状态用文字 + 图标颜色两重表达，文字是主载体，色觉异常也能读。
 *
 * 只有「在/不在校园网」两态：原生认证链已移除，认证成没成应用侧没有可靠判据，
 * 不再显示「已认证/未认证」。
 */
@Composable
private fun NetChip(
    ssid: String,
    isCampusWifi: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val label = if (isCampusWifi) "校园网" else "校园网外"
    val tint = if (isCampusWifi) status.success else status.blocked
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$ssid，$label，打开校园网" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(scheme.surfaceContainer)
                .border(1.dp, cardBorderColor(), RoundedCornerShape(15.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Wifi,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = tint,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                color = scheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** 今日的一节课。左侧固定 44 宽放起止时间，正在上的那节加底色与左条，不只靠颜色：还带「在上」药丸。 */
@Composable
private fun TodayRow(course: Course, inSession: Boolean, divider: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val borderColor = cardBorderColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(if (inSession) Modifier.background(scheme.primaryContainer) else Modifier)
            .then(if (divider) Modifier.rowDivider(borderColor) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (inSession) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 30.dp)
                    .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 3.dp, bottomEnd = 3.dp, bottomStart = 0.dp))
                    .background(scheme.primary),
            )
        }
        Column(modifier = Modifier.width(44.dp)) {
            Text(
                text = course.startTime.ifBlank { course.sectionLabel },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
            )
            if (course.endTime.isNotBlank()) {
                Text(
                    text = course.endTime,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(course.room, course.teachers).filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { course.sectionLabel.ifBlank { "教室未标注" } },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (inSession) {
            Pill(text = "在上", kind = PillKind.Prim, mark = StatusLevel.SUCCESS)
        }
    }
}

/** 常用入口卡：30 图标底 + 名称 + 去向说明。高度由同一行的最高者决定。 */
/**
 * 通栏扫码大卡。横向布局与入口小卡同一套材质（surfaceContainerLow 底、1dp 描边、
 * 同一圆角），作为「常用」区的首块要明显高过普通小卡：图标容器 44、上下留白加大，
 * 副标题给两行。触控高度由内容撑出，远大于 48dp 下限。
 */
@Composable
private fun ScanCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(UiShapes.Card)
            .background(scheme.surfaceContainerLow)
            .border(1.dp, cardBorderColor(), UiShapes.Card)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(UiShapes.Icon)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = scheme.onPrimaryContainer,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "扫码",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "打水、洗衣机、空调：识别后自动转交微信或支付宝",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 入口小卡。图标容器色按 [EntryIconTint] 轮换，说明行固定占两行，
 * 同一行的卡片按内容最高者取齐。
 */
@Composable
private fun EntryCard(
    service: CampusService,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer) = when (service.iconTint) {
        EntryIconTint.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        EntryIconTint.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        EntryIconTint.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    Column(
        modifier = modifier
            .clip(UiShapes.Card)
            .background(scheme.surfaceContainerLow)
            .border(1.dp, cardBorderColor(), UiShapes.Card)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(UiShapes.Icon)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = service.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = onContainer,
            )
        }
        Text(
            text = service.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // 说明行固定占两行，单行与两行的卡片高度才一致；超出部分省略而不是撑高。
        Text(
            text = service.subtitle,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
            color = scheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoticeRow(
    notice: Notice,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val borderColor = cardBorderColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(if (divider) Modifier.rowDivider(borderColor) else Modifier)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 未读只留一个小圆点；已读留空位，标题左缘对得齐
        UnreadDot(read = notice.read)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Formats.noticeTitle(notice.title),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill(
                    // 药丸放来源：两个来源混排后，光看日期分不清是研究生院还是教务处
                    text = if (notice.source == "jwc_notice") "教务处" else "研究生院",
                    kind = if (notice.source == "jwc_notice") PillKind.Prim else PillKind.Default,
                )
                Text(
                    text = notice.date,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** B 组跳转失败要有两步内的替代路径，因此每次都把兜底做成 Snackbar 提示。 */
private fun handleService(
    service: CampusService,
    launcher: ExternalLauncher,
    onOpenService: (CampusService) -> Unit,
    onMessage: (String) -> Unit,
) {
    // 有原生页的服务一律先进原生页（电费、办事大厅、评奖评优等）；原生页自己会处理会话回退。
    if (service.nativeRoute != null) {
        onOpenService(service)
        return
    }
    when (val target = service.target) {
        is ServiceTarget.InternalWeb -> onOpenService(service)
        is ServiceTarget.ExternalWeb -> {
            if (!launcher.openUrl(target.url)) {
                launcher.copyToClipboard(target.url, service.title)
                onMessage("打不开网页，网址已复制：${target.url}")
            }
        }
        is ServiceTarget.ExternalApp -> {
            if (!launcher.openAppOrStore(target.packageName, target.appName)) {
                onMessage("未找到${target.appName}，请到应用商店搜索「${target.appName}」")
            }
        }
        is ServiceTarget.WeChat -> {
            if (!launcher.openWeChat()) {
                launcher.copyToClipboard(target.keyword, "微信关键词")
                onMessage("未安装微信，名称「${target.keyword}」已复制")
            } else {
                onMessage("在微信里搜索「${target.keyword}」公众号或小程序")
            }
        }
    }
}
