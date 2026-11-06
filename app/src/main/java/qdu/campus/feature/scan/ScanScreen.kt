package qdu.campus.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import qdu.campus.platform.common.CrashLogger
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.UiSpacing
import qdu.campus.platform.system.ExternalLauncher
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.Executors

/**
 * 扫码页。全屏相机取景，识别出码后停止、在底部面板里给出归属判断与转交动作；
 * 面板里的「继续扫码」清掉结果重新取景。分类与转交的口径见 docs/scan-slice.md。
 * 设备码一律转交外部程序，本页不提供内嵌 WebView：明文 http 被网络安规拦、
 * 厂商短链被 UA 门禁拦，内嵌打开没有一条走得通（2026-11-06 真机实证）。
 */
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher = remember { ExternalLauncher(context) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 相机画面录进 backdrop，顶栏两颗按钮才能做液态玻璃（折射的是实时取景）。
    val backdrop = rememberLayerBackdrop()
    var cameraBindFailed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (hasPermission) {
            CameraPreview(
                torchOn = state.torchOn,
                zoomActive = state.result == null,
                analyzer = remember { { raw -> viewModel.onScanResult(raw) } },
                onBindFailed = { cameraBindFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            )
        }

        // 相机绑定失败时不再只剩一个准星框挂黑屏：给一句明确的提示。
        // 之前 catch 把异常整个吞掉，用户只看到黑屏，报障时也无从排查。
        if (hasPermission && cameraBindFailed && state.result == null) {
            Text(
                text = "相机启动失败。请确认没有其他应用占用相机，重启应用再试；若仍失败，用「更多 · 我要反馈」报障。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(16.dp),
            )
        }

        // 顶栏：返回 + 手电筒。液态玻璃圆钮，直接折在取景画面上。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = UiSpacing.PageHorizontal, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassIconButton(
                onClick = onBack,
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "返回",
                backdrop = backdrop,
            )
            if (hasPermission) {
                GlassIconButton(
                    onClick = viewModel::toggleTorch,
                    icon = if (state.torchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                    contentDescription = if (state.torchOn) "关闭手电筒" else "打开手电筒",
                    backdrop = backdrop,
                )
            }
        }

        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = UiSpacing.PageHorizontal),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "需要相机权限才能扫码",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "相机只用于识别二维码，不会拍摄或存储照片。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(text = "授权相机")
                }
            }
        } else if (state.result == null) {
            // 取景态：中央准星框 + 框下的一句提示。提示垫黑色半透明底，亮场景也读得清。
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Reticle()
                Text(
                    text = "对准设备上的二维码",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        } else {
            ResultPanel(
                content = state.result!!,
                onCopy = { text, label -> launcher.copyToClipboard(text, label) },
                onOpenUriWithApp = { uri, pkg -> launcher.openUriWithApp(uri, pkg) },
                onOpenChooser = { uri -> launcher.openChooser(uri, "用以下应用打开") },
                onOpenWeChat = launcher::openWeChat,
                onOpenAlipay = launcher::openAlipay,
                onOpenLearningApp = { url ->
                    val pkg = learningAppPackage(url)
                    pkg != null && launcher.openApp(pkg)
                },
                onResume = viewModel::resumeScan,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = UiSpacing.PageHorizontal, vertical = 12.dp),
            )
        }
    }
}

/** 相机预览与识别。绑定与解绑都在这里做，离开页面即停止取流。 */
@Composable
private fun CameraPreview(
    torchOn: Boolean,
    zoomActive: Boolean,
    analyzer: (String) -> Unit,
    onBindFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
            // COMPATIBLE 用 TextureView 渲染：SurfaceView 画在独立图层上，
            // layerBackdrop 录不到，液态玻璃按钮会折不出画面。
            previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener(
                {
                    try {
                        val cameraProvider = future.get()
                        val preview = androidx.camera.core.Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        val executor = Executors.newSingleThreadExecutor()
                        analysis.setAnalyzer(executor, BarcodeAnalyzer(analyzer))
                        cameraProvider.unbindAll()
                        val bound = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                        camera = bound
                        provider = cameraProvider
                    } catch (e: Exception) {
                        // 绑定失败（如相机被占用）：不崩溃，留深色底与提示，并记进日志供报障导出
                        CrashLogger.log(ctx, "相机绑定失败", e)
                        onBindFailed()
                    }
                },
                ContextCompat.getMainExecutor(ctx),
            )
            previewView
        },
        modifier = modifier,
    )

    LaunchedEffect(torchOn, camera) {
        camera?.cameraControl?.enableTorch(torchOn)
    }

    // 自动变焦：远处的码在 1x 下往往小到识别不动，取景阶段按 1x、2x、3x、5x 阶梯推进，
    // 每档平滑推近后停留约 1.3 秒，让识别引擎在当前焦距上跑几帧；到顶后回到 1x 重新扫一轮。
    // 出结果（zoomActive 变 false）或离开页面时取消，并平滑退回 1x，不跳变。
    LaunchedEffect(zoomActive, camera) {
        val cam = camera ?: return@LaunchedEffect
        val control = cam.cameraControl
        if (!zoomActive) {
            val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
            if (current != 1f) {
                animate(current, 1f, animationSpec = tween(250)) { value, _ ->
                    control.setZoomRatio(value)
                }
            }
            return@LaunchedEffect
        }
        val maxRatio = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
        // 镜头到不了 5x 的机型只取够得着的档位；只有 1x 的（理论上不存在）就不扫
        val steps = listOf(1f, 2f, 3f, 5f).filter { it <= maxRatio }.ifEmpty { listOf(1f) }
        while (isActive) {
            for (target in steps) {
                if (!isActive) return@LaunchedEffect
                val current = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                if (current != target) {
                    animate(
                        initialValue = current,
                        targetValue = target,
                        animationSpec = tween(600, easing = FastOutSlowInEasing),
                    ) { value, _ ->
                        control.setZoomRatio(value)
                    }
                }
                delay(1300)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { provider?.unbindAll() }
        }
    }
}

