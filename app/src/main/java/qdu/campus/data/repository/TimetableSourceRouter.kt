package qdu.campus.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.NoScheduleCourse
import qdu.campus.domain.model.SemesterOption
import qdu.campus.domain.model.SyncState
import qdu.campus.domain.model.TimetableLevel
import qdu.campus.domain.repository.RefreshOutcome
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.domain.repository.TimetableRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 课表按层次分流：研究生走研究生系统同步，本科读随包发布的课程总表。
 *
 * 页面注入的还是同一个 TimetableRepository，切换层次不动页面代码；
 * 层次没变时另一路的流一次都不会被收集，不会白读 Room。
 *
 * 唯一的例外是「研究生系统登录后同步课表」——那里要的是研究生课表本身，
 * 与用户此刻在看哪个层次无关，所以直接注入 [TimetableRepositoryImpl]。
 * 首页与前台任务要跟着用户看的层次走，继续用这个接口。
 */
@Singleton
class TimetableSourceRouter @Inject constructor(
    private val graduate: TimetableRepositoryImpl,
    private val undergraduate: KcapTimetableRepository,
    private val settings: SettingsRepository,
) : TimetableRepository {

    override fun observeCourses(semesterId: String): Flow<List<Course>> =
        route { observeCourses(semesterId) }

    override fun observeSections(termId: String): Flow<List<ClassSection>> =
        route { observeSections(termId) }

    override fun observeNoSchedule(semesterId: String): Flow<List<NoScheduleCourse>> =
        route { observeNoSchedule(semesterId) }

    override fun observeSyncState(): Flow<SyncState?> = route { observeSyncState() }

    override fun observeLatestTerm(): Flow<String?> = route { observeLatestTerm() }

    override fun observeSemesterOptions(): Flow<List<SemesterOption>> = route { observeSemesterOptions() }

    override suspend fun refresh(): RefreshOutcome =
        if (settings.timetableLevel.first() == TimetableLevel.Undergraduate) {
            undergraduate.refresh()
        } else {
            graduate.refresh()
        }

    private fun <T> route(select: TimetableRepository.() -> Flow<T>): Flow<T> =
        settings.timetableLevel
            .map { level ->
                if (level == TimetableLevel.Undergraduate) undergraduate else graduate
            }
            .flatMapLatest { source -> source.select() }
}
