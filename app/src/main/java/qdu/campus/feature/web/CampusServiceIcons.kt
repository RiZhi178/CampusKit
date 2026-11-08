package qdu.campus.feature.web

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EventSeat
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 入口图标。原型按行为归类：用水类走水滴、电力类走闪电、
 * 办事类走建筑；跳转去别的 App 的不在图标上做特殊标记，靠副标题说去向。
 * 打水/洗衣机/空调三卡已删（由首页通栏扫码卡取代），对应图标一并移除。
 */
val CampusService.icon: ImageVector
    get() = when (this) {
        CampusService.BATH -> Icons.Outlined.WaterDrop
        CampusService.CHECK_IN -> Icons.Outlined.Check
        CampusService.ELECTRICITY -> Icons.Outlined.Bolt
        CampusService.EHALL -> Icons.Outlined.AccountBalance
        CampusService.XG -> Icons.Outlined.School
        CampusService.XG_AWARD -> Icons.Outlined.WorkspacePremium
        CampusService.XG_LEAVE -> Icons.Outlined.CalendarMonth
        CampusService.LIBRARY_SEAT -> Icons.Outlined.EventSeat
        CampusService.REPAIR -> Icons.Outlined.Build
        CampusService.LIBRARY -> Icons.Outlined.LocalLibrary
        CampusService.PAYMENT -> Icons.Outlined.Payments
    }

/**
 * 入口卡图标底色。原型让相邻卡片轮换三组容器色，避免一整屏同色；
 * 第七项又回到 primary，与第一项呼应。
 */
enum class EntryIconTint { Primary, Tertiary, Secondary }

val CampusService.iconTint: EntryIconTint
    get() = when (this) {
        CampusService.CHECK_IN -> EntryIconTint.Tertiary
        CampusService.ELECTRICITY -> EntryIconTint.Secondary
        else -> EntryIconTint.Primary
    }
