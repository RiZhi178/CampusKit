package qdu.campus.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class SeatLabelTest {

    @Test
    fun `取正式编号的尾段作为房间内座位号`() {
        assertEquals("40", SeatLabel.of("H8-ZXS01-40"))
        assertEquals("7", SeatLabel.of("H8-ZXS01-07"))
        assertEquals("0", SeatLabel.of("H8-ZXS01-000"))
    }

    @Test
    fun `尾段不是数字时给占位符，不把整串编号上屏`() {
        assertEquals("·", SeatLabel.of(""))
        assertEquals("·", SeatLabel.of("H8-ZXS01"))
        assertEquals("·", SeatLabel.of("H8-ZXS01-A3"))
    }

    @Test
    fun `回执带上房间名与座位号`() {
        assertEquals("八楼自习室 座位 40", SeatLabel.bookingPlace("八楼自习室", "H8-ZXS01-40"))
        assertEquals("座位 40", SeatLabel.bookingPlace(null, "H8-ZXS01-40"))
        assertEquals("座位 40", SeatLabel.bookingPlace("  ", "H8-ZXS01-40"))
        assertEquals("八楼自习室 座位 ·", SeatLabel.bookingPlace("八楼自习室", "无编号"))
    }
}
