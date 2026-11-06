package qdu.campus.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.data.local.db.CampusDatabase
import qdu.campus.data.remote.github.isNewerVersion
import qdu.campus.domain.model.AppearanceMode
import qdu.campus.domain.model.AppearanceScheme
import qdu.campus.domain.model.SessionState
import qdu.campus.domain.repository.NoticeRepository
import qdu.campus.domain.repository.SessionRepository
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.platform.common.CrashLogger
import qdu.campus.platform.system.UpdateFetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val session: SessionState = SessionState.Unknown,
    val scheme: AppearanceScheme = AppearanceScheme.Indigo,
    val mode: AppearanceMode = AppearanceMode.SYSTEM,
    val message: String? = null,
    val update: UpdateCheckState = UpdateCheckState.Idle,
)

/** 检查更新的状态机：由「检查更新」行触发，结果回 UI 决定提示还是弹窗。 */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState

    /** 远端不比本机新：仓库没有 release、tag 更旧或相同。 */
    data object Latest : UpdateCheckState

    /** 检查失败（网络不通、限流、解析失败）。 */
    data object Failed : UpdateCheckState

    /** 远端有更新：带上版本号、发布说明与 Release 页链接。 */
    data class Available(
        val version: String,
        val notes: String,
        val url: String,
    ) : UpdateCheckState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settings: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val noticeRepository: NoticeRepository,
    private val database: CampusDatabase,
    private val updateFetcher: UpdateFetcher,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collect {
                _state.value = _state.value.copy(session = it)
            }
        }
        viewModelScope.launch {
            settings.appearanceScheme.collect {
                _state.value = _state.value.copy(scheme = it)
            }
        }
        viewModelScope.launch {
            settings.appearanceMode.collect {
                _state.value = _state.value.copy(mode = it)
            }
        }
    }

    fun clearTimetable() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dao = database.timetableDao()
                dao.clearCourses()
                dao.clearNoScheduleAll()
                dao.clearSectionsAll()
                dao.clearTerms()
                // 本科课表的课程是「班级勾选 + 随包资产」实时推导的，不落 Room；
                // 勾选与学期、层次不清，Room 清完课表立刻原样回来。
                settings.clearTimetableData()
            }
            _state.value = _state.value.copy(message = "课表缓存已清除")
        }
    }

    fun clearNotices() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                noticeRepository.clear()
            }
            _state.value = _state.value.copy(message = "通知记录已清除")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 顺序：凭据 → WebView 存储 → 数据库 → 设置 → 日志
                sessionRepository.clearAll()
                noticeRepository.clear()
                database.clearAllTables()
                settings.clearAllData()
                CrashLogger.clear(appContext)
            }
            _state.value = _state.value.copy(message = "全部本地数据已清除")
        }
    }

    fun showMessage(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun dismissUpdate() {
        _state.value = _state.value.copy(update = UpdateCheckState.Idle)
    }

    /**
     * 检查更新：拉 GitHub releases/latest 与本机版本号比较。
     * 仓库还没有 release 视同已是最新；无更新与失败给轻提示，有更新弹窗带发布说明与 Release 页入口。
     * 只由用户手动触发，不做后台轮询，也不自动下载安装包。
     */
    fun checkUpdate() {
        if (_state.value.update is UpdateCheckState.Checking) return
        _state.value = _state.value.copy(update = UpdateCheckState.Checking)
        viewModelScope.launch {
            val current = appVersionLabel()
            val outcome = withContext(Dispatchers.IO) { updateFetcher.fetchLatest() }
            _state.value = _state.value.copy(
                update = when (outcome) {
                    UpdateFetcher.Outcome.NoRelease -> UpdateCheckState.Latest
                    UpdateFetcher.Outcome.Failed -> UpdateCheckState.Failed
                    is UpdateFetcher.Outcome.Success ->
                        if (!isNewerVersion(current, outcome.release.version)) {
                            UpdateCheckState.Latest
                        } else {
                            UpdateCheckState.Available(
                                outcome.release.version,
                                outcome.release.notes,
                                outcome.release.url,
                            )
                        }
                },
            )
        }
    }

    /** 只取 versionName 用于比较（比较只看主.次.修订与阶段，不带 code）。 */
    private fun appVersionLabel(): String =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "0.0.0"
}
