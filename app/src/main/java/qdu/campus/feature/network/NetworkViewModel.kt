package qdu.campus.feature.network

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import qdu.campus.domain.model.CampusNetworkEnvironment
import qdu.campus.platform.system.NetworkEnvironmentReader
import javax.inject.Inject

data class NetworkUiState(
    val environment: CampusNetworkEnvironment = CampusNetworkEnvironment(),
)

/**
 * 只读网络环境，不发请求、不认证。
 *
 * 读一次是同步的系统调用，但挂在 ViewModel 上是为了回到前台、从 Wi-Fi 面板返回时能重读，
 * 而不是把检测塞进 Composable 的重组里反复执行。
 */
@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val reader: NetworkEnvironmentReader,
) : ViewModel() {

    private val _state = MutableStateFlow(NetworkUiState())
    val state: StateFlow<NetworkUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = NetworkUiState(reader.current())
    }
}
