package qdu.campus.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 起始路由。只在进程起来时读一次「引导是否走完」，
 * 之后不再跟随这个值变化：引导页确认时也会写这个键，
 * 若继续跟随会把 NavHost 重建，把用户从登录页弹回首页。
 */
@HiltViewModel
class StartupViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            _startDestination.value =
                if (settings.onboardingDone.first()) Routes.HOME else Routes.ONBOARDING
        }
    }
}
