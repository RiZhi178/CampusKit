package qdu.campus.domain.usecase

import qdu.campus.domain.model.Seat
import qdu.campus.domain.model.SeatRule
import qdu.campus.domain.model.SeatStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 座位可约状态与可选时段。
 *
 * 场所管理系统不下发状态字段，网页端的座位颜色是用 `resvRule` + `openTimes` +
 * `resvInfo` 在浏览器里现算的。这里按同一套规则重算，规则或字段变了只改本文件。
 */
object SeatAvailability {

    private const val MINUTES_PER_DAY = 1440
    private const val FALLBACK_INTERVAL = 30

    /** 可约日期窗口，闭区间。窗口来自规则，与房间无关时取任一座位的规则即可。 */
    fun bookableWindow(rule: SeatRule?, today: LocalDate): ClosedDateRange? {
        if (rule == null) return null
        val from = today.plusDays(ceilDay(rule.latestResvTime).toLong())
        val to = today.plusDays(ceilDay(rule.earliestResvTime).toLong())
        if (to < from) return null
        return ClosedDateRange(from, to)
    }

    /**
     * 座位在 [start, end] 内的状态。
     *
     * 网页端先选时段再看座位，所以状态是相对时段的，不是相对整天的。
     */
    fun status(seat: Seat, start: LocalDateTime, end: LocalDateTime, now: LocalDateTime): SeatStatus {
        val rule = seat.rule ?: return SeatStatus.Closed
        if (seat.onlyView) return SeatStatus.OnSiteOnly
        if (seat.openTimes.isEmpty()) return SeatStatus.Closed
        if (seat.openStart == seat.openEnd) return SeatStatus.Closed
        if (start.isBefore(now)) return SeatStatus.Closed

        val today = now.toLocalDate()
        val window = bookableWindow(rule, today) ?: return SeatStatus.Closed
        val date = start.toLocalDate()
        if (date < window.from || date > window.to) return SeatStatus.Closed

        if (!seat.openTimes.any { contains(it.startTime, it.endTime, start, end) }) {
            return SeatStatus.Closed
        }

        val covering = seat.occupancies.filter { it.covers(start, end) }
        if (covering.isNotEmpty()) return SeatStatus.Busy
        if (seat.occupancies.any { it.overlaps(start, end) }) return SeatStatus.Partial
        return SeatStatus.Free
    }

    /** 可选开始时间，"HH:mm"，升序去重。当天只给出此刻之后的整点间隔。 */
    fun startOptions(seat: Seat, date: LocalDate, now: LocalDateTime): List<String> {
        val rule = seat.rule ?: return emptyList()
        val interval = interval(rule.timeInterval)
        val minDuration = maxOf(rule.minResvTime, interval)
        // 当天的下限要**严格晚于**此刻：整点边界上（如 12:30 整）若把 12:30 也放进选项，
        // 一分钟后状态重算就判「开始时间已过」，整张表会集体翻成不开放（AVD 实测踩过）。
        val floor = if (date == now.toLocalDate()) {
            ceilTo(now.hour * 60 + now.minute + 1, interval)
        } else {
            0
        }
        return seat.openTimes.flatMap { segment ->
            val from = minutes(segment.startTime) ?: return@flatMap emptyList()
            val to = minutes(segment.endTime) ?: return@flatMap emptyList()
            val first = maxOf(from, floor)
            val last = to - minDuration
            if (first > last) emptyList() else (first..last step interval).map(::hhmm)
        }.distinct().sorted()
    }

    /** 给定开始时间后的可选结束时间，受最长预约时长与该时段关门时间双重约束。 */
    fun endOptions(seat: Seat, start: String): List<String> {
        val rule = seat.rule ?: return emptyList()
        val interval = interval(rule.timeInterval)
        val from = minutes(start) ?: return emptyList()
        val minDuration = maxOf(rule.minResvTime, interval)
        return seat.openTimes.mapNotNull { segment ->
            val segStart = minutes(segment.startTime) ?: return@mapNotNull null
            val segEnd = minutes(segment.endTime) ?: return@mapNotNull null
            if (from < segStart || from >= segEnd) return@mapNotNull null
            val cap = if (rule.maxResvTime > 0) minOf(from + rule.maxResvTime, segEnd) else segEnd
            val first = from + minDuration
            if (first > cap) emptyList() else (first..cap step interval).map(::hhmm)
        }.flatten().distinct().sorted()
    }

    /**
     * 网页端的 `ceilDay`：整天数向下取，不足一天进一位。
     * 规则里的 earliest/latestResvTime 都是分钟，0 表示当天。
     */
    internal fun ceilDay(value: Int): Int = when {
        value <= 0 -> 0
        value % MINUTES_PER_DAY == 0 -> value / MINUTES_PER_DAY
        else -> value / MINUTES_PER_DAY + 1
    }

    private fun interval(value: Int): Int = if (value > 0) value else FALLBACK_INTERVAL

    private fun contains(openStart: String, openEnd: String, start: LocalDateTime, end: LocalDateTime): Boolean {
        val from = minutes(openStart) ?: return false
        val to = minutes(openEnd) ?: return false
        val s = start.hour * 60 + start.minute
        val e = end.hour * 60 + end.minute
        return s >= from && e <= to && e > s
    }

    private fun ceilTo(value: Int, step: Int): Int = ((value + step - 1) / step) * step

    internal fun minutes(hhmm: String): Int? {
        val parts = hhmm.split(':')
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    internal fun hhmm(total: Int): String = "%02d:%02d".format(total / 60, total % 60)
}

/** 闭区间日期范围。 */
data class ClosedDateRange(val from: LocalDate, val to: LocalDate) {
    fun days(): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var cursor = from
        while (!cursor.isAfter(to)) {
            out += cursor
            cursor = cursor.plusDays(1)
        }
        return out
    }
}
