package qdu.campus.platform.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import qdu.campus.domain.model.AppearanceScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 色值基准来自 color-schemes.html，那里每个十六进制都是 material-color-utilities 按
 * SPEC_2021 实测的输出。这里用它做断言，色值本身不允许抄进生产代码。
 *
 * 六个角色够用是因为它们分别能暴露四类错误：
 * primary / primaryContainer 看变体与 tone 传对没有，surface 系列看中性梯度，
 * tertiaryContainer 看第三色板，onSurface 看前景反算。
 */
class PaletteTest {

    private val roles = listOf(
        "primary", "primaryContainer", "surface", "onSurface", "surfaceContainerHigh", "tertiaryContainer",
    )

    private val reference = mapOf(
        AppearanceScheme.Indigo to Pair("565992 E0E0FF FBF8FF 1B1B21 EAE7EF FFD8EE", "BFC2FF 3E4278 131318 E4E1E9 2A292F 5E3C52"),
        AppearanceScheme.AppleGreen to Pair("516527 D3EC9E FAFAEE 1A1C15 E9E9DD BCECE4", "B7D084 3A4D10 12140D E3E3D7 292B23 204E48"),
        AppearanceScheme.Rose to Pair("864B6F FFD8EB FFF8F8 211A1D F3E4E9 FFDBCB", "FAB1DA 6B3457 181115 EDDFE4 30282C 653D28"),
        // 石墨走 SchemeNeutral，一旦被当成 TonalSpot 处理，primary 会变成 36618E。
        AppearanceScheme.Graphite to Pair("575F6B DBE3F1 FBF9FA 1B1B1D EAE7E9 D6E3F7", "BFC7D5 3F4753 131314 E4E2E3 2A2A2B 3B4858"),
    )

    private data class Pair(val light: String, val dark: String)

    @Test
    fun `key roles match the reference palettes`() {
        assertEquals("方案数量变了就要同步 color-schemes.html", 4, AppearanceScheme.entries.size)
        reference.forEach { (scheme, hexes) ->
            listOf(false to hexes.light.split(" "), true to hexes.dark.split(" ")).forEach { (isDark, expected) ->
                val actual = generateColorScheme(scheme, isDark)
                roles.forEachIndexed { index, role ->
                    assertEquals(
                        "${scheme.label} ${if (isDark) "dark" else "light"} $role",
                        expected[index],
                        actual.hexOf(role),
                    )
                }
            }
        }
    }

    /** 每套取四对里最弱的一对，阈值 4.5:1。 */
    @Test
    fun `the four weakest pairs stay above four point five`() {
        AppearanceScheme.entries.forEach { scheme ->
            listOf(false, true).forEach { isDark ->
                val c = generateColorScheme(scheme, isDark)
                val pairs = listOf(
                    "onSurface / surface" to contrast(c.onSurface, c.surface),
                    "onPrimary / primary" to contrast(c.onPrimary, c.primary),
                    "onPrimaryContainer / primaryContainer" to contrast(c.onPrimaryContainer, c.primaryContainer),
                    "onSurfaceVariant / surface" to contrast(c.onSurfaceVariant, c.surface),
                )
                val weakest = pairs.minBy { it.second }
                assertEquals(
                    "${scheme.label} ${if (isDark) "dark" else "light"} 最弱的一对是 ${weakest.first}（${"%.2f".format(weakest.second)}:1）",
                    true,
                    weakest.second >= 4.5,
                )
            }
        }
    }

    /**
     * 「保留颜色」把表面往主色抬，抬多了次级文字就掉到 4.5 以下：
     * 20% 主色 + 7% 白时 surfaceContainerHighest 只剩 3.81:1，所以当前用 12% / 4%。
     */
    @Test
    fun `tinted dark surfaces keep secondary text readable`() {
        val surfaces = listOf(
            "surface", "surfaceContainerLow", "surfaceContainerHigh", "surfaceContainerHighest",
        )
        AppearanceScheme.entries.forEach { scheme ->
            val c = generateColorScheme(scheme, isDark = true, tintedDark = true)
            surfaces.forEach { role ->
                val bg = when (role) {
                    "surface" -> c.surface
                    "surfaceContainerLow" -> c.surfaceContainerLow
                    "surfaceContainerHigh" -> c.surfaceContainerHigh
                    else -> c.surfaceContainerHighest
                }
                val ratio = contrast(c.onSurfaceVariant, bg)
                assertEquals(
                    "${scheme.label} 保留颜色 ${role} 上 onSurfaceVariant 只有 ${"%.2f".format(ratio)}:1",
                    true,
                    ratio >= 4.5,
                )
            }
        }
    }

    /** 状态色固定四档，不随方案走；只随明暗换明度，两版都要过 4.5。 */
    @Test
    fun `status colours clear four point five on both surfaces`() {
        listOf(false, true).forEach { isDark ->
            val status = statusColors(isDark)
            AppearanceScheme.entries.forEach { scheme ->
                val c = generateColorScheme(scheme, isDark)
                listOf("surface" to c.surface, "surfaceContainerLow" to c.surfaceContainerLow).forEach { (name, bg) ->
                    listOf(
                        "success" to status.success,
                        "warning" to status.warning,
                        "danger" to status.danger,
                        "blocked" to status.blocked,
                    ).forEach { (label, color) ->
                        val ratio = contrast(color, bg)
                        assertEquals(
                            "${scheme.label} ${if (isDark) "dark" else "light"} $label / $name 只有 ${"%.2f".format(ratio)}:1",
                            true,
                            ratio >= 4.5,
                        )
                    }
                }
            }
        }
    }

    /** primaryContainer 等于种子色本身，说明误用了 SchemeContent。 */
    @Test
    fun `no scheme puts the seed into primaryContainer`() {
        AppearanceScheme.entries.forEach { scheme ->
            listOf(false, true).forEach { isDark ->
                val container = generateColorScheme(scheme, isDark).primaryContainer.toArgb() and 0xFFFFFF
                assertNotEquals("容器色不应等于种子色", scheme.seed.toInt() and 0xFFFFFF, container)
            }
        }
    }

    private fun ColorScheme.hexOf(role: String): String {
        val color: Color = when (role) {
            "primary" -> primary
            "primaryContainer" -> primaryContainer
            "surface" -> surface
            "onSurface" -> onSurface
            "surfaceContainerHigh" -> surfaceContainerHigh
            "tertiaryContainer" -> tertiaryContainer
            else -> error("未覆盖的角色：$role")
        }
        return "%06X".format(color.toArgb() and 0xFFFFFF)
    }

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val light = max(luminance(a), luminance(b))
        val dark = min(luminance(a), luminance(b))
        return (light + 0.05) / (dark + 0.05)
    }
}
