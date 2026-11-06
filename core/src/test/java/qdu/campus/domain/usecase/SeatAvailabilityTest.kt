package qdu.campus.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import qdu.campus.domain.model.Seat
import qdu.campus.domain.model.SeatOccupancy
import qdu.campus.domain.model.SeatOpenTime
import qdu.campus.domain.model.SeatRule
import qdu.campus.domain.model.SeatStatus
import java.time.LocalDate
import java.time.LocalDateTime

class SeatAvailabilityTest {

    /** 房间 34 的真实规则：不限最短、最长 6 小时、半小时一格、可约到明天。 */
    private val rule = SeatRule(
        minResvTime = 0,
        maxResvTime = 360,
        timeInterval = 30,
        earliestResvTime = 1440,
        latestResvTime = 0,
        cancelTime = 30,
    )

    private val today = LocalDate.of(2026, 11, 3)
    private val now: LocalDateTime = today.atTime(10, 0)

    private fun seat(
        rule: SeatRule? = this.rule,
        openTimes: List<SeatOpenTime> = listOf(SeatOpenTime("07:00", "22:00")),
        openStart: String = "07:00",
        openEnd: String = "22:00",
        occupancies: List<SeatOccupancy> = emptyList(),
        onlyView: Boolean = false,
    ) = Seat(
        devId = 1994,
        devName = "003",
        devSn = 3,
        position = null,
        openStart = openStart,
        openEnd = openEnd,
        openTimes = openTimes,
        rule = rule,
        occupancies = occupancies,
        onlyView = onlyView,
    )

    @Test
    fun `ceilDay 与网页端一致`() {
        assertEquals(0, SeatAvailability.ceilDay(0))
        assertEquals(0, SeatAvailability.ceilDay(-30))
        assertEquals(1, SeatAvailability.ceilDay(1440))
        assertEquals(2, SeatAvailability.ceilDay(1500))
        assertEquals(1, SeatAvailability.ceilDay(1))
        assertEquals(2, SeatAvailability.ceilDay(2880))
        assertEquals(3, SeatAvailability.ceilDay(2881))
    }

    @Test
    fun `可约窗口按规则的远端与近端算`() {
        // 服务端命名与语义相反：latestResvTime 是近端，earliestResvTime 是远端
        val window = SeatAvailability.bookableWindow(rule, today)!!
        assertEquals(today, window.from)
        assertEquals(today.plusDays(1), window.to)
        assertEquals(listOf(today, today.plusDays(1)), window.days())
    }

    @Test
    fun `只能约当天时窗口只有一天`() {
        val onlyToday = SeatAvailability.bookableWindow(rule.copy(earliestResvTime = 0), today)!!
        assertEquals(today, onlyToday.from)
        assertEquals(today, onlyToday.to)
    }

    @Test
    fun `远端早于近端时没有可约日期`() {
        val inverted = rule.copy(latestResvTime = 2880, earliestResvTime = 1440)
        assertNull(SeatAvailability.bookableWindow(inverted, today))
        assertNull(SeatAvailability.bookableWindow(null, today))
    }

    @Test
    fun `没有占用的座位在开放时段内空闲`() {
        val start = today.plusDays(1).atTime(9, 0)
        assertEquals(
            SeatStatus.Free,
            SeatAvailability.status(seat(), start, start.plusHours(3), now),
        )
    }

    @Test
    fun `占用完整覆盖时段为已约满`() {
        val start = today.plusDays(1).atTime(9, 0)
        val end = start.plusHours(3)
        val busy = seat(
            occupancies = listOf(SeatOccupancy("u", start.minusMinutes(30), end.plusMinutes(30))),
        )
        assertEquals(SeatStatus.Busy, SeatAvailability.status(busy, start, end, now))
        // 边界重合也算覆盖
        val exact = seat(occupancies = listOf(SeatOccupancy("u", start, end)))
        assertEquals(SeatStatus.Busy, SeatAvailability.status(exact, start, end, now))
    }

