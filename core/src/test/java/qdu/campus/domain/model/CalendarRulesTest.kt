package qdu.campus.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 校历调停课的日级换算规则，配对口径来自教务处 2026 年节假日教学安排通知。 */
class CalendarRulesTest {

    private val holiday = LocalDate.of(2026, 10, 1)
    private val makeup = LocalDate.of(2026, 9, 20)

    private val overrides = mapOf(
        holiday to DayOverride(date = holiday, holiday = true),
        makeup to DayOverride(date = makeup, holiday = false, followsDate = LocalDate.of(2026, 10, 6)),
    )

    @Test
    fun `假期日停课返回 null`() {
        assertNull(CalendarRules.follows(holiday, overrides))
    }

    @Test
    fun `调休日返回被借的那天`() {
        assertEquals(LocalDate.of(2026, 10, 6), CalendarRules.follows(makeup, overrides))
    }

    @Test
    fun `没有规则的日期照常按自身`() {
        val normal = LocalDate.of(2026, 9, 15)
        assertEquals(normal, CalendarRules.follows(normal, overrides))
        assertEquals(normal, CalendarRules.follows(normal, emptyMap()))
    }

    @Test
    fun `调休日缺配对时按自身执行`() {
        val loose = mapOf(makeup to DayOverride(date = makeup, holiday = false, followsDate = null))
        assertEquals(makeup, CalendarRules.follows(makeup, loose))
    }

    @Test
    fun `标注只给文字不做颜色状态`() {
        assertEquals("休", CalendarRules.note(holiday, overrides))
        assertEquals("调", CalendarRules.note(makeup, overrides))
        assertNull(CalendarRules.note(LocalDate.of(2026, 9, 15), overrides))
    }
}
