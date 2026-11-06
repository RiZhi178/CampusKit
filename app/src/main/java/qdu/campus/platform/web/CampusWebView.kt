package qdu.campus.platform.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import qdu.campus.domain.model.WebDomain
import kotlinx.coroutines.delay

/**
 * 内嵌 WebView 的统一容器。
 *
 * 统一开启宽视口与概览缩放，避免 PC 页面在手机上一进去就要双指拖拽；
 * 连续 [timeoutMillis] 没有任何加载进展，或触发 onReceivedError 时，交给调用方显示错误态，禁止白屏。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CampusWebView(
    url: String,
    modifier: Modifier = Modifier,
    userAgent: String? = null,
    cssInjection: String? = null,
    scriptInjection: String? = null,
    timeoutMillis: Long = 8_000,
    /** 隐藏式承载（补会话）要关掉返回键接管，否则它会把调用页的返回吃掉。 */
    handleBack: Boolean = true,
    onProgress: (Int) -> Unit = {},
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String) -> Unit = {},
    onError: (String) -> Unit = {},
    onExternalUrl: (String) -> Boolean = { false },
    onWebViewReady: (WebView) -> Unit = {},
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var finished by remember(url) { mutableStateOf(false) }
    // 只记调用方要求加载的地址。CAS 跳转后 view.url 会变成 ticket 回调，
    // 若拿它和入参比较，进度变化引发的每次重组都会把流程拽回登录页，
    // 一次性 ticket 被反复消费，页面就报「认证失败」。
    var loadedUrl by remember { mutableStateOf<String?>(null) }
    // 超时按「卡住不动」计，不按「整条链总时长」计：进度每前进一格就重新计时。
    // 固定总时长会把还在正常加载的重页面判死——电费落地页（zzmp）首屏要拉 8.5 MB
    // 未压缩脚本（gzip 后约 4.5 MB，且 Cache-Control: no-cache），四跳 CAS 桥再走完，
    // 手机上进度到 100 常已超过 8 秒；一旦报错，调用方会卸载这个 WebView，
    // 在途的请求被 stopLoading 掐掉，重试又从头开始同一条链，于是永远打不开。
    var lastProgress by remember(url) { mutableStateOf(0) }

    // 网页里的 <input type="file"> 需要 WebChromeClient 主动接住，否则点「上传图片」什么都不发生
    // （宿舍报修的手机号与图片都是必填，不接这一层就提交不了）。
    // 回调只能持有一次，选完必须把结果交回去并清空，否则下一次点击会被系统忽略。
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = fileChooserCallback
        fileChooserCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        val uris = if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val clip = data?.clipData
            when {
                clip != null -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
                data?.data != null -> listOf(data.data!!)
                else -> emptyList()
            }
        } else {
            emptyList()
        }
        callback.onReceiveValue(uris.toTypedArray().takeIf { it.isNotEmpty() })
    }

    LaunchedEffect(url, lastProgress) {
        delay(timeoutMillis)
        if (!finished) onError("加载超时")
    }

    if (handleBack) {
        BackHandler(enabled = webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    // 办事大厅等页面在 iframe 里跨子域取统一认证会话，默认策略会拦掉。
                    runCatching { CookieManager.getInstance().setAcceptThirdPartyCookies(this, true) }
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        if (!userAgent.isNullOrBlank()) userAgentString = userAgent
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            view: WebView,
                            filePathCallback: ValueCallback<Array<Uri>>,
                            fileChooserParams: FileChooserParams,
                        ): Boolean {
                            // 上一回调没交结果就再点一次时，先把旧的回 null 掉，避免悬空
                            fileChooserCallback?.onReceiveValue(null)
                            fileChooserCallback = filePathCallback
                            val intent = runCatching { fileChooserParams.createIntent() }
                                .getOrNull()
                                ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = fileChooserParams.acceptTypes.firstOrNull { it.isNotBlank() } ?: "*/*"
                                }
                            val launched = runCatching { fileChooserLauncher.launch(intent) }.isSuccess
                            if (!launched) {
                                fileChooserCallback = null
                                filePathCallback.onReceiveValue(null)
                            }
                            return launched
                        }

                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            onProgress(newProgress)
                            if (newProgress > lastProgress) lastProgress = newProgress
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            finished = false
                            // 新的一跳：进度从 0 起算，超时窗口也随之重开
                            lastProgress = 0
                            onPageStarted(url.orEmpty())
                        }

                        override fun onPageFinished(view: WebView, loadedUrl: String?) {
                            finished = true
                            injectCss(view, cssInjection)
                            scriptInjection?.let { runScript(view, it) }
                            applyInitialScale(view)
                            onPageFinished(loadedUrl.orEmpty())
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val target = request.url
                            // 明文导航只在确认支持 https 的域上升回加密；
                            // csyy / baoxiu 没有 443，强制升级会直接把页面挡在门外
                            if (target.scheme == "http") {
                                val domain = WebDomain.ofHost(target.host)
                                if (domain != null && !domain.clearTextOnly) {
                                    // 回调里同步 loadUrl 会重入导航流程，排到下一帧再加载
                                    val upgraded = target.toString().replaceFirst("http://", "https://")
                                    view.post { view.loadUrl(upgraded) }
                                    return true
                                }
                            }
                            return onExternalUrl(target.toString())
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                onError(error.description?.toString() ?: "页面加载失败")
                            }
                        }
                    }
                    loadUrl(url)
                    loadedUrl = url
                    webViewRef = this
                    onWebViewReady(this)
                }
            },
            update = { view ->
                if (url.isNotBlank() && url != loadedUrl) {
                    loadedUrl = url
                    view.loadUrl(url)
                }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }
}

/** 按页面宽度与屏宽比设置初始缩放，解决固定宽度 PC 页的显示问题。 */
private fun applyInitialScale(view: WebView) {
    view.evaluateJavascript(
        "(function(){var w=document.documentElement.scrollWidth||document.body.scrollWidth;" +
            "var s=window.innerWidth/w;return s.toFixed(2);})()",
    ) { result ->
        val scale = result?.trim()?.removeSurrounding("\"")?.toFloatOrNull()
        if (scale != null && scale > 0f && scale < 1f) {
            view.setInitialScale((scale * 100).toInt())
        }
    }
}

/** CSS 注入失败时静默跳过，不影响页面功能。 */
private fun injectCss(view: WebView, css: String?) {
    if (css.isNullOrBlank()) return
    val escaped = css.replace("\n", " ").replace("'", "\\'")
    view.evaluateJavascript(
        "(function(){try{var s=document.createElement('style');" +
            "s.textContent='$escaped';document.head.appendChild(s);}catch(e){}})()",
        null,
    )
}

/** 页面脚本注入只用于预填表单，失败静默跳过。 */
private fun runScript(view: WebView, script: String) {
    runCatching { view.evaluateJavascript(script, null) }
}
