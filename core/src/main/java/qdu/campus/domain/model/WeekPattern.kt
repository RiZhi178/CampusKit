package qdu.campus.domain.model

/**
 * 周次集合的人话描述。
 *
 * 服务端给的是 `pkzcmx` 逐个周次的明细，直接拼成「3,4,5,6,7…」既读不动也看不出
 * 单双周；原型 05 屏写的是「第 3–18 周（每周）」「第 3–10 周（单周）」，按那份口径生成。
 */
object WeekPattern {

    /** 空集合返回空串，占位文案由调用方决定。 */
    fun describe(weeks: Set<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.toSortedSet()
        val first = sorted.min()
        val last = sorted.max()
        if (sorted.size == 1) return "第 $first 周"
        return when {
            sorted.size == last - first + 1 -> "第 $first–$last 周（每周）"
            sorted.all { it % 2 == 1 } -> "第 $first–$last 周（单周）"
            sorted.all { it % 2 == 0 } -> "第 $first–$last 周（双周）"
            else -> runs(sorted.toList()).joinToString("、") { range ->
                if (range.first == range.last) "第 ${range.first} 周"
                else "第 ${range.first}–${range.last} 周"
            }
        }
    }

    /** 拆成连续段，供上面那种既非整周连续、也非单双周整齐的周次用。 */
    private fun runs(sorted: List<Int>): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var start = sorted.first()
        var previous = start
        sorted.drop(1).forEach { value ->
            if (value != previous + 1) {
                result += start..previous
                start = value
            }
            previous = value
        }
        result += start..previous
        return result
    }
}
