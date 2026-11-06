package qdu.campus.feature.baoxiu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qdu.campus.data.remote.baoxiu.BaoxiuAuthException
import qdu.campus.domain.model.RepairDraft
import qdu.campus.domain.model.RepairOption
import qdu.campus.domain.repository.BaoxiuRepository
import qdu.campus.platform.common.RemoteDataException
import javax.inject.Inject

@HiltViewModel
class BaoxiuFormViewModel @Inject constructor(
    private val repository: BaoxiuRepository,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val campuses: List<RepairOption> = emptyList(),
        val buildings: List<RepairOption> = emptyList(),
        val groups: List<RepairOption> = emptyList(),
        val items: List<RepairOption> = emptyList(),
        val draft: RepairDraft = RepairDraft(),
        val photoUri: String? = null,
        val submitting: Boolean = false,
        val error: String? = null,
        val loginRequired: Boolean = false,
        /** 提交成功：页面据此退回列表并提示。 */
        val done: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var photoBytes: ByteArray? = null
    private var photoName: String = "photo.jpg"
    private var photoMime: String = "image/jpeg"

    init {
        loadOptions()
    }

    fun loadOptions() {
        _state.value = _state.value.copy(loading = true, error = null, loginRequired = false)
        viewModelScope.launch {
            runCatching {
                val campuses = repository.campuses()
                val groups = repository.groups()
                // 楼栋必须在选定校区之后才有意义，先给空列表。
                Triple(campuses, groups, repository.lastDraft())
            }.onSuccess { (campuses, groups, last) ->
                val draft = last?.takeIf { it.campusId.isNotBlank() } ?: RepairDraft()
                _state.value = _state.value.copy(
                    loading = false,
                    campuses = campuses,
                    groups = groups,
                    draft = draft,
                )
                if (draft.campusId.isNotBlank()) loadBuildings(draft.campusId, keepSelection = true)
            }.onFailure { t ->
                val auth = t is BaoxiuAuthException
                _state.value = _state.value.copy(
                    loading = false,
                    loginRequired = auth,
                    error = if (auth) null else (t as? RemoteDataException)?.message ?: "表单选项加载失败",
                )
            }
        }
    }

    fun selectCampus(option: RepairOption) {
        _state.value = _state.value.copy(
            draft = _state.value.draft.copy(
                campusId = option.id,
                campusName = option.label,
                buildingId = "",
                buildingName = "",
            ),
            buildings = emptyList(),
        )
        loadBuildings(option.id, keepSelection = false)
    }

    private fun loadBuildings(campusId: String, keepSelection: Boolean) {
        viewModelScope.launch {
            runCatching { repository.buildings(campusId) }
                .onSuccess { list ->
                    val draft = _state.value.draft
                    val stillValid = keepSelection && list.any { it.id == draft.buildingId }
                    _state.value = _state.value.copy(
                        buildings = list,
                        draft = if (stillValid) draft else draft.copy(buildingId = "", buildingName = ""),
                    )
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        error = (t as? RemoteDataException)?.message ?: "楼栋列表加载失败",
                    )
                }
        }
    }

    fun selectBuilding(option: RepairOption) {
        _state.value = _state.value.copy(
            draft = _state.value.draft.copy(buildingId = option.id, buildingName = option.label),
        )
    }

    fun selectGroup(option: RepairOption) {
        _state.value = _state.value.copy(
            draft = _state.value.draft.copy(groupId = option.id, groupName = option.label, itemId = "", itemName = ""),
            items = emptyList(),
        )
        viewModelScope.launch {
            runCatching { repository.items(option.id) }
                .onSuccess { list -> _state.value = _state.value.copy(items = list) }
                .onFailure { t ->
                    _state.value = _state.value.copy(
                        error = (t as? RemoteDataException)?.message ?: "事项目录加载失败",
                    )
                }
        }
    }

    fun selectItem(option: RepairOption) {
        _state.value = _state.value.copy(
            draft = _state.value.draft.copy(itemId = option.id, itemName = option.label),
        )
    }

    fun setAddress(value: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(detailAddress = value))
    }

    fun setDesc(value: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(desc = value))
    }

    fun setPhone(value: String) {
        _state.value = _state.value.copy(draft = _state.value.draft.copy(phone = value))
    }

    fun attachPhoto(uri: String, bytes: ByteArray, name: String, mime: String) {
        photoBytes = bytes
        photoName = name
        photoMime = mime
        _state.value = _state.value.copy(photoUri = uri, error = null)
    }

    fun clearPhoto() {
        photoBytes = null
        _state.value = _state.value.copy(photoUri = null)
    }

    fun submit() {
        val s = _state.value
        if (s.submitting) return
        val draft = s.draft

        val problem = when {
            draft.campusId.isBlank() -> "请选择校区"
            draft.buildingId.isBlank() -> "请选择楼栋"
            draft.detailAddress.isBlank() -> "请填写详细地址"
            draft.groupId.isBlank() -> "请选择事项分组"
            draft.itemId.isBlank() -> "请选择事项"
            draft.desc.isBlank() -> "请填写报修描述"
            photoBytes == null -> "请上传报修图片"
            // 站点对联系方式做 11 位手机号校验，填别的一律回「手机号格式不正确」，本地先拦一层。
            !PHONE_PATTERN.matches(draft.phone.trim()) -> "手机号格式不正确"
            else -> null
        }
        if (problem != null) {
            _state.value = s.copy(error = problem)
            return
        }

        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            runCatching {
                val bytes = photoBytes ?: error("图片丢失")
                val url = repository.uploadPhoto(bytes, photoName, photoMime)
                repository.submit(draft.copy(photoUrl = url, phone = draft.phone.trim()))
            }.onSuccess {
                _state.value = _state.value.copy(submitting = false, done = true)
            }.onFailure { t ->
                _state.value = _state.value.copy(
                    submitting = false,
                    loginRequired = t is BaoxiuAuthException,
                    error = if (t is BaoxiuAuthException) null
                    else (t as? RemoteDataException)?.message ?: "提交失败，请稍后重试",
                )
            }
        }
    }

    fun consumeError() {
        _state.value = _state.value.copy(error = null)
    }

    private companion object {
        /** 网页端的校验口径就是 11 位手机号。 */
        val PHONE_PATTERN = Regex("^1[3-9]\\d{9}$")
    }
}
