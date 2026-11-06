package qdu.campus.feature.scan

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 扫码页状态。识别一旦出结果就暂停，防止连续帧把同一个码反复上屏；
 * 「继续扫码」清掉结果重新开始。手电筒与相机控制放在 UI 层，这里只存开关值。
 */
@HiltViewModel
class ScanViewModel @Inject constructor() : ViewModel() {

    data class UiState(
        /** 已识别的结果；null 表示还在取景。 */
        val result: QrContent? = null,
        val torchOn: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onScanResult(raw: String) {
        if (_state.value.result != null) return
        _state.value = _state.value.copy(result = QrContentClassifier.classify(raw))
    }

    fun resumeScan() {
        _state.value = _state.value.copy(result = null)
    }

    fun toggleTorch() {
        _state.value = _state.value.copy(torchOn = !_state.value.torchOn)
    }
}
