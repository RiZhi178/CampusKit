package qdu.campus.data.remote.yjsxt

import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.NoScheduleCourse

/**
 * `pksj` 与周次字段的时间解析。放在 mapper 而不是 UI，页面只拿到结构化结果。
 *
 * `pksj` 规则：1 位星期 + 每 2 位一节。
 * 40304 -> 周四 03、04 节；405060708 -> 周四 05-08 节。
 */
object ScheduleCodeParser {

    data class Parsed(val day: Int, val sections: List<Int>)

    fun parse(timeCode: String?): Parsed? {
        val code = timeCode?.trim().orEmpty()
        if (code.length < 3) return null
        val day = code.first().digitToIntOrNull() ?: return null
        if (day !in 1..7) return null
        val sections = code.drop(1)
            .chunked(2)
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..20 }
        if (sections.isEmpty()) return null
        return Parsed(day, sections)
    }

    /**
     * 周次以 pkzcmx 为准；单双周、非连续周次都由这份明细决定。
     * 上限只为止损：真出了个离谱数字，宁可不显示也不能把周次切换撑到几百周。
     */
    fun parseWeeks(weekDetail: String?): Set<Int> {
        if (weekDetail.isNullOrBlank()) return emptySet()
        return weekDetail.split(',', '，')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..MAX_WEEKS }
            .toSet()
    }

    private const val MAX_WEEKS = 60
}

object CourseMapper {

    fun toCourse(dto: CourseDto, fallbackSemesterId: String): Course? {
        val parsed = ScheduleCodeParser.parse(dto.timeCode) ?: return null
        val name = dto.name?.trim().orEmpty()
        if (name.isEmpty()) return null
        val id = dto.id?.trim().orEmpty()
            .ifEmpty { "${name}_${dto.timeCode}_${dto.weekDetail}" }
        return Course(
            id = id,
            semesterId = dto.semesterId?.trim().orEmpty().ifEmpty { fallbackSemesterId },
            name = name,
            code = dto.code.orEmpty(),
            classNo = dto.classNo.orEmpty(),
            teachers = (dto.teachers ?: dto.teachersAlt).orEmpty(),
            room = dto.room.orEmpty(),
            campus = dto.campus.orEmpty(),
            day = parsed.day,
            sections = parsed.sections.distinct().sorted(),
            weeks = ScheduleCodeParser.parseWeeks(dto.weekDetail),
            startTime = dto.startTime.orEmpty(),
            endTime = dto.endTime.orEmpty(),
            category = dto.category.orEmpty(),
            hours = dto.hours ?: 0,
            credits = dto.credits ?: 0.0,
            department = dto.department.orEmpty(),
            status = dto.status.orEmpty(),
        )
    }

    fun toCourses(list: List<CourseDto>, semesterId: String): List<Course> =
        list.mapNotNull { toCourse(it, semesterId) }
            .distinctBy { it.id }

    fun toNoSchedule(dto: NoScheduleDto, semesterId: String): NoScheduleCourse? {
        val name = (dto.name ?: dto.nameAlt)?.trim().orEmpty()
        if (name.isEmpty()) return null
        return NoScheduleCourse(
            id = dto.id?.trim().orEmpty().ifEmpty { "nokb_$name" },
            semesterId = dto.semesterId?.trim().orEmpty().ifEmpty { semesterId },
            name = name,
            credits = (dto.credits ?: dto.creditsAlt)?.toInt() ?: 0,
            department = dto.department.orEmpty(),
            teachers = (dto.teachers ?: dto.teachersAlt).orEmpty(),
            category = dto.category.orEmpty(),
        )
    }

    fun toSections(dto: SemesterDto): List<ClassSection> =
        dto.sections.orEmpty()
            .mapNotNull { big ->
                val indexes = big.sectionIndexes
                    ?.split(',')
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    .orEmpty()
                val index = indexes.firstOrNull() ?: return@mapNotNull null
                ClassSection(
                    index = index,
                    startTime = big.startTime.orEmpty(),
                    endTime = big.endTime.orEmpty(),
                    period = big.period.orEmpty(),
                    label = (big.name ?: big.bigName).orEmpty().ifEmpty { "第${index}大节" },
                    covers = indexes,
                )
            }
            .distinctBy { it.index }
            .sortedBy { it.index }
}