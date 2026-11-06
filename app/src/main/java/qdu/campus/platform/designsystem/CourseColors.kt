package qdu.campus.platform.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** 课程块的一对颜色：底色与文字色。 */
@Immutable
data class CourseSurface(val container: Color, val onContainer: Color)

/**
 * 同一门课在网格里恒定同色，原型 04 屏的三档课程块分别落在
 * primary / tertiary / secondary 的容器色上，这里按同一套角色轮换。
 *
 * 只用现成角色，不自建色值：石墨方案下三档都是中性色，正好符合它「只讲明度层级」的定位。
 */
@Composable
fun courseSurface(key: String): CourseSurface {
    val scheme = MaterialTheme.colorScheme
    return when ((key.hashCode() and Int.MAX_VALUE) % CourseColorSlots) {
        0 -> CourseSurface(scheme.primaryContainer, scheme.onPrimaryContainer)
        1 -> CourseSurface(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        else -> CourseSurface(scheme.secondaryContainer, scheme.onSecondaryContainer)
    }
}

/** 轮换的档数，与原型里的 b1/b2/b3 对应。 */
private const val CourseColorSlots = 3
