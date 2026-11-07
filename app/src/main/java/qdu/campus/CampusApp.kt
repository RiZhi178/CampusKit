package qdu.campus

import android.app.Application
import android.util.Log
import android.webkit.WebSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import qdu.campus.data.local.seed.AssetSeeder
import qdu.campus.platform.common.CrashLogger
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.system.NoticeNotifier
import qdu.campus.platform.web.WebSessionCoordinator
import qdu.campus.domain.repository.SessionRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CampusApp : Application() {

    @Inject
    lateinit var notifier: NoticeNotifier

    @Inject
    lateinit var session: WebSessionCoordinator

    @Inject
    lateinit var sessionState: SessionRepository

    @Inject
    lateinit var assetSeeder: AssetSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        notifier.ensureChannel()
        // OkHttp 的 UA 必须与 WebView 一致，否则同一会话在服务端看来是两个客户端
        runCatching {
            HttpClientFactory.USER_AGENT = WebSettings.getDefaultUserAgent(this)
        }
        // 同步回灌，必须早于第一个 WebView 加载，否则校内系统会再次要求登录
        runCatching { session.restorePersistedCookies() }
            .onFailure { Log.w(TAG, "Cookie 回灌失败", it) }
        // 回灌完立刻按本机是否有票定态，否则首页状态卡要等前台探测才从「未登录」改口
        sessionState.warmStart()
        // 随包资产种子进 Room（本科课表 / 楼栋字典 / 校历规则）。指纹没变的学期直接跳过，
        // 只有首装或资产更新才真正写库；失败不挡启动，页面按「暂无数据」处理，下次再试。
        appScope.launch { assetSeeder.seedAll() }
    }

    private companion object {
        const val TAG = "CampusApp"
    }
}
