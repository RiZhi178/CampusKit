package qdu.campus.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import qdu.campus.domain.model.Seat
import qdu.campus.domain.model.SeatPosition

class SeatTilingTest {

    private fun seat(id: Int, x: Float, y: Float): Seat = Seat(
        devId = id,
        devName = "H8-ZXS01-$id",
        devSn = 100000 + id,
        position = SeatPosition(x, y),
        openStart = "07:00",
        openEnd = "22:00",
        openTimes = emptyList(),
        rule = null,
        occupancies = emptyList(),
        onlyView = false,
    )

    private fun grid(cols: Int, rows: Int): List<Seat> {
        val out = mutableListOf<Seat>()
        var id = 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = if (cols == 1) 50f else 4f + c * (92f / (cols - 1))
                val y = if (rows == 1) 50f else 4f + r * (92f / (rows - 1))
                out += seat(id++, x, y)
            }
        }
        return out
    }

    @Test
    fun `32 座以内走全图，33 座开始切瓦片`() {
        assertFalse(SeatTiling.shouldUseTiles(32))
        assertTrue(SeatTiling.shouldUseTiles(33))
        assertFalse(SeatTiling.shouldUseTiles(0))
    }

    @Test
    fun `小房间即使切分也不丢座位`() {
        // UI 在 32 座以内不走瓦片，这里只保证这个纯函数本身不乱
        val seats = grid(cols = 8, rows = 4)
        val tiles = SeatTiling.build(seats)
        assertEquals(seats.size, tiles.sumOf { it.seats.size })
        assertEquals(seats.map { it.devId }.toSet(), tiles.flatMap { t -> t.seats.map { it.devId } }.toSet())
    }

    @Test
    fun `120 座切成四片且不丢座位`() {
        val seats = grid(cols = 12, rows = 10)
        val tiles = SeatTiling.build(seats)

        assertEquals(4, tiles.size)
        assertEquals(listOf("A", "B", "C", "D"), tiles.map { it.label })
        // 一片都没超过上限，且所有座位恰好出现一次
        assertTrue(tiles.all { it.seats.size in 1..40 })
        val ids = tiles.flatMap { tile -> tile.seats.map { it.devId } }
        assertEquals(seats.size, ids.size)
        assertEquals(seats.map { it.devId }.toSet(), ids.toSet())
        // 顺序：先上后下、先左后右
        assertTrue(tiles[0].bounds.y0 <= tiles[2].bounds.y0)
        assertTrue(tiles[0].bounds.x0 <= tiles[1].bounds.x0)
    }

    @Test
    fun `没有坐标的座位不参与切分`() {
        val seats = listOf(
            seat(1, 10f, 10f).copy(position = null),
            seat(2, 20f, 20f),
        )
        val tiles = SeatTiling.build(seats)
        val total = tiles.sumOf { it.seats.size }
        assertEquals(1, total)
    }

    @Test
    fun `贴边的座位也算进片里`() {
        val seats = listOf(seat(1, 0f, 0f), seat(2, 100f, 100f))
        val tiles = SeatTiling.build(seats)
        assertEquals(2, tiles.sumOf { it.seats.size })
    }

    @Test
    fun `空列表不产生瓦片`() {
        assertTrue(SeatTiling.build(emptyList()).isEmpty())
    }

    @Test
    fun `放大倍率保证相邻座位的热区不重叠`() {
        // 间距本来就够，用基础倍率即可
        assertEquals(2.4f, SeatTiling.zoomForTouch(48f, 2.4f), 0.001f)
        assertEquals(2.4f, SeatTiling.zoomForTouch(20f, 2.4f), 0.001f)
        // 密集房间按 48 / 间距 抬高倍率，放大后间距恰好落在 48dp
        val dense = SeatTiling.zoomForTouch(10f, 2.4f)
        assertEquals(4.8f, dense, 0.001f)
        assertTrue(10f * dense >= SeatTiling.MIN_TOUCH_DP)
        // 拿不到间距时不要算出无穷大，退回基础倍率
        assertEquals(2.4f, SeatTiling.zoomForTouch(0f, 2.4f), 0.001f)
        assertEquals(2.4f, SeatTiling.zoomForTouch(-1f, 2.4f), 0.001f)
    }
}
