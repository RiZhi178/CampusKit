package qdu.campus.domain.usecase

import qdu.campus.domain.model.Seat

/** 房间内的一块方形区域，坐标沿用服务端的 0–100 百分比。 */
data class SeatTileBounds(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

/** 一个瓦片：它占房间的哪一块、块里有哪些座位。 */
data class SeatTile(
    val label: String,
    val bounds: SeatTileBounds,
    val seats: List<Seat>,
)

/**
 * 座位图的分档与切分。
 *
 * 32 座以内用网格全图：手机一屏放得下 8 列 × 4 行 48dp 的方格，既看得见房间形状又点得准。
 * 超过 32 座改瓦片：一次只画一片，片内座位固定大小，靠概览图找方向。
 *
 * 切分按座位数较多的那一轴二分，每片不超过 [MAX_SEATS_PER_TILE]，最多切 [MAX_DEPTH] 层。
 * 120 座的房间切成 4 片 × 30 座；之所以不再细切，是一张四人对坐的桌子被切开之后，
 * 两个座位会落在不同的片里，用户对着实景反而找不到。
 *
 * 只有带 `position` 的座位参与切分，没有坐标的由调用方单独列在「未定位座位」里。
 */
object SeatTiling {

    /** 不超过这个座位数就用全图，超过就切瓦片。 */
    const val FULL_MAP_LIMIT = 32

    /** 可点座位的最小热区，单位 dp。与 AGENTS 1.7 的 48dp 口径一致。 */
    const val MIN_TOUCH_DP = 48f

    /**
     * 放大视图需要的倍率：放大后相邻座位间距不小于 [MIN_TOUCH_DP]，
     * 这样每个座位的 48dp 热区才不会互相压住、点 A 落到 B 上。
     * 房间很密时倍率会高于基础倍率，代价是放大图要拖着看，比点不准好。
     */
    fun zoomForTouch(stepDp: Float, baseZoom: Float): Float {
        if (stepDp <= 0f) return baseZoom
        return maxOf(baseZoom, MIN_TOUCH_DP / stepDp)
    }

    /** 单片上限：座位数、列数、行数，任一超了就继续切。 */
    private const val MAX_SEATS_PER_TILE = 40
    private const val MAX_COLUMNS = 6
    private const val MAX_ROWS = 8
    private const val MAX_DEPTH = 4

    fun shouldUseTiles(seatedCount: Int): Boolean = seatedCount > FULL_MAP_LIMIT

    /** 按几何位置切分。座位在片内、片间都不会重复，也不会丢。 */
    fun build(seats: List<Seat>): List<SeatTile> {
        val placed = seats.filter { it.position != null }
        if (placed.isEmpty()) return emptyList()

        val chunks = mutableListOf<Pair<SeatTileBounds, List<Seat>>>()
        divide(placed, SeatTileBounds(0f, 0f, 100f, 100f), 0, chunks)

        // 概览图上的顺序：先上后下、先左后右，和人的阅读顺序一致。
        chunks.sortWith(compareBy({ it.first.y0 }, { it.first.x0 }))
        return chunks.mapIndexed { index, chunk ->
            SeatTile(label = labelOf(index), bounds = chunk.first, seats = chunk.second)
        }
    }

    /**
     * 二分切分：哪一边的座位排数多就沿哪一边切，切到「座位数、列数、行数都在上限内」为止。
     *
     * 不用四叉切，是因为四叉会把 13 列的横排一次切成 6 列 + 6 列的小块，片数翻倍而每片还是偏宽；
     * 二分按实际排数走，切出来的片更接近「一屏能放下的那些座位」。
     */
    private fun divide(
        seats: List<Seat>,
        bounds: SeatTileBounds,
        depth: Int,
        out: MutableList<Pair<SeatTileBounds, List<Seat>>>,
    ) {
        if (seats.isEmpty()) return
        val columns = seats.mapNotNull { it.position?.x }.distinct().size
        val rows = seats.mapNotNull { it.position?.y }.distinct().size
        val fits = seats.size <= MAX_SEATS_PER_TILE && columns <= MAX_COLUMNS && rows <= MAX_ROWS
        if (fits || depth >= MAX_DEPTH) {
            out += bounds to seats
            return
        }

        val alongX = columns >= rows
        val values = seats.mapNotNull { seat -> (if (alongX) seat.position?.x else seat.position?.y) }
        val split = median(values)
        val first = seats.filter { seat ->
            val v = if (alongX) seat.position?.x else seat.position?.y
            (v ?: 0f) < split
        }
        val second = seats.filter { seat ->
            val v = if (alongX) seat.position?.x else seat.position?.y
            (v ?: 0f) >= split
        }
        // 所有坐标都相同的情况，切不动就直接收下，避免无限递归。
        if (first.isEmpty() || second.isEmpty()) {
            out += bounds to seats
            return
        }

        val firstBounds: SeatTileBounds
        val secondBounds: SeatTileBounds
        if (alongX) {
            firstBounds = SeatTileBounds(bounds.x0, bounds.y0, split, bounds.y1)
            secondBounds = SeatTileBounds(split, bounds.y0, bounds.x1, bounds.y1)
        } else {
            firstBounds = SeatTileBounds(bounds.x0, bounds.y0, bounds.x1, split)
            secondBounds = SeatTileBounds(bounds.x0, split, bounds.x1, bounds.y1)
        }
        divide(first, firstBounds, depth + 1, out)
        divide(second, secondBounds, depth + 1, out)
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    /** A、B、C…；超过 26 片（正常房间不会出现）时退回「片27」。 */
    private fun labelOf(index: Int): String =
        if (index < 26) ('A' + index).toString() else "片${index + 1}"
}
