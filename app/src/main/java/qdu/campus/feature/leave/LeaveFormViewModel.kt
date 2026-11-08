package qdu.campus.feature.leave

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import qdu.campus.data.remote.xg.XgFormValidator
import qdu.campus.domain.model.XgFieldError
import qdu.campus.domain.model.XgLeaveFormPage
import qdu.campus.domain.repository.XgRepository
import qdu.campus.platform.common.XgAuthException
import qdu.campus.platform.web.WebSessionCoordinator
import javax.inject.Inject

/**
 * 请假表单兼详情。`id` 为空是新建，带记录号是详情（只读 + 撤销）。
 *
 * 站点是整表单回发，控件清单就是请求体，所以这里把用户改动单独存进 [UiState.edits]，
 * 控件原值与服务端重渲染后的选项都留在 [UiState.page] 里。省市区三级联动靠回发重绑下级选项，
 * 但服务端不渲染 selected、值由页面脚本回填，原生读回来是空——因此用户选过的码必须以 edits 为准，
 * 不能信重渲染后的控件值。每次联动换一份新的 page（带新视图状态与下级选项），edits 原样保留。
 *
 * 提交前先按标签上的校验元数据本地拦一道（省一次往返），成败仍以服务端注入的 layer.alert
 * 加列表记录增减为准：成功才退回列表，被拒就把服务端那句话摆出来让人改。
 */
