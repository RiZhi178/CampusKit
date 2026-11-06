package qdu.campus.data.remote.ehall

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import qdu.campus.domain.model.EhallApp
import qdu.campus.domain.model.EhallTaskCounts

/**
 * 办事大厅（苏迪 AMP 2.3.x）`/jsonp/` 前缀接口的响应。
 *
 * 与其它系统都不同的一点：**未登录不发 401，也不发业务码**，HTTP 恒 200，
 * 只在 JSON 里留 `{"hasLogin":false,"contextPath":""}`，其余键整个不存在。
 * 判登录与否只能读 [EhallAppListDto.hasLogin]，缺字段按未登录处理。
 */
@Serializable
data class EhallAppListDto(
    val hasLogin: Boolean = false,
    val contextPath: String? = null,
    /** 热门/推荐/新上线。真实响应里这一项和 [categoryList] 里的条目**是同一条**，展平要去重。 */
    val hotRecNewAppList: List<EhallAppDto> = emptyList(),
    val categoryList: List<EhallCategoryDto> = emptyList(),
)

/**
 * 分类节点。`subCategoryList` 在真实响应里是 null，但门户的分类树支持两级，
 * 展平时按深度优先走，父类自己没条目就只贡献分类名。
 */
@Serializable
data class EhallCategoryDto(
    val categoryId: String? = null,
    val categoryName: String? = null,
    val appList: List<EhallAppDto> = emptyList(),
    val subCategoryList: List<EhallCategoryDto>? = null,
)

/**
 * 服务条目。字段名与真实响应一致，服务端多出的几十个键（`appPc`、`appMobile`、
 * `appPcCardList`、`assessAvgScore`…）全部忽略。
 *
 * `entranceUrl` 在顶层是 null，真值藏在 `appPc` / `appMobile` 里，所以不取它——
 * 打开入口统一走 `appShow?appId=`，由服务端补上第三方应用自己的登录握手。
 */
@Serializable
data class EhallAppDto(
    val appId: String? = null,
    val appName: String? = null,
    val description: String? = null,
    val vendorName: String? = null,
    val hasPermission: Boolean? = null,
    val maintaining: Boolean? = null,
    val isNew: Int? = null,
)

/**
 * 展平成「分类 → 条目」的有序列表。
 *
 * 只走 [EhallAppListDto.categoryList]：`hotRecNewAppList` 是同一批条目的副本，
 * 真实样本（2026-11-04 匿名取到的一条「校园文化活动」）在两处各出现一次，
 * 两边都收会在列表里重一遍。缺 appId 或名称的条目丢弃，其余一律降级为默认值。
 */
fun EhallAppListDto.toServices(): List<EhallApp> {
    val seen = HashSet<String>()
    val out = mutableListOf<EhallApp>()

    fun walk(category: EhallCategoryDto, parent: String) {
        // 取自己这一级的名字，没有才退回上一级：两级树里叶子分类才是有用标签
        val label = category.categoryName.orEmpty().ifBlank { parent }
        category.appList.forEach { dto ->
            val id = dto.appId?.takeIf { it.isNotBlank() } ?: return@forEach
            val title = dto.appName?.takeIf { it.isNotBlank() } ?: return@forEach
            // 同一 appId 只保留第一次出现的分类归属
            if (!seen.add(id)) return@forEach
            out += EhallApp(
                id = id,
                name = title,
                description = dto.description?.takeIf { it.isNotBlank() && it != title }
                    ?: dto.vendorName.orEmpty(),
                category = label,
                hasPermission = dto.hasPermission ?: true,
                maintaining = dto.maintaining == true,
            )
        }
        category.subCategoryList.orEmpty().forEach { walk(it, label) }
    }

    categoryList.forEach { walk(it, "") }
    return out
}

/**
 * 解析侧边栏任务计数的 JSONP 响应。
 *
 * 实测载荷是 `amp_jsonp_callback([0,0,0])`，三个数依次待办、已办、流程跟踪
 * （门户自己也是这么取的，见 amp.js 的 `data[0] //待办`）。
 * **不带 callback 参数时服务端返回一页「系统异常」HTML**，所以这个接口必须按 JSONP 取。
 * 括号缺失、载荷不是数组、数组短于三项都算解析失败返回 null，由上层当作「没有计数」处理。
 */
fun parseEhallTaskCounts(raw: String): EhallTaskCounts? {
    val open = raw.indexOf('(')
    val close = raw.lastIndexOf(')')
    if (open < 0 || close <= open) return null
    val items = runCatching {
        Json.parseToJsonElement(raw.substring(open + 1, close)).jsonArray
    }.getOrNull()
    if (items == null || items.size < 3) return null

    // 三项少读出一个都不能补 0：「待办 0 项」是说没有事要办，和「没取到」是两回事。
    val numbers = items.take(3).map { (it as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull() }
    val pending = numbers[0] ?: return null
    val done = numbers[1] ?: return null
    val tracked = numbers[2] ?: return null
    return EhallTaskCounts(pending = pending, done = done, tracked = tracked)
}
