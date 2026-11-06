package qdu.campus.platform.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * WebView 上方的域名条。显示真实地址是为了让用户能核对是不是校方页面，
 * 地址本身取自 WebView 回调，不是应用写死的文案。
 */
@Composable
fun UrlBar(
    displayUrl: String,
    modifier: Modifier = Modifier,
    /** 传 null 表示加载结束，只留一条分隔线；非空时按百分比画进度。 */
    loadProgress: Float? = null,
    onRefresh: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(scheme.surfaceContainer)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surface)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = scheme.onSurfaceVariant,
                )
                Text(
                    text = displayUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRefresh != null) {
                QduIconButton(
                    onClick = onRefresh,
                    icon = Icons.Outlined.Refresh,
                    contentDescription = "重新加载页面",
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
        if (loadProgress != null) {
            LinearProgressIndicator(
                progress = { loadProgress.coerceIn(0.08f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = scheme.primary,
                trackColor = scheme.surfaceContainerHighest,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(scheme.outlineVariant),
            )
        }
    }
}
