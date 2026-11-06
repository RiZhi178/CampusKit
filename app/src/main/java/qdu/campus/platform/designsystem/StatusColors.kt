package qdu.campus.platform.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * 状态色。不进 ColorScheme，因为 M3 没有这四个角色；
 * 四档固定为绿 / 黄 / 红 / 灰，只随明暗换明度，不随方案换色相——
 * 换方案时「成功」不能由靛蓝变成玫紫，否则同一个绿点在不同方案下含义就变了。
 */
@Immutable
data class StatusColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val blocked: Color,
)

val LocalStatusColors = staticCompositionLocalOf<StatusColors> {
    error("StatusColors not provided")
}

/**
 * 四档取值实测对比度（surface 与 surfaceContainerLow 上取最弱的一档）：
 * 浅色 绿 5.12 / 黄 5.00 / 红 6.24 / 灰 5.52，深色均高于 8:1，四档都过 4.5。
 * 黄只能是偏暗的土黄：更亮的黄在浅底上只够当底色，当文字与细线读不清。
 *
 * 不依赖配色方案，因此单独成一个纯函数，好让单测直接拿常量断言对比度。
 */
internal fun statusColors(isDark: Boolean): StatusColors = StatusColors(
    success = if (isDark) Color(0xFF7ED694L) else Color(0xFF1F7A3DL),
    warning = if (isDark) Color(0xFFF2C94CL) else Color(0xFF946200L),
    danger = if (isDark) Color(0xFFFFB4ABL) else Color(0xFFB3261EL),
    // 「不可用」是中性状态，不占用 error。灰比 outline 深一档：座位卡上短杠是唯一载体，
    // outline 在浅底上只有约 3.6:1，细到看不见。
    blocked = if (isDark) Color(0xFFA9AFB8L) else Color(0xFF5F6672L),
)

@Composable
fun rememberStatusColors(isDark: Boolean): StatusColors = statusColors(isDark)

/**
 * 四种状态各有形状，不只靠颜色区分：圆点=正常、倒三角=需要注意、×=失败、短杠=不可用。
 * 短杠给的是「这条路走不通」而不是「坏了」，所以不占用 error 色。
 * 无数据仍然是什么标记都不画，不另立第五种。
 */
enum class StatusLevel { SUCCESS, WARNING, DANGER, BLOCKED }

/**
 * 圆点 5dp、倒三角 8 × 5dp、× 对角 6dp（两条 1.5dp 线段）、短杠 8 × 1.5dp。
 * 四个尺寸是刻意对齐的，同一行里视觉重量要一致。
 */
@Composable
fun StatusMark(
    level: StatusLevel,
    modifier: Modifier = Modifier,
) {
    val status = LocalStatusColors.current
    val color = when (level) {
        StatusLevel.SUCCESS -> status.success
        StatusLevel.WARNING -> status.warning
        StatusLevel.DANGER -> status.danger
        StatusLevel.BLOCKED -> status.blocked
    }
    val markWidth = when (level) {
        StatusLevel.SUCCESS -> 5.dp
        StatusLevel.WARNING -> 8.dp
        StatusLevel.DANGER -> 6.dp
        StatusLevel.BLOCKED -> 8.dp
    }
    val markHeight = when (level) {
        StatusLevel.WARNING -> 5.dp
        StatusLevel.BLOCKED -> 1.5.dp
        else -> markWidth
    }
    Canvas(modifier = modifier.then(Modifier.size(markWidth, markHeight))) {
        when (level) {
            StatusLevel.SUCCESS -> drawCircle(color)

            StatusLevel.WARNING -> drawPath(
                path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                },
                color = color,
            )

            StatusLevel.BLOCKED -> drawRect(color)

            StatusLevel.DANGER -> {
                val stroke = 1.5.dp.toPx()
                val inset = stroke / 2f
                drawLine(
                    color = color,
                    start = Offset(inset, inset),
                    end = Offset(size.width - inset, size.height - inset),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = color,
                    start = Offset(size.width - inset, inset),
                    end = Offset(inset, size.height - inset),
                    strokeWidth = stroke,
                )
            }
        }
    }
}
