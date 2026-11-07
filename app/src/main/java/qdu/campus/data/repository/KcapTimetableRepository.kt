package qdu.campus.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import qdu.campus.data.local.db.TimetableDao
import qdu.campus.data.local.db.UgTimetableDao
import qdu.campus.data.local.db.toIntList
import qdu.campus.data.remote.jwc.KcapLesson
import qdu.campus.data.remote.jwc.KcapParser
import qdu.campus.data.remote.jwc.UgSections
import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.NoScheduleCourse
import qdu.campus.domain.model.SemesterOption
import qdu.campus.domain.model.SyncState
import qdu.campus.domain.model.TimetableLevel
import qdu.campus.domain.repository.RefreshOutcome
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.domain.repository.TimetableRepository
import qdu.campus.platform.common.Formats
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本科生课表：数据来自教务处课程总表的随包资产，启动时由 [qdu.campus.data.local.seed.AssetSeeder]
 * 种进 Room，这里只查库，不连任何服务器，也不再直接读 assets。
 *
 * 因此这里没有「同步」可言——refresh() 只确认库里确实有这个学期。
 * 课程归属由用户勾选的班级决定，勾选变了课表立刻跟着变，不需要再拉一次。
 * 种子还没跑完时查出来是空表：页面显示空态，种子完成后重进即可，不用报错。
 */
@Singleton
class KcapTimetableRepository @Inject constructor(
    private val ugTimetableDao: UgTimetableDao,
    private val timetableDao: TimetableDao,
    private val settings: SettingsRepository,
) : TimetableRepository {

    override fun observeCourses(semesterId: String): Flow<List<Course>> =
        settings.undergraduateClasses
            .map { chosen -> buildCourses(semesterId, chosen) }
            .flowOn(Dispatchers.IO)

    override fun observeSections(termId: String): Flow<List<ClassSection>> = flow {
        // 作息按「今天」定档：5 月 1 日与 10 月 1 日整体挪半小时，是学校的安排。
        // 一学期中途换档要等到重新进入课表才生效，觉得时间不对可在「学期与节次」里逐节改。
        emit(UgSections.sections(UgSections.seasonOf(Formats.today())))
    }

    /** 本科没有「未排课」这一说，课程总表里没有时间的实践环节本来就不进课表。 */
    override fun observeNoSchedule(semesterId: String): Flow<List<NoScheduleCourse>> = flow {
        emit(emptyList())
    }

    override fun observeSyncState(): Flow<SyncState?> =
        settings.undergraduateAppliedAt.map { appliedAt ->
            appliedAt?.let { SyncState(SOURCE, it, null) }
        }

    override fun observeLatestTerm(): Flow<String?> = flow {
        emit(runCatching { ugTimetableDao.latestSemester() }.getOrNull())
    }.flowOn(Dispatchers.IO)

    /** 可选学期 = ug_timetable_entries 里实际有数据的学期，名称与起始日来自共用的 terms 表。 */
    override fun observeSemesterOptions(): Flow<List<SemesterOption>> = flow {
        emit(
            runCatching {
                ugTimetableDao.semesterMeta().map { meta ->
                    SemesterOption(
                        id = meta.id,
                        label = meta.name?.ifBlank { null } ?: meta.id,
                        termStart = meta.startDate.orEmpty(),
                    )
                }
            }.getOrDefault(emptyList()),
        )
    }.flowOn(Dispatchers.IO)

    /**
     * 「刷新」对随包发布的表只是确认库里有这个学期。
     *
     * 设置里的学期标识在刚装完时是空的、也可能还是研究生那一路留下的，
     * 本科跟着库走：认不出来就取最新一份，别把这种正常情况报成失败。
     */
    override suspend fun refresh(): RefreshOutcome = try {
        val count = ugTimetableDao.count(resolveSemester(settings.semesterId.first()))
        if (count == 0) {
            RefreshOutcome.Failure("课程总表还没有写入本地数据库，稍后重进课表页")
        } else {
            RefreshOutcome.Success(count)
        }
    } catch (e: Exception) {
        RefreshOutcome.Failure("本科课表读取失败：${e.message ?: "本地数据库打不开"}")
    }

    private suspend fun buildCourses(semesterId: String, chosen: Set<String>): List<Course> {
        if (chosen.isEmpty()) return emptyList()
        val resolved = resolveSemester(semesterId)
        val season = UgSections.seasonOf(Formats.today())
        // 库是「按班级建独立课表」：选中的班各取自己那份，跨班合上的同一条课（合班同教师同教室）
        // id 相同，去重后只显示一次。
        return ugTimetableDao.loadByClasses(resolved, chosen)
            .map { entity -> entity.className to entity.toLesson() }
            .mapNotNull { (className, lesson) -> KcapParser.toCourse(lesson, className, resolved, season) }
            .distinctBy { it.id }
    }

    /** 页面给的学期标识在表里不存在（比如刚从研究生那边切过来）时，取最新一份表。 */
    suspend fun semesterOption(semesterId: String): SemesterOption? {
        val metas = runCatching { ugTimetableDao.semesterMeta() }.getOrDefault(emptyList())
        return (metas.firstOrNull { it.id == semesterId } ?: metas.firstOrNull())
            ?.let { SemesterOption(it.id, it.name?.ifBlank { null } ?: it.id, it.startDate.orEmpty()) }
    }

    /**
     * 勾选框上的班级列表。全校一千多个班，页面靠搜索框定位，这里只保证顺序稳定。
     */
    suspend fun classNames(semesterId: String): List<String> =
        runCatching { ugTimetableDao.classNames(resolveSemester(semesterId)) }.getOrDefault(emptyList())

    /** 这些班里一共有几节课，用于「已选 N 个班，共 M 节课」的即时反馈。 */
    suspend fun courseCount(semesterId: String, chosen: Set<String>): Int =
        buildCourses(semesterId, chosen).size

    /**
     * 应用勾选：写入班级、学期与学期起始日，并把课表来源切到本科。
     *
     * 学期与起始日随种子进 terms 表——那是清洗时按校历填好的，
     * 不该让用户再照着一份 Excel 手输一遍。
     */
    suspend fun applyClasses(semesterId: String, classes: Set<String>) {
        val resolved = resolveSemester(semesterId)
        settings.setUndergraduateClasses(classes)
        settings.setSemesterId(resolved)
        semesterOption(resolved)?.termStart
            ?.takeIf { it.isNotBlank() }
            ?.let { settings.setTermStartDate(it) }
        settings.setTimetableLevel(TimetableLevel.Undergraduate)
    }

    /** 页面给的学期标识可能是研究生那套（或从没选过）。课程总表里没有就取最新一份。 */
    private suspend fun resolveSemester(semesterId: String): String =
        if (ugTimetableDao.count(semesterId) > 0) semesterId
        else ugTimetableDao.latestSemester() ?: semesterId

    /** 库里的行按 core 资产格式拼回，解析与「assets 直读」走同一套代码。 */
    private fun qdu.campus.data.local.db.UgTimetableEntryEntity.toLesson(): KcapLesson =
        KcapLesson(
            id = id,
            name = name,
            code = code,
            teachers = teachers,
            dept = dept,
            campus = campus,
            kind = kind,
            day = day,
            periods = periods.toIntList(),
            weeks = weeks,
            room = room,
            credits = credits,
            hours = hours,
        )

    private companion object {
        const val SOURCE = "kcap_timetable"
    }
}
