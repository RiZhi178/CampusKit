package qdu.campus.feature.seat

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
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
import qdu.campus.domain.model.SeatPosition
import qdu.campus.domain.model.SeatReservation
import qdu.campus.domain.model.SeatRoom
import qdu.campus.domain.model.SeatStatus
import qdu.campus.platform.common.Formats
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.designsystem.Banner
import qdu.campus.platform.designsystem.BannerKind
import qdu.campus.platform.designsystem.LocalStatusColors
import qdu.campus.domain.model.SessionState
import qdu.campus.platform.designsystem.SessionGate
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
                // 门禁以统一认证会话为准：未登录一律不放行（房间加载成功也不给看）；
                // loggedOut 兜中途掉线——统一认证还在但座位系统会话丢了。
                state.session != SessionState.Valid || state.loggedOut -> SessionGate(
                    title = "预约座位需要先登录",
                    reason = "登录后就能选座、查看你的预约了。",
                    onLogin = onOpenWebLogin,
                    modifier = Modifier.fillMaxSize(),
                    onRetry = {
                        viewModel.retryLogin()
                        viewModel.refresh()
                    },
                    retryLabel = "已登录，重试",
                )

                // 统一 loading：首次数据（房间清单）没就绪前不把半成品推给用户
                state.loadingRooms && state.rooms.isEmpty() -> AppLoading(modifier = Modifier.fillMaxSize())

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
        // 首载由页面级统一 loading 接管；走到这里说明加载结束仍没有房间，才是真空态。
        PlaceholderBlock(title = "没有可用的自习室", description = "点右上角刷新，或稍后再试。")
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
 * - **32 座以内**：网格全图。座位抽象成圆角矩形，房间抽象成格子边长一致的网格，
 *   座位按坐标的相对次序落格：挨得近的坐标归并到同一格线，同桌座位自然聚成一组，
 *   明显大的间距（过道）多留一格，墙体只作画布边界不占格。
 * - **超过 32 座**：瓦片法。一次只画一片，顶上用片名切换，底下用概览图定位——
 *   一屏塞不下几十上百个座位，硬塞只会既看不清也点不准。片内同样用网格。
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
        } else if (placed.isNotEmpty()) {
            Column {
                SeatTileCanvas(
                    tile = SeatTile(
                        label = "全图",
                        bounds = SeatTileBounds(0f, 0f, 100f, 100f),
                        seats = placed.map { it.seat },
                    ),
                    entries = placed,
                    onSelect = onSelect,
                )
                MapHintRow(
                    text = "等距网格按相对位置摆放，过道多留一格；编号是房间内的座位号",
                    onZoom = { zoomed = true },
                )
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
            text = "整间 ${entries.size} 座，分 ${tiles.size} 片；片内按等距网格摆放，热区一律 48dp，超出宽度可左右拖动，均可直接点选。",
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

/** 片内画布：座位抽象成圆角矩形，房间抽象成格子边长一致的等距网格；热区不小于 48dp，画布宽于可用区时横向滚动。 */
@Composable
private fun SeatTileCanvas(
    tile: SeatTile,
    entries: List<SeatEntry>,
    onSelect: (SeatEntry) -> Unit,
    cellSize: Dp = SEAT_TILE_CELL,
) {
    val density = LocalDensity.current
    val layout = remember(tile, density, cellSize) { tileLayout(tile, 12.dp, density, cellSize) }
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

/** 片内座位的视觉边长，固定 48dp，与热区一致。 */
private val SEAT_TILE_CELL = 48.dp

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
 * 片内取景：把座位放进格子边长一致的等距网格，只保留相对位置，不按真实距离绘制。
 *
 * 先把横纵各自挨得近的坐标归并成格线（抖动算同一条线），再按次序排格号；
 * 相邻格线间距明显大于同桌间距（过道）时多空一格，墙体只作画布边界不占格。
 * 桌组因此自然聚拢，不会被服务端的坐标抖动拆散。
 */
private fun tileLayout(
    tile: SeatTile,
    padding: Dp,
    density: Density,
    cellSize: Dp = SEAT_TILE_CELL,
): TileLayout {
    val seats = tile.seats.mapNotNull { it.position }
    return with(density) {
        val cellPx = cellSize.toPx()
        val hitPx = maxOf(SEAT_TOUCH.toPx(), cellPx)
        val gapPx = 8.dp.toPx()
        val padPx = padding.toPx()
        val stepPx = cellPx + gapPx
        if (seats.isEmpty()) {
            return@with TileLayout(
                width = (hitPx + padPx * 2).toDp(),
                height = (hitPx + padPx * 2).toDp(),
                cell = cellSize,
                hit = hitPx.toDp(),
                xOf = { padPx.toDp() },
                yOf = { padPx.toDp() },
            )
        }
        val columns = clusterAxis(seats.map { it.x })
        val rows = clusterAxis(seats.map { it.y })
        val unitX = minLineGap(seats, axisX = true)
        val unitY = minLineGap(seats, axisX = false)
        val colIndex = gridIndices(columns, aisleThreshold(unitX))
        val rowIndex = gridIndices(rows, aisleThreshold(unitY))
        val maxCol = colIndex.lastOrNull() ?: 0
        val maxRow = rowIndex.lastOrNull() ?: 0
        TileLayout(
            width = (padPx * 2 + maxCol * stepPx + cellPx).toDp(),
            height = (padPx * 2 + maxRow * stepPx + cellPx).toDp(),
            cell = cellSize,
            hit = hitPx.toDp(),
            xOf = { x -> (padPx + colIndex[columns.nearestIndex(x)] * stepPx).toDp() },
            yOf = { y -> (padPx + rowIndex[rows.nearestIndex(y)] * stepPx).toDp() },
        )
    }
}

/** 同桌座位的正常间距；过道判定阈值取它的 1.5 倍。量不出来时不留过道格。 */
private fun aisleThreshold(unit: Float?): Float =
    if (unit != null && unit > 0f) unit * SEAT_AISLE_FACTOR else Float.MAX_VALUE

/** 坐标抖动的归并半径，百分比单位；实测同一排的抖动不超过 1。 */
private const val SEAT_COORD_EPS = 1.0f

/** 相邻格线间距超过同桌正常间距的这个倍数，就认定中间隔着一条过道，多空一格。 */
private const val SEAT_AISLE_FACTOR = 1.5f

/**
 * 把一维坐标归并成格线：排序后相邻差在 [SEAT_COORD_EPS] 内的算同一条线，取均值。
 * 返回升序格线坐标。
 */
private fun clusterAxis(values: List<Float>): List<Float> {
    if (values.isEmpty()) return emptyList()
    val sorted = values.sorted()
    val lines = ArrayList<Float>()
    var groupStart = 0
    for (i in 1 until sorted.size) {
        if (sorted[i] - sorted[i - 1] > SEAT_COORD_EPS) {
            var sum = 0f
            for (j in groupStart until i) sum += sorted[j]
            lines.add(sum / (i - groupStart))
            groupStart = i
        }
    }
    var sum = 0f
    for (j in groupStart until sorted.size) sum += sorted[j]
    lines.add(sum / (sorted.size - groupStart))
    return lines
}

/** 格线排格号：首条为 0，相邻间距超过 [aisleThreshold]（过道）时隔 2 格，否则挨着排。 */
private fun gridIndices(lines: List<Float>, aisleThreshold: Float): IntArray {
    val indices = IntArray(lines.size)
    for (i in 1 until lines.size) {
        indices[i] = indices[i - 1] + if (lines[i] - lines[i - 1] > aisleThreshold) 2 else 1
    }
    return indices
}

/** 坐标到最近格线的下标；空表返回 0，让调用方兜底。 */
private fun List<Float>.nearestIndex(value: Float): Int {
    if (isEmpty()) return 0
    var best = 0
    var bestDist = Float.MAX_VALUE
    forEachIndexed { index, line ->
        val dist = kotlin.math.abs(line - value)
        if (dist < bestDist) {
            bestDist = dist
            best = index
        }
    }
    return best
}

/** 同一行（y 相同）相邻座位的最小 x 间距；[axisX] 为 false 时求同一列的最小 y 间距。无同行/列座位时返回 null。 */
private fun minLineGap(seats: List<SeatPosition>, axisX: Boolean): Float? {
    var best = Float.MAX_VALUE
    seats.groupBy { if (axisX) it.y else it.x }.values.forEach { line ->
        val values = line.map { if (axisX) it.x else it.y }.sorted()
        for (i in 1 until values.size) {
            val gap = values[i] - values[i - 1]
            if (gap > 0f && gap < best) best = gap
        }
    }
    return if (best == Float.MAX_VALUE) null else best
}

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    SeatTileCanvas(
                        tile = SeatTile(
                            label = "全图",
                            bounds = SeatTileBounds(0f, 0f, 100f, 100f),
                            seats = placed.map { it.seat },
                        ),
                        entries = placed,
                        onSelect = onSelect,
                        // 放大图把格子抬到 1.5 倍，编号更清楚；热区跟着格子走。
                        cellSize = SEAT_TILE_CELL * 1.5f,
                    )
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
