package qdu.campus.platform.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 账号卡（校园卡形态）。设计稿见 `reference/AccountCard.kt`，这里是按本工程的
 * 主题与数据现状做的转写。
 *
 * 硬约束（改之前先读）：
 * 1. 高度固定 188.dp / 124.dp，不用 aspectRatio。宽度随屏幕在 320–430 之间浮动，
 *    固定高度才能保证头像与文字不被挤压。
 * 2. 除水印外不用 offset 定位内容；徽章靠 Row 的 Alignment.Top 贴顶。
 * 3. 卡面颜色不跟随深浅主题：实体卡的颜色是印上去的，跟着系统变色会削弱「这是一张卡」的知觉。
 * 4. 不放校徽、校名与英文名；水印只是抽象装饰，不得换成真实校徽。
 * 5. 未登录文案固定：「尚未登录」/「登录后可查看课表、电费等」/「立即登录」，
 *    徽章「未登录」/「已登录」，前置状态圆点。
 * 6. 卡面只放姓名与脱敏学号；学位、学院、有效期属于账号二级页。
 * 7. 不用姓名首字做头像——首字随账号变化，人形图标才稳定。
 */
sealed interface AccountCardState {

    /** 未登录。卡面只剩引导，不显示任何身份字段。 */
    data object SignedOut : AccountCardState

    /** 已登录。学号必须是脱敏后的字符串，脱敏在 UI 层之前完成。 */
    data class SignedIn(
        val name: String? = null,
        val studentId: String? = null,
    ) : AccountCardState
}

/**
 * @param compact 首页用 true（124.dp），更多页与账号页用 false（188.dp）。
 * @param onClick 已登录时整卡可点；未登录请传 null，避免误触到空页。
 * @param onSignIn 未登录按钮回调。
 */
