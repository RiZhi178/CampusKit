package qdu.campus.platform.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 动效只用于四处状态变化：认证成功、认证失败、设备下线完成、通知刷新。
 * 列表滚动与页面切换不加装饰动效。
 */
val StateSpring: AnimationSpec<Float> = spring(dampingRatio = 0.8f, stiffness = 220f)

/** 骨架屏 shimmer 一轮的时长。无限重复，直到内容替换骨架。 */
val ShimmerSpec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
    animation = tween(durationMillis = 1500),
    repeatMode = RepeatMode.Restart,
)
