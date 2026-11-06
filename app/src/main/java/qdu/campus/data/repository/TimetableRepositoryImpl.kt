package qdu.campus.data.repository

import qdu.campus.platform.common.YjsxtAuthException
import qdu.campus.data.local.db.CourseEntity
import qdu.campus.data.local.db.NoScheduleCourseEntity
import qdu.campus.data.local.db.SyncDao
import qdu.campus.data.local.db.SyncMetadataEntity
import qdu.campus.data.local.db.TermEntity
import qdu.campus.data.local.db.TimetableDao
import qdu.campus.data.local.db.joinToText
import qdu.campus.data.local.db.toIntList
import qdu.campus.data.local.db.toIntSet
import qdu.campus.data.remote.yjsxt.CourseMapper
import qdu.campus.data.remote.yjsxt.YjsxtApi
import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.NoScheduleCourse
import qdu.campus.domain.model.SemesterOption
import qdu.campus.domain.model.SyncState
import qdu.campus.domain.repository.RefreshOutcome
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimetableRepositoryImpl @Inject constructor(
    private val dao: TimetableDao,
    private val syncDao: SyncDao,
    private val api: YjsxtApi,
    private val settings: SettingsRepository,
) : TimetableRepository {

    override fun observeCourses(semesterId: String): Flow<List<Course>> =
        dao.observeCourses(semesterId).map { list -> list.map(TimetableMapper::toCourse) }

    override fun observeSections(termId: String): Flow<List<ClassSection>> =
        dao.observeSections(termId).map { list -> list.map(TimetableMapper::toSection) }

    override fun observeNoSchedule(semesterId: String): Flow<List<NoScheduleCourse>> =
        dao.observeNoSchedule(semesterId)
            .map { list -> list.map(TimetableMapper::toNoSchedule) }

    override fun observeSyncState(): Flow<SyncState?> =
        syncDao.observe(SOURCE).map { entity ->
            entity?.let { SyncState(it.source, it.lastSuccessAt, it.message) }
        }

    override fun observeLatestTerm(): Flow<String?> = dao.observeLatestSemester()

    /**
     * 研究生这边能确定的只有「当前学期」这一个标识，它来自设置项；
     * 可供切换的候选学期是页面按标识规则推出来的，不在这条链上。
     */
    override fun observeSemesterOptions(): Flow<List<SemesterOption>> =
        settings.semesterId.map { id -> listOf(SemesterOption(id = id, label = id)) }

    override suspend fun refresh(): RefreshOutcome {
        val semesterId = settings.semesterId.first()
        val startDate = settings.termStartDate.first()
        return try {
            sync(semesterId, startDate)
        } catch (e: Exception) {
            failure(e)
        }
    }

    private suspend fun sync(semesterId: String, startDate: String): RefreshOutcome {
        val semester = api.fetchSemester(semesterId)
            ?: return RefreshOutcome.Failure("学期配置为空")

        val bigSectionId = semester.sections?.firstOrNull()?.let {
            it.bigSectionId ?: it.id
        }
        // pkgl002id 是后端的占位参数，传首个大节 ID 即可返回全量课表，只请求一次
        if (bigSectionId.isNullOrBlank()) {
            return RefreshOutcome.Failure("大节配置缺失，无法请求课表")
        }

        val courses = CourseMapper.toCourses(api.fetchCourses(semesterId, bigSectionId), semesterId)
        val noSchedule = runCatching { api.fetchNoSchedule(semesterId) }
            .getOrDefault(emptyList())
            .mapNotNull { CourseMapper.toNoSchedule(it, semesterId) }
        val sections = CourseMapper.toSections(semester)

        dao.replaceTimetable(
            semesterId = semesterId,
            term = TermEntity(
                id = semesterId,
                name = semester.name ?: semesterId,
                startDate = startDate.ifBlank { null },
            ),
            courses = courses.map(TimetableMapper::toEntity),
            noSchedule = noSchedule.map(TimetableMapper::toEntity),
            sections = sections.map {
                TimetableMapper.toEntity(semesterId, it)
            },
        )
        syncDao.upsert(SyncMetadataEntity(SOURCE, System.currentTimeMillis(), null))
        return RefreshOutcome.Success(courses.size)
    }

    /** 失败不改缓存，只把原因写进同步记录，页面据此提示「显示的是上次同步」。 */
    private suspend fun failure(error: Throwable): RefreshOutcome {
        val auth = error is YjsxtAuthException
        val message = when {
            // 研究生系统只认自己的登录态：统一认证的票换不过去（2026-11-05 实测，
            // 票据合法、CAS 也认，但它的 getToken 在校验前就回「认证失败」）。
            // 所以这里必须说清是「要单独登录」，不能再说统一认证过期。
            auth -> "课表同步失败：研究生系统需要单独登录"
            else -> error.message ?: "课表更新失败"
        }
        syncDao.upsert(SyncMetadataEntity(SOURCE, null, message))
        return RefreshOutcome.Failure(message, loginRequired = auth)
    }

    companion object {
        const val SOURCE = "yjsxt_timetable"
    }
}

