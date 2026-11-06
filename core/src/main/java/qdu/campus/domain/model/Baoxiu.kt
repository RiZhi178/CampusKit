package qdu.campus.domain.model

/** 「我的报修」列表的一项。 */
data class RepairTicket(
    val id: String,
    /** 列表标题：`事项-描述`，与网页端一致。 */
    val title: String,
    val location: String,
    val state: String?,
    val stateText: String,
    val applyTime: String,
    val photoUrl: String?,
)

/** 工单详情。字段取自 `query/<工单号>`，进度另取 `queryFlow/<工单号>`。 */
data class RepairDetail(
    val id: String,
    val applicantName: String,
    val phone: String,
    val location: String,
    /** 两级事项拼成的一行，例如「木工（门锁窗、家具维修）-门锁窗、家具维修」。 */
    val itemPath: String,
    val desc: String,
    val applyTime: String,
    val state: String?,
    val stateText: String,
    val photoUrl: String?,
    val flow: List<RepairFlowNode> = emptyList(),
)

/** 进度流水的一跳。 */
data class RepairFlowNode(
    val id: String,
    val title: String,
    val time: String,
    val remark: String,
)

/** 下拉项：校区、楼栋、事项分组、事项都复用这个形状（`value` 是 32 位 ID）。 */
data class RepairOption(
    val id: String,
    val label: String,
)

/** 「我要报修」表单的草稿。字段与 `addRepair` 的请求体一一对应。 */
data class RepairDraft(
    val campusId: String = "",
    val campusName: String = "",
    val buildingId: String = "",
    val buildingName: String = "",
    val detailAddress: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val itemId: String = "",
    val itemName: String = "",
    val desc: String = "",
    val photoUrl: String? = null,
    val phone: String = "",
)
