package qdu.campus.domain.usecase

import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.WeekSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WeekScheduleTest {

    private fun course(
        id: String,
        day: Int,
        sections: List<Int>,
        weeks: Set<Int>,
    ) = Course(
        id = id,
        semesterId = "2026-2027-1",
        name = "课程$id",
        day = day,
        sections = sections,
        weeks = weeks,
    )

    @Test
    fun `当前周按起始日推算`() {
        val start = LocalDate.of(2026, 9, 1) // 周二
        assertEquals(1, WeekCalculator.currentWeek(start, LocalDate.of(2026, 9, 1)))
        assertEquals(1, WeekCalculator.currentWeek(start, LocalDate.of(2026, 9, 6)))
        assertEquals(2, WeekCalculator.currentWeek(start, LocalDate.of(2026, 9, 7)))
        assertEquals(3, WeekCalculator.currentWeek(start, LocalDate.of(2026, 9, 14)))
    }

    @Test
    fun `未开学或缺少起始日按第 1 周`() {
        assertEquals(1, WeekCalculator.currentWeek(null))
        assertEquals(1, WeekCalculator.currentWeek(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1)))
    }

    @Test
    fun `周视图按周过滤非连续周次`() {
        val courses = listOf(
            course("a", day = 1, sections = listOf(1, 2), weeks = setOf(1, 3, 5)),
            course("b", day = 1, sections = listOf(3, 4), weeks = setOf(2, 4)),
        )
        val schedule = BuildWeekScheduleUseCase()(
            courses = courses,
            sections = listOf(ClassSection(1, "08:00", "09:50", "上午", "第一大节")),
            week = 3,
        )
        assertEquals(listOf("a"), schedule.coursesOf(1).map { it.id })
        assertTrue(schedule.coursesOf(2).isEmpty())
    }

    @Test
    fun `同一天课程按起始小节排序`() {
        val courses = listOf(
            course("late", day = 2, sections = listOf(5, 6), weeks = setOf(1)),
            course("early", day = 2, sections = listOf(1, 2), weeks = setOf(1)),
        )
        val schedule = BuildWeekScheduleUseCase()(courses, emptyList(), 1)
        assertEquals(listOf("early", "late"), schedule.coursesOf(2).map { it.id })
    }

    @Test
    fun `本周日期区间按起始周对齐到周一`() {
        // 2026-09-01 是周二，第一周要从它所在周的周一算起
        val (monday, sunday) = WeekCalculator.weekDates(
            LocalDate.of(2026, 9, 1),
            week = 2,
        ) ?: error("应当给出区间")
        assertEquals(LocalDate.of(2026, 9, 7), monday)
        assertEquals(LocalDate.of(2026, 9, 13), sunday)
        assertEquals(null, WeekCalculator.weekDates(null, 1))
    }

    @Test
    fun `此刻只标记时间窗口内的课程`() {
        val schedule = scheduleOf(course("a", day = 3, sections = listOf(3, 4), weeks = setOf(1)))
        assertEquals(setOf("a"), schedule.liveCourseIds(3, LocalTime.of(11, 0)))
        // 大节之间有休息段，落在窗口外就不算在上课
        assertEquals(emptySet<String>(), schedule.liveCourseIds(3, LocalTime.of(12, 30)))
        assertEquals(emptySet<String>(), schedule.liveCourseIds(4, LocalTime.of(11, 0)))
    }

    @Test
    fun `同一格排了多门时全部标记`() {
        val schedule = scheduleOf(
            course("a", day = 3, sections = listOf(3, 4), weeks = setOf(1)),
            course("b", day = 3, sections = listOf(3, 4), weeks = setOf(1)),
        )
        assertEquals(setOf("a", "b"), schedule.liveCourseIds(3, LocalTime.of(11, 0)))
    }

    private fun scheduleOf(vararg items: Course) = WeekSchedule(
        week = 1,
        days = (1..7).associateWith { day -> items.filter { it.day == day } },
        sections = listOf(
            ClassSection(1, "08:00", "09:50", "上午", "第一大节", covers = listOf(1, 2)),
            ClassSection(2, "10:10", "12:00", "上午", "第二大节", covers = listOf(3, 4)),
        ),
    )
}