@HiltViewModel
class LeaveFormViewModel @Inject constructor(
    private val repository: XgRepository,
    private val coordinator: WebSessionCoordinator,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val recordId: String = savedStateHandle.get<String>("id").orEmpty()

    val isNew: Boolean get() = recordId.isEmpty()

    data class UiState(
        val loading: Boolean = true,
        val page: XgLeaveFormPage? = null,
        /** 用户改动，键是控件 name。显示与回发都按 edits 优先于控件原值。 */
        val edits: Map<String, String> = emptyMap(),
        /** 提交前列表里的记录号，用来核对是否真的多出一条。 */
        val existingIds: Set<String> = emptySet(),
        val blocked: Boolean = false,
        val blockedMessage: String = "",
        val submitting: Boolean = false,
        val cascading: Boolean = false,
        val revoking: Boolean = false,
        val error: String? = null,
        val needsLogin: Boolean = false,
        val message: String? = null,
        val messageOk: Boolean = false,
        val fieldErrors: List<XgFieldError> = emptyList(),
        /** 新建提交成功：页面据此退回列表。 */
        val done: Boolean = false,
        /** 详情撤销成功：页面据此退回列表。 */
        val revoked: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        coordinator.restorePersistedCookies(force = true)
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null, needsLogin = false) }
        viewModelScope.launch {
            if (isNew) {
                // 新建先读列表：拿在途互斥门禁，并记下提交前的记录基线
                runCatching { repository.leaveList() }
                    .onSuccess { list ->
                        if (!list.canApply) {
                            _state.update {
                                it.copy(
                                    loading = false,
                                    blocked = true,
                                    blockedMessage = list.blockedMessage.ifEmpty {
                                        "当前状态不能请假或有请假未完成！"
                                    },
                                    existingIds = list.recordIds,
                                )
                            }
                            return@launch
                        }
                        _state.update { it.copy(existingIds = list.recordIds) }
                        loadForm()
                    }
                    .onFailure { fail(it) }
            } else {
                loadForm()
            }
        }
    }

    private suspend fun loadForm() {
        runCatching { repository.leaveForm(recordId) }
            .onSuccess { page ->
                _state.update {
                    it.copy(
                        loading = false,
                        page = page,
                        blocked = page.blocked,
                        blockedMessage = if (page.blocked) "当前状态不能请假或有请假未完成！" else "",
                        edits = emptyMap(),
                        error = null,
                        needsLogin = false,
                    )
                }
            }
            .onFailure { fail(it) }
    }

    /** 文本、日期、单选都走这里改值；顺手清掉这个字段上一次的校验错。 */
    fun setValue(name: String, value: String) {
        _state.update { s ->
            s.copy(
                edits = s.edits + (name to value),
                fieldErrors = s.fieldErrors.filterNot { it.name == name },
                message = null,
            )
        }
    }

    /**
     * 下拉选择。省 / 市是联动项：选完回发重绑下级，并清掉已失效的下级选择；
     * 区是叶子，选完只改值。其余下拉（学期、外出范围）也只改值。
     */
    fun selectOption(name: String, value: String) {
        val page = _state.value.page ?: return
        if (page.form.control(name) == null) return
        val isProvince = name.endsWith("ProvinceDdl")
        val isCity = name.endsWith("CityDdl")
        if (!isProvince && !isCity) {
            setValue(name, value)
            return
        }
        if (_state.value.cascading) return
        val downstream = if (isProvince) listOf("CityDdl", "AreaDdl") else listOf("AreaDdl")
        val edits = (_state.value.edits + (name to value))
            .filterKeys { key -> downstream.none { key.endsWith(it) } }
        _state.update { it.copy(cascading = true, edits = edits, error = null, message = null) }
        viewModelScope.launch {
            runCatching { repository.cascadeArea(page, name, edits) }
                .onSuccess { refreshed ->
                    _state.update { it.copy(cascading = false, page = refreshed) }
                }
                .onFailure { t ->
                    if (t is XgAuthException) {
                        _state.update { it.copy(cascading = false, needsLogin = true) }
                    } else {
                        _state.update {
                            it.copy(cascading = false, error = t.message ?: "联动加载失败，请重试")
                        }
                    }
                }
        }
    }

    fun submit() {
        val s = _state.value
        val page = s.page ?: return
        if (s.submitting || s.cascading) return
        val errors = XgFormValidator.validate(page.form, page.form.values(s.edits))
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, error = errors.first().message, message = null) }
            return
        }
        _state.update { it.copy(submitting = true, error = null, fieldErrors = emptyList(), message = null) }
        viewModelScope.launch {
            runCatching { repository.submitLeave(page, s.edits, s.existingIds) }
                .onSuccess { result ->
                    if (result.ok) {
                        _state.update { it.copy(submitting = false, done = true) }
                    } else {
                        _state.update {
                            it.copy(
                                submitting = false,
                                existingIds = result.list.recordIds,
                                message = result.message.ifEmpty { "提交没有生效，请检查后重试" },
                                messageOk = false,
                            )
                        }
                    }
                }
                .onFailure { t ->
                    if (t is XgAuthException) {
                        _state.update { it.copy(submitting = false, needsLogin = true) }
                    } else {
                        _state.update {
                            it.copy(submitting = false, error = t.message ?: "提交失败，请稍后重试")
                        }
                    }
                }
        }
    }

    /** 详情内撤销。触发项是 DelSave=Button，成败以重读列表后该记录还在不在为准。 */
    fun revoke() {
        val page = _state.value.page ?: return
        if (_state.value.revoking) return
        _state.update { it.copy(revoking = true, error = null, message = null) }
        viewModelScope.launch {
            runCatching { repository.revokeLeaveDetail(page) }
                .onSuccess { result ->
                    if (result.ok) {
                        _state.update { it.copy(revoking = false, revoked = true) }
                    } else {
                        _state.update {
                            it.copy(
                                revoking = false,
                                message = result.message.ifEmpty { "撤销没有生效，请重试" },
                                messageOk = false,
                            )
                        }
                    }
                }
                .onFailure { t ->
                    if (t is XgAuthException) {
                        _state.update { it.copy(revoking = false, needsLogin = true) }
                    } else {
                        _state.update {
                            it.copy(revoking = false, error = t.message ?: "撤销失败，请稍后重试")
                        }
                    }
                }
        }
    }

    fun refreshOnReturn() {
        if (_state.value.needsLogin) load()
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun fail(t: Throwable) {
        if (t is XgAuthException) {
            _state.update { it.copy(loading = false, needsLogin = true, error = null) }
        } else {
            _state.update { it.copy(loading = false, error = t.message ?: "请假信息暂时取不到") }
        }
    }
}