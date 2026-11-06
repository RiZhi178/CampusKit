package qdu.campus.platform.common

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 时间显示统一入口，避免各页面各自格式化。 */
object Formats {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val dateFormatter = DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日 HH:mm", Locale.CHINA)

    fun today(): LocalDate = LocalDate.now(zone)

    /** 当前日期与时刻。座位的可约窗口和「今天还能不能约」都靠它判定。 */
    fun todayTime(): LocalDateTime = LocalDateTime.now(zone)

    /** 当前挂钟时间，固定按学校所在时区取，避免手机时区设置影响「在不在上课」。 */
    fun now(): LocalTime = LocalTime.now(zone)

    /** 学分：整数学分不显示成「2.0」，本科的半学分（0.5、1.5）保留原样。 */
    fun credit(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    /**
     * 距下一个整分钟还有多久，多给 100ms 让心跳落在边界之后。
     *
     * 座位的可约时段以分钟为粒度，卡在 59.9 秒重算拿到的还是上一分钟，
     * 下一次就得再等一整分钟。
     */
    fun millisUntilNextMinute(): Long {
        val now = todayTime()
        val boundary = now.plusMinutes(1).withSecond(0).withNano(0)
        return Duration.between(now, boundary).toMillis() + 100L
    }

    /** 课表里的时间串形如「10:10」；解析不了返回 null，由调用方退回节次文案。 */
    fun parseTime(value: String?): LocalTime? =
        value?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalTime.parse(it) }.getOrNull()
        }

    fun hhmm(time: LocalTime): String = String.format(Locale.CHINA, "%02d:%02d", time.hour, time.minute)

    fun monthDay(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return ""
        return dateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    }

    /** 「10 月 28 日」。 */
    fun monthDay(date: LocalDate): String = dateFormatter.format(date)

    /** 「10 月 28 日 · 周三」，首页状态条的日期行。 */
    fun dateWithWeekday(date: LocalDate): String =
        "${monthDay(date)} · ${weekdayLabel(date.dayOfWeek.value)}"

    fun fullDate(epochMillis: Long?): String {
        if (epochMillis == null || epochMillis <= 0L) return ""
        return fullDateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))
    }

    /** 「11 月 2 日 13:04」，消息记录用。 */
    fun dateTime(epochMillis: Long): String =
        dateTimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

    /** 同一格式，给已经转成本地时间的调用方用。为空返回空串。 */
    fun dateTime(value: LocalDateTime?): String =
        value?.let(dateTimeFormatter::format).orEmpty()

    /** 「13:04」。为空返回空串。 */
    fun hhmm(value: LocalDateTime?): String =
        value?.let { String.format(Locale.CHINA, "%02d:%02d", it.hour, it.minute) }.orEmpty()

    /** 在线时长：把秒数写成「x 小时 y 分」。 */
    fun duration(seconds: Long?): String {
        if (seconds == null || seconds < 0) return ""
        if (seconds < 60) return "$seconds 秒"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "$hours 小时 $minutes 分" else "$minutes 分"
    }

    fun bytes(value: Long?): String {
        if (value == null || value < 0) return ""
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var index = 0
        while (size >= 1024 && index < units.lastIndex) {
            size /= 1024
            index++
        }
        return if (index == 0) "${size.toLong()} ${units[index]}"
        else String.format(Locale.CHINA, "%.1f %s", size, units[index])
    }

    fun weekdayLabel(day: Int): String =
        when (day) {
            1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
            5 -> "周五"; 6 -> "周六"; 7 -> "周日"
            else -> ""
        }

    /** 「11·02」，首页「今日」旁的日期。 */
    fun monthDayDot(date: LocalDate): String =
        String.format(Locale.CHINA, "%02d·%02d", date.monthValue, date.dayOfMonth)

    /**
     * 学期标识转可读名：`2026-2027-1` → 「2026–2027 学年 第一学期」。
     * 校历没有单独的名称接口，标识本身就是人可读的，解析不了时原样显示，
     * 免得把一个用户填进去的值换成空白。
     */
    fun semesterLabel(semesterId: String): String {
        val parts = semesterId.split('-')
        if (parts.size < 3) return semesterId
        val yearEnd = parts[1].toIntOrNull() ?: return semesterId
        val index = parts[2].toIntOrNull() ?: return semesterId
        val ordinal = when (index) {
            1 -> "第一"; 2 -> "第二"; 3 -> "第三"; 4 -> "第四"
            else -> return semesterId
        }
        return "${yearEnd - 1}–$yearEnd 学年 ${ordinal}学期"
    }

    /**
     * 通知标题里的日期截掉：列表下方的小字已经写了日期，标题再带一遍只会挤掉正文。
     * 截完不足 4 个字就保留原样，免得把「2026-10-28」这类纯日期标题变成空串。
     */
    fun noticeTitle(title: String): String {
        val match = DATE_IN_TITLE.find(title) ?: return title.trim()
        val head = title.substring(0, match.range.first).trimEnd(
            ' ', '　', '（', '(', '[', '【', '-', '—', '·', '、', ',', '，',
        )
        return if (head.length >= 4) head else title.trim()
    }

    private val DATE_IN_TITLE =
        Regex("""\d{4}\s*[-/.年]\s*\d{1,2}\s*[-/.月]\s*\d{1,2}\s*日?""")

    /**
     * 学号脱敏：保留前 4 位与后 4 位，中间统一 4 个星号（2024****0137）。
     * 没有学号时返回 null，交调用方决定说什么：这里若返回「未登录」，
     * 就等于让一个格式化函数替会话状态表态，而首页卡片另有来源，两行会自相矛盾。
     */
    fun maskStudentId(id: String?): String? {
        if (id.isNullOrBlank()) return null
        if (id.length <= 8) return id
        return id.take(4) + "*".repeat(4) + id.takeLast(4)
    }
}
