package qdu.campus.domain.model

import java.time.LocalDate
import java.time.LocalTime

/** 学期。startDate 为手动设置或推算的第一周周一，用于计算当前教学周。 */
data class Term(
    val id: String,
    val name: String,
    val startDate: LocalDate? = null,
)

/**
 * 「学期与节次」里可供选的学期。
 *
 * 每一项由数据源自己给出：研究生系统是当前那一个，本科生课程总表是随包发布的那几份，
 * 各自的标识与人话名字不一样，所以不在页面上拼。
 */
data class SemesterOption(
    val id: String,
    val label: String,
    /** 该学期的第一周周一，`2026-09-07`；空串表示要用户自己填。 */
    val termStart: String = "",
)

/** 大节（节次）配置，来自 searchPkgl002List 的 pkgl00201List。 */
data class ClassSection(
    val index: Int,
    val startTime: String,
    val endTime: String,
    /** 上午 / 下午 / 晚上 */
    val period: String,
    val label: String,
    /** 该大节覆盖的小节号，来自 zyxjs，例如 01,02 */
    val covers: List<Int> = emptyList(),
) {
    /** 课程是否落在本大节内。 */
    fun contains(courseSections: List<Int>): Boolean =
        if (covers.isEmpty()) courseSections.firstOrNull() == index
        else courseSections.any { it in covers }

    /** 此刻是否正在上这一大节。时间串取自节次配置，可能被用户覆写成非法值，判不了就当作不在。 */
    fun isLive(now: LocalTime): Boolean {
        val from = hhmm(startTime) ?: return false
        val to = hhmm(endTime) ?: return false
        return now >= from && now < to
    }
}

/**
 * 一门已排课的课程记录。一个 pkzcmx 时间可能对应多条记录，
 * sections 为该记录占用的小节号，weeks 为有效的教学周集合。
 */
data class Course(
    val id: String,
    val semesterId: String,
    val name: String,
    val code: String = "",
    val classNo: String = "",
    val teachers: String = "",
    val room: String = "",
    val campus: String = "",
    /** 1 = 周一，7 = 周日 */
    val day: Int,
    val sections: List<Int>,
    val weeks: Set<Int>,
    val startTime: String = "",
    val endTime: String = "",
    val category: String = "",
    val hours: Int = 0,
    /** 学分。本科生有 0.5、1.5 这类半学分，所以不是整数。 */
    val credits: Double = 0.0,
    val department: String = "",
    val status: String = "",
) {
    val sectionLabel: String
        get() = if (sections.isEmpty()) "" else {
            val start = sections.first()
            val end = sections.last()
            if (start == end) "第${start}节" else "第${start}-${end}节"
        }

    /** 课程块的配色标识：同一门课在网格里要恒定同色，编号比课程名稳，缺失时退回课程名。 */
    val colorKey: String get() = code.ifBlank { name }

    fun activeOn(week: Int): Boolean = week in weeks

    /** 「第 3–18 周（每周）」这类人话周次，详情与本周说明都用它。 */
    val weekLabel: String get() = WeekPattern.describe(weeks)
}

/** 未排课课程（findNoKb），只展示课程名、学分与开课单位。 */
data class NoScheduleCourse(
    val id: String,
    val semesterId: String,
    val name: String,
    val credits: Int = 0,
    val department: String = "",
    val teachers: String = "",
    val category: String = "",
)

/** 单周课表。days 的键为 1..7。 */
data class WeekSchedule(
    val week: Int,
    val days: Map<Int, List<Course>>,
    val sections: List<ClassSection>,
) {
    fun coursesOf(day: Int): List<Course> = days[day].orEmpty()

    /**
     * 此刻正在上的课程：先找到时间窗口盖住现在的那个大节，再取这一列里落在其中的课。
     * 同一格排了多门时全部返回，标记不能只挑第一门。
     */
    fun liveCourseIds(day: Int, now: LocalTime): Set<String> {
        val section = sections.firstOrNull { it.isLive(now) } ?: return emptySet()
        return coursesOf(day).filter { section.contains(it.sections) }.map { it.id }.toSet()
    }
}

/** 校内通知：研究生院三个栏目与教务处教务通知，合并后按时间倒序展示。 */
data class Notice(
    val fingerprint: String,
    val title: String,
    val date: String,
    val column: String = "",
    val url: String,
    val read: Boolean = false,
    /** 数据来源：`grad_notice`（研究生院三栏）或 `jwc_notice`（教务处教务通知）。 */
    val source: String = "grad_notice",
)

/** 同步元数据，用于「数据更新于 X 月 X 日」与非阻断提示。 */
data class SyncState(
    val source: String,
    val lastSuccessAt: Long?,
    val message: String? = null,
)

/** 节次时间串形如「10:10」；解不了返回 null，调用方按「不在这一节」处理。 */
private fun hhmm(value: String): LocalTime? =
    value.trim().takeIf { it.isNotEmpty() }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
