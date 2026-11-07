package qdu.campus.domain.usecase

import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.DayOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 调停课在周视图里的生效方式。日期与配对全部取 2026 年秋季学期的真实安排：
 * 学期 2026-09-07（周一）开学，第 1 周 = 09-07~09-13。
 * 中秋 09-25~27（第 3 周周五到周日）放假；国庆 10-01~10-07 放假
 * （10-01~04 落在第 4 周，10-05~07 落在第 5 周）；
 * 09-20（第 2 周周日）执行 10-06（第 5 周周二）的教学安排；
 * 10-10（第 5 周周六）执行 10-07（同为第 5 周周三）的教学安排。
 */
class WeekOverrideTest {

    private val termStart = LocalDate.of(2026, 9, 7)
    private val useCase = BuildWeekScheduleUseCase()

    private fun course(
        id: String,
        day: Int,
        weeks: Set<Int>,
    ) = qdu.campus.domain.model.Course(
        id = id,
        semesterId = "2026-2027-1",
        name = "课程$id",
        day = day,
        sections = listOf(1, 2),
        weeks = weeks,
    )

    private val overrides = (1..7).associate { day ->
        val date = LocalDate.of(2026, 10, day)
        date to DayOverride(date, holiday = true)
    } + mapOf(
        LocalDate.of(2026, 9, 25) to DayOverride(LocalDate.of(2026, 9, 25), holiday = true),
        LocalDate.of(2026, 9, 26) to DayOverride(LocalDate.of(2026, 9, 26), holiday = true),
        LocalDate.of(2026, 9, 27) to DayOverride(LocalDate.of(2026, 9, 27), holiday = true),
        LocalDate.of(2026, 9, 20) to DayOverride(
            LocalDate.of(2026, 9, 20), holiday = false, followsDate = LocalDate.of(2026, 10, 6),
        ),
        LocalDate.of(2026, 10, 10) to DayOverride(
            LocalDate.of(2026, 10, 10), holiday = false, followsDate = LocalDate.of(2026, 10, 7),
        ),
    )

    private fun mondayOf(week: Int) = WeekCalculator.mondayOf(termStart).plusWeeks((week - 1).toLong())

    @Test
    fun `国庆周的工作日整天停课`() {
        // 第 4 周 = 09-28 ~ 10-04：周二 09-29 正常，10-01（四）10-02（五）假期
        val schedule = useCase(
            courses = listOf(course("tue", day = 2, weeks = (1..18).toSet())),
            sections = emptyList(),
            week = 4,
            weekStart = mondayOf(4),
            overrides = overrides,
        )
        assertTrue(schedule.coursesOf(2).isNotEmpty()) // 09-29 正常周二，有课
        assertTrue(schedule.coursesOf(4).isEmpty()) // 10-01 假期
        assertTrue(schedule.coursesOf(5).isEmpty()) // 10-02 假期
        assertEquals("休", schedule.dayNotes[4])
        assertEquals("休", schedule.dayNotes[5])
    }

    @Test
    fun `中秋周五停课标注在列头`() {
        // 第 3 周 = 09-21 ~ 09-27：周五 09-25 假期，周三 09-23 正常
        val schedule = useCase(
            courses = listOf(course("wed", day = 3, weeks = (1..18).toSet()), course("fri", day = 5, weeks = (1..18).toSet())),
            sections = emptyList(),
            week = 3,
            weekStart = mondayOf(3),
            overrides = overrides,
        )
        assertTrue(schedule.coursesOf(3).isNotEmpty())
        assertTrue(schedule.coursesOf(5).isEmpty())
        assertEquals("休", schedule.dayNotes[5])
    }

    @Test
    fun `调休日借课连教学周一起借`() {
        // 09-20 是第 2 周周日，执行 10-06（第 5 周周二）的安排
        val schedule = useCase(
            courses = listOf(
                course("w5tue", day = 2, weeks = setOf(5)),
                course("w2tue", day = 2, weeks = setOf(2)),
                course("w2sun", day = 7, weeks = setOf(2)),
            ),
            sections = emptyList(),
            week = 2,
            weekStart = mondayOf(2),
            overrides = overrides,
        )
        // 第 2 周周日显示的是第 5 周周二的课，不是第 2 周周二的，也不显示周日自己的
        assertEquals(listOf("w5tue"), schedule.coursesOf(7).map { it.id })
        assertEquals("调", schedule.dayNotes[7])
    }

    @Test
    fun `第 5 周周六执行同周周三的课`() {
        // 10-10 与 10-07 同在第 5 周：借课周次不变，10-07 自己是假期
        val schedule = useCase(
            courses = listOf(
                course("w5wed", day = 3, weeks = setOf(5)),
                course("w5sat", day = 6, weeks = setOf(5)),
            ),
            sections = emptyList(),
            week = 5,
            weekStart = mondayOf(5),
            overrides = overrides,
        )
        assertEquals(listOf("w5wed"), schedule.coursesOf(6).map { it.id })
        assertEquals("调", schedule.dayNotes[6])
        assertTrue(schedule.coursesOf(3).isEmpty()) // 10-07 假期停课
    }

    @Test
    fun `没有规则时行为与旧版一致`() {
        val courses = listOf(course("tue", day = 2, weeks = (1..18).toSet()))
        val withNull = useCase(courses, emptyList(), 3, mondayOf(3), emptyMap())
        val legacy = useCase(courses, emptyList(), 3)
        assertEquals(legacy.days, withNull.days)
        assertTrue(withNull.dayNotes.isEmpty())
    }

    @Test
    fun `缺起始日时忽略规则退回旧行为`() {
        val courses = listOf(course("tue", day = 2, weeks = (1..18).toSet()))
        val schedule = useCase(courses, emptyList(), 4, weekStart = null, overrides = overrides)
        assertEquals(listOf("tue"), schedule.coursesOf(2).map { it.id })
        assertTrue(schedule.dayNotes.isEmpty())
    }
}
