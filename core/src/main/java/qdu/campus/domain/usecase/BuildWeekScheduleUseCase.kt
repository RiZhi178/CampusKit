package qdu.campus.domain.usecase

import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.Course
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
 */
class BuildWeekScheduleUseCase {

    operator fun invoke(
        courses: List<Course>,
        sections: List<ClassSection>,
        week: Int,
    ): WeekSchedule {
        val days = (1..7).associateWith { day ->
            courses
                .filter { it.day == day && it.activeOn(week) }
                .sortedBy { it.sections.firstOrNull() ?: Int.MAX_VALUE }
        }
        return WeekSchedule(week = week, days = days, sections = sections)
    }
}
