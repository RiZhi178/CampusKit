package qdu.campus.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import qdu.campus.domain.model.WebDomain
import qdu.campus.platform.designsystem.QduBottomBar
import qdu.campus.platform.designsystem.QduTab
import qdu.campus.platform.designsystem.UiSpacing
import qdu.campus.platform.designsystem.qduTapDeadZone
import qdu.campus.feature.account.AccountScreen
import qdu.campus.feature.appraisal.AppraisalScreen
import qdu.campus.feature.auth.LoginScreen
import qdu.campus.feature.baoxiu.BaoxiuDetailScreen
import qdu.campus.feature.baoxiu.BaoxiuFormScreen
import qdu.campus.feature.baoxiu.BaoxiuListScreen
import qdu.campus.feature.ehall.EhallScreen
import qdu.campus.feature.electricity.DormElectricityScreen
import qdu.campus.feature.electricity.RoomCodeScreen
import qdu.campus.feature.home.HomeCardEditScreen
import qdu.campus.feature.home.HomeScreen
import qdu.campus.feature.library.LibraryScreen
import qdu.campus.feature.network.NetworkScreen
import qdu.campus.feature.notice.NoticeScreen
import qdu.campus.feature.onboarding.OnboardingScreen
import qdu.campus.feature.scan.ScanScreen
import qdu.campus.feature.seat.SeatScreen
import qdu.campus.feature.settings.AppearanceScreen
import qdu.campus.feature.settings.SettingsScreen
import qdu.campus.feature.timetable.TimetableScreen
import qdu.campus.feature.timetable.YjsxtLoginScreen
import qdu.campus.feature.web.CampusService
import qdu.campus.feature.web.UrlWebScreen
import qdu.campus.feature.web.WebScreen

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** 页签间的淡入淡出。默认 700ms 会被玻璃底栏放大成一段"内容浮出"的过程。 */
private const val TabFadeMillis = 120

private val TabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { fadeIn(tween(TabFadeMillis)) }

private val TabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { fadeOut(tween(TabFadeMillis)) }

