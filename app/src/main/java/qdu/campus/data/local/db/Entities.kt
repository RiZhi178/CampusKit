package qdu.campus.data.local.db

import androidx.room.Entity
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

/** 纯 Kotlin 的逗号列表转换，避免给 Room 增加 TypeConverter。 */
internal fun String.toIntList(): List<Int> =
    split(',').mapNotNull { it.trim().toIntOrNull() }

internal fun String.toIntSet(): Set<Int> = toIntList().toSet()

internal fun List<Int>.joinToText(): String = joinToString(",")

internal fun Set<Int>.joinToText(): String = sorted().joinToString(",")
