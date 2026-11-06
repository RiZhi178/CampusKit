package qdu.campus.platform.designsystem

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * 卡片描边色。原型用 outlineVariant 的 60% 透明而不是投影：
 * 深色下投影不可见，浅色下描边比阴影更稳。
 */
@Composable
fun cardBorderColor(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

/** 内容卡：surfaceContainerLow 底 + 描边 + 12 圆角。padded=false 给整组列表用。 */
@Composable
fun QduCard(
    modifier: Modifier = Modifier,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(UiShapes.Card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, cardBorderColor(), UiShapes.Card)
            .padding(if (padded) UiSpacing.CardInner else 0.dp),
        content = content,
    )
}

/** 行底分隔线，最后一行不画。 */
fun Modifier.rowDivider(color: Color): Modifier = drawBehind {
    val thickness = 1.dp.toPx()
    drawRect(
        color = color,
        topLeft = Offset(0f, size.height - thickness),
        size = Size(size.width, thickness),
    )
}

/**
 * 设置行：32 图标底 + 标题 + 副标题 + 尾部。整行可点，热区至少 48。
 * 值直接写在行内，避免「点进去才知道设成什么」。
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMaxLines: Int = 2,
    leading: ImageVector? = null,
    leadingTint: Color? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
    chevron: Boolean = true,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val borderColor = cardBorderColor()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(if (divider) Modifier.rowDivider(borderColor) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = UiSpacing.CardInner, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(UiShapes.Icon)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leading,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = leadingTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
        if (chevron && onClick != null) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 应用栏图标按钮：视觉 40，热区补足 48。 */
@Composable
fun QduIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

@Composable
fun QduBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QduIconButton(
        onClick = onClick,
        icon = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = "返回",
        modifier = modifier,
    )
}

/** 设置分组标题：11 大写字距，上 18 下 7。 */
@Composable
fun GroupHead(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 2.dp, top = UiSpacing.SectionTop, bottom = UiSpacing.SectionBottom),
    )
}

/** 内容区分组标题；[label] 是标题后的附注（如日期），右侧可挂「全部」「离线」之类的跳转。 */
@Composable
fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    more: String? = null,
    onMore: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!label.isNullOrBlank()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (more != null && onMore != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(UiShapes.Icon.topStart))
                    .clickable(role = Role.Button, onClick = onMore)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = more,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 底部导航的一项。[label] 只用于无障碍朗读，不参与布局。 */
data class QduTab(
    val label: String,
    val icon: ImageVector,
)

/**
 * 悬浮胶囊底栏：胶囊四周留白，不贴屏幕边缘。
 * 胶囊本体交给 Backdrop 画液态玻璃——折射、模糊、镜面高光和投影都按形状实时合成，
 * 透出身后的滚动内容，不再垫一层实色底。选中态沿用 Backdrop 官方 LiquidBottomTabs：
 * 一块半透明胶囊在页签下方滑动（浅色叠 10% 黑、深色叠 10% 白），图标本身不再套
 * 40dp 圆形容器，也没有描边。图标语义由 [QduTab.label] 交给无障碍服务，不占一行字高。
 */
@Composable
fun QduBottomBar(
    tabs: List<QduTab>,
    selectedIndex: Int,
    backdrop: Backdrop,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val indicatorColor =
        if (dark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                horizontal = UiSpacing.TabBarPaddingHorizontal,
                vertical = UiSpacing.TabBarPaddingVertical,
            ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(UiSpacing.TabBarHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        vibrancy()
                        blur(10.dp.toPx())
                        lens(18.dp.toPx(), 20.dp.toPx())
                    },
                    highlight = { Highlight.Default.copy(alpha = 0.8f) },
                    shadow = {
                        Shadow(
                            radius = 16.dp,
                            color = Color.Black.copy(alpha = if (dark) 0.4f else 0.18f),
                        )
                    },
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            val cellWidth = (maxWidth - 16.dp) / tabs.size
            val indicatorOffset by animateDpAsState(
                targetValue = cellWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "tabIndicator",
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .offset(x = indicatorOffset)
                    .width(cellWidth)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(indicatorColor),
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavTab(
                        tab = tab,
                        isSelected = index == selectedIndex,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTab(
    tab: QduTab,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            modifier = Modifier.size(20.dp),
            // 面型图标本身就比线型重，选中态改用主色而不是再加深：
            // 两者叠在一起时靠色相区分，不再单靠明度。未选中保持中性灰。
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * 悬浮胶囊是半透明的，身后的列表项滚上来时会从胶囊上下沿露头，用户瞄着底栏按下去
 * 却直接进了那一行。这段区域把轻点吞掉，竖向拖动让给身后的内容，列表仍能从这里起手滚。
 *
 * 必须自己实现 PointerInputModifierNode，而不是用 pointerInput{}：默认的 pointerInput 节点
 * 不与兄弟共享指针，叠在 NavHost 上会把拖动一起吞掉（实测列表在死区内滚不动）。
 * 只有 sharePointerInputWithSiblings 返回 true，事件才会在本节点看过之后再传给下面的内容。
 *
 * 拦轻点要在 Initial 通道：共享指针后，身后的内容在 Main 通道排在本节点之前，
 * 抬手先被 clickable 消费掉，这里再消费已经晚了（实测照样进页面）。
 * Initial 通道本节点在先，此时抬手还没人碰，直接消费；内容的
 * waitForUpOrCancellation 见到已消费就判为取消，点击不再触发。
 *
 * 页签不受影响——底栏叠在本区域之上且不共享指针，命中测试到它就停，这里收不到那一次手势。
 */
fun Modifier.qduTapDeadZone(): Modifier = this then TapDeadZoneElement

private object TapDeadZoneElement : ModifierNodeElement<TapDeadZoneNode>() {
    override fun create(): TapDeadZoneNode = TapDeadZoneNode()

    override fun update(node: TapDeadZoneNode) = Unit

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = javaClass.hashCode()
}

private class TapDeadZoneNode : Modifier.Node(), PointerInputModifierNode {
    private var trackedPointer: PointerId? = null
    private var dragging = false

    override fun sharePointerInputWithSiblings(): Boolean = true

    override fun onCancelPointerInput() {
        trackedPointer = null
        dragging = false
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (pass != PointerEventPass.Initial) return
        val tracked = trackedPointer
        if (tracked == null) {
            val down = pointerEvent.changes.firstOrNull { it.pressed && !it.previousPressed }
            if (down != null) {
                trackedPointer = down.id
                dragging = false
            }
            return
        }
        val change = pointerEvent.changes.firstOrNull { it.id == tracked } ?: return
        val slopPx = with(requireDensity()) { UiSpacing.TapDeadZoneSlop.toPx() }
        if (change.positionChange().getDistance() > slopPx) {
            dragging = true
        }
        if (change.previousPressed && !change.pressed) {
            if (!dragging) change.consume()
            trackedPointer = null
        }
    }
}