    @Test
    fun `占用只压住一部分时段为部分占用`() {
        // devId 2187 的真实占用 08:30–14:30
        val occupied = seat(
            occupancies = listOf(
                SeatOccupancy("u", today.plusDays(1).atTime(8, 30), today.plusDays(1).atTime(14, 30)),
            ),
        )
        // 09:00–12:00 被整段包住，是约满不是部分
        val inside = today.plusDays(1).atTime(9, 0)
        assertEquals(
            SeatStatus.Busy,
            SeatAvailability.status(occupied, inside, inside.plusHours(3), now),
        )
        // 13:00–16:00 只有一半被压住
        val straddling = today.plusDays(1).atTime(13, 0)
        assertEquals(
            SeatStatus.Partial,
            SeatAvailability.status(occupied, straddling, straddling.plusHours(3), now),
        )
    }

    @Test
    fun `时段首尾相接不算冲突`() {
        val start = today.plusDays(1).atTime(14, 30)
        val touching = seat(
            occupancies = listOf(
                SeatOccupancy("u", today.plusDays(1).atTime(8, 30), start),
            ),
        )
        assertEquals(SeatStatus.Free, SeatAvailability.status(touching, start, start.plusHours(2), now))
    }

    @Test
    fun `超出可约日期为关闭`() {
        val far = today.plusDays(3).atTime(9, 0)
        assertEquals(SeatStatus.Closed, SeatAvailability.status(seat(), far, far.plusHours(1), now))
    }

    @Test
    fun `已经过去的时段为关闭`() {
        val past = today.atTime(8, 0)
        assertEquals(SeatStatus.Closed, SeatAvailability.status(seat(), past, past.plusHours(1), now))
    }

    @Test
    fun `越出开放时间与缺规则都是关闭`() {
        val early = today.plusDays(1).atTime(6, 0)
        assertEquals(
            SeatStatus.Closed,
            SeatAvailability.status(seat(), early, early.plusHours(1), now),
        )
        val late = today.plusDays(1).atTime(21, 0)
        assertEquals(
            SeatStatus.Closed,
            SeatAvailability.status(seat(), late, late.plusHours(2), now),
        )
        assertEquals(
            SeatStatus.Closed,
            SeatAvailability.status(seat(rule = null), today.plusDays(1).atTime(9, 0), today.plusDays(1).atTime(10, 0), now),
        )
        // openStart == openEnd 是服务端表达「今天不开放」的方式
        assertEquals(
            SeatStatus.Closed,
            SeatAvailability.status(
                seat(openStart = "07:00", openEnd = "07:00"),
                today.plusDays(1).atTime(9, 0),
                today.plusDays(1).atTime(10, 0),
                now,
            ),
        )
        assertEquals(
            SeatStatus.Closed,
            SeatAvailability.status(
                seat(openTimes = emptyList()),
                today.plusDays(1).atTime(9, 0),
                today.plusDays(1).atTime(10, 0),
                now,
            ),
        )
    }

    @Test
    fun `只可现场使用的座位不参与线上预约`() {
        val start = today.plusDays(1).atTime(9, 0)
        assertEquals(
            SeatStatus.OnSiteOnly,
            SeatAvailability.status(seat(onlyView = true), start, start.plusHours(1), now),
        )
    }

    @Test
    fun `跨多个开放时段时只要有一段容纳即可`() {
        val segments = listOf(SeatOpenTime("07:00", "12:00"), SeatOpenTime("13:00", "22:00"))
        val start = today.plusDays(1).atTime(14, 0)
        assertEquals(
            SeatStatus.Free,
            SeatAvailability.status(seat(openTimes = segments), start, start.plusHours(2), now),
        )
        // 午休这一段没人开放，跨过去的时段不可约
        val across = today.plusDays(1).atTime(11, 0)
        assertEquals(
            SeatStatus.Closed,
            SeatAvailability.status(seat(openTimes = segments), across, across.plusHours(3), now),
        )
    }

    @Test
    fun `非当天的开始时间从开门起按半小时排`() {
        val options = SeatAvailability.startOptions(seat(), today.plusDays(1), now)
        assertEquals("07:00", options.first())
        // 最长 6 小时，21:30 之后开始就凑不满一格
        assertEquals("21:30", options.last())
        assertEquals(30, options.size)
        assertEquals("07:30", options[1])
    }

