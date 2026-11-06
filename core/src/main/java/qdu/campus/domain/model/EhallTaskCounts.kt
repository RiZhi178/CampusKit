package qdu.campus.domain.model

/**
 * 办事大厅侧边栏的任务计数。
 *
 * 来源是 `/taskcenterapp/.../getTaskCount.do`，实测返回 `amp_jsonp_callback([0,0,0])`，
 * 三个数按门户自己 [amp.js] 的取用顺序分别是待办、已办、流程跟踪。
 * 门户只把待办和流程跟踪显示成角标，已办取出来但不在列表页展示。
 */
data class EhallTaskCounts(
    val pending: Int,
    val done: Int,
    val tracked: Int,
) {
    val hasWork: Boolean get() = pending > 0 || tracked > 0
}
