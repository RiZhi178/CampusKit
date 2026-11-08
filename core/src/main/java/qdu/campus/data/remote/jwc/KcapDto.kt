package qdu.campus.data.remote.jwc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 教务处「课程总表（本科生）」清洗后的格式。
 *
 * 原始 xlsx 由开发机上的 `tools/kcap-clean.mjs` 清洗成随包资产；
 * 这里只描述清洗产物，不描述教务处的表——表的口径变了先改工具，再改这里。
 */
@Serializable
data class KcapIndex(
    val semesters: List<KcapSemester> = emptyList(),
)

@Serializable
data class KcapSemester(
    val id: String,
    val label: String = "",
    /** 第一周周一，`2026-09-07`。随表发布，用户不用手填。 */
    @SerialName("termStart") val termStartDate: String = "",
    val file: String = "",
    val records: Int = 0,
    val classes: Int = 0,
    val source: String = "",
    /** 清洗日期，页面用它说明数据有多新。 */
    val generatedAt: String = "",
)

/**
 * 一个学期「按班级建独立课表」的资产：每个行政班一份自己的课表。
 *
 * 2026-11-06 起替代旧的按排课记录展开的 JSONL。原始表按课程组织、平行教学班
 * 共用同一批「上课班级」，App 按班过滤后同一格会并排十几条；改在清洗阶段展开
 * 班级区间（`26自动化[01-07]班` 是 7 个班）并把每个班组织成独立课表，
 * 平行教学班组在班内只留一条——教师全列（任选其一），地点留空。
 */
@Serializable
data class KcapTimetables(
    val id: String = "",
    val classes: List<KcapClass> = emptyList(),
)

@Serializable
data class KcapClass(
    val name: String,
    /** 归属学院（对照教务处专业设置表，清洗阶段分好）；空串 = 未分类（教学班号类噪声班名）。 */
    val college: String = "",
    val lessons: List<KcapLesson> = emptyList(),
)

@Serializable
data class KcapLesson(
    val id: String,
    val name: String = "",
    val code: String = "",
    /** 平行教学班组里把组内教师都列上：学生任选其一，总表没写归属。 */
    val teachers: String = "",
    val dept: String = "",
    val campus: String = "",
    val kind: String = "",
    /** 1 = 周一。 */
    val day: Int = 0,
    /** 小节号，`[3, 4]`。 */
    val periods: List<Int> = emptyList(),
    /** 周次，区间或明细混用：`1-16`、`1,3,5`、`1-6,15-16`。 */
    val weeks: String = "",
    /** 平行教学班组留空，其余为该班实际上课地点。 */
    val room: String = "",
    val credits: Double = 0.0,
    val hours: Int = 0,
)
