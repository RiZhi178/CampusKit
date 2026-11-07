package qdu.campus.domain.model

import java.time.LocalDate

/**
 * 校历调停课的日级规则，一行 = 一天，来自 Room 的 `calendar_days` 表
 * （种子源是 assets 里的校历资产，配对来自教务处通知）。
 *
 * 只描述规则，不改动任何课表数据：courses / sections / ug_timetable_entries
 * 原样保留，周视图渲染时按这里的结果临时换算。
 */
data class DayOverride(
    val date: LocalDate,
    /** true = 假期日，当天课全部停上；false = 调休日，执行 [followsDate] 的教学安排。 */
    val holiday: Boolean,
    /**
     * 调休日执行哪一天的教学安排。配对必须写具体日期而不是星期几：
     * 借的是那天所在教学周的课（如 2026-09-20 执行 2026-10-06 的安排，
     * 上的是第 4 周周二的课，与 9 月 20 日自己所在的第 3 周无关）。
     */
    val followsDate: LocalDate? = null,
)

/**
 * 调停课的换算规则。
 *
 * 口径与教务处通知一致：假期日整天无课；调休日全天执行被借那天的安排，
 * 原当天的课让路。没有规则的日期照常按自身星期取课。
 */
object CalendarRules {

    /**
     * [date] 这天实际执行「哪一天」的教学安排。
     * 返回 null = 假期日停课；否则返回被借的日期（普通日返回自身）。
     */
    fun follows(date: LocalDate, overrides: Map<LocalDate, DayOverride>): LocalDate? {
        val override = overrides[date] ?: return date
        return if (override.holiday) null else (override.followsDate ?: date)
    }

    /** 该日的展示标注：假期「休」、调休「调」。普通日无标注。 */
    fun note(date: LocalDate, overrides: Map<LocalDate, DayOverride>): String? {
        val override = overrides[date] ?: return null
        return if (override.holiday) "休" else "调"
    }
}
