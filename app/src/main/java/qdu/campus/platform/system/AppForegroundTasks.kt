package qdu.campus.platform.system

import qdu.campus.domain.model.SessionState
import qdu.campus.domain.repository.NoticeRepository
import qdu.campus.domain.repository.SessionRepository
import qdu.campus.domain.repository.TimetableRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 前台任务：冷启动/回到前台时真实校验一次会话，再刷新课表与通知；
 * 之后每 6 小时检查一次通知。自动检查固定开启，不提供开关。
 *
 * 由 Activity 的生命周期启动与取消，进程退到后台后不再计时，
 * 不做后台常驻轮询。
 */
@Singleton
class AppForegroundTasks @Inject constructor(
    private val session: SessionRepository,
    private val timetable: TimetableRepository,
    private val notices: NoticeRepository,
) {

    private var lastContentRefresh = 0L

    suspend fun run() {
        val state = session.validate()
        val now = System.currentTimeMillis()
        // 同一次进程里频繁切前台不重复整表刷新，手动下拉仍然随时可用。
        if (state == SessionState.Valid && now - lastContentRefresh > CONTENT_REFRESH_INTERVAL_MS) {
            lastContentRefresh = now
            timetable.refresh()
            notices.refresh()
        }
        while (currentCoroutineContext().isActive) {
            delay(CHECK_INTERVAL_MS)
            if (session.state.value == SessionState.Valid) {
                notices.refresh()
            }
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val CONTENT_REFRESH_INTERVAL_MS = 5 * 60 * 1000L
    }
}
