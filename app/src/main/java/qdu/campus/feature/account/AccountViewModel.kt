package qdu.campus.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.domain.model.SessionState
import qdu.campus.domain.repository.ProfileRepository
import qdu.campus.domain.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val session: SessionState = SessionState.Unknown,
    val studentId: String? = null,
    /** 账号卡上的姓名；没同步到时为 null，卡片退回「已登录」。 */
    val userName: String? = null,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val profile: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.state.collect { session ->
                _state.value = _state.value.copy(
                    session = session,
                    studentId = sessionRepository.studentId(),
                    userName = profile.cachedName(),
                )
            }
        }
    }

    /** 退出登录：只清会话凭据，学号留着，下次统一认证仍然预填。 */
    fun logout() {
        viewModelScope.launch {
            sessionRepository.clearSession()
            _state.value = _state.value.copy(session = SessionState.Unknown)
        }
    }

    /** 切换账号：连学号一起清掉，回到引导页重新填。 */
    fun switchAccount(onReady: () -> Unit) {
        viewModelScope.launch {
            sessionRepository.clearAll()
            _state.value = AccountUiState()
            onReady()
        }
    }
}
