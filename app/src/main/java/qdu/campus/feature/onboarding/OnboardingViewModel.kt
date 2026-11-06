package qdu.campus.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    /**
     * 两条出口都只是「记下引导页看过了」再放行。
     *
     * 不在这里写学号：引导页已经不再收集学号。老版本存过的那份仍然保留，
     * 登录预填与账号卡的脱敏学号还能继续用，但不能用空值把它覆盖掉。
     */
    fun finish(onReady: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone(true)
            onReady()
        }
    }
}
