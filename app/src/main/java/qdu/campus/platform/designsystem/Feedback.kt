package qdu.campus.platform.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.delay

/** 横幅底色。成功和失败是同一条通道的两种性质，不是两种组件。 */
enum class BannerKind { Failure, Success }

/**
 * 结果提示条：下划线动作 + 按性质换底色，失败 errorContainer、成功 primaryContainer。
 * 会话失效、同步失败走这里，下单与取消的回执也走这里，不弹阻断式对话框。
 */
@Composable
fun Banner(
    text: String,
    modifier: Modifier = Modifier,
    kind: BannerKind = BannerKind.Failure,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container =
        if (kind == BannerKind.Success) scheme.primaryContainer else scheme.errorContainer
    val onContainer =
        if (kind == BannerKind.Success) scheme.onPrimaryContainer else scheme.onErrorContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(UiShapes.Card)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = if (kind == BannerKind.Success) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.Warning
            },
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .align(Alignment.CenterVertically),
            tint = onContainer,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = onContainer,
            )
            if (actionLabel != null && onAction != null) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textDecoration = TextDecoration.Underline,
                    ),
                    color = onContainer,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * 轻提醒：「已清除」「预约成功」这类一闪而过的回执。
 *
 * 底部居中的一颗药丸，出现 [delayMillis] 后自动调 [onDismiss]，不给任何可点动作——
 * 需要用户处理的失败提示走 [InlineNotice] 或 [Banner]，别用这里。
 * 容器本身不拦截触摸，盖住的内容照常可点。
 */
@Composable
fun TransientToast(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Long = 1_500,
) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(delayMillis)
        onDismiss()
    }
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(scheme.inverseSurface.copy(alpha = 0.92f))
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/** 底部常驻说明条：判定口径、注意事项这类「用完就该看见」的话。 */
@Composable
fun HintBar(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Info,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainer)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(0.dp))
            .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(14.dp)
                .align(Alignment.Top),
            tint = scheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 骨架块。刷新时用骨架而不是转圈：行高与真实内容一致，数据到位后不跳版。
 * alpha 传 0.3f 表示「这一格本来就没课」，与有课的骨架区分开。
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    alpha: Float = 1f,
) {
    Box(modifier = modifier.skeletonSweep(alpha).clip(shape))
}

@Composable
private fun Modifier.skeletonSweep(alpha: Float): Modifier {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val peak = MaterialTheme.colorScheme.surfaceContainerHighest
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = ShimmerSpec,
        label = "sweep",
    )
    return drawBehind {
        drawRect(base, alpha = alpha)
        val band = size.width * 0.7f
        val center = (-0.35f + 1.7f * progress) * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, peak, Color.Transparent),
                start = Offset(center - band, 0f),
                end = Offset(center + band, 0f),
            ),
            alpha = alpha,
        )
    }
}

/** 断网与加载失败的整屏版式：图标 + 标题 + 原因 + 竖排动作。 */
@Composable
fun ErrWrap(
    title: String,
    reason: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Warning,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = scheme.onSurfaceVariant,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = actions,
        )
    }
}

/** 会话门禁整屏：Lock 图标 + 标题 + 说明（bodyMedium）+ 主按钮 + 可选重试。 */
@Composable
fun SessionGate(
    title: String,
    reason: String,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    loginLabel: String = "去登录",
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "重试",
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = scheme.onSurfaceVariant,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            QduButton(
                text = loginLabel,
                onClick = onLogin,
                modifier = Modifier.padding(top = 18.dp),
            )
            if (onRetry != null) {
                QduButton(
                    text = retryLabel,
                    onClick = onRetry,
                    kind = QduButtonKind.Tonal,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
        }
    }
}
