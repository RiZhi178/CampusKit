package qdu.campus.data.local.seed

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qdu.campus.data.local.db.CalendarDao
import qdu.campus.data.local.db.CalendarDayEntity
import qdu.campus.data.local.db.CampusDatabase
import qdu.campus.data.local.db.DormBuildingDao
import qdu.campus.data.local.db.DormBuildingEntity
import qdu.campus.data.local.db.SyncDao
import qdu.campus.data.local.db.SyncMetadataEntity
import qdu.campus.data.local.db.TermEntity
import qdu.campus.data.local.db.UgTimetableDao
import qdu.campus.data.local.db.UgTimetableEntryEntity
import qdu.campus.feature.electricity.BuildingCatalog
import qdu.campus.data.remote.jwc.KcapParser
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 随包资产 -> Room 的启动种子。
 *
 * assets 里的 kcap 课表、zzmp 楼栋字典、校历规则不再被页面直接消费：
 * 启动时在这里解析进 Room，页面一律查库。assets 文件降级为「种子的源」，
 * 与 APK 里其余资源一样只随包更新。
 *
 * 重建判据是资产字节的 SHA-256，记在 sync_metadata 里：文件没变就跳过，
 * 变了（新学期、字典重采集、校历修订）才整学期重写。种子失败不抛出——
 * 页面按「还没有数据」处理，下次启动重试，不能因为种子失败把启动流程拖崩。
 */
@Singleton
class AssetSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: CampusDatabase,
    private val ugTimetableDao: UgTimetableDao,
    private val dormBuildingDao: DormBuildingDao,
    private val calendarDao: CalendarDao,
    private val syncDao: SyncDao,
) {
    suspend fun seedAll() {
        runCatching { seedUgTimetables() }
            .onFailure { android.util.Log.w(TAG, "本科课表种子失败", it) }
        runCatching { seedDormBuildings() }
            .onFailure { android.util.Log.w(TAG, "楼栋字典种子失败", it) }
        runCatching { seedCalendar() }
            .onFailure { android.util.Log.w(TAG, "校历种子失败", it) }
    }

    /** kcap 按班级课表：一学期一文件，指纹变了整学期重建；学期元数据顺带补进 terms。 */
    private suspend fun seedUgTimetables() = withContext(Dispatchers.IO) {
        val index = KcapParser.parseIndex(readAsset("kcap/index.json"))
        for (semester in index) {
            if (semester.file.isBlank()) continue
            val bytes = readAssetBytes("kcap/${semester.file}")
            val fingerprint = sha256(bytes)
            if (syncDao.observeOnce("seed:kcap:${semester.id}")?.message == fingerprint) continue

            val timetables = KcapParser.parseTimetables(bytes.decodeToString()) ?: continue
            val rows = timetables.classes.flatMap { cls ->
                cls.lessons.map { lesson ->
                    UgTimetableEntryEntity(
                        id = lesson.id,
                        semesterId = semester.id,
                        className = cls.name,
                        name = lesson.name,
                        code = lesson.code,
                        teachers = lesson.teachers,
                        dept = lesson.dept,
                        campus = lesson.campus,
                        kind = lesson.kind,
                        day = lesson.day,
                        periods = lesson.periods.joinToString(","),
                        weeks = lesson.weeks,
                        room = lesson.room,
                        credits = lesson.credits,
                        hours = lesson.hours,
                    )
                }
            }
            db.withTransaction {
                ugTimetableDao.deleteBySemester(semester.id)
                ugTimetableDao.insertAll(rows)
                // terms 是本科与研究生共用的学期清单；已在（研究生一路先写）就不动
                if (db.timetableDao().loadTerm(semester.id) == null && semester.termStartDate.isNotBlank()) {
                    db.timetableDao().upsertTerm(
                        TermEntity(id = semester.id, name = semester.label.ifBlank { semester.id }, startDate = semester.termStartDate),
                    )
                }
                syncDao.upsert(SyncMetadataEntity("seed:kcap:${semester.id}", System.currentTimeMillis(), fingerprint))
            }
        }
    }

    /** 电费楼栋字典：单文件全量替换。 */
    private suspend fun seedDormBuildings() = withContext(Dispatchers.IO) {
        val bytes = readAssetBytes("zzmp/buildings.json")
        val fingerprint = sha256(bytes)
        if (syncDao.observeOnce("seed:dorm")?.message == fingerprint) return@withContext
        val campuses = BuildingCatalog.parse(bytes.decodeToString())
        val rows = campuses.flatMap { campus ->
            campus.buildings.map { building ->
                DormBuildingEntity(
                    buildingId = building.buildingId,
                    campusId = campus.campusId,
                    campus = campus.campus,
                    name = building.name,
                    code = building.code,
                )
            }
        }
        db.withTransaction {
            dormBuildingDao.clear()
            dormBuildingDao.insertAll(rows)
            syncDao.upsert(SyncMetadataEntity("seed:dorm", System.currentTimeMillis(), fingerprint))
        }
    }

    /** 校历调停课规则：单文件全量替换（按学年清旧，再插新）。 */
    private suspend fun seedCalendar() = withContext(Dispatchers.IO) {
        val bytes = readAssetBytes("calendar/2026-2027.json")
        val fingerprint = sha256(bytes)
        if (syncDao.observeOnce("seed:calendar")?.message == fingerprint) return@withContext
        val dto = calendarJson.decodeFromString(CalendarAssetDto.serializer(), bytes.decodeToString())
        val rows = dto.days.map { day ->
            CalendarDayEntity(
                date = day.date,
                academicYear = dto.academicYear,
                kind = day.kind,
                name = day.name,
                followsDate = day.followsDate,
            )
        }
        db.withTransaction {
            calendarDao.deleteByAcademicYear(dto.academicYear)
            calendarDao.insertAll(rows)
            syncDao.upsert(SyncMetadataEntity("seed:calendar", System.currentTimeMillis(), fingerprint))
        }
    }

    private fun readAsset(path: String): String = readAssetBytes(path).decodeToString()

    private fun readAssetBytes(path: String): ByteArray =
        context.assets.open(path).use { it.readBytes() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "AssetSeeder"

        private val calendarJson = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

/** assets/calendar/<学年>.json 的种子格式，只保留规则需要的字段。 */
@kotlinx.serialization.Serializable
private data class CalendarAssetDto(
    val academicYear: String = "",
    val days: List<DayDto> = emptyList(),
) {
    @kotlinx.serialization.Serializable
    data class DayDto(
        val date: String = "",
        val kind: String = "",
        val name: String = "",
        val followsDate: String? = null,
    )
}
