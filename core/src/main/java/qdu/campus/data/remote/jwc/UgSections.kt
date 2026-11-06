package qdu.campus.data.remote.jwc

import qdu.campus.domain.model.ClassSection
import java.time.LocalDate

/**
 * 本科生作息与节次表。
 *
 * 出处：教务处《关于 2026 年部分节假日期间教学工作及作息时间安排的通知》。
 * 每小节课 50 分钟，课间 10 分钟；第 5 节起分春夏、秋冬两档，
 * 换档日期是 5 月 1 日与 10 月 1 日——所以一学期的上课时间会中途整体前移半小时，
 * 这是学校安排，不是数据错。用户觉得不对，可在「学期与节次」里逐节改。
 *
 * 本科生课表按两小节并成一大节来排（第 1–2 节连上最常见），第 11 节单独一大节。
 */
object UgSections {

    enum class Season { SpringSummer, AutumnWinter }

    /** 一小节的起止时间，`08:00` / `08:50`。 */
    data class Period(val index: Int, val startTime: String, val endTime: String)

    private val morning = listOf(
        Period(1, "08:00", "08:50"),
        Period(2, "09:00", "09:50"),
        Period(3, "10:10", "11:00"),
        Period(4, "11:10", "12:00"),
    )

    private val afternoonSS = listOf(
        Period(5, "14:00", "14:50"),
        Period(6, "15:00", "15:50"),
        Period(7, "16:00", "16:50"),
        Period(8, "17:00", "17:50"),
    )

    private val afternoonAW = listOf(
        Period(5, "13:30", "14:20"),
        Period(6, "14:30", "15:20"),
        Period(7, "15:30", "16:20"),
        Period(8, "16:30", "17:20"),
    )

    private val eveningSS = listOf(
        Period(9, "19:00", "19:50"),
        Period(10, "20:00", "20:50"),
        Period(11, "21:00", "21:50"),
    )

    private val eveningAW = listOf(
        Period(9, "18:30", "19:20"),
        Period(10, "19:30", "20:20"),
        Period(11, "20:30", "21:20"),
    )

    /** 两小节并成的一大节；第 11 节落单，是因为当天只排到第 11 节。 */
    private val groups = listOf(
        intArrayOf(1, 2) to "上午",
        intArrayOf(3, 4) to "上午",
        intArrayOf(5, 6) to "下午",
        intArrayOf(7, 8) to "下午",
        intArrayOf(9, 10) to "晚上",
        intArrayOf(11) to "晚上",
    )

    fun periods(season: Season): List<Period> =
        morning + (if (season == Season.SpringSummer) afternoonSS else afternoonAW) +
            (if (season == Season.SpringSummer) eveningSS else eveningAW)

    /** 5 月 1 日至 9 月 30 日执行春夏作息，其余执行秋冬作息。 */
    fun seasonOf(date: LocalDate): Season {
        val md = date.monthValue * 100 + date.dayOfMonth
        return if (md in 501..930) Season.SpringSummer else Season.AutumnWinter
    }

    fun sections(season: Season): List<ClassSection> {
        val byIndex = periods(season).associateBy { it.index }
        return groups.mapIndexed { position, (covers, dayPart) ->
            val first = byIndex[covers.first()]
            val last = byIndex[covers.last()]
            ClassSection(
                index = position + 1,
                startTime = first?.startTime.orEmpty(),
                endTime = last?.endTime.orEmpty(),
                period = dayPart,
                label = if (covers.size == 1) "第${covers.first()}节"
                else "第${covers.first()}-${covers.last()}节",
                covers = covers.toList(),
            )
        }
    }

    /** 某几个小节的整体起止，用于课程详情上的时间行；小节号越界就返回空串。 */
    fun timeRange(season: Season, smallPeriods: List<Int>): Pair<String, String> {
        val byIndex = periods(season).associateBy { it.index }
        val first = smallPeriods.minOrNull()?.let { byIndex[it] } ?: return "" to ""
        val last = smallPeriods.maxOrNull()?.let { byIndex[it] } ?: return "" to ""
        return first.startTime to last.endTime
    }
}
