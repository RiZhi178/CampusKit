package qdu.campus.feature.seat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import qdu.campus.domain.model.Seat
import qdu.campus.domain.model.SeatReservation
import qdu.campus.domain.model.SeatRoom
import qdu.campus.domain.model.SeatStatus
import qdu.campus.domain.repository.SeatRepository
import qdu.campus.domain.usecase.SeatAvailability
import qdu.campus.domain.usecase.SeatLabel
import qdu.campus.platform.common.CsyyAuthException
import qdu.campus.platform.common.Formats
import qdu.campus.platform.common.everyMinute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/** 一个座位连同它在当前所选时段下的状态。 */
data class SeatEntry(val seat: Seat, val status: SeatStatus)

enum class SeatTab { Pick, Mine }

/**
 * 顶部横幅的一条消息。操作回执和失败提示共用一条通道，
 * 但成功不能挂 errorContainer 的红底，所以性质要跟着消息一起走。
 */
data class SeatMessage(val text: String, val isSuccess: Boolean)

/** 我的预约里需要二次确认的写操作。取消与提前结束都不可撤销，先问一句再发。 */
sealed interface SeatPendingAction {
    val reservation: SeatReservation

    data class Cancel(override val reservation: SeatReservation) : SeatPendingAction
    data class EndEarly(override val reservation: SeatReservation) : SeatPendingAction
}

data class SeatUiState(
    val tab: SeatTab = SeatTab.Pick,
    val rooms: List<SeatRoom> = emptyList(),
    val selectedRoom: SeatRoom? = null,
    val dates: List<LocalDate> = emptyList(),
    val date: LocalDate? = null,
    val startOptions: List<String> = emptyList(),
    val endOptions: List<String> = emptyList(),
    val start: String = "",
    val end: String = "",
    val seats: List<SeatEntry> = emptyList(),
    /** 座位数据最近一次成功拉取的时刻。断网保留缓存时用它说明数据有多旧。 */
    val seatsUpdatedAt: LocalDateTime? = null,
    val reservations: List<SeatReservation> = emptyList(),
    /** 非空表示确认弹层打开，值即待预约的座位。 */
    val pending: Seat? = null,
    /** 非空表示取消/提前结束的确认弹层打开。 */
    val pendingAction: SeatPendingAction? = null,
    /** 页面里的「此刻」由心跳更新：签到截止与可操作按钮都相对它判断。 */
    val now: LocalDateTime = Formats.todayTime(),
    val loadingRooms: Boolean = false,
    val loadingSeats: Boolean = false,
    val loadingReservations: Boolean = false,
    val submitting: Boolean = false,
    val message: SeatMessage? = null,
    /** 取消/提前离开失败时的弹窗正文；写操作失败只弹窗，不挂顶部横幅。 */
    val actionFailure: String? = null,
    val loggedOut: Boolean = false,
) {
    val freeCount: Int get() = seats.count { it.status == SeatStatus.Free }
}

