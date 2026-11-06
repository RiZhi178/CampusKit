package qdu.campus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.domain.model.AppearanceMode
import qdu.campus.domain.model.AppearanceScheme
import qdu.campus.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 外观的三个设置项。首帧就给默认值，避免主题在冷启动时跳一次。 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {

    val scheme: StateFlow<AppearanceScheme> = settings.appearanceScheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceScheme.Indigo)

    val mode: StateFlow<AppearanceMode> = settings.appearanceMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceMode.SYSTEM)

    val darkTinted: StateFlow<Boolean> = settings.darkTinted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
