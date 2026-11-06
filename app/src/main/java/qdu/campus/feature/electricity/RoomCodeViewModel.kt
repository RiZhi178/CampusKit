package qdu.campus.feature.electricity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qdu.campus.data.local.assets.DormCatalogReader
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.domain.repository.ZzmpRepository
import qdu.campus.platform.common.RoomCodeRejectedException
import javax.inject.Inject

/**
 * 房间编号的绑定。
 *
 * 核对走充值页自己的查询链路（见 [ZzmpRepository.checkRoomCode]），编号不存在时立刻回
 * 「对应房间没有账户」，不用把用户送到网页端再被拒一次。2026-11-05 实测这一步不产生待支付单。
 *
 * 只有服务端的定论才拦住绑定：会话过期、连不上属于「这次没查成」，而编号是用户按楼栋
 * 规则自己算出来的，照常落本机，回宿舍电费页会再查一次——预校验是便利，不该成为阻塞点。
 *
 * 查到的账户信息不在这一页摆：绑定成功就退回宿舍电费页，由那一页负责显示与刷新。
 *
 * 楼栋候选来自随包的报修系统楼栋字典（[DormCatalogReader]，166 栋 / 8 校区），
 * 简称推不出来的楼栋照常给手填框。
 */
@HiltViewModel
class RoomCodeViewModel @Inject constructor(
    private val repository: ZzmpRepository,
    private val settings: SettingsRepository,
    catalogReader: DormCatalogReader,
) : ViewModel() {

    data class UiState(
        val checking: Boolean = false,
        /** 服务端判定「这个编号没有账户」的原文（已翻成人话）。null 表示没被拒。 */
        val rejected: String? = null,
        /** 随包楼栋字典；读不到就是空列表，页面退回纯手填。 */
        val catalog: List<CatalogCampus> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(catalog = catalogReader.campuses())
        }
    }

    /** 换了楼栋或房号就作废上一次的拒绝，免得旧提示挂在新编码下面。 */
    fun clear() {
        if (_state.value != UiState(catalog = _state.value.catalog)) {
            _state.value = _state.value.copy(rejected = null, checking = false)
        }
    }

    fun bind(code: String, onBound: () -> Unit) {
        if (_state.value.checking) return
        _state.value = _state.value.copy(checking = true)
        viewModelScope.launch {
            runCatching { repository.checkRoomCode(code) }
                .onSuccess {
                    settings.setDormElectricityCode(code)
                    onBound()
                }
                .onFailure { error ->
                    if (error is RoomCodeRejectedException) {
                        _state.value = UiState(rejected = error.message, catalog = _state.value.catalog)
                    } else {
                        settings.setDormElectricityCode(code)
                        onBound()
                    }
                }
        }
    }
}
