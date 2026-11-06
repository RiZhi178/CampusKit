package qdu.campus.data.remote.baoxiu

import qdu.campus.domain.model.RepairDetail
import qdu.campus.domain.model.RepairFlowNode
import qdu.campus.domain.model.RepairOption
import qdu.campus.domain.model.RepairTicket

/**
 * 报修 DTO → 领域模型的映射，都是纯函数，便于单测。
 *
 * 状态码只把实测到的两个写成中文：`20` 等待调度、`12` 已撤销（2026-11-05 录到的
 * 列表行 `state` 与 `stateStr` 一一对应）。其余状态一律用服务端给的 `stateStr`，
 * 拿不到就退回「状态 N」，**不要猜中文文案**。
 */
fun BaoxiuOptionDto.toOption(): RepairOption? {
    val id = value?.takeIf { it.isNotBlank() } ?: return null
    return RepairOption(id = id, label = label?.takeIf { it.isNotBlank() } ?: id)
}

fun List<BaoxiuOptionDto>.toOptions(): List<RepairOption> = mapNotNull { it.toOption() }

fun BaoxiuTicketDto.toTicket(): RepairTicket? {
    val ticketId = id?.takeIf { it.isNotBlank() } ?: return null
    val item = maintenanceItem.orEmpty().trim()
    val desc = maintenanceDesc.orEmpty().trim()
    return RepairTicket(
        id = ticketId,
        title = when {
            item.isNotEmpty() && desc.isNotEmpty() -> "$item-$desc"
            item.isNotEmpty() -> item
            else -> desc
        },
        location = listOf(building, detailAddress)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString("-")
            .ifEmpty { campus.orEmpty() },
        state = state,
        stateText = stateText(state, stateStr),
        applyTime = applyTime.orEmpty(),
        photoUrl = photoUrl?.takeIf { it.startsWith("http") },
    )
}

fun BaoxiuPageDto.toTickets(): List<RepairTicket> = list
    .filterNot { it.isDraft }
    .mapNotNull { it.toTicket() }

fun BaoxiuRepairInfoDto.toDetail(
    flow: BaoxiuFlowResponse?,
    campusName: String?,
    buildingName: String?,
    groupName: String?,
    itemName: String?,
): RepairDetail {
    // 详情主档只回两级事项的 ID，名字要靠下拉表反查；查不到时退回 ID，不编造名称。
    val group = groupName?.takeIf { it.isNotBlank() } ?: maintenanceGroupId.orEmpty()
    val item = itemName?.takeIf { it.isNotBlank() } ?: maintenanceItemId.orEmpty()
    return RepairDetail(
        id = id.orEmpty(),
        applicantName = applicantName.orEmpty(),
        phone = applicantPhone.orEmpty(),
        location = listOf(campusName, buildingName, detailAddress)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(""),
        itemPath = listOf(group, item).filter { it.isNotEmpty() }.joinToString("-"),
        desc = maintenanceDesc.orEmpty(),
        applyTime = applyTime.orEmpty(),
        state = state,
        stateText = stateText(state, null),
        // 详情接口本身不回照片地址，完成照在进度接口里；没有就留空。
        photoUrl = flow?.completePhotoUrl?.takeIf { it.startsWith("http") },
        flow = flow?.repairFlow.orEmpty().mapNotNull { it.toNode() },
    )
}

fun BaoxiuFlowDto.toNode(): RepairFlowNode? {
    val nodeId = id?.takeIf { it.isNotBlank() } ?: return null
    return RepairFlowNode(
        id = nodeId,
        title = operateDetail.orEmpty().ifEmpty { "状态变更" },
        time = updateTime.orEmpty(),
        remark = remark.orEmpty(),
    )
}

fun stateText(state: String?, stateStr: String?): String {
    val text = stateStr?.trim().orEmpty()
    if (text.isNotEmpty()) return text
    return when (state) {
        "20" -> "等待调度"
        "12" -> "已撤销"
        null, "" -> ""
        else -> "状态 $state"
    }
}
