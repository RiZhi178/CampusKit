package qdu.campus.feature.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.platform.web.WebSessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WebViewModel @Inject constructor(
    private val coordinator: WebSessionCoordinator,
) : ViewModel() {

    /**
     * 门禁看的是统一认证的 TGT（`CASTGC`），不是 authserver 有没有 Cookie：
     * 未登录时访问认证页同样会种下 JSESSIONID，按域判非空会把门禁直接放行。
     */
    private val _casAuthenticated = MutableStateFlow(hasCasSession())
    val casAuthenticated: StateFlow<Boolean> = _casAuthenticated.asStateFlow()

    /** 从登录页返回时重新读一次 CookieManager，登录成功立即解除门禁。 */
    fun refreshCasSession() {
        _casAuthenticated.value = hasCasSession()
    }

    private fun hasCasSession(): Boolean = coordinator.hasCasTicket()

    /** 页面加载完成后回收 Cookie，供原生请求复用。 */
    fun persistCookies() {
        viewModelScope.launch {
            coordinator.saveFromWebView()
            refreshCasSession()
        }
    }
}