    @Test
    fun `当天的开始时间不能早于此刻`() {
        val at1307 = today.atTime(13, 7)
        val options = SeatAvailability.startOptions(seat(), today, at1307)
        assertEquals("13:30", options.first())
        assertEquals("21:30", options.last())
        assertEquals(17, options.size)
    }

    @Test
    fun `此刻正好落在边界上时不给出这一档`() {
        // AVD 实测：12:30 整把 12:30 列为可选，一分钟后状态重算就判「开始时间已过」，整张表翻成不开放
        val at1300 = today.atTime(13, 0)
        val options = SeatAvailability.startOptions(seat(), today, at1300)
        assertEquals("13:30", options.first())
        // 锁住不变式：给出的第一档在下一分钟仍然可选
        val start = today.atTime(13, 30)
        assertEquals(
            SeatStatus.Free,
            SeatAvailability.status(seat(), start, start.plusHours(1), today.atTime(13, 1)),
        )
    }

    @Test
    fun `此刻已经晚于最后一个可开始时间时没有选项`() {
        val late = today.atTime(21, 45)
        assertTrue(SeatAvailability.startOptions(seat(), today, late).isEmpty())
    }

    @Test
    fun `没有规则就没有可选时间`() {
        assertTrue(SeatAvailability.startOptions(seat(rule = null), today.plusDays(1), now).isEmpty())
        assertTrue(SeatAvailability.endOptions(seat(rule = null), "09:00").isEmpty())
    }

    @Test
    fun `结束时间受最长时长与关门时间双重限制`() {
        val options = SeatAvailability.endOptions(seat(), "08:00")
        assertEquals("08:30", options.first())
        // 08:00 + 360 分钟 = 14:00，早于 22:00 关门
        assertEquals("14:00", options.last())
        assertEquals(12, options.size)
    }

    @Test
    fun `临近关门时结束时间被关门时间截断`() {
        assertEquals(listOf("22:00"), SeatAvailability.endOptions(seat(), "21:30"))
        // 21:00 + 6 小时 = 次日 03:00，被截到 22:00
        assertEquals(listOf("21:30", "22:00"), SeatAvailability.endOptions(seat(), "21:00"))
    }

    @Test
    fun `开始时间不在任何开放时段内就没有结束选项`() {
        assertTrue(SeatAvailability.endOptions(seat(), "06:00").isEmpty())
        assertTrue(SeatAvailability.endOptions(seat(), "22:00").isEmpty())
        assertTrue(SeatAvailability.endOptions(seat(), "坏时间").isEmpty())
    }

    @Test
    fun `不限时长时结束时间一直到关门`() {
        val unlimited = seat(rule = rule.copy(maxResvTime = 0))
        val options = SeatAvailability.endOptions(unlimited, "08:00")
        assertEquals("08:30", options.first())
        assertEquals("22:00", options.last())
        assertEquals(28, options.size)
    }

    @Test
    fun `最短时长生效时开始与结束都往后推`() {
        val withMin = seat(rule = rule.copy(minResvTime = 60))
        // 最短 1 小时，最后一个可开始时间从 21:30 退到 21:00
        assertEquals("21:00", SeatAvailability.startOptions(withMin, today.plusDays(1), now).last())
        assertEquals("09:00", SeatAvailability.endOptions(withMin, "08:00").first())
    }

    @Test
    fun `时间间隔缺失时退回半小时`() {
        val noInterval = seat(rule = rule.copy(timeInterval = 0))
        val options = SeatAvailability.startOptions(noInterval, today.plusDays(1), now)
        assertEquals("07:00", options.first())
        assertEquals("07:30", options[1])
    }

    @Test
    fun `hhmm 与 minutes 互逆`() {
        assertEquals(0, SeatAvailability.minutes("00:00"))
        assertEquals(420, SeatAvailability.minutes("07:00"))
        assertEquals(1320, SeatAvailability.minutes("22:00"))
        assertNull(SeatAvailability.minutes("24:00"))
        assertNull(SeatAvailability.minutes("7"))
        assertNull(SeatAvailability.minutes(""))
        assertEquals("07:05", SeatAvailability.hhmm(425))
        assertEquals("22:00", SeatAvailability.hhmm(1320))
    }
}