/** Room 实体与领域模型之间的搬运，放在数据层，UI 与 domain 都不依赖实体。 */
internal object TimetableMapper {

    fun toCourse(entity: CourseEntity): Course = Course(
        id = entity.id,
        semesterId = entity.semesterId,
        name = entity.name,
        code = entity.code,
        classNo = entity.classNo,
        teachers = entity.teachers,
        room = entity.room,
        campus = entity.campus,
        day = entity.day,
        sections = entity.sections.toIntList(),
        weeks = entity.weeks.toIntSet(),
        startTime = entity.startTime,
        endTime = entity.endTime,
        category = entity.category,
        hours = entity.hours,
        credits = entity.credits.toDouble(),
        department = entity.department,
        status = entity.status,
    )

    fun toEntity(course: Course): CourseEntity = CourseEntity(
        id = course.id,
        semesterId = course.semesterId,
        name = course.name,
        code = course.code,
        classNo = course.classNo,
        teachers = course.teachers,
        room = course.room,
        campus = course.campus,
        day = course.day,
        sections = course.sections.joinToText(),
        weeks = course.weeks.joinToText(),
        startTime = course.startTime,
        endTime = course.endTime,
        category = course.category,
        hours = course.hours,
        // 研究生学分历来按整数存（DTO 里就是先取整再落库），列类型不动，避免为此跑一次表迁移。
        credits = course.credits.toInt(),
        department = course.department,
        status = course.status,
    )

    fun toSection(entity: qdu.campus.data.local.db.SectionEntity): ClassSection =
        ClassSection(
            index = entity.sectionIndex,
            startTime = entity.startTime,
            endTime = entity.endTime,
            period = entity.period,
            label = entity.label,
            covers = entity.covers.toIntList(),
        )

    fun toEntity(
        termId: String,
        section: ClassSection,
    ): qdu.campus.data.local.db.SectionEntity =
        qdu.campus.data.local.db.SectionEntity(
            termId = termId,
            sectionIndex = section.index,
            startTime = section.startTime,
            endTime = section.endTime,
            period = section.period,
            label = section.label,
            covers = section.covers.joinToText(),
        )

    fun toNoSchedule(entity: NoScheduleCourseEntity): NoScheduleCourse = NoScheduleCourse(
        id = entity.id,
        semesterId = entity.semesterId,
        name = entity.name,
        credits = entity.credits,
        department = entity.department,
        teachers = entity.teachers,
        category = entity.category,
    )

    fun toEntity(course: NoScheduleCourse): NoScheduleCourseEntity = NoScheduleCourseEntity(
        id = course.id,
        semesterId = course.semesterId,
        name = course.name,
        credits = course.credits,
        department = course.department,
        teachers = course.teachers,
        category = course.category,
    )
}