/** 四栏：首页 / 课表 / 通知 / 我的。顺序与原型底部导航一致；图标取面型，选中后染主色。 */
private val tabs = listOf(
    Tab(Routes.HOME, "首页", Icons.Filled.Home),
    Tab(Routes.TIMETABLE, "课表", Icons.Filled.CalendarMonth),
    Tab(Routes.NOTICE, "通知", Icons.Filled.Notifications),
    Tab(Routes.SETTINGS, "更多", Icons.Filled.Menu),
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val startup: StartupViewModel = hiltViewModel()
    val startDestination by startup.startDestination.collectAsStateWithLifecycle()

    val resolved = startDestination
    if (resolved == null) {
        // 起始路由要在建图前定下来，读首选项是一次挂起调用，先给一块同色底避免出现白闪。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        )
        return
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedTab = tabs.indexOfFirst { it.route == currentRoute }

    // 底栏不再占布局：NavHost 铺满整屏并被录进 backdrop，底栏叠在它上面取背景做玻璃。
    val backdrop = rememberLayerBackdrop()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        NavHost(
            navController = navController,
            startDestination = resolved,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    // 不把引导页弹出栈：02 的返回箭头要能回到 01，
                    // 真正进应用时再由 enterApp 一次性把引导页换成首页。
                    onStartLogin = { navController.navigate(Routes.LOGIN) },
                    onSkip = { navController.enterApp() },
                )
            }

            composable(
                route = Routes.HOME,
                enterTransition = TabEnter,
                exitTransition = TabExit,
                popEnterTransition = TabEnter,
                popExitTransition = TabExit,
            ) {
                HomeScreen(
                    onOpenService = { service -> navController.openService(service) },
                    onOpenScan = { navController.navigate(Routes.SCAN) },
                    onOpenNetwork = { navController.navigate(Routes.NETWORK) },
                    onOpenNotice = { url -> navController.navigate(Routes.webUrl(url)) },
                    onOpenNoticeList = { navController.navigateTab(Routes.NOTICE) },
                    onOpenTimetable = { navController.navigateTab(Routes.TIMETABLE) },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                    onOpenCardEdit = { navController.navigate(Routes.HOME_CARDS) },
                )
            }

            composable(
                route = Routes.TIMETABLE,
                enterTransition = TabEnter,
                exitTransition = TabExit,
                popEnterTransition = TabEnter,
                popExitTransition = TabExit,
            ) {
                TimetableScreen(
                    onOpenLogin = { navController.navigate(Routes.YJSXT_LOGIN) },
                    onOpenStudentLogin = {
                        navController.navigate(
                            Routes.webUrl("https://xjw.qdu.edu.cn/jsxsd/login", "本科教务系统登录"),
                        )
                    },
                )
            }

            composable(
                route = Routes.NOTICE,
                enterTransition = TabEnter,
                exitTransition = TabExit,
                popEnterTransition = TabEnter,
                popExitTransition = TabExit,
            ) {
                NoticeScreen(
                    onOpenNotice = { notice ->
                        navController.navigate(Routes.webUrl(notice.url, notice.title))
                    },
                )
            }

            composable(
                route = Routes.SETTINGS,
                enterTransition = TabEnter,
                exitTransition = TabExit,
                popEnterTransition = TabEnter,
                popExitTransition = TabExit,
            ) {
                SettingsScreen(
                    onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    onOpenDormElectricity = { navController.navigate(Routes.DORM_ELECTRICITY) },
                    onOpenAppearance = { navController.navigate(Routes.APPEARANCE) },
                    onOpenService = { service -> navController.openService(service) },
                )
            }

            composable(Routes.APPEARANCE) {
                AppearanceScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.HOME_CARDS) {
                HomeCardEditScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.NETWORK) {
                NetworkScreen(
                    onOpenSelfService = { url ->
                        navController.navigate(Routes.webUrl(url, "校园网自助服务"))
                    },
                )
            }

            composable(Routes.LOGIN) {
                LoginScreen(
                    onFinished = { navController.enterApp() },
                    onBack = { navController.popBackStack() },
                )
            }

            // 研究生系统只认自己的登录，课表页从这里进去登一次
            composable(Routes.YJSXT_LOGIN) {
                YjsxtLoginScreen(
                    onBack = { navController.popBackStack() },
                    onDone = { navController.popBackStack() },
                )
            }

            composable(Routes.ACCOUNT) {
                AccountScreen(
                    onBack = { navController.popBackStack() },
                    onSwitchAccount = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(Routes.HOME) { inclusive = false }
                        }
                    },
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                )
            }

            composable(Routes.DORM_ELECTRICITY) {
                DormElectricityScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBinding = { navController.navigate(Routes.ROOM_CODE) },
                    onOpenRecharge = {
                        navController.navigate(Routes.web(CampusService.ELECTRICITY.id))
                    },
                    onOpenLogin = {
                        navController.navigate(Routes.web(CampusService.ELECTRICITY.id))
                    },
                )
            }

            composable(Routes.ROOM_CODE) {
                RoomCodeScreen(
                    onBack = { navController.popBackStack() },
                    // 绑定成功就退回宿舍电费页，由那一页重查并显示账户信息
                    onBound = { navController.popBackStack() },
                    onOpenRecharge = {
                        navController.navigate(Routes.web(CampusService.ELECTRICITY.id))
                    },
                )
            }

            composable(Routes.SEAT) {
                SeatScreen(
                    onBack = { navController.popBackStack() },
                    // 会话缺失时回到同一个内嵌页登录，Cookie 由 CookieBridge 带回原生请求
                    onOpenWebLogin = {
                        navController.navigate(Routes.web(CampusService.LIBRARY_SEAT.id))
                    },
                )
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onBack = { navController.popBackStack() },
                    // 缺会话时进统一认证页；图书馆自己的会话头由页面脚本补，不把人送去官网
                    onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    // 数据库导航这类没原生化的资源仍从图书馆门户进
                    onOpenPortal = {
                        navController.navigate(Routes.webUrl(WebDomain.LIB.homeUrl, "图书馆门户"))
                    },
                )
            }

            composable(Routes.XG_AWARD) {
                AppraisalScreen(
                    onBack = { navController.popBackStack() },
                    // 阶段一不自动补会话：缺会话时送回学工门户，那一下卡片由人自己点
                    onOpenWebLogin = {
                        navController.navigate(Routes.web(CampusService.XG.id))
                    },
                )
            }

            composable(Routes.EHALL) {
                EhallScreen(
                    onBack = { navController.popBackStack() },
                    onOpenService = { url, title ->
                        navController.navigate(Routes.webUrl(url, title))
                    },
                    onOpenWebLogin = {
                        navController.navigate(Routes.web(CampusService.EHALL.id))
                    },
                )
            }

            composable(Routes.BAOXIU) {
                BaoxiuListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { ticketId ->
                        navController.navigate(Routes.baoxiuDetail(ticketId))
                    },
                    onOpenForm = { navController.navigate(Routes.BAOXIU_NEW) },
                    onOpenWebLogin = {
                        navController.navigate(Routes.web(CampusService.REPAIR.id))
                    },
                )
            }

            composable(
                route = Routes.BAOXIU_DETAIL,
                arguments = listOf(navArgument("ticketId") { type = NavType.StringType }),
            ) {
                BaoxiuDetailScreen(
                    onBack = { navController.popBackStack() },
                    onCancelled = { navController.popBackStack() },
                    onOpenWebLogin = {
                        navController.navigate(Routes.web(CampusService.REPAIR.id))
                    },
                )
            }

            composable(Routes.BAOXIU_NEW) {
                BaoxiuFormScreen(
                    onBack = { navController.popBackStack() },
                    onSubmitted = {
                        // 提交成功后直接回列表，由列表重新拉一次拿到新工单号。
                        navController.popBackStack()
                        navController.navigate(Routes.BAOXIU) {
                            popUpTo(Routes.BAOXIU) { inclusive = true }
                        }
                    },
                    onOpenWebLogin = {
                        navController.navigate(Routes.web(CampusService.REPAIR.id))
                    },
                )
            }

            composable(Routes.SCAN) {
                ScanScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.WEB,
                arguments = listOf(navArgument("serviceId") { type = NavType.StringType }),
            ) { entry ->
                val service = CampusService.fromId(entry.arguments?.getString("serviceId"))
                if (service == null) {
                    UrlWebScreen(url = "about:blank", title = "未知入口", onBack = {
                        navController.popBackStack()
                    })
                } else {
                    WebScreen(
                        service = service,
                        onBack = { navController.popBackStack() },
                        onOpenLogin = { navController.navigate(Routes.LOGIN) },
                    )
                }
            }

            composable(
                route = Routes.WEB_URL,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val args = entry.arguments
                UrlWebScreen(
                    url = Uri.decode(args?.getString("url").orEmpty()),
                    title = Uri.decode(args?.getString("title").orEmpty()).ifBlank { "页面" },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // 底栏与死区的出现跟随导航过渡淡入淡出：currentBackStackEntry 在过渡开始时就
        // 已切到目标页，不加动画的话返回还在淡出、底栏已经整块压上来，体验很割裂。
        // 进场 280ms 与页面过渡重叠着完成，退场快走（120ms）不挡返回的视线。
        // 注意不能再用 if(selectedTab>=0) 包外层：那样离场时内容被直接移除，退场动画没有机会跑。
        AnimatedVisibility(
            visible = selectedTab >= 0,
            enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 },
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // 死区叠在内容之上、底栏之下：胶囊上下这条带里的轻点被吞掉，
            // 免得身后露头的列表项被误触；竖向拖动不吞，列表照样能从这里起手滚。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .qduTapDeadZone()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .height(UiSpacing.BottomBarDeadZone),
            )
        }
        AnimatedVisibility(
            visible = selectedTab >= 0,
            enter = fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 3 },
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            QduBottomBar(
                tabs = tabs.map { QduTab(it.label, it.icon) },
                // 非页签路由上 selectedTab 是 -1，退场动画期间钳到 0，避免选中胶囊滑出界
                selectedIndex = selectedTab.coerceAtLeast(0),
                backdrop = backdrop,
                onSelect = { index ->
                    val target = tabs[index]
                    if (target.route != currentRoute) {
                        navController.navigateTab(target.route)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 切换底栏页签。以图的起始目的地为基准弹栈，
 * 无论当前页是点底栏进来的还是从首页「今日」「全部」跳进来的，
 * 栈里最多只留一个目标页实例，底栏再点首页能直接弹回。
 */
private fun NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 打开一个服务入口。有原生页的走原生，其余内嵌 WebView。
 * 外部 App、外部网页与微信这几类由列表页自己处理，不在这里接管。
 */
private fun NavController.openService(service: CampusService) {
    val native = service.nativeRoute
    when {
        native != null -> navigate(native)
        service.isInternal -> navigate(Routes.web(service.id))
    }
}

/**
 * 进应用：引导页与登录页都从栈里清掉，首页成为唯一底。
 *
 * 从首页或「我的」进来时栈里已经有 HOME，弹回去即可；
 * 从引导页进来时栈是 [引导页, 登录页]，popBackStack 找不到 HOME 会返回 false，
 * 只能用 navigate + popUpTo(引导页, inclusive) 把这两页一起换掉。
 */
private fun NavController.enterApp() {
    if (popBackStack(Routes.HOME, inclusive = false)) return
    navigate(Routes.HOME) {
        popUpTo(Routes.ONBOARDING) { inclusive = true }
        launchSingleTop = true
    }
}
