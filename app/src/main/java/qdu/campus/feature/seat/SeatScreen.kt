package qdu.campus.feature.seat

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import qdu.campus.domain.usecase.SeatLabel
import qdu.campus.domain.usecase.SeatTileBounds
import qdu.campus.domain.usecase.SeatTile
import qdu.campus.domain.usecase.SeatTiling
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import qdu.campus.domain.model.Seat
import qdu.campus.domain.model.SeatReservation
import qdu.campus.domain.model.SeatRoom
import qdu.campus.domain.model.SeatStatus
import qdu.campus.platform.common.Formats
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.designsystem.Banner
import qdu.campus.platform.designsystem.BannerKind
import qdu.campus.platform.designsystem.LocalStatusColors
import qdu.campus.platform.designsystem.ErrWrap
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.DialogButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.TransientToast
import qdu.campus.platform.designsystem.listBottomGuard
import qdu.campus.platform.designsystem.SectionCard
import qdu.campus.platform.designsystem.Segmented
import qdu.campus.platform.designsystem.UiShapes
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 自习室座位。选座与我的预约两页，房间列表免登录，其余走 csyy 会话。
 *
 * 时段先选、座位后看：网页端就是这个顺序，状态是相对时段的，
 * 反过来会出现「看着空闲但一下单就冲突」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatScreen(
    onBack: () -> Unit,
    onOpenWebLogin: () -> Unit,
    viewModel: SeatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预约座位") },
                navigationIcon = { QduBackButton(onClick = onBack) },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            Segmented(
                options = listOf("选座", "我的预约"),
                selectedIndex = if (state.tab == SeatTab.Pick) 0 else 1,
                onSelect = { index ->
                    viewModel.switchTab(if (index == 0) SeatTab.Pick else SeatTab.Mine)
                },
            )

            // 未登录时下面的错误块已经把话说完了，顶部再挂一条同样的横幅只是重复。
            state.message?.takeIf { !state.loggedOut && !it.isSuccess }?.let { message ->
                Banner(
                    text = message.text,
                    kind = BannerKind.Failure,
                    actionLabel = "关闭",
                    onAction = viewModel::dismissMessage,
                )
            }

            when {
                // 房间列表是公开的，掉线时它照样有值，所以只看会话标记本身。
                state.loggedOut -> ErrWrap(
                    title = "座位系统未登录",
                    reason = "座位与预约记录由统一认证放行，登录一次后即可在本页直接选座预约。",
                    actions = {
                        QduButton(text = "去登录", onClick = onOpenWebLogin)
                        Spacer(Modifier.height(8.dp))
                        QduButton(
                            text = "已登录，重试",
                            kind = QduButtonKind.Tonal,
                            onClick = {
                                viewModel.retryLogin()
                                viewModel.refresh()
                            },
                        )
                    },
                )

                state.tab == SeatTab.Pick -> PickPane(
                    state = state,
                    onRoom = viewModel::selectRoom,
                    onDate = viewModel::selectDate,
                    onStart = viewModel::selectStart,
                    onEnd = viewModel::selectEnd,
                    onSeat = viewModel::pickSeat,
                )

                else -> MinePane(
                    state = state,
                    onCancel = viewModel::askCancel,
                    onEndEarly = viewModel::askEndEarly,
                )
            }
            }

            state.message?.takeIf { it.isSuccess }?.let {
                TransientToast(
                    message = it.text,
                    onDismiss = viewModel::dismissMessage,
                    modifier = Modifier.padding(bottom = listBottomGuard()),
                )
            }
        }
    }

    state.pending?.let { seat ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPending,
            title = { Text("座位 ${seatLabel(seat)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 确认弹层要能独立核对：房间名不写出来，从长列表点进来只剩一个座位编号。
                    state.selectedRoom?.let { room ->
                        Text(
                            roomPath(room),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${Formats.monthDayDot(state.date ?: Formats.today())} " +
                            "${state.start}–${state.end}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "预约规则",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = bookingRule(seat.rule?.cancelTime ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                DialogButton(
                    text = if (state.submitting) "提交中…" else "确认预约",
                    kind = QduButtonKind.Primary,
                    enabled = !state.submitting,
                    onClick = viewModel::confirmBooking,
                )
            },
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = viewModel::dismissPending,
                )
            },
        )
    }

    state.actionFailure?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissActionFailure,
            title = { Text("操作失败") },
            text = { Text(text) },
            confirmButton = {
                DialogButton(
                    text = "知道了",
                    kind = QduButtonKind.Primary,
                    onClick = viewModel::dismissActionFailure,
                )
            },
        )
    }

    state.pendingAction?.let { action ->
        val reservation = action.reservation
        val isCancel = action is SeatPendingAction.Cancel
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingAction,
            title = { Text(if (isCancel) "取消这条预约？" else "提前离开？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        reservation.roomName.ifBlank { "座位预约" },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${Formats.dateTime(reservation.start)} – ${Formats.hhmm(reservation.end)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (isCancel) "取消后该时段会重新放出，此操作不能撤销。"
                        else "提前离开后此刻起的剩余时段会重新放出，此操作不能撤销。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                DialogButton(
                    text = if (isCancel) "确认取消" else "确认离开",
                    kind = if (isCancel) QduButtonKind.Danger else QduButtonKind.Primary,
                    enabled = !state.submitting,
                    onClick = viewModel::confirmPendingAction,
                )
            },
            dismissButton = {
                DialogButton(
                    text = "返回",
                    kind = QduButtonKind.Ghost,
                    onClick = viewModel::dismissPendingAction,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickPane(
    state: SeatUiState,
    onRoom: (SeatRoom) -> Unit,
    onDate: (LocalDate) -> Unit,
    onStart: (String) -> Unit,
    onEnd: (String) -> Unit,
    onSeat: (SeatEntry) -> Unit,
) {
    if (state.rooms.isEmpty()) {
        if (state.loadingRooms) {
            Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) { AppLoading() }
        } else {
            // 这一页没有下拉刷新，只有顶栏的刷新按钮；提示要指向真实存在的那个入口。
            PlaceholderBlock(title = "没有可用的自习室", description = "点右上角刷新，或稍后再试。")
        }
        return
    }

    // 整页一起滚：筛选区（房间、日期、时段、图例）在小屏上能占掉大半屏，
    // 之前只有座位图自己滚，筛选区下半部分根本够不着。
    // 座位图内部保留自己的横向滚动，与竖向正交，互不抢手势。
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        RoomPicker(rooms = state.rooms, selected = state.selectedRoom, onSelect = onRoom)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.dates, key = { it.toEpochDay() }) { date ->
                FilterChip(
                    selected = date == state.date,
                    onClick = { onDate(date) },
                    label = {
                        Text(
                            "${Formats.monthDayDot(date)} ${Formats.weekdayLabel(date.dayOfWeek.value)}",
                        )
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeDropdown(
                label = "开始",
                options = state.startOptions,
                selected = state.start,
                onSelect = onStart,
                modifier = Modifier.weight(1f),
            )
            TimeDropdown(
                label = "结束",
                options = state.endOptions,
                selected = state.end,
                onSelect = onEnd,
                modifier = Modifier.weight(1f),
            )
        }

        StatusLegend(
            freeCount = state.freeCount,
            total = state.seats.size,
            updatedAt = state.seatsUpdatedAt,
        )

        when {
            state.loadingSeats -> Box(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                Alignment.Center,
            ) { AppLoading() }

            state.seats.isEmpty() -> PlaceholderBlock(
                title = "这一天没有座位数据",
                description = "换一个日期或房间试试。",
            )

            else -> SeatMapCard(
                entries = state.seats,
                onSelect = onSeat,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomPicker(
    rooms: List<SeatRoom>,
    selected: SeatRoom?,
    onSelect: (SeatRoom) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            // 收起态只写叶子名：整条路径 30 多字，singleLine 下尾部省略会把「余 N」直接吃掉。
            // 完整路径留在展开的菜单项里，余量挂到 label 上——label 是描边上的小字，不会被截断。
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = {
                Text(selected?.let { "自习室 · 余 ${it.remainCount}" } ?: "自习室")
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            rooms.forEach { room ->
                DropdownMenuItem(
                    text = { Text(roomLabel(room)) },
                    onClick = {
                        expanded = false
                        onSelect(room)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusLegend(
    freeCount: Int,
    total: Int,
    updatedAt: LocalDateTime?,
) {
    SectionCard {
        // 用 FlowRow 而不是 Row：窄屏下 Row 会把「不开放」拆成「不开 / 放」两行，图例整体换行才可读。
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "空闲 $freeCount / $total",
                style = MaterialTheme.typography.titleSmall,
            )
            LegendMark(level = SeatStatus.Free, label = "可约")
            LegendMark(level = SeatStatus.Partial, label = "部分占用")
            LegendMark(level = SeatStatus.Busy, label = "已占用")
            LegendMark(level = SeatStatus.Closed, label = "不开放 / 仅现场")
        }
        // 断网时展示的是缓存，必须写清这份数据是什么时候的，别让人以为刚刷新过。
        if (updatedAt != null) {
            Text(
                text = "数据更新于 ${Formats.hhmm(updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** 图例与座位图用同一套「填充 + 边框」编码，避免图例说一套、图上画一套。 */
@Composable
private fun LegendMark(level: SeatStatus, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SeatSwatch(status = level)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 图例里的小方块，尺寸比座位小一点，编码与 [SeatCell] 一致；不开放类带 ×。 */
@Composable
private fun SeatSwatch(status: SeatStatus) {
    val colors = seatColors(status)
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.fill)
            .seatBorder(colors.border, colors.dashed)
            .then(if (colors.cross) Modifier.crossMark(colors.border) else Modifier),
    )
}

/**
 * 座位平面图。按座位数分两档（分界见 [SeatTiling.FULL_MAP_LIMIT]）：
 *
 * - **32 座以内**：网格全图。画布按房间比例给（宽占满、高按 0.78），座位按服务端的
 *   `coordinate` 百分比落点，靠墙一圈与中间桌组一眼能看出来。格子边长取房间里最小相邻
 *   间距的 86%，保证互不重叠；坐标过密时改成等距网格，并在「放大」里看真实位置。
 * - **超过 32 座**：瓦片法。一次只画一片，顶上用片名切换，底下用概览图定位——
 *   一屏塞不下几十上百个座位，硬塞只会既看不清也点不准。
 *
 * 所有可点座位的热区固定 48dp，与相邻座位的间距不小于 48dp；做不到就不在这一层开放点击，
 * 让用户去「放大」里选。状态仍是「填充 + 边框实虚 + 删除线」，颜色之外还有第二重编码。
 * 座位显示的是**房间内编号**（`devName` 尾段，如 `H8-ZXS01-40` → 40），不是 `devSn`
 * （实测是 188047 这类内部主键，写上去没法看）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeatMapCard(
    entries: List<SeatEntry>,
    onSelect: (SeatEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placed = entries.filter { it.seat.position != null }
    val loose = entries.filter { it.seat.position == null }
    var zoomed by remember { mutableStateOf(false) }
    val useTiles = SeatTiling.shouldUseTiles(placed.size)

    SectionCard(modifier = modifier) {
        if (useTiles) {
            TiledSeatMap(entries = placed, onSelect = onSelect)
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val metrics = remember(placed, maxWidth, density) {
                    seatMapMetrics(placed, maxWidth, density)
                }
                if (metrics != null) {
                    Column {
                        if (metrics.cell < SEAT_LABEL_MIN_CELL) {
                            // 房间不大但坐标很密时，按真实位置画会缩成一堆点；这时退回等距网格，
                            // 把每一排、每一列的相对次序保留下来，编号也能看清。
                            SeatTileCanvas(
                                tile = SeatTile(
                                    label = "全图",
                                    bounds = SeatTileBounds(0f, 0f, 100f, 100f),
                                    seats = placed.map { it.seat },
                                ),
                                entries = placed,
                                onSelect = onSelect,
                            )
                        } else {
                            SeatCanvas(
                                placed = placed,
                                cell = metrics.cell,
                                canvas = metrics.canvas,
                                showLabel = metrics.showLabel,
                                // 相邻座位的 48dp 热区会互相压住时，这张图只看不点，选座走「放大」。
                                interactive = metrics.step >= SEAT_TOUCH,
                                onSelect = onSelect,
                            )
                        }
                        // 两种画法共用一个「放大」入口：以前等距网格那一支只写了提示却没给按钮。
                        MapHintRow(
                            text = when {
                                metrics.cell < SEAT_LABEL_MIN_CELL ->
                                    "按排与列等距摆放；房间的真实位置在「放大」里看"
                                metrics.step < SEAT_TOUCH ->
                                    "座位较密，图上不直接选座；点「放大」再选"
                                else -> "按房间实际位置摆放，编号是房间内的座位号"
                            },
                            onZoom = { zoomed = true },
                        )
                    }
                }
            }
        }

        if (loose.isNotEmpty()) {
            Text(
                text = "未定位座位",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = if (placed.isEmpty()) 0.dp else 10.dp),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                loose.forEach { entry ->
                    SeatCell(entry = entry, onClick = { onSelect(entry) })
                }
            }
        }
    }

    if (zoomed) {
        ZoomedSeatDialog(
            placed = placed,
            // 放大图里选完就收起：否则确认弹层关掉后这一层还压在页面上，挡住选座页。
            onSelect = { entry ->
                zoomed = false
                onSelect(entry)
            },
            onDismiss = { zoomed = false },
        )
    }
}

/** 图下说明与「放大」入口。两种画法共用，避免某一支只写说明却不给按钮。 */
@Composable
private fun MapHintRow(text: String, onZoom: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        QduButton(
            text = "放大",
            onClick = onZoom,
            kind = QduButtonKind.Ghost,
            small = true,
        )
    }
}

/**
 * 瓦片视图（超过 [SeatTiling.FULL_MAP_LIMIT] 座时用）。
 *
 * 一次只画一片：片内座位固定 48dp、编号直接可读，顶上一排片名切换（顺带报每片空位数），
 * 底下小概览图标出这片在房间的哪一角。大房间不这么做的话，一屏既要塞下上百个座位、
 * 又要保证热区，两头都做不到。
 */
@Composable
private fun TiledSeatMap(
    entries: List<SeatEntry>,
    onSelect: (SeatEntry) -> Unit,
) {
    val tiles = remember(entries) { SeatTiling.build(entries.map { it.seat }) }
    val byId = remember(entries) { entries.associateBy { it.seat.devId } }
    if (tiles.isEmpty()) return
    var active by remember(tiles) { mutableStateOf(0) }
    val current = tiles.getOrNull(active) ?: tiles.first()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tiles.forEachIndexed { index, tile ->
                val free = tile.seats.count { seat -> byId[seat.devId]?.status == SeatStatus.Free }
                FilterChip(
                    selected = index == active,
                    onClick = { active = index },
                    label = { Text("${tile.label} 片 · 空 $free") },
                )
            }
        }

        SeatTileCanvas(
            tile = current,
            entries = current.seats.mapNotNull { byId[it.devId] },
            onSelect = onSelect,
        )

        Text(
            text = "整间 ${entries.size} 座，分 ${tiles.size} 片；片内格子随宽度缩放，热区一律 48dp，均可直接点选。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TileOverview(
            tiles = tiles,
            active = active,
            freeCounts = tiles.map { tile -> tile.seats.count { seat -> byId[seat.devId]?.status == SeatStatus.Free } },
            onPick = { active = it },
        )
    }
}

/** 片内画布：在片的小范围内重新按百分比落点，并按最小相邻间距把格距撑到 48dp + 10dp。 */
@Composable
private fun SeatTileCanvas(
    tile: SeatTile,
    entries: List<SeatEntry>,
    onSelect: (SeatEntry) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val available = maxWidth
        val layout = remember(tile, available, density) {
            tileLayout(tile, 12.dp, density, available)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .size(layout.width, layout.height)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            ) {
                entries.forEach { entry ->
                    val position = entry.seat.position ?: return@forEach
                    Box(
                        modifier = Modifier.offset(
                            x = layout.xOf(position.x),
                            y = layout.yOf(position.y),
                        ),
                    ) {
                        SeatCell(
                            entry = entry,
                            onClick = { onSelect(entry) },
                            size = layout.cell,
                            hitSize = maxOf(layout.hit, layout.cell),
                        )
                    }
                }
            }
        }
    }
}

/** 概览图：按各片在房间里的实际位置摆放，点字母跳片。 */
@Composable
private fun TileOverview(
    tiles: List<SeatTile>,
    active: Int,
    freeCounts: List<Int>,
    onPick: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "房间概览：字母标出各片位置；切换用上方片名，较大的片也能直接点",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(SEAT_OVERVIEW_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        ) {
            val fullWidth = maxWidth
            val fullHeight = maxHeight
            tiles.forEachIndexed { index, tile ->
                val bounds = tile.bounds
                val isActive = index == active
                val tileWidth = fullWidth * ((bounds.x1 - bounds.x0) / 100f)
                val tileHeight = fullHeight * ((bounds.y1 - bounds.y0) / 100f)
                // 房间被切得很细时，概览里的格子比 48dp 还小，点它只会点错。
                // 那些格子只作位置标注，切换仍走上方的片名。
                val tappable = tileWidth >= SEAT_TOUCH && tileHeight >= SEAT_TOUCH
                val free = freeCounts.getOrElse(index) { 0 }
                Box(
                    modifier = Modifier
                        .offset(
                            x = fullWidth * (bounds.x0 / 100f),
                            y = fullHeight * (bounds.y0 / 100f),
                        )
                        .size(width = tileWidth, height = tileHeight)
                        .background(
                            if (isActive) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = RoundedCornerShape(6.dp),
                        )
                        .clickable(enabled = tappable, role = Role.Button) { onPick(index) }
                        .semantics {
                            contentDescription = "${tile.label} 片，空 $free 座"
                            stateDescription = when {
                                !tappable -> "仅标注位置，用上方片名切换"
                                isActive -> "当前片"
                                else -> "可切换"
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clearAndSetSemantics {},
                    ) {
                        Text(
                            text = tile.label,
                            style = if (tappable) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                MaterialTheme.typography.labelSmall
                            },
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (tappable) {
                            Text(
                                text = "空 $free",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 片内座位的默认边长，也是最外层可点区域。 */
private val SEAT_TILE_CELL = 48.dp

/** 视觉格子的下限；热区仍是 [SEAT_TOUCH]，只是里面画的小方块变小。 */
private val SEAT_TILE_MIN_CELL = 32.dp

/** 所有可点座位的热区，取自 core 里的同一口径。 */
private val SEAT_TOUCH = SeatTiling.MIN_TOUCH_DP.dp
private val SEAT_OVERVIEW_HEIGHT = 132.dp

private class TileLayout(
    val width: Dp,
    val height: Dp,
    val cell: Dp,
    val hit: Dp,
    val xOf: (Float) -> Dp,
    val yOf: (Float) -> Dp,
)

/**
 * 片内取景：把这一片里出现过的 x、y 各自排序，按序号排成等距的行列。
 *
 * 为什么不用原始百分比：片内只要有两个座位挨得近（服务端坐标抖动 0.5% 就会发生），
 * 按百分比放大后整片画布会被拉到屏幕外，剩下的座位全跑到横向滚动区里，看起来像丢了。
 * 瓦片本身就只交代「这一片里谁挨着谁」，等距网格足够表达；跨片的相对位置由概览图负责。
 *
 * 边长优先 48dp；一行放不下就把视觉格子按可用宽度压到 32dp 下限，
 * 但格子间距（也是热区起点间距）始终不小于 48dp，所以相邻热区不会重叠、点 A 不会落到 B。
 * 再放不下就交给外层横向滚动。
 */
private fun tileLayout(
    tile: SeatTile,
    padding: Dp,
    density: Density,
    available: Dp,
): TileLayout {
    val xs = tile.seats.mapNotNull { it.position?.x }.distinct().sorted()
    val ys = tile.seats.mapNotNull { it.position?.y }.distinct().sorted()
    val cols = xs.size.coerceAtLeast(1)
    val rows = ys.size.coerceAtLeast(1)
    return with(density) {
        val gapPx = 8.dp.toPx()
        val hitPx = SEAT_TOUCH.toPx()
        val padPx = padding.toPx()
        val availPx = (available.toPx() - padPx * 2).coerceAtLeast(1f)

        var cellPx = SEAT_TILE_CELL.toPx()
        var stepPx = cellPx + gapPx
        if (cols * stepPx - gapPx > availPx) {
            val fitStep = (availPx + gapPx) / cols
            cellPx = (fitStep - gapPx).coerceAtLeast(SEAT_TILE_MIN_CELL.toPx())
            stepPx = maxOf(cellPx + gapPx, hitPx)
        }
        // 画布按热区算尺寸，最后一个座位的 48dp 不会画到边框外面。
        val widthPx = (cols - 1) * stepPx + hitPx + padPx * 2
        val heightPx = (rows - 1) * stepPx + hitPx + padPx * 2
        TileLayout(
            width = widthPx.toDp(),
            height = heightPx.toDp(),
            cell = cellPx.toDp(),
            hit = hitPx.toDp(),
            xOf = { x -> (padPx + xs.nearestIndex(x) * stepPx).toDp() },
            yOf = { y -> (padPx + ys.nearestIndex(y) * stepPx).toDp() },
        )
    }
}

/** 坐标 → 列/行序号。浮点数用最近邻匹配，避免 33.300003 这类抖动查不到自己。 */
private fun List<Float>.nearestIndex(value: Float): Int {
    if (isEmpty()) return 0
    var best = 0
    var bestDiff = Float.MAX_VALUE
    forEachIndexed { index, candidate ->
        val diff = kotlin.math.abs(candidate - value)
        if (diff < bestDiff) {
            bestDiff = diff
            best = index
        }
    }
    return best
}

/** 放大视图的基础倍率：够看清两位数编号，又不至于一个座位占满屏幕。密房间会按间距再抬高。 */
private const val SEAT_ZOOM = 2.4f

/** 画布宽高比。房间本身没给尺寸，取一个常见的自习室比例，宁可略扁不拉太高。 */
private const val SEAT_CANVAS_ASPECT = 0.78f

private data class SeatMapMetrics(
    val canvas: DpSize,
    val cell: Dp,
    /** 相邻座位的最小间距，决定这一层能不能直接点选。 */
    val step: Dp,
    val showLabel: Boolean,
)

/** 小于这个格宽就不画编号，硬塞会糊成一团。 */
private val SEAT_LABEL_MIN_CELL = 22.dp

/**
 * 用最小相邻间距定格子边长：同一排相邻两个座位的百分比差乘以画布宽度，就是它们之间的实际距离。
 * 取 86% 给边界留白。房间只有一个座位时退回一个默认格宽。
 */
private fun seatMapMetrics(
    placed: List<SeatEntry>,
    maxWidth: Dp,
    density: Density,
): SeatMapMetrics? {
    val positions = placed.mapNotNull { it.seat.position }
    if (positions.isEmpty()) return null
    val canvasH = maxWidth * SEAT_CANVAS_ASPECT
    return with(density) {
        val gapX = minGapPercent(positions.map { it.x }) ?: 6f
        val gapY = minGapPercent(positions.map { it.y }) ?: 8f
        val stepX = maxWidth.value * gapX / 100f
        val stepY = canvasH.value * gapY / 100f
        val side = minOf(stepX * 0.86f, stepY * 0.86f, 40f).coerceAtLeast(9f)
        val cell = side.dp
        SeatMapMetrics(
            canvas = DpSize(maxWidth, canvasH),
            cell = cell,
            step = minOf(stepX, stepY).dp,
            showLabel = cell >= SEAT_LABEL_MIN_CELL,
        )
    }
}

/** 一维坐标里最小的非零间距（百分比）。座位都叠在一条线上时返回 null。 */
private fun minGapPercent(values: List<Float>): Float? {
    val sorted = values.sorted()
    var best: Float? = null
    for (i in 1 until sorted.size) {
        val gap = sorted[i] - sorted[i - 1]
        if (gap > 0.5f && (best == null || gap < best)) best = gap
    }
    return best
}

@Composable
private fun SeatCanvas(
    placed: List<SeatEntry>,
    cell: Dp,
    canvas: DpSize,
    showLabel: Boolean,
    interactive: Boolean,
    onSelect: (SeatEntry) -> Unit,
) {
    // 放大后视觉格子可能超过 48dp，热区要跟着长，否则点得到方块边却没反应。
    val hit = maxOf(SEAT_TOUCH, cell)
    Box(
        modifier = Modifier
            .size(canvas)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        placed.forEach { entry ->
            val position = entry.seat.position ?: return@forEach
            Box(
                modifier = Modifier.offset(
                    x = canvas.width * (position.x / 100f) - hit / 2f,
                    y = canvas.height * (position.y / 100f) - hit / 2f,
                ),
            ) {
                SeatCell(
                    entry = entry,
                    onClick = { onSelect(entry) },
                    size = cell,
                    hitSize = hit,
                    showLabel = showLabel,
                    interactive = interactive,
                )
            }
        }
    }
}

/** 放大视图：整块画布按倍率重画，装在双向滚动里，大房间可以拖着看。 */
@Composable
private fun ZoomedSeatDialog(
    placed: List<SeatEntry>,
    onSelect: (SeatEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        SectionCard {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "座位平面图",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    QduButton(text = "关闭", onClick = onDismiss, kind = QduButtonKind.Ghost, small = true)
                }
                Spacer(Modifier.height(10.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val density = LocalDensity.current
                    val metrics = remember(placed, maxWidth, density) {
                        seatMapMetrics(placed, maxWidth, density)
                    } ?: return@BoxWithConstraints
                    // 密房间里 2.4 倍还不够把相邻座位拉开 48dp，此时按间距再抬倍率，
                    // 保证放大图里每个热区互不重叠。
                    val zoom = SeatTiling.zoomForTouch(metrics.step.value, SEAT_ZOOM)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SeatCanvas(
                            placed = placed,
                            cell = metrics.cell * zoom,
                            canvas = DpSize(metrics.canvas.width * zoom, metrics.canvas.height * zoom),
                            showLabel = true,
                            interactive = true,
                            onSelect = onSelect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatCell(
    entry: SeatEntry,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    /** 可点区域与视觉格子分开：格子可以缩到 32dp，热区仍是 48dp。 */
    hitSize: Dp = size,
    showLabel: Boolean = true,
    interactive: Boolean = true,
) {
    val colors = seatColors(entry.status)
    val clickable = interactive && (entry.status == SeatStatus.Free || entry.status == SeatStatus.Partial)
    val status = statusLabel(entry.status)
    Box(
        modifier = Modifier
            .size(hitSize)
            .then(
                if (clickable) {
                    Modifier.clickable(role = Role.Button, onClickLabel = "选择座位", onClick = onClick)
                } else {
                    Modifier
                },
            )
            // 状态同时写在读屏描述里：图上的填充与边框对读屏不成立。
            // 描述里已含状态，不再另设 stateDescription，免得读屏把同一句念两遍。
            .semantics { contentDescription = "${seatLabel(entry)}，$status" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.fill)
                .seatBorder(colors.border, colors.dashed)
                .then(if (colors.cross) Modifier.crossMark(colors.border) else Modifier)
                // 编号已经写进外层描述，别让读屏把同一个号念两遍。
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            // 不开放/仅现场画 ×（与图例同一编码），不再显示编号；编号在内容描述里仍可读到
            if (showLabel && !colors.cross) {
                Text(
                    text = seatLabel(entry),
                    style = if (size >= 32.dp) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelSmall
                    },
                    color = colors.text,
                    textDecoration = if (entry.status == SeatStatus.Busy) TextDecoration.LineThrough else null,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 座位在房间里的编号：取 `devName` 的尾段（`H8-ZXS01-40` → `40`），去前导零。
 * 不用 `devSn`（188047 这类内部主键），也不把整条正式编号上屏。
 */
private fun seatLabel(entry: SeatEntry): String = SeatLabel.of(entry.seat.devName)

private fun seatLabel(seat: Seat): String = SeatLabel.of(seat.devName)

@Immutable
private data class SeatVisual(val fill: Color, val border: Color, val text: Color, val dashed: Boolean, val cross: Boolean)

/**
 * 五态在图上靠「填充 + 边框实/虚 + × / 删除线」区分，不只靠颜色：
 * 可约实心带色，部分占用同色但虚线边，已占用是中性底 + 删除线，
 * 不开放与仅现场是浅红底 + 实线红边 + ×（2026-11-06 起，原空心虚边与可约几乎不可分）。
 *
 * 边框一律用状态原色，不加透明度：叠到浅底上以后，45% 的灰只有 1.9:1、70% 也只有 2.9:1，
 * 都低于 UI 组件需要的 3:1，虚线会细到看不出「不开放」和「可约」的区别。
 */
@Composable
private fun seatColors(status: SeatStatus): SeatVisual {
    val statusColors = LocalStatusColors.current
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        SeatStatus.Free -> SeatVisual(
            fill = statusColors.success.copy(alpha = 0.16f),
            border = statusColors.success,
            text = scheme.onSurface,
            dashed = false,
            cross = false,
        )
        SeatStatus.Partial -> SeatVisual(
            fill = statusColors.warning.copy(alpha = 0.18f),
            border = statusColors.warning,
            text = scheme.onSurface,
            dashed = true,
            cross = false,
        )
        SeatStatus.Busy -> SeatVisual(
            fill = scheme.surfaceContainerHighest,
            border = statusColors.blocked,
            text = scheme.onSurfaceVariant,
            dashed = false,
            cross = false,
        )
        SeatStatus.Closed, SeatStatus.OnSiteOnly -> SeatVisual(
            fill = statusColors.blocked.copy(alpha = 0.16f),
            border = statusColors.blocked,
            text = scheme.onSurfaceVariant,
            dashed = false,
            cross = true,
        )
    }
}

/** 在格子中央画一个 ×。图例小方块与不可选座位共用，颜色跟边框走。 */
private fun Modifier.crossMark(color: Color): Modifier = drawBehind {
    val inset = size.minDimension * 0.3f
    val cap = StrokeCap.Round
    drawLine(
        color,
        Offset(inset, inset),
        Offset(size.width - inset, size.height - inset),
        strokeWidth = 1.6.dp.toPx(),
        cap = cap,
    )
    drawLine(
        color,
        Offset(size.width - inset, inset),
        Offset(inset, size.height - inset),
        strokeWidth = 1.6.dp.toPx(),
        cap = cap,
    )
}

/** 实线或虚线圆角描边。Compose 没有现成的虚线 Modifier，用 drawBehind 画。 */
private fun Modifier.seatBorder(color: Color, dashed: Boolean): Modifier = drawBehind {
    val stroke = if (dashed) 1.dp.toPx() else 1.2.dp.toPx()
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(Offset.Zero, size),
                cornerRadius = CornerRadius(9.dp.toPx(), 9.dp.toPx()),
            ),
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = stroke,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null,
        ),
    )
}

@Composable
private fun MinePane(
    state: SeatUiState,
    onCancel: (SeatReservation) -> Unit,
    onEndEarly: (SeatReservation) -> Unit,
) {
    when {
        state.loadingReservations && state.reservations.isEmpty() -> Box(
            Modifier.fillMaxWidth().padding(vertical = 32.dp),
            Alignment.Center,
        ) { AppLoading() }

        state.reservations.isEmpty() -> PlaceholderBlock(
            title = "还没有预约记录",
            description = "近 30 天与未来 60 天的座位预约会显示在这里。",
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(state.reservations, key = { it.uuid }) { reservation ->
                ReservationCard(
                    reservation = reservation,
                    now = state.now,
                    onCancel = onCancel,
                    onEndEarly = onEndEarly,
                )
            }
        }
    }
}

/**
 * 一条预约一张卡。左滑把卡片推到一边，露出取消与提前离开两个按钮。
 * 不直接用手势触发操作：两条写操作都不可撤销，必须先看到按钮并点一次确认。
 * 手势之外另挂无障碍自定义动作，TalkBack 用户不必依赖滑动。
 *
 * 吸附口径（2026-11-06 定）：松手只有两种落点——全开或全关，不允许停在半路。
 * 只要是一次明确的左滑（向左甩，或慢速拖动离起点超过一点），不管拖了多少距离都完整展开；
 * 向右甩则收回。之前的实现里拖动与收尾动画的协程会互相覆盖，卡片可能停在任意位置。
 */
@Composable
private fun ReservationCard(
    reservation: SeatReservation,
    now: LocalDateTime,
    onCancel: (SeatReservation) -> Unit,
    onEndEarly: (SeatReservation) -> Unit,
) {
    val phase = reservation.phaseAt(now)
    val cancelable = reservation.canCancel(now)
    val endable = reservation.endEarly
    // 两个按钮的实际宽度随系统字体变化，写死 200dp 会在放大字体后让左边那颗仍被卡片盖住。
    // 量出这一行的真实宽度，卡片就按它让位。
    val actionsWidth = remember(reservation.uuid) { mutableIntStateOf(0) }
    fun limitPx(): Float = actionsWidth.intValue.toFloat().coerceAtLeast(1f)
    val density = LocalDensity.current
    // 慢速拖动的展开门槛：越过这条线就算「要开」，与滑动距离无关
    val openEps = remember(density) { with(density) { 20.dp.toPx() } }
    val offset = remember(reservation.uuid) { Animatable(0f) }
    var actionsOpen by remember(reservation.uuid) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // 一次只允许一个位移协程：新的取消旧的，拖动的 snapTo 与松手的 animateTo 不会互相覆盖
    var motionJob by remember(reservation.uuid) { mutableStateOf<Job?>(null) }
    val dragState = rememberDraggableState { delta ->
        motionJob?.cancel()
        motionJob = scope.launch {
            offset.snapTo((offset.value + delta).coerceIn(-limitPx(), 0f))
        }
    }

    fun settle(velocity: Float) {
        motionJob?.cancel()
        val open = when {
            velocity > 0f -> false
            velocity < -800f -> true
            else -> offset.value <= -openEps
        }
        actionsOpen = open
        motionJob = scope.launch {
            offset.animateTo(if (open) -limitPx() else 0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(UiShapes.Card)
            .semantics {
                customActions = listOfNotNull(
                    if (cancelable) {
                        CustomAccessibilityAction("取消预约") {
                            onCancel(reservation)
                            true
                        }
                    } else {
                        null
                    },
                    if (endable) {
                        CustomAccessibilityAction("提前离开") {
                            onEndEarly(reservation)
                            true
                        }
                    } else {
                        null
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                // 放在 padding 外层，量到的宽度才含右侧留白。
                .onSizeChanged { actionsWidth.intValue = it.width }
                .padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QduButton(
                text = "取消预约",
                kind = QduButtonKind.Danger,
                small = true,
                // 收起时按钮被卡片盖住，但触摸仍可能落到底层，只在真正露出后才允许点。
                enabled = cancelable && actionsOpen,
                onClick = {
                    actionsOpen = false
                    scope.launch { offset.animateTo(0f) }
                    onCancel(reservation)
                },
            )
            QduButton(
                text = "提前离开",
                kind = QduButtonKind.Tonal,
                small = true,
                enabled = endable && actionsOpen,
                onClick = {
                    actionsOpen = false
                    scope.launch { offset.animateTo(0f) }
                    onEndEarly(reservation)
                },
            )
        }
        SectionCard(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    // 手势被系统打断（如来电）也走这里、速度按 0 处理，卡片照样落到两端
                    onDragStopped = { velocity -> settle(velocity) },
                ),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 正式编号（H8-ZXS01-01）不上屏：记录里认的是房间与时段，
                // 编号对用户没有信息量，网页端列表也没拿它当标题。
                Text(
                    text = reservation.roomName.ifBlank { "座位预约" },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${Formats.dateTime(reservation.start)} – ${Formats.hhmm(reservation.end)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SignStatusLine(reservation = reservation, now = now, phase = phase)
                if (reservation.theme.isNotBlank()) {
                    Text(
                        text = "主题：${reservation.theme}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                actionHint(phase, cancelable, endable)?.let { hint ->
                    Text(
                        text = hint.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hint.error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (cancelable || endable) {
                    Text(
                        text = "左滑卡片可取消或提前离开",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun roomPath(room: SeatRoom): String =
    listOf(room.campus, room.building, room.name)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

private fun roomLabel(room: SeatRoom): String =
    roomPath(room) + "（余 ${room.remainCount}）"

private fun statusLabel(status: SeatStatus): String = when (status) {
    SeatStatus.Free -> "可约"
    SeatStatus.Partial -> "部分占用"
    SeatStatus.Busy -> "已占用"
    SeatStatus.Closed -> "不开放"
    SeatStatus.OnSiteOnly -> "仅现场"
}

/**
 * 预约相对「此刻」的阶段，只用于文案，不再决定按钮。
 *
 * 写操作的可否由服务端字段决定：
 * - 取消：网页端 `isShowCancel` 只看 `resvStatus`——`(4 & status) == 0 && (128 & status) == 0`，
 *   位 4 = 已生效、位 128 = 已结束。实测开始后再删，服务端回「预约在当前状态下不能删除」。
 *   没有「开始前 N 分钟停止取消」这一档；房间规则里的 `cancelTime` 是生效后到场取设备的宽限期，
 *   超时未签到记违约一次，不是取消截止时间。
 * - 提前离开：服务端在每条记录上给 `endEarly` 旗标，签到后才为真；网页端也只认它。
 */
private enum class ReservationPhase {
    Upcoming, Ongoing, Ended,
}

private const val STATUS_EFFECTIVE = 4
private const val STATUS_FINISHED = 128

private fun SeatReservation.canCancel(now: LocalDateTime): Boolean {
    val begin = start ?: return false
    if (!now.isBefore(begin)) return false
    return status and STATUS_EFFECTIVE == 0 && status and STATUS_FINISHED == 0
}

private data class ActionHint(val text: String, val error: Boolean)

private fun actionHint(
    phase: ReservationPhase,
    cancelable: Boolean,
    endable: Boolean,
): ActionHint? = when {
    phase == ReservationPhase.Upcoming -> ActionHint(
        text = if (cancelable) {
            "开始前可以取消；预约一开始就不能取消，也不能删除。"
        } else {
            "这条预约已不能取消。"
        },
        error = false,
    )

    phase == ReservationPhase.Ongoing && !endable -> ActionHint(
        text = "已开始：不能取消；到场签到后才能提前离开。",
        error = true,
    )

    phase == ReservationPhase.Ongoing -> ActionHint("已签到，可随时提前离开。", false)
    else -> null
}

/** 预约弹窗里的规则提示。`cancelTime` 是生效后的签到宽限期，不是取消截止时间。 */
private fun bookingRule(cancelTime: Int): String {
    val head = "开始前可取消；预约一旦开始就不能取消。"
    val grace = if (cancelTime > 0) {
        "生效后 $cancelTime 分钟内未到场签到，记违约一次、预约不再保留。"
    } else {
        ""
    }
    return head + grace + "提前离开需到场签到后才可用。"
}

private fun SeatReservation.phaseAt(now: LocalDateTime): ReservationPhase {
    val begin = start ?: return ReservationPhase.Ended
    val finish = end ?: return ReservationPhase.Ended
    return when {
        now < begin -> ReservationPhase.Upcoming
        realEnd == null && now < finish -> ReservationPhase.Ongoing
        else -> ReservationPhase.Ended
    }
}

/**
 * 到馆签到状态。csyy 只在列表里下发 `signTime` 与 `latestCheckInTime`，
 * 没有可调用的签到端点，所以这里如实展示「签没签、最晚几点」，别把占位说成已签到。
 */
@Composable
private fun SignStatusLine(
    reservation: SeatReservation,
    now: LocalDateTime,
    phase: ReservationPhase,
) {
    val deadline = reservation.latestCheckInTime
    val overdue = phase != ReservationPhase.Ended && reservation.signTime == null &&
        deadline != null && now.isAfter(deadline)
    val text = when {
        reservation.signTime != null -> "已签到 ${Formats.hhmm(reservation.signTime)}"
        phase == ReservationPhase.Ended -> "未签到"
        overdue -> "已过签到时限（${Formats.hhmm(deadline)}），未签到可能记为违约"
        deadline != null -> "最晚 ${Formats.hhmm(deadline)} 前到场签到"
        else -> "未签到"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = when {
            overdue -> MaterialTheme.colorScheme.error
            reservation.signTime != null -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
