package qdu.campus.platform.designsystem

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 字阶直接沿用 Material 3 内建 Typography，不另立字号。 */
val QduTypography = Typography()

/** 组件层只用 M3 默认梯度里的 12（medium）、16（large）、28（extraLarge）三档。 */
object UiShapes {
    val Card = RoundedCornerShape(12.dp)
    val Overlay = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(28.dp)
    val Panel = RoundedCornerShape(28.dp)
    val Icon = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(12.dp)
}

/** M3 默认梯度：4 / 8 / 12 / 16 / 28，不覆盖。 */
val QduShapes = Shapes()

/** 版式与边界：左右 16、卡片内 14、分区间距 18 / 9、底部导航 60。 */
object UiSpacing {
    val PageHorizontal = 16.dp
    val CardInner = 14.dp
    val SectionTop = 18.dp
    val SectionBottom = 9.dp

    /** 只有图标的底栏：48 热区 + 上下各 6，比带文字时矮 20。 */
    val TabBarHeight = 60.dp

    /** 悬浮胶囊的留白：上下各 10，左右 24。 */
    val TabBarPaddingVertical = 10.dp
    val TabBarPaddingHorizontal = 24.dp

    /** 胶囊与内容之间的间距，也是死区往胶囊上方多挡的量。 */
    val TabBarContentGap = 16.dp

    /** 胶囊带：自身 60 加上下留白各 10，不含系统导航栏。 */
    val TabBarBand = TabBarHeight + TabBarPaddingVertical * 2

    /** 悬浮底栏不再占布局，列表让出胶囊带再加与内容的间距。 */
    val ListBottomGuard = TabBarBand + TabBarContentGap

    /** 底栏死区与列表避让同高：胶囊带整条，再加胶囊上方露头的 16。 */
    val BottomBarDeadZone = ListBottomGuard

    /** 死区内超过这个位移算拖动，交还给内容层滚动；与 Compose 默认触控容差一致。 */
    val TapDeadZoneSlop = 8.dp

    /** 课表：行高 86，时间列 30，块内文字 11。 */
    val TimetableRowHeight = 86.dp
    val TimetableTimeColumn = 30.dp
    val TimetableHeaderHeight = 30.dp
}

/**
 * 底栏悬浮后不再占布局，列表要自己让出它；再叠系统导航栏，
 * 三键导航（48）下手势条（24）那点余量也不会让最后一行钻到胶囊底下。
 */
@Composable
fun listBottomGuard(): Dp =
    UiSpacing.ListBottomGuard + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
