package qdu.campus.domain.model

/**
 * 办事大厅（苏迪 AMP）里的一个服务入口。
 *
 * 只有文字字段：服务端给的图标是 `middleIcon` 图片 URL，本项目刻意不引图片加载库，
 * 列表按名称与描述排。`id` 是 `appId`，打开入口用它换 `appShow` 跳转，
 * 不直接用 `pcOpenUrl` —— 后者是应用自己的地址，第三方应用多数还要再走一次登录握手。
 */
data class EhallApp(
    val id: String,
    val name: String,
    val description: String,
    /** 所属分类名；服务端没给分类时为空串。 */
    val category: String,
    /** false 表示当前角色看不到详情，页面标出来但不隐藏，点了让它自己拒。 */
    val hasPermission: Boolean,
    val maintaining: Boolean,
)