@HiltViewModel
class SeatViewModel @Inject constructor(
    private val repository: SeatRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SeatUiState())
    val state: StateFlow<SeatUiState> = _state.asStateFlow()

    /**
     * 每次拉座位都记一个序号。用户连着换房间或日期时，旧请求回来得晚也不能盖掉新选择，
     * 否则会出现「选了 B 房间、图上却是 A 房间的座位」。
     */
    private var seatsRequestId = 0L

    /** 最近一次成功的座位快照，按「房间 + 日期」记，断网时只在同一条件下拿出来用。 */
    private var seatsCacheKey: Pair<Int, LocalDate>? = null
    private var seatsCache: List<Seat> = emptyList()

    init {
        loadRooms()
        startClockTicker()
    }

    fun switchTab(tab: SeatTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == SeatTab.Mine && _state.value.reservations.isEmpty()) loadReservations()
    }

    fun selectRoom(room: SeatRoom) {
        if (room.id == _state.value.selectedRoom?.id) return
        _state.update { it.copy(selectedRoom = room, start = "", end = "") }
        loadSeats()
    }

    fun selectDate(date: LocalDate) {
        if (date == _state.value.date) return
        _state.update { it.copy(date = date, start = "", end = "") }
        loadSeats()
    }

    fun selectStart(value: String) {
        val seat = referenceSeat() ?: return
        val ends = SeatAvailability.endOptions(seat, value)
        _state.update {
            it.copy(
                endOptions = ends,
                start = value,
                // 换了开始时间后原来的结束时间大概率不再合法，退回第一个可选值
                end = ends.firstOrNull { candidate -> candidate == it.end } ?: ends.firstOrNull().orEmpty(),
            )
        }
        recomputeStatuses()
    }

    fun selectEnd(value: String) {
        _state.update { it.copy(end = value) }
        recomputeStatuses()
    }

    fun pickSeat(entry: SeatEntry) {
        if (entry.status != SeatStatus.Free) {
            _state.update { it.copy(message = SeatMessage(describe(entry.status), false)) }
            return
        }
        _state.update { it.copy(pending = entry.seat) }
    }

    fun dismissPending() {
        _state.update { it.copy(pending = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    fun dismissActionFailure() {
        _state.update { it.copy(actionFailure = null) }
    }

    fun confirmBooking() {
        val seat = _state.value.pending ?: return
        if (_state.value.submitting) return
        // 弹层打开期间心跳不动选择，用户可能盯着它两三分钟才点确认，所以这里按此刻再判一次
        if (selectedRange()?.first?.isBefore(Formats.todayTime()) == true) {
            _state.update {
                it.copy(
                    pending = null,
                    message = SeatMessage("开始时间已过，可选时段已刷新，请重新选择", false),
                )
            }
            advanceClock()
            return
        }
        val range = selectedRange() ?: return
        val place = SeatLabel.bookingPlace(_state.value.selectedRoom?.name, seat.devName)
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val outcome = runCatching {
                repository.book(seat, range.first, range.second, "")
            }
            _state.update {
                it.copy(
                    submitting = false,
                    pending = null,
                    message = outcome.fold(
                        onSuccess = { ack -> SeatMessage("$ack：$place", true) },
                        onFailure = { SeatMessage(failureMessage(it), false) },
                    ),
                    loggedOut = sessionLost(outcome),
                )
            }
            if (outcome.isSuccess) {
                loadSeats()
                loadReservations()
            }
        }
    }

    fun askCancel(reservation: SeatReservation) {
        _state.update { it.copy(pendingAction = SeatPendingAction.Cancel(reservation)) }
    }

    fun askEndEarly(reservation: SeatReservation) {
        _state.update { it.copy(pendingAction = SeatPendingAction.EndEarly(reservation)) }
    }

    fun dismissPendingAction() {
        _state.update { it.copy(pendingAction = null) }
    }

    fun confirmPendingAction() {
        val action = _state.value.pendingAction ?: return
        if (_state.value.submitting) return
        val uuid = action.reservation.uuid
        _state.update { it.copy(pendingAction = null, submitting = true) }
        viewModelScope.launch {
            val outcome = runCatching {
                when (action) {
                    is SeatPendingAction.Cancel -> repository.cancel(uuid)
                    is SeatPendingAction.EndEarly -> repository.endEarly(uuid)
                }
            }
            _state.update { it.copy(submitting = false) }
            reportAction(outcome)
        }
    }

    fun retryLogin() {
        // 横幅也要一起清掉：登录成功后旧错误还挂着，看起来像重试没生效。
        _state.update { it.copy(loggedOut = false, message = null) }
    }

    fun refresh() {
        when (_state.value.tab) {
            // loadRooms 自己会拉一次座位，这里再调一次只会多发一个请求。
            SeatTab.Pick -> loadRooms()
            SeatTab.Mine -> loadReservations()
        }
    }

    private fun reportAction(outcome: Result<String>) {
        val failure = outcome.exceptionOrNull()
        _state.update {
            it.copy(
                message = outcome.getOrNull()?.let { text -> SeatMessage(text, true) },
                actionFailure = failure?.let { error -> failureMessage(error) },
                loggedOut = failure is CsyyAuthException,
            )
        }
        if (outcome.isSuccess) loadReservations()
    }

    /**
     * 会话状态只由需要登录的接口决定，且不粘滞：重新登录成功后下一次拉取就把它清掉。
     * `seatMenu` 实测是公开的（无 Cookie 也返回 code:0），说明不了会话在不在，
     * 所以 loadRooms 不参与判定，否则掉线时房间照常加载、登录入口却永远不出现。
     */
    private fun sessionLost(outcome: Result<*>): Boolean =
        outcome.exceptionOrNull() is CsyyAuthException

    private fun loadRooms() {
        _state.update { it.copy(loadingRooms = true) }
        viewModelScope.launch {
            val outcome = runCatching { repository.rooms() }
            val fresh = outcome.getOrNull()
            _state.update { current ->
                // 房间列表失败时别把上次的列表抹掉：离线要能继续看着缓存选座。
                val rooms = fresh ?: current.rooms
                val selected = current.selectedRoom?.let { room ->
                    rooms.firstOrNull { it.id == room.id }
                } ?: rooms.firstOrNull { it.totalCount > 0 } ?: rooms.firstOrNull()
                current.copy(
                    loadingRooms = false,
                    rooms = rooms,
                    selectedRoom = selected,
                    date = current.date ?: Formats.today(),
                    message = current.message ?: outcome.exceptionOrNull()?.let { SeatMessage(failureMessage(it), false) },
                )
            }
            // 房间拉失败但还有缓存房间时，也要顺带给座位一次重试机会；
            // 连缓存都没有、selectedRoom 为空时 loadSeats 自己会直接返回。
            loadSeats()
        }
    }

    private fun loadSeats() {
        val room = _state.value.selectedRoom ?: return
        val date = _state.value.date ?: Formats.today()
        val requestId = ++seatsRequestId
        _state.update { it.copy(loadingSeats = true) }
        viewModelScope.launch {
            val outcome = runCatching { repository.seats(room.id, date) }
            // 期间又换了房间/日期（或心跳触发了重拉），这份响应已经过期，直接丢掉。
            if (requestId != seatsRequestId) return@launch
            val fresh = outcome.getOrNull()
            // 断网时沿用同一房间、同一日期的缓存，不要把座位图清成空白。
            // 换了房间却没拉到数据就不能拿上一个房间的座位顶上，那会选错位置。
            val seats = fresh ?: if (seatsCacheKey == (room.id to date)) seatsCache else emptyList()
            if (fresh != null) {
                seatsCache = fresh
                seatsCacheKey = room.id to date
            }
            val reference = seats.firstOrNull { it.rule != null && it.openTimes.isNotEmpty() }
            val window = SeatAvailability.bookableWindow(reference?.rule, Formats.today())
            val dates = window?.days()?.takeIf { it.isNotEmpty() } ?: listOf(date)
            val resolvedDate = if (date in dates) date else dates.first()
            val now = Formats.todayTime()
            val starts = reference?.let { SeatAvailability.startOptions(it, resolvedDate, now) }.orEmpty()
            val start = _state.value.start.takeIf { it in starts } ?: starts.firstOrNull().orEmpty()
            val ends = reference?.let { SeatAvailability.endOptions(it, start) }.orEmpty()
            val end = _state.value.end.takeIf { it in ends } ?: ends.firstOrNull().orEmpty()

            _state.update { current ->
                current.copy(
                    loadingSeats = false,
                    seats = seats.map { SeatEntry(it, SeatStatus.Closed) },
                    dates = dates,
                    date = resolvedDate,
                    startOptions = starts,
                    endOptions = ends,
                    start = start,
                    end = end,
                    now = now,
                    seatsUpdatedAt = if (fresh != null) now else current.seatsUpdatedAt,
                    message = current.message ?: outcome.exceptionOrNull()?.let { SeatMessage(failureMessage(it), false) },
                    loggedOut = sessionLost(outcome),
                )
            }
            recomputeStatuses()
            // 只有真的拿到了新数据才需要为「日期不在可约窗口里」再拉一次，断网时不发第二次请求。
            if (fresh != null && resolvedDate != date) loadSeats()
        }
    }

    private fun loadReservations() {
        _state.update { it.copy(loadingReservations = true) }
        viewModelScope.launch {
            val today = Formats.today()
            val outcome = runCatching {
                repository.reservations(today.minusDays(RESERVATION_LOOKBACK_DAYS), today.plusDays(60))
            }
            _state.update { current ->
                current.copy(
                    loadingReservations = false,
                    // 新预约排在前面：最常看的是刚订下的那条，按时间正序会被推到列表底部。
                    reservations = (outcome.getOrNull() ?: current.reservations)
                        .sortedByDescending { it.start ?: LocalDateTime.MIN },
                    now = Formats.todayTime(),
                    message = current.message ?: outcome.exceptionOrNull()?.let { SeatMessage(failureMessage(it), false) },
                    loggedOut = sessionLost(outcome),
                )
            }
        }
    }

    /**
     * 每过一个整分钟就重算一次与「此刻」有关的东西。
     *
     * 可选开始时间和座位状态都是相对当前时刻算出来的，只在加载时算一次的后果是页面停在打开那一刻：
     * 14:59 列出 15:00 这一档，15:01 它还挂在选项里，点下去整张表却判不开放。
     * 网页端靠用户自己刷新，App 里没人会盯着分钟走，所以由心跳代劳。
     * 写法参照 reference/ZZUPocket 的 PomodoroEngine：单条协程循环 + delay，
     * 不引时钟库，也不在各个 composable 里各起一个。
     */
    private fun startClockTicker() {
        viewModelScope.launch { everyMinute(::advanceClock) }
    }

    /** 只吃本机时间，不发请求；数据本身的新旧仍由用户点刷新决定。 */
    private fun advanceClock() {
        // 「此刻」先落进状态：我的预约里的签到时限提示挂在它上面，不该跟时段重算的早退绑在一起。
        _state.update { it.copy(now = Formats.todayTime()) }
        val current = _state.value
        // 确认弹层开着时不动选择：用户正在核对的就是这一组时间，悄悄改掉比报错更糟
        if (current.pending != null || current.loadingSeats) return
        val reference = referenceSeat() ?: return
        val window = SeatAvailability.bookableWindow(reference.rule, Formats.today())?.days().orEmpty()
        if (window.isEmpty()) return
        // 跨天后可约窗口整体前移，日期列表和座位数据一起失效，重算不如重新拉一次
        if (window != current.dates) {
            loadSeats()
            return
        }
        val starts = SeatAvailability.startOptions(reference, current.date ?: Formats.today(), Formats.todayTime())
        // 过点的那一档直接从列表里消失并顺延到下一档；这是时间往前走的结果，不是用户的错，
        // 所以不挂横幅，选择框自己变化就是提示。
        val start = current.start.takeIf { it in starts } ?: starts.firstOrNull().orEmpty()
        val ends = if (start.isBlank()) emptyList() else SeatAvailability.endOptions(reference, start)
        _state.update {
            it.copy(
                startOptions = starts,
                endOptions = ends,
                start = start,
                end = current.end.takeIf { candidate -> candidate in ends } ?: ends.firstOrNull().orEmpty(),
            )
        }
        recomputeStatuses()
    }

    /** 状态随时段变化，所以时段一改就整表重算，不重新请求。 */
    private fun recomputeStatuses() {
        val range = selectedRange()
        val now = Formats.todayTime()
        _state.update { current ->
            current.copy(
                seats = current.seats.map { entry ->
                    entry.copy(
                        // 没有合法时段（比如今天剩下的档位全部过点）时没有「相对哪个时段」可言，整表不开放
                        status = range?.let {
                            SeatAvailability.status(entry.seat, it.first, it.second, now)
                        } ?: SeatStatus.Closed,
                    )
                },
            )
        }
    }

    /** 用第一个带规则的座位当时段基准：同一房间的规则与开放时段是共用的。 */
    private fun referenceSeat(): Seat? =
        _state.value.seats.firstOrNull { it.seat.rule != null && it.seat.openTimes.isNotEmpty() }?.seat

    private fun selectedRange(): Pair<LocalDateTime, LocalDateTime>? {
        val current = _state.value
        val date = current.date ?: return null
        val start = current.start.takeIf { it.isNotBlank() } ?: return null
        val end = current.end.takeIf { it.isNotBlank() } ?: return null
        val startTime = parse(start) ?: return null
        val endTime = parse(end) ?: return null
        if (!endTime.isAfter(startTime)) return null
        return date.atTime(startTime) to date.atTime(endTime)
    }

    private fun parse(hhmm: String): LocalTime? = Formats.parseTime(hhmm)

    private fun failureMessage(error: Throwable): String = when (error) {
        is CsyyAuthException -> error.message ?: "座位系统需要重新登录"
        else -> error.message ?: "操作失败"
    }

    private fun describe(status: SeatStatus): String = when (status) {
        SeatStatus.Free -> ""
        SeatStatus.Partial -> "所选时段与已有预约冲突"
        SeatStatus.Busy -> "所选时段已被占用"
        SeatStatus.Closed -> "当前不开放或不满足最短预约时长"
        SeatStatus.OnSiteOnly -> "该座位仅支持现场预约"
    }

    private companion object {
        const val RESERVATION_LOOKBACK_DAYS = 30L
    }
}