@Composable
fun AccountCard(
    state: AccountCardState,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    onSignIn: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val m = if (compact) CardMetrics.Compact else CardMetrics.Full
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(m.height.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(CardTokens.SurfaceTop, CardTokens.SurfaceBottom)),
            ),
    ) {
        // 全文件唯一用 offset 的地方：它是装饰层，不参与内容布局。
        CardWatermark(
            size = m.watermark.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-46).dp),
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = m.padH.dp, vertical = m.padV.dp),
        ) {
            when (state) {
                is AccountCardState.SignedIn -> SignedInBody(state, m, onClick)
                AccountCardState.SignedOut -> SignedOutBody(m, onSignIn)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 尺寸令牌。数值来自设计稿的规格表，改这里等于改设计。
// ---------------------------------------------------------------------------

private object CardTokens {

    /** 卡面渐变（152°）。取自品牌种子 #5C569C，与 App 图标底色 #362F74 同源。 */
    val SurfaceTop = Color(0xFF413A85)
    val SurfaceBottom = Color(0xFF241E4E)

    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceMuted = Color(0xB3FFFFFF)
    val OnSurfaceFaint = Color(0x99FFFFFF)

    /** 未登录态的虚线外框与徽章描边。 */
    val OutlineFaint = Color(0x52FFFFFF)
    val OutlineStrong = Color(0x80FFFFFF)

    val AvatarTop = Color(0xFF6C64AE)
    val AvatarBottom = Color(0xFF4B4489)

    val StatusOnline = Color(0xFF8BD8B0)

    val ButtonContainer = Color(0xF2FFFFFF)
    val ButtonContent = Color(0xFF2B2560)

    /** 虚线徽章的底色，用来遮住穿过的水印线条。 */
    val BadgeScrim = Color(0xBF3A3376)

    val WatermarkStroke = Color(0x25FFFFFF)

    val ChipStroke = Color(0x8CE8D6A0)
    val ChipFill = Color(0x29E8D6A0)
}

private data class CardMetrics(
    val isCompact: Boolean,
    val height: Int,
    val padH: Int,
    val padV: Int,
    val avatar: Int,
    val avatarEmpty: Int,
    val avatarIcon: Int,
    val nameSize: Int,
    val idSize: Int,
    val gapAvatar: Int,
    val nameIdGap: Int,
    val buttonHeight: Int,
    val buttonWidth: Int?,
    val buttonTextSize: Int,
    val watermark: Int,
) {
    companion object {
        /** 账号页。361 × 188。 */
        val Full = CardMetrics(
            isCompact = false, height = 188, padH = 16, padV = 16,
            avatar = 80, avatarEmpty = 72, avatarIcon = 36,
            nameSize = 24, idSize = 11, gapAvatar = 16, nameIdGap = 8,
            buttonHeight = 44, buttonWidth = null, buttonTextSize = 14,
            watermark = 158,
        )

        /** 首页。361 × 124。 */
        val Compact = CardMetrics(
            isCompact = true, height = 124, padH = 16, padV = 14,
            avatar = 60, avatarEmpty = 48, avatarIcon = 26,
            nameSize = 19, idSize = 10, gapAvatar = 12, nameIdGap = 3,
            buttonHeight = 44, buttonWidth = 104, buttonTextSize = 13,
            watermark = 118,
        )
    }
}

// ---------------------------------------------------------------------------
// 两种状态
// ---------------------------------------------------------------------------

@Composable
private fun SignedInBody(
    state: AccountCardState.SignedIn,
    m: CardMetrics,
    onClick: (() -> Unit)?,
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = MutableInteractionSource(),
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    Column(Modifier.fillMaxSize().then(clickable)) {
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(filled = true, size = m.avatar, iconSize = m.avatarIcon)
            Spacer(Modifier.width(m.gapAvatar.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.name?.takeIf { it.isNotBlank() } ?: "已登录",
                    color = CardTokens.OnSurface,
                    fontSize = m.nameSize.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (m.nameSize * 0.06f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(m.nameIdGap.dp))
                Text(
                    text = state.studentId?.takeIf { it.isNotBlank() }
                        ?.let { "学号 $it" }
                        ?: "统一认证会话有效",
                    color = CardTokens.OnSurfaceMuted,
                    fontSize = m.idSize.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = (m.idSize * 0.08f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            AccountBadge(signedIn = true, modifier = Modifier.align(Alignment.Top))
        }

        if (!m.isCompact) {
            CardFooter()
        }
    }
}

@Composable
private fun SignedOutBody(
    m: CardMetrics,
    onSignIn: () -> Unit,
) {
    if (m.isCompact) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AccountAvatar(
                filled = false,
                size = m.avatarEmpty,
                iconSize = (m.avatarIcon * 0.86f).toInt(),
            )
            Spacer(Modifier.width(m.gapAvatar.dp))
            Column(Modifier.weight(1f)) {
                SignedOutText(m)
            }
            Spacer(Modifier.width(12.dp))
            SignInButton(
                width = m.buttonWidth,
                height = m.buttonHeight,
                textSize = m.buttonTextSize,
                onClick = onSignIn,
            )
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AccountAvatar(
                    filled = false,
                    size = m.avatarEmpty,
                    iconSize = (m.avatarIcon * 0.86f).toInt(),
                )
                Spacer(Modifier.width(m.gapAvatar.dp))
                Column(Modifier.weight(1f)) {
                    SignedOutText(m)
                }
                Spacer(Modifier.width(12.dp))
                AccountBadge(signedIn = false, modifier = Modifier.align(Alignment.Top))
            }
            SignInButton(
                width = m.buttonWidth,
                height = m.buttonHeight,
                textSize = m.buttonTextSize,
                onClick = onSignIn,
            )
        }
    }
}

@Composable
private fun SignedOutText(m: CardMetrics) {
    Text(
        text = "尚未登录",
        color = CardTokens.OnSurface,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = "登录后可查看课表、电费等",
        color = CardTokens.OnSurfaceFaint,
        fontSize = if (m.isCompact) 10.sp else 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---------------------------------------------------------------------------
// 零件
// ---------------------------------------------------------------------------

/**
 * 圆形头像位。`filled = false` 时不填底色、外框改虚线、图标降到 40% 透明度：
 * 形态与已登录一致，只差「填没填内容」。虚线不能用 Modifier.border，
 * 得在 Canvas 里用 dashPathEffect 画圆。
 */
@Composable
private fun AccountAvatar(filled: Boolean, size: Int, iconSize: Int) {
    Box(
        modifier = Modifier.size(size.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radiusPx = this.size.minDimension / 2f
            if (filled) {
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(CardTokens.AvatarTop, CardTokens.AvatarBottom),
                        start = Offset.Zero,
                        end = Offset(this.size.width, this.size.height),
                    ),
                    radius = radiusPx,
                )
                drawCircle(
                    color = Color(0x38FFFFFF),
                    radius = radiusPx - 0.5.dp.toPx(),
                    style = Stroke(1.dp.toPx()),
                )
            } else {
                drawCircle(
                    color = CardTokens.OutlineFaint,
                    radius = radiusPx - 0.5.dp.toPx(),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(3.dp.toPx(), 3.dp.toPx()),
                        ),
                    ),
                )
            }
        }

        val stroke = if (filled) CardTokens.OnSurface else Color(0x66FFFFFF)
        Canvas(Modifier.size(iconSize.dp)) {
            val s = this.size.minDimension / 24f
            val w = Stroke(1.5f * s)
            drawCircle(
                color = stroke,
                radius = 3.7f * s,
                center = Offset(12f * s, 8.6f * s),
                style = w,
            )
            val shoulder = Path().apply {
                moveTo(4.7f * s, 19.8f * s)
                cubicTo(5.8f * s, 16.3f * s, 8.5f * s, 14.5f * s, 12f * s, 14.5f * s)
                cubicTo(15.5f * s, 14.5f * s, 18.2f * s, 16.3f * s, 19.3f * s, 19.8f * s)
            }
            drawPath(shoulder, color = stroke, style = w)
        }
    }
}

