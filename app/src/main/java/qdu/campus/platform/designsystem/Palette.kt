package qdu.campus.platform.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import qdu.campus.domain.model.AppearanceScheme
import qdu.campus.domain.model.PaletteVariant
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeTonalSpot

private const val OPAQUE = 0xFF000000L

private val colors = MaterialDynamicColors()

/**
 * 由种子生成整套角色。
 *
 * SPEC_2021 必须显式传：MaterialKolor 的默认色规会随版本走，
 * 2025 色规下 primary / container 的 tone 与本项目的色值基准不一致。
 * 浅深各生成一次，不从浅色反推深色。
 */
fun generateColorScheme(
    scheme: AppearanceScheme,
    isDark: Boolean,
    tintedDark: Boolean = false,
): ColorScheme {
    val hct = Hct.fromInt(scheme.seed.toInt())
    // Platform 必须显式传：4.1.1 把默认值声明成 internal，外部模块无法省略。
    // PaletteTest 用色值基准兜底，平台若改变 tone 会立刻断言失败。
    val dynamic: DynamicScheme = when (scheme.variant) {
        PaletteVariant.NEUTRAL ->
            SchemeNeutral(hct, isDark, 0.0, ColorSpec.SpecVersion.SPEC_2021, DynamicScheme.Platform.PHONE)

        PaletteVariant.TONAL_SPOT ->
            SchemeTonalSpot(hct, isDark, 0.0, ColorSpec.SpecVersion.SPEC_2021, DynamicScheme.Platform.PHONE)
    }
    fun argb(color: DynamicColor): Color =
        Color(OPAQUE or (color.getArgb(dynamic).toLong() and 0xFFFFFFL))

    // 全部角色逐一映射，漏一个就会回落成 M3 的默认紫，整屏发蓝紫。
    // 不含 shadow：本版 ColorScheme 已移除该参数。
    val base = ColorScheme(
        primary = argb(colors.primary()),
        onPrimary = argb(colors.onPrimary()),
        primaryContainer = argb(colors.primaryContainer()),
        onPrimaryContainer = argb(colors.onPrimaryContainer()),
        inversePrimary = argb(colors.inversePrimary()),
        secondary = argb(colors.secondary()),
        onSecondary = argb(colors.onSecondary()),
        secondaryContainer = argb(colors.secondaryContainer()),
        onSecondaryContainer = argb(colors.onSecondaryContainer()),
        tertiary = argb(colors.tertiary()),
        onTertiary = argb(colors.onTertiary()),
        tertiaryContainer = argb(colors.tertiaryContainer()),
        onTertiaryContainer = argb(colors.onTertiaryContainer()),
        error = argb(colors.error()),
        onError = argb(colors.onError()),
        errorContainer = argb(colors.errorContainer()),
        onErrorContainer = argb(colors.onErrorContainer()),
        background = argb(colors.background()),
        onBackground = argb(colors.onBackground()),
        surface = argb(colors.surface()),
        onSurface = argb(colors.onSurface()),
        surfaceVariant = argb(colors.surfaceVariant()),
        onSurfaceVariant = argb(colors.onSurfaceVariant()),
        surfaceDim = argb(colors.surfaceDim()),
        surfaceBright = argb(colors.surfaceBright()),
        surfaceContainerLowest = argb(colors.surfaceContainerLowest()),
        surfaceContainerLow = argb(colors.surfaceContainerLow()),
        surfaceContainer = argb(colors.surfaceContainer()),
        surfaceContainerHigh = argb(colors.surfaceContainerHigh()),
        surfaceContainerHighest = argb(colors.surfaceContainerHighest()),
        outline = argb(colors.outline()),
        outlineVariant = argb(colors.outlineVariant()),
        scrim = argb(colors.scrim()),
        surfaceTint = argb(colors.surfaceTint()),
        primaryFixed = argb(colors.primaryFixed()),
        primaryFixedDim = argb(colors.primaryFixedDim()),
        onPrimaryFixed = argb(colors.onPrimaryFixed()),
        onPrimaryFixedVariant = argb(colors.onPrimaryFixedVariant()),
        secondaryFixed = argb(colors.secondaryFixed()),
        secondaryFixedDim = argb(colors.secondaryFixedDim()),
        onSecondaryFixed = argb(colors.onSecondaryFixed()),
        onSecondaryFixedVariant = argb(colors.onSecondaryFixedVariant()),
        tertiaryFixed = argb(colors.tertiaryFixed()),
        tertiaryFixedDim = argb(colors.tertiaryFixedDim()),
        onTertiaryFixed = argb(colors.onTertiaryFixed()),
        onTertiaryFixedVariant = argb(colors.onTertiaryFixedVariant()),
        inverseSurface = argb(colors.inverseSurface()),
        inverseOnSurface = argb(colors.inverseOnSurface()),
    )
    return if (isDark && tintedDark) tintDarkSurfaces(base) else base
}

/**
 * 「保留颜色」：深色下让表面带上主色的色相，底不再是近乎无彩的深灰。
 *
 * 只动背景与表面一族，文字角色一概不碰。幅度是实测定的上限：
 * 20% 主色 + 7% 白会把 surfaceContainerHighest 抬到 onSurfaceVariant 只剩 3.81:1，
 * 12% + 4% 时最弱一处 4.90:1，才守得住 4.5 的线。
 * 浅色下不生效：浅底本来就是彩度极低的白，染色只会让整屏发灰。
 */
internal fun tintDarkSurfaces(scheme: ColorScheme): ColorScheme {
    val accent = scheme.primary
    fun lift(color: Color): Color = lerp(lerp(color, accent, 0.12f), Color.White, 0.04f)
    return scheme.copy(
        background = lift(scheme.background),
        surface = lift(scheme.surface),
        surfaceDim = lift(scheme.surfaceDim),
        surfaceBright = lift(scheme.surfaceBright),
        surfaceContainerLowest = lift(scheme.surfaceContainerLowest),
        surfaceContainerLow = lift(scheme.surfaceContainerLow),
        surfaceContainer = lift(scheme.surfaceContainer),
        surfaceContainerHigh = lift(scheme.surfaceContainerHigh),
        surfaceContainerHighest = lift(scheme.surfaceContainerHighest),
        // surfaceVariant 不染：它是低强调容器（分隔、轨道），染色后 onSurfaceVariant 只剩 3.99:1。
        surfaceTint = scheme.primary,
    )
}
