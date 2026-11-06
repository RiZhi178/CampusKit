package qdu.campus.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WeekPatternTest {

    @Test
    fun `连续周次收成一段并标注每周`() {
        assertEquals("第 3–18 周（每周）", WeekPattern.describe((3..18).toSet()))
    }

    @Test
    fun `单双周按奇偶标注`() {
        assertEquals("第 3–9 周（单周）", WeekPattern.describe(setOf(3, 5, 7, 9)))
        assertEquals("第 2–8 周（双周）", WeekPattern.describe(setOf(2, 4, 6, 8)))
    }

    @Test
    fun `只有一周不硬凑说法`() {
        assertEquals("第 8 周", WeekPattern.describe(setOf(8)))
        assertEquals("", WeekPattern.describe(emptySet()))
    }

    @Test
    fun `说不清的周次逐段列出`() {
        assertEquals("第 1–3 周、第 5 周、第 7 周", WeekPattern.describe(setOf(1, 2, 3, 5, 7)))
    }
}