/**
 * 状态徽章：文案固定「未登录」/「已登录」，前置 5.dp 圆点。
 * 虚线底是为了遮住穿过徽章的水印线条。
 */
@Composable
private fun AccountBadge(signedIn: Boolean, modifier: Modifier = Modifier) {
    val pill = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .clip(pill)
            .background(if (signedIn) Color(0x1FFFFFFF) else CardTokens.BadgeScrim)
            .border(
                width = 1.dp,
                color = if (signedIn) Color(0x47FFFFFF) else CardTokens.OutlineStrong,
                shape = pill,
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (signedIn) CardTokens.StatusOnline else Color.Transparent)
                .then(
                    if (signedIn) Modifier
                    else Modifier.border(1.dp, Color(0x8CFFFFFF), CircleShape),
                ),
        )
        Text(
            text = if (signedIn) "已登录" else "未登录",
            color = if (signedIn) Color(0xEBFFFFFF) else Color(0xD1FFFFFF),
            fontSize = 9.sp,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
    }
}

/** 卡底：芯片 + 一卡通字样。纯装饰，整体删掉不影响信息完整性。 */
@Composable
private fun CardFooter() {
    Column {
        HorizontalDivider(thickness = 1.dp, color = Color(0x21FFFFFF))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(width = 22.dp, height = 16.dp)) {
                val cr = CornerRadius(3.dp.toPx())
                drawRoundRect(color = CardTokens.ChipFill, cornerRadius = cr)
                drawRoundRect(
                    color = CardTokens.ChipStroke,
                    cornerRadius = cr,
                    style = Stroke(1.dp.toPx()),
                )
                drawLine(
                    color = CardTokens.ChipStroke,
                    start = Offset(size.width / 2, 0f),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = CardTokens.ChipStroke,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "校园一卡通",
                color = Color(0x80FFFFFF),
                fontSize = 9.sp,
                letterSpacing = 1.1.sp,
            )
        }
    }
}

@Composable
private fun SignInButton(
    width: Int?,
    height: Int,
    textSize: Int,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .then(if (width == null) Modifier.fillMaxWidth() else Modifier.width(width.dp))
            .height(height.dp),
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CardTokens.ButtonContainer,
            contentColor = CardTokens.ButtonContent,
        ),
        elevation = null,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = "立即登录",
            fontSize = textSize.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (textSize * 0.05f).sp,
        )
    }
}

// ---------------------------------------------------------------------------
// 底纹
// ---------------------------------------------------------------------------

/**
 * 抽象徽章线稿：双环 + 拱门（三根立柱）+ 两道水波 + 顶部小圆。
 * 借院校徽章的语汇，但构图不与任何真实校徽对应。
 * 坐标系按 200 × 200 设计，描边宽度随尺寸等比缩放。
 */
@Composable
private fun CardWatermark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 200f
        scale(s, s, pivot = Offset.Zero) {
            val color = CardTokens.WatermarkStroke

            drawCircle(color, radius = 86f, center = Offset(100f, 100f), style = Stroke(1.5f))
            drawCircle(color, radius = 76f, center = Offset(100f, 100f), style = Stroke(0.6f))

            val arch = Path().apply {
                moveTo(66f, 128f)
                lineTo(66f, 92f)
                arcTo(
                    rect = Rect(66f, 58f, 134f, 126f),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                lineTo(134f, 128f)
            }
            drawPath(arch, color = color, style = Stroke(1.4f))

            listOf(84f to 94f, 100f to 90f, 116f to 94f).forEach { (x, y) ->
                drawLine(color, Offset(x, 128f), Offset(x, y), strokeWidth = 0.8f)
            }

            drawLine(color, Offset(58f, 128f), Offset(142f, 128f), strokeWidth = 1.2f)

            listOf(142f, 154f).forEach { y ->
                val wave = Path().apply {
                    moveTo(52f, y)
                    cubicTo(62.7f, y - 6f, 73.3f, y - 6f, 84f, y)
                    cubicTo(94.7f, y + 6f, 105.3f, y + 6f, 116f, y)
                    cubicTo(126.7f, y - 6f, 137.3f, y - 6f, 148f, y)
                }
                drawPath(wave, color = color, style = Stroke(1.1f))
            }

            drawCircle(color, radius = 4f, center = Offset(100f, 52f), style = Stroke(1.2f))
        }
    }
}
