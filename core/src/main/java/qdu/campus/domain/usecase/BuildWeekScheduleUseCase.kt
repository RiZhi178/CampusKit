package qdu.campus.domain.usecase

import qdu.campus.domain.model.CalendarRules
import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.DayOverride
import qdu.campus.domain.model.WeekSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 教学周计算：以学期起始周的周一为第 1 周。 */
object WeekCalculator {

    fun mondayOf(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    /** 返回当前教学周；startDate 为空或尚未开学时返回 1。 */
    fun currentWeek(startDate: LocalDate?, today: LocalDate = LocalDate.now()): Int {
        if (startDate == null) return 1
        val start = mondayOf(startDate)
        val diff = ChronoUnit.DAYS.between(start, today).toInt()
        if (diff < 0) return 1
        return diff / 7 + 1
    }

    fun maxWeek(courses: List<Course>): Int =
        courses.flatMap { it.weeks }.maxOrNull() ?: 20

    /** 指定教学周的周一与周日，给周切换条的「10 月 26 日 – 11 月 1 日」用；没有起始日就报不出。 */
    fun weekDates(startDate: LocalDate?, week: Int): Pair<LocalDate, LocalDate>? {
        val start = startDate ?: return null
        val monday = mondayOf(start).plusWeeks((week - 1).coerceAtLeast(0).toLong())
        return monday to monday.plusDays(6)
    }
}

/**
 * 把课程集合按指定周筛成周视图数据。
 * 单双周与非连续周次完全由 pkzcmx 解析出的 weeks 决定，这里只做包含判断。
 *
 * 调停课：给出该周周一（[weekStart]）与日级规则（[overrides]）后，每天先经
 * [CalendarRules.follows] 换算成「实际执行哪一天的教学安排」再取课——
 * 假期日整天为空；调休日显示被借那天的课（连教学周一起借，如 9 月 20 日
 * 上的是 10 月 6 日所在周的第 4 周周二课）。课表数据本身不动。
 */
class BuildWeekScheduleUseCase {

    operator fun invoke(
        courses: List<Course>,
        sections: List<ClassSection>,
        week: Int,
        weekStart: LocalDate? = null,
        overrides: Map<LocalDate, DayOverride> = emptyMap(),
    ): WeekSchedule {
        val days = (1..7).associateWith { day ->
            // 没有 weekStart 就退回旧行为：按列的星期取课，不消费调停课规则。
            val follows = weekStart
                ?.plusDays((day - 1).toLong())
                ?.let { CalendarRules.follows(it, overrides) }
            if (weekStart != null && follows == null) {
                // 假期日：整天停课
                emptyList()
            } else {
                val effectiveDay = follows?.dayOfWeek?.value ?: day
                // 借课连周次一起借：被借那天的课按它自己所在的教学周取，
                // 否则第 3 周周日会错误地显示第 3 周周二的课（应显示第 4 周的）。
                val effectiveWeek = if (weekStart != null && follows != null) {
                    (week + ChronoUnit.WEEKS.between(weekStart, WeekCalculator.mondayOf(follows)).toInt())
                        .coerceAtLeast(1)
                } else {
                    week
                }
                courses
                    .filter { it.day == effectiveDay && it.activeOn(effectiveWeek) }
                    .sortedBy { it.sections.firstOrNull() ?: Int.MAX_VALUE }
            }
        }
        val notes = if (weekStart == null) {
            emptyMap()
        } else {
            (0..6).mapNotNull { offset ->
                val date = weekStart.plusDays(offset.toLong())
                CalendarRules.note(date, overrides)?.let { date.dayOfWeek.value to it }
            }.toMap()
        }
        return WeekSchedule(week = week, days = days, sections = sections, dayNotes = notes)
    }
}
