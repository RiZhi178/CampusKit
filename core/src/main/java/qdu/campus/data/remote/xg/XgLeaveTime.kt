package qdu.campus.data.remote.xg

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 请假时间的口径，全局只留这一处。
 *
 * 站点的 laydate 配的是 `yyyy-MM-dd HH`（只到小时），但服务端回填与 2026-11-07 实测提交
 * 用的都是 `yyyy-MM-dd HH:mm`，两种都要认。时长按整小时截断：实测 22:00–22:30 那条
 * 列表里印的是「0天0小时」，半小时被抹平，所以这里也不四舍五入。
 */
object XgLeaveTime {

    const val PATTERN = "yyyy-MM-dd HH:mm"

    private val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern(PATTERN)
    private val HOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH")

    fun parse(text: String): LocalDateTime? {
        val trimmed = text.trim()
        return runCatching { LocalDateTime.parse(trimmed, MINUTE) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(trimmed, HOUR) }.getOrNull()
    }

    fun format(time: LocalDateTime): String = MINUTE.format(time)

    /** 表单上那句「共计 N 天 N 小时」；时间没填全或者倒挂时返回空串，界面就不印。 */
    fun durationText(begin: String, end: String): String {
        val from = parse(begin) ?: return ""
        val to = parse(end) ?: return ""
        val hours = Duration.between(from, to).toHours()
        if (hours < 0) return ""
        return "${hours / 24}天${hours % 24}小时"
    }
}