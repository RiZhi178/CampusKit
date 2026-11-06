package qdu.campus

import android.app.Application
import android.util.Log
import android.webkit.WebSettings
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
    }

    private companion object {
        const val TAG = "CampusApp"
    }
}
