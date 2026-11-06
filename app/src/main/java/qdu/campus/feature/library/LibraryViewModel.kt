package qdu.campus.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.domain.model.LibraryHolding
import qdu.campus.domain.model.LibraryLoan
import qdu.campus.domain.model.LibraryReader
import qdu.campus.domain.model.LibraryRecord
import qdu.campus.domain.model.WebDomain
import qdu.campus.platform.common.LibraryAuthException
import qdu.campus.platform.common.RemoteDataException
import qdu.campus.domain.repository.LibraryRepository
import qdu.campus.platform.web.WebSessionCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 两个标签页。[ordinal] 直接喂给分段控件。 */
enum class LibraryTab { SEARCH, BORROWING }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.SEARCH,
    val query: String = "",
    val results: List<LibraryRecord> = emptyList(),
    val numFound: Int = 0,
    /** 已取到第几页；0 表示还没查过，页面据此决定显示提示还是结果。 */
    val page: Int = 0,
    val searching: Boolean = false,
    val canLoadMore: Boolean = false,
    val searchError: String? = null,

    val selected: LibraryRecord? = null,
    val holdings: List<LibraryHolding> = emptyList(),
    val holdingsLoading: Boolean = false,
    val holdingsError: String? = null,

    val loans: List<LibraryLoan> = emptyList(),
    val history: List<LibraryLoan> = emptyList(),
    val loanTotal: Int = 0,
    val historyTotal: Int = 0,
    val maxLoan: Int? = null,
    val reader: LibraryReader? = null,
    val borrowingLoading: Boolean = false,
    val borrowingLoaded: Boolean = false,
    val borrowingError: String? = null,

    /** 正在用内嵌页静默补会话，页面显示一行进度并把 WebView 藏起来。 */
    val syncing: Boolean = false,
    /** 补不到会话：整页换成登录引导，检索也不再假装「没有结果」。 */
    val sessionMissing: Boolean = false,
) {
    val historyHidden: Int get() = (historyTotal - history.size).coerceAtLeast(0)

    val searchHint: String
        get() = when {
            page == 0 -> "输入书名、作者或 ISBN，查馆藏与在架册数。"
            results.isEmpty() -> "没有检索到「$query」。换个关键词，书名只填主干部分更容易命中。"
            else -> ""
        }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val coordinator: WebSessionCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    private var booting = false
    private var syncStartedAt = 0L

    init {
        bootstrap()
    }

    /**
     * 进页面先确认会话，不等用户点「重试」。
     *
     * 超星那对 `jwtHeader`/`jwt` 是图书馆页面用自己的脚本写进 `document.cookie` 的，
     * 原生请求造不出来，所以缺会话时只能借一次静默的内嵌页把它补齐；
     * 补到之后顺手落盘，下次冷启动直接回灌。
     */
    fun bootstrap() {
        if (booting) return
        booting = true
        viewModelScope.launch {
            coordinator.restorePersistedCookies(force = true)
            val hasSession = repository.hasSession()
            booting = false
            if (hasSession) {
                _state.update { it.copy(syncing = false, sessionMissing = false) }
                loadBorrowing()
                return@launch
            }
            if (!coordinator.hasCasTicket()) {
                // 连统一认证都没有，补会话的网页链只会停在登录表单
                _state.update { it.copy(syncing = false, sessionMissing = true) }
                return@launch
            }
            syncStartedAt = System.currentTimeMillis()
            _state.update { it.copy(syncing = true, sessionMissing = false) }
        }
    }

    /** 内嵌页每加载完一页就查一次会话；链上有好几跳，最后一跳落地后才有那对 Cookie。 */
    fun onSyncPageFinished() {
        if (!_state.value.syncing) return
        viewModelScope.launch {
            // 页面脚本在 load 事件之后才写 Cookie，等一拍再读
            delay(800)
            if (!_state.value.syncing) return@launch
            if (repository.hasSession()) {
                coordinator.saveFromWebView(listOf(WebDomain.LIB_SEARCH))
                _state.update { it.copy(syncing = false, sessionMissing = false) }
                loadBorrowing()
            } else if (System.currentTimeMillis() - syncStartedAt > SYNC_LIMIT_MS) {
                endSync()
            }
        }
    }

    fun onSyncFailed() {
        if (_state.value.syncing) endSync()
    }

    fun syncTimedOut() {
        if (_state.value.syncing) endSync()
    }

    private fun endSync() {
        _state.update { it.copy(syncing = false, sessionMissing = true) }
    }

    /** 登录引导里的「重试」：重新回灌一次 Cookie；本机有票就再补一次会话。 */
    fun retrySession() {
        _state.update { it.copy(sessionMissing = false) }
        bootstrap()
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun selectTab(tab: LibraryTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == LibraryTab.BORROWING && !_state.value.borrowingLoaded && !_state.value.borrowingLoading) {
            loadBorrowing()
        }
    }

    fun dismissErrors() {
        _state.update { it.copy(searchError = null, holdingsError = null, borrowingError = null) }
    }

    /** 磁贴式入口没有关键词，所以不自动检索；点搜索或回车才发请求。 */
    fun search() = fetch(page = 1, append = false)

    fun loadMore() {
        val current = _state.value
        if (current.searching || !current.canLoadMore) return
        fetch(page = current.page + 1, append = true)
    }

    fun refresh() {
        if (!repository.hasSession()) {
            bootstrap()
            return
        }
        if (_state.value.tab == LibraryTab.BORROWING) loadBorrowing() else fetch(page = 1, append = false)
    }

    fun openBook(record: LibraryRecord) {
        _state.update {
            it.copy(selected = record, holdings = emptyList(), holdingsError = null, holdingsLoading = true)
        }
        viewModelScope.launch {
            val outcome = runCatching { repository.holdings(record.recordId) }
            val error = outcome.exceptionOrNull()
            if (error is LibraryAuthException) _state.update { it.copy(sessionMissing = true) }
            _state.update { current ->
                if (current.selected?.recordId != record.recordId) return@update current
                current.copy(
                    holdingsLoading = false,
                    holdings = outcome.getOrDefault(current.holdings),
                    holdingsError = error?.let(::failureText),
                )
            }
        }
    }

    fun closeBook() {
        _state.update { it.copy(selected = null, holdings = emptyList(), holdingsError = null) }
    }

    private fun fetch(page: Int, append: Boolean) {
        val keyword = _state.value.query.trim()
        if (keyword.isEmpty() || _state.value.searching) return
        _state.update {
            it.copy(
                searching = true,
                searchError = null,
                selected = null,
                results = if (append) it.results else emptyList(),
                numFound = if (append) it.numFound else 0,
            )
        }
        viewModelScope.launch {
            val outcome = runCatching { repository.search(keyword, page) }
            val error = outcome.exceptionOrNull()
            if (error is LibraryAuthException) _state.update { it.copy(sessionMissing = true) }
            _state.update { current ->
                val fetched = outcome.getOrNull()
                // 关键词已经变了，这一页回来也没用了，直接丢掉
                if (current.query.trim() != keyword) return@update current.copy(searching = false)
                current.copy(
                    searching = false,
                    results = if (append) current.results + fetched?.records.orEmpty() else fetched?.records.orEmpty(),
                    numFound = fetched?.numFound ?: current.numFound,
                    page = if (fetched != null) fetched.page else current.page,
                    canLoadMore = fetched?.hasMore == true,
                    searchError = error?.let(::failureText),
                )
            }
        }
    }

    /**
     * 借阅、历史、读者身份三件事并发取。
     *
     * 借阅是主数据：它判定登录状态；历史与身份坏了不影响这一页成立，
     * 取不到就当没有，不报错拦住用户。
     */
    private fun loadBorrowing() {
        val current = _state.value
        if (current.borrowingLoading) return
        _state.update { it.copy(borrowingLoading = true, borrowingError = null) }
        viewModelScope.launch {
            val history = async { runCatching { repository.loanHistory() }.getOrNull() }
            val reader = async { runCatching { repository.reader() }.getOrNull() }
            val outcome = runCatching { repository.loans() }
            val loans = outcome.getOrNull()
            val loansHistory = history.await()
            val readerInfo = reader.await()
            val error = outcome.exceptionOrNull()
            _state.update { state ->
                state.copy(
                    borrowingLoading = false,
                    borrowingLoaded = true,
                    loans = loans?.loans ?: state.loans,
                    loanTotal = loans?.total ?: state.loanTotal,
                    maxLoan = loans?.maxLoan ?: state.maxLoan,
                    history = loansHistory?.loans ?: state.history,
                    historyTotal = loansHistory?.total ?: state.historyTotal,
                    reader = readerInfo ?: state.reader,
                    sessionMissing = state.sessionMissing || error is LibraryAuthException,
                    borrowingError = error?.let(::failureText),
                )
            }
        }
    }

    /**
     * 会话类错误交给 [LibraryUiState.sessionMissing] 那扇门，别在横幅里重复一遍；
     * 其余错误照抄服务端中文消息。
     */
    private fun failureText(error: Throwable): String? = when (error) {
        is LibraryAuthException -> null
        is RemoteDataException -> error.message
        else -> "图书馆暂时读不到，检查网络后再试"
    }

    private companion object {
        /** 静默补会话的总时长上限；超过就换登录引导，不让页面一直空转。 */
        const val SYNC_LIMIT_MS = 25_000L
    }
}
