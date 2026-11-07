package qdu.campus.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "terms")
data class TermEntity(
    @PrimaryKey val id: String,
    val name: String,
    val startDate: String? = null,
)

@Entity(tableName = "sections", primaryKeys = ["termId", "sectionIndex"])
data class SectionEntity(
    val termId: String,
    val sectionIndex: Int,
    val startTime: String,
    val endTime: String,
    val period: String,
    val label: String,
    /** 逗号分隔的小节号，例如 "1,2" */
    val covers: String = "",
)

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val name: String,
    val code: String,
    val classNo: String,
    val teachers: String,
    val room: String,
    val campus: String,
    val day: Int,
    /** 逗号分隔的小节号，例如 "3,4" */
    val sections: String,
    /** 逗号分隔的教学周，例如 "3,4,5,6" */
    val weeks: String,
    val startTime: String,
    val endTime: String,
    val category: String,
    val hours: Int,
    val credits: Int,
    val department: String,
    val status: String,
)

@Entity(tableName = "no_schedule_courses")
data class NoScheduleCourseEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val name: String,
    val credits: Int,
    val department: String,
    val teachers: String,
    val category: String,
)

@Entity(tableName = "notices")
data class NoticeEntity(
    @PrimaryKey val fingerprint: String,
    val title: String,
    val date: String,
    val column: String,
    val url: String,
    val read: Boolean,
    /** 数据来源：`grad_notice`（研究生院三栏）或 `jwc_notice`（教务处教务通知）。 */
    val source: String,
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val source: String,
    val lastSuccessAt: Long?,
    val message: String?,
)

/**
 * 本科生班级课表，一行 = 一个班的一节课（assets 里 kcap 资产启动种子后的落库形态）。
 *
 * 字段与 core 的 [qdu.campus.data.remote.jwc.KcapLesson] 一一对应，读取端拼回
 * KcapLesson 走同一套 `KcapParser.toCourse`，保证「assets 直读」与「Room 直读」
 * 产出完全相同的领域对象。`periods` / `weeks` 存原串（`3,4` / `1-16`），解析交给 core。
 * id 是内容哈希（含班级维度），同班内天然唯一，重建资产后 id 不变。
 */
@Entity(
    tableName = "ug_timetable_entries",
    indices = [Index(value = ["semesterId", "className"])],
)
data class UgTimetableEntryEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val className: String,
    val name: String,
    val code: String,
    /** 平行教学班组里把组内教师都列上：学生任选其一。 */
    val teachers: String,
    val dept: String,
    val campus: String,
    val kind: String,
    /** 1 = 周一。 */
    val day: Int,
    /** 小节号逗号串，`3,4`。 */
    val periods: String,
    /** 周次串，`1-16`、`1,3,5`、`1-6,15-16`。 */
    val weeks: String,
    /** 平行教学班组留空。 */
    val room: String,
    val credits: Double,
    val hours: Int,
)

/** 电费房间码用的校区-楼栋字典（assets 里 zzmp/buildings.json 种子后的落库形态）。 */
@Entity(tableName = "dorm_buildings")
data class DormBuildingEntity(
    @PrimaryKey val buildingId: String,
    val campusId: String,
    val campus: String,
    val name: String,
    /** 财务处口径的楼简称（浩1 / 浏C / 溆2），推不出来时为 null。 */
    val code: String?,
)

/**
 * 校历调停课的日级规则（assets 里 calendar 资产种子后的落库形态）。
 *
 * 一天一条：假期日当天课全部停上；调休日执行 [followsDate] 那天的教学安排
 * （配对来自教务处通知，如 2026-09-20 执行 2026-10-06 的安排——借的是
 * 10 月 6 日所在教学周的周二课，不是 9 月 20 日所在周的）。
 * 课表原表（sections / courses / ug_timetable_entries）一概不动。
 */
@Entity(tableName = "calendar_days")
data class CalendarDayEntity(
    /** ISO 日期，`2026-10-01`。 */
    @PrimaryKey val date: String,
    /** 校学年标识，`2026-2027`。 */
    val academicYear: String,
    /** `HOLIDAY`（放假）或 `MAKEUP`（调休上课）。 */
    val kind: String,
    val name: String,
    /** 调休日执行哪一天的教学安排；假期日为 null。 */
    val followsDate: String?,
)

/** 纯 Kotlin 的逗号列表转换，避免给 Room 增加 TypeConverter。 */
internal fun String.toIntList(): List<Int> =
    split(',').mapNotNull { it.trim().toIntOrNull() }

internal fun String.toIntSet(): Set<Int> = toIntList().toSet()

internal fun List<Int>.joinToText(): String = joinToString(",")

internal fun Set<Int>.joinToText(): String = sorted().joinToString(",")