/**
 * 液态玻璃圆钮。与底栏同一套 Backdrop 参数（vibrancy + blur + lens + 高光 + 投影），
 * 折的是身后实时取景，不是一块死色。图标用白字：相机场景明暗不定，
 * 白色符号叠玻璃高光在明暗两面都立得住，语义由 contentDescription 交给读屏。
 */
@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .size(44.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(12.dp.toPx())
                    lens(14.dp.toPx(), 16.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = 0.8f) },
                shadow = {
                    Shadow(
                        radius = 12.dp,
                        color = Color.Black.copy(alpha = if (dark) 0.4f else 0.2f),
                    )
                },
            )
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = Color.White,
        )
    }
}

/**
 * 取景准星：四角圆角括号 + 中心十字。纯画布绘制，白色描边外再垫一层暗色同形描边，
 * 亮背景上依然可辨。框不遮挡取景中央，识别按整帧跑，框只负责告诉用户往哪对。
 */
@Composable
private fun Reticle(modifier: Modifier = Modifier) {
    Canvas(modifier.size(240.dp)) {
        val stroke = 3.dp.toPx()
        val corner = 30.dp.toPx()
        val radius = 12.dp.toPx()
        fun cornerPath(start: Offset, via: Offset, end: Offset): Path = Path().apply {
            moveTo(start.x, start.y)
            quadraticTo(via.x, via.y, end.x, end.y)
        }
        val w = size.width
        val h = size.height
        val brackets = listOf(
            // 左上：从左边向下一段，圆角折到上边向右一段
            cornerPath(Offset(0f, corner), Offset(0f, 0f), Offset(corner, 0f)),
            // 右上
            cornerPath(Offset(w - corner, 0f), Offset(w, 0f), Offset(w, corner)),
            // 右下
            cornerPath(Offset(w, h - corner), Offset(w, h), Offset(w - corner, h)),
            // 左下
            cornerPath(Offset(corner, h), Offset(0f, h), Offset(0f, h - corner)),
        )
        val cap = Stroke(width = stroke, cap = StrokeCap.Round)
        // 暗色垫底描边：同样路径略粗，先画，亮背景上勾出轮廓
        brackets.forEach { path ->
            drawPath(
                path,
                Color.Black.copy(alpha = 0.35f),
                style = Stroke(width = stroke * 2f, cap = StrokeCap.Round),
            )
        }
        brackets.forEach { path ->
            drawPath(path, Color.White, style = cap)
        }
        // 中心十字：白色短线，先垫暗色
        val arm = 9.dp.toPx()
        val cx = w / 2f
        val cy = h / 2f
        val cross = listOf(
            Offset(cx - arm, cy) to Offset(cx + arm, cy),
            Offset(cx, cy - arm) to Offset(cx, cy + arm),
        )
        cross.forEach { (a, b) ->
            drawLine(Color.Black.copy(alpha = 0.35f), a, b, strokeWidth = stroke * 2f, cap = StrokeCap.Round)
        }
        cross.forEach { (a, b) ->
            drawLine(Color.White, a, b, strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/** ML Kit 逐帧识别。只取第一个可用结果，识别完成后帧照常关闭。 */
// CameraX 1.5 起 ImageProxy 只剩 getImage()（旧的 getMediaImage 已删），
// 该方法带 @ExperimentalGetImage 标注，用 androidx 的 OptIn 放行。
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private class BarcodeAnalyzer(
    private val onResult: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()

    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image
        if (image == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val value = codes.firstOrNull()?.rawValue
                if (!value.isNullOrBlank()) onResult(value)
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}

/** 结果面板：类型、原文、一个「去外部应用」主按钮（按类型自动路由）与「继续扫码」。 */
@Composable
private fun ResultPanel(
    content: QrContent,
    onCopy: (text: String, label: String) -> Unit,
    onOpenUriWithApp: (uri: String, packageName: String) -> Boolean,
    onOpenChooser: (uri: String) -> Boolean,
    onOpenWeChat: () -> Boolean,
    onOpenAlipay: () -> Boolean,
    onOpenLearningApp: (url: String) -> Boolean,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    QduCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = typeLabel(content),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "已识别",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Text(
                text = content.rawText(),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        handlePrimary(
                            content, onCopy, onOpenUriWithApp,
                            onOpenChooser, onOpenWeChat, onOpenAlipay, onOpenLearningApp,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = primaryLabel(content), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "继续扫码", maxLines = 1)
                }
            }
            if (content is QrContent.Web && learningAppPackage(content.url) != null) {
                // 学习类码（如超星）：签到必须发生在用户登录的学习通会话里，enc 是动态的，
                // 外部打开页面完成不了。转交 = 拉起学习通，由用户用它自己的扫一扫重扫。
                Text(
                    text = "这是学习平台码：将打开学习通，用它的扫一扫重扫屏幕上的码完成签到" +
                        "（二维码会周期刷新，动作要快）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (content is QrContent.Web && learningAppPackage(content.url) == null) {
                // 普通网页走系统选择器由浏览器接手；设备码（饮水机/洗衣机）也走这里，
                // 但厂商页面多带会话门禁，浏览器大概率打不开——真机实证见 docs/scan-slice.md。
                Text(
                    text = "设备厂商的码通常要求在微信/支付宝内打开，浏览器可能被拦；" +
                        "届时可用微信或支付宝的扫一扫重扫机身上的码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun QrContent.rawText(): String = when (this) {
    is QrContent.WeChatScheme -> raw
    is QrContent.WeChatMiniProgram -> raw
    is QrContent.AlipayScheme -> raw
    is QrContent.Web -> url
    is QrContent.PlainText -> raw
}

private fun typeLabel(content: QrContent): String = when (content) {
    is QrContent.WeChatScheme -> "微信码"
    is QrContent.WeChatMiniProgram -> "微信小程序码"
    is QrContent.AlipayScheme -> "支付宝码"
    is QrContent.Web -> "网页链接"
    is QrContent.PlainText -> "文本内容"
}

private fun primaryLabel(content: QrContent): String = when (content) {
    is QrContent.PlainText -> "复制内容"
    else -> "去外部应用"
}

/**
 * 学习平台域名 → 应用包名。扫到的 URL 命中就定向转交（拉起官方 App，签到在其会话内完成）。
 * 按真实需要扩充：青大目前主要用超星（学习通），后续接别的平台先在这里登记、再进真机验证。
 */
private fun learningAppPackage(url: String): String? {
    val host = runCatching { android.net.Uri.parse(url).host?.lowercase() }.getOrNull()
        ?: return null
    return when {
        host == "chaoxing.com" || host.endsWith(".chaoxing.com") -> "com.chaoxing.mobile"
        else -> null
    }
}

private fun handlePrimary(
    content: QrContent,
    onCopy: (String, String) -> Unit,
    onOpenUriWithApp: (String, String) -> Boolean,
    onOpenChooser: (String) -> Boolean,
    onOpenWeChat: () -> Boolean,
    onOpenAlipay: () -> Boolean,
    onOpenLearningApp: (url: String) -> Boolean,
) {
    when (content) {
        is QrContent.WeChatScheme -> {
            if (!onOpenUriWithApp(content.raw, "com.tencent.mm")) {
                onCopy(content.raw, "二维码内容")
                onOpenWeChat()
            }
        }
        is QrContent.WeChatMiniProgram -> {
            onCopy(content.raw, "小程序码内容")
            onOpenWeChat()
        }
        is QrContent.AlipayScheme -> {
            if (!onOpenUriWithApp(content.raw, "com.eg.android.AlipayGphone")) {
                onCopy(content.raw, "二维码内容")
                onOpenAlipay()
            }
        }
        is QrContent.Web -> {
            // 路由口径（2026-11-06 定）：学习平台码 → 拉起对应官方 App（签到在其会话内完成，
            // 见 docs/scan-slice.md）；其余网页 → 系统应用选择器交给浏览器。
            // 微信/支付宝不接 http 链接、设备码页面带会话门禁，这两条是 2026-11-06 真机实证，
            // 所以网页类不再尝试定向转交微信/支付宝，也不再进内嵌 WebView。
            val learning = learningAppPackage(content.url)
            when {
                learning != null -> {
                    if (!onOpenLearningApp(content.url)) {
                        onOpenChooser(content.url)
                    }
                }
                else -> {
                    if (!onOpenChooser(content.url)) {
                        onCopy(content.url, "链接")
                    }
                }
            }
        }
        is QrContent.PlainText -> onCopy(content.raw, "二维码内容")
    }
}
