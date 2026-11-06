package qdu.campus.data.remote.jwc

import kotlinx.serialization.json.Json
import qdu.campus.domain.model.Course

/**
 * 清洗产物 -> 领域模型。
 *
 * 资产是「按班级建独立课表」的结构（`KcapTimetables`），平行教学班组在清洗阶段已合并：
 * 组内教师全列、地点留空，单个班级读出来就是一套课表，不再需要按班过滤或二次合并。
 * 单条解析失败只丢那一条；整体格式对不上（字段改名）会一条都解不出来，
 * 由调用方按「空课表」处理，页面显示为空比显示半张表安全。
 */
object KcapParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parseIndex(text: String): List<KcapSemester> =
        runCatching { json.decodeFromString(KcapIndex.serializer(), text).semesters }
            .getOrDefault(emptyList())

    fun parseTimetables(text: String): KcapTimetables? =
        runCatching { json.decodeFromString(KcapTimetables.serializer(), text) }.getOrNull()

    /** 勾选框上的班级列表：按名字排序。 */
    fun classNames(timetables: KcapTimetables): List<String> =
        timetables.classes.map { it.name }.sorted()

    /**
     * 一个班的课表条目 -> 一门课。学期标识由调用方给（资产里的 id 是「哪份表」，
     * 落库与页面用的 semesterId 走同一套，切换本科/研究生时由页面写入）。
     */
    fun toCourse(lesson: KcapLesson, className: String, semesterId: String, season: UgSections.Season): Course? {
        val name = lesson.name.trim()
        if (name.isEmpty()) return null
        val day = lesson.day.takeIf { it in 1..7 } ?: return null
        val sections = lesson.periods.filter { it in 1..MAX_PERIOD }.distinct().sorted()
        if (sections.isEmpty()) return null
        val weeks = parseWeeks(lesson.weeks)
        if (weeks.isEmpty()) return null
        val (start, end) = UgSections.timeRange(season, sections)
        return Course(
            id = lesson.id,
            semesterId = semesterId,
            name = name,
            code = lesson.code,
            classNo = className,
            teachers = lesson.teachers,
            room = lesson.room,
            campus = lesson.campus,
            day = day,
            sections = sections,
            weeks = weeks,
            startTime = start,
            endTime = end,
            category = lesson.kind,
            hours = lesson.hours,
            credits = lesson.credits,
            department = lesson.dept,
            status = "",
        )
    }

    /**
     * 周次。清洗后的写法是「1-16」「1,3,5」「1-6,15-16」这类区间与明细混排，
     * 研究生那套 ScheduleCodeParser 只认逗号枚举，所以这里单独实现。
     * 上限为止损：真出现离谱周次，宁可不显示也不能把周次切换撑到几百项。
     */
    fun parseWeeks(text: String?): Set<Int> {
        if (text.isNullOrBlank()) return emptySet()
        return text.split(',', '，', ';', '；')
            .flatMap { token ->
                val parts = token.trim().split('-', '－', '~')
                val from = parts.firstOrNull()?.toIntOrNull() ?: return@flatMap emptyList()
                if (parts.size == 1) listOf(from)
                else {
                    val to = parts[1].toIntOrNull() ?: return@flatMap listOf(from)
                    if (to < from) emptyList() else (from..minOf(to, MAX_WEEKS + 1)).toList()
                }
            }
            .filter { it in 1..MAX_WEEKS }
            .toSet()
    }

    private const val MAX_WEEKS = 40
    private const val MAX_PERIOD = 12
}
