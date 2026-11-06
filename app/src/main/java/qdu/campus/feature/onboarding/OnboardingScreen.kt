package qdu.campus.feature.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import qdu.campus.R
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind

/**
 * 引导页（原型屏 01）。只在首次启动出现一次，三句话交代凭据怎么处理，
 * 不做权限申请、不插教程，也不收集任何输入：登录凭据全在校方页面里完成。
 */
@Composable
fun OnboardingScreen(
    onStartLogin: () -> Unit,
    onSkip: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 26.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // 与桌面图标同一套素材：深色底 + 白色前景（108dp 画布，图形收在中央安全区）。
            // 前景整幅铺进圆里，图形占比与 Launcher 上看到的桌面图标一致。
            Box(
                modifier = Modifier
                    .padding(top = 44.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = "青岛大学\n校园生活服务",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 22.dp),
            )
            Text(
                text = "课表、通知、校园网认证与校内入口，在一个应用内完成。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 34.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                TrustRow(
                    icon = Icons.Outlined.Lock,
                    title = "不接触你的密码",
                    description = "登录在校方统一认证页面内完成，客户端只接管会话，不读取、不保存密码。",
                )
                TrustRow(
                    icon = Icons.Outlined.Key,
                    title = "凭据加密留在本机",
                    description = "会话由 Android Keystore 加密后落盘，仅本机可解，不参与云备份。",
                )
                TrustRow(
                    icon = Icons.Outlined.Shield,
                    title = "不上传任何数据",
                    description = "没有统计与崩溃上报，课程与通知只写入本机数据库。",
                )
            }
        }

        QduButton(
            text = "开始登录",
            onClick = { viewModel.finish(onStartLogin) },
            modifier = Modifier.fillMaxWidth(),
        )
        // 原型屏 01 只有「开始登录」一个出口，登录一旦走不通整个应用就进不去。
        // PRD 的软门禁本来就是「可看缓存、用时拦」，这里把这条路显式画出来。
        QduButton(
            text = "暂不登录，先看本机缓存",
            onClick = { viewModel.finish(onSkip) },
            kind = QduButtonKind.Ghost,
            small = true,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp),
        )
        Text(
            text = "继续即表示已阅读免责声明\n非青岛大学官方客户端，数据仅存本机",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun TrustRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = scheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}
