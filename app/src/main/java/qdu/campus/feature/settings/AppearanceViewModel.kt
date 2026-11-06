package qdu.campus.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.domain.model.AppearanceMode
import qdu.campus.domain.model.AppearanceScheme
import qdu.campus.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    val scheme: StateFlow<AppearanceScheme> = settings.appearanceScheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceScheme.Indigo)

    val mode: StateFlow<AppearanceMode> = settings.appearanceMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceMode.SYSTEM)

    val darkTinted: StateFlow<Boolean> = settings.darkTinted
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun selectScheme(value: AppearanceScheme) {
        viewModelScope.launch { settings.setAppearanceScheme(value) }
    }

    fun selectMode(value: AppearanceMode) {
        viewModelScope.launch { settings.setAppearanceMode(value) }
    }

    fun setDarkTinted(value: Boolean) {
        viewModelScope.launch { settings.setDarkTinted(value) }
    }
}
