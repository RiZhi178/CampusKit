package qdu.campus.platform.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 加载态。Expressive 的 LoadingIndicator 在 beta 版 API 未稳定，这里统一封装以便替换。 */
@Composable
fun AppLoading(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(28.dp),
        strokeWidth = 3.dp,
    )
}

/**
 * 通知未读标记：只有一个小圆点，已读留空但保留占位，标题左缘才对齐。
 * 圆点不承担全部信息——读屏从语义里仍能听到「未读 / 已读」。
 */
@Composable
fun UnreadDot(read: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(14.dp)
            .padding(4.dp)
            .semantics { contentDescription = if (read) "已读" else "未读" },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (read) Color.Transparent else MaterialTheme.colorScheme.primary),
        )
    }
}

/** 分区容器：标题 + 内容，卡片圆角取 medium。 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            if (title != null || trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    trailing?.invoke()
                }
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

/**
 * 非阻断提示条：会话失效、同步失败等放在页面顶部，不弹对话框。
 * isError 用 errorContainer 区分失败与一般提示，失败不能靠文案前缀表达。
 */
@Composable
fun InlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = onContainer,
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                QduButton(
                    text = actionLabel,
                    onClick = onAction,
                    kind = if (isError) QduButtonKind.Danger else QduButtonKind.Ghost,
                    small = true,
                )
            }
        }
    }
}

/** 空态与错误态共用：一句说明 + 可选动作。 */
@Composable
fun PlaceholderBlock(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actionLabel != null && onAction != null) {
            QduButton(text = actionLabel, onClick = onAction, kind = QduButtonKind.Ghost, small = true)
        }
    }
}
