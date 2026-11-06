package qdu.campus.platform.designsystem

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import qdu.campus.domain.model.AppearanceMode
import qdu.campus.domain.model.AppearanceScheme

/**
 * 主题入口。方案决定色相，明暗决定 tone 朝向，「保留颜色」只在深色下给表面染色。
 *
 * 色板由种子现场生成并 remember，不放 Application 级缓存：
 * 缓存在方案切换后不会刷新，界面会停在旧色上。
 */
@Composable
fun QduTheme(
    scheme: AppearanceScheme = AppearanceScheme.Indigo,
    mode: AppearanceMode = AppearanceMode.SYSTEM,
    darkTinted: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (mode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val colorScheme = remember(scheme, isDark, darkTinted) {
        generateColorScheme(scheme, isDark, tintedDark = darkTinted)
    }
    val statusColors = rememberStatusColors(isDark)

    // 系统栏图标明暗按 surface 的实际亮度判断：深色方案的 surface 亮度并不固定，
    // 用 isDark 布尔值会在浅底深字的方案上把图标染成白色。
    val view = LocalView.current
    val lightSystemBars = colorScheme.surface.luminance() > 0.5f
    SideEffect {
        val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightSystemBars
            isAppearanceLightNavigationBars = lightSystemBars
        }
    }

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = QduTypography,
            shapes = QduShapes,
            content = content,
        )
    }
}
