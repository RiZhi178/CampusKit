package qdu.campus.feature.timetable

import android.webkit.WebView
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qdu.campus.data.repository.TimetableRepositoryImpl
import qdu.campus.domain.repository.RefreshOutcome
import qdu.campus.platform.designsystem.HintBar
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.web.CampusWebView
import qdu.campus.platform.web.WebSessionCoordinator
import javax.inject.Inject

/**
 * 站点根路径会按它自己的 `getLoginType` 落到账号页，客户端不去猜子路由。
 */
private const val YJSXT_LOGIN_URL = "https://yjsxt.qdu.edu.cn/"

/**
 * 研究生系统的独立登录入口。
 *
 * 为什么需要它：统一认证的票换不出研究生系统的会话（2026-11-05 实测，票据合法、
 * CAS 也认，但它的 `getToken` 在校验前就回「认证失败」，见 `docs/interfaces/yjsxt.md`）。
 * 这个系统是独立账号，只能让它自己的页面登一次。
 *
 * 边界（AGENTS 2.3）：密码只输在校方页面里，客户端不读取、不预填、不保存；
 * 登录成功后只接管它种下的令牌（`QZKJ-YJSBB-TOEKN` 或 `sessionStorage.originalToken`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YjsxtLoginScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: YjsxtLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("研究生系统登录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    QduButton(
                        text = if (state.working) "正在同步…" else "完成",
                        onClick = { viewModel.finishNow(onDone) },
                        kind = QduButtonKind.Ghost,
                        small = true,
                        enabled = !state.working,
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
            if (state.loading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HintBar(
                text = state.message
                    ?: "课表来自研究生系统，它只认自己的登录。密码由校方页面接收，本应用不读取、不保存。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val error = state.error
            if (error != null) {
                HintBar(
                    text = error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            CampusWebView(
                url = YJSXT_LOGIN_URL,
                modifier = Modifier.fillMaxSize(),
                onProgress = viewModel::onProgress,
                onPageFinished = viewModel::onPageFinished,
                onError = viewModel::onError,
                onWebViewReady = viewModel::attachWebView,
                timeoutMillis = 20_000,
            )
        }
    }
}

data class YjsxtLoginUiState(
    val loading: Boolean = true,
    val progress: Int = 0,
    val working: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class YjsxtLoginViewModel @Inject constructor(
    private val coordinator: WebSessionCoordinator,
    // 这里同步的是研究生课表本身，与用户此刻在看哪个层次无关，所以绕开层次分流。
    private val timetable: TimetableRepositoryImpl,
) : ViewModel() {

    private val _state = MutableStateFlow(YjsxtLoginUiState())
    val state: StateFlow<YjsxtLoginUiState> = _state.asStateFlow()

    private var webView: WebView? = null

    /** 一次页面加载只认一遍，重定向会让 finished 连着来好几次。 */
    private var probing = false

    var finished = false
        private set

    fun attachWebView(view: WebView) {
        webView = view
    }

    fun onProgress(value: Int) {
        _state.value = _state.value.copy(progress = value, loading = value < 100)
    }

    fun onError(message: String) {
        _state.value = _state.value.copy(loading = false, error = message)
    }

    /**
     * 页面加载完不一定等于登录成功：登录页本身也会 finish。
     * 这里尝试取一次令牌，取不到就静默等用户继续操作，不打断输入。
     */
    fun onPageFinished(url: String) {
        if (finished) return
        probe(poll = false, onDone = null, announce = false)
    }

    /** 手动收尾：用户点了「完成」。此时多等几轮，给页面 JS 落令牌的时间。 */
    fun finishNow(onDone: () -> Unit) {
        if (finished) {
            onDone()
            return
        }
        _state.value = _state.value.copy(
            working = true,
            message = "正在确认研究生系统会话…",
        )
        probe(poll = true, onDone = onDone, announce = true)
    }

    private fun probe(poll: Boolean, onDone: (() -> Unit)?, announce: Boolean) {
        val view = webView ?: return
        if (probing) return
        probing = true
        viewModelScope.launch {
            var token = runCatching { coordinator.captureYjsxtToken(view) }.getOrNull()
            var attempts = 0
            // 登录页的令牌是页面 JS 写完才有的，刚 finish 时常常还差一步
            while (token.isNullOrBlank() && poll && attempts < 8) {
                delay(1_200)
                attempts += 1
                token = runCatching { coordinator.captureYjsxtToken(view) }.getOrNull()
            }
            probing = false

            if (token.isNullOrBlank()) {
                // 自动探测不吭声：登录页本身也会 finish，一进来就弹「没检测到」只会误导
                if (announce) {
                    _state.value = _state.value.copy(
                        working = false,
                        message = "还没检测到登录成功。在这个页面里完成登录后，再点右上角「完成」。",
                    )
                }
                return@launch
            }

            finished = true
            _state.value = _state.value.copy(
                working = true,
                message = "已登录，正在同步课表…",
            )
            val outcome = runCatching { timetable.refresh() }.getOrNull()
            _state.value = _state.value.copy(
                working = false,
                message = when (outcome) {
                    is RefreshOutcome.Success -> null
                    is RefreshOutcome.Failure -> "已登录，但课表没同步下来：${outcome.message}"
                    else -> "已登录，课表将在下次刷新时更新。"
                },
            )
            onDone?.invoke()
        }
    }

}
