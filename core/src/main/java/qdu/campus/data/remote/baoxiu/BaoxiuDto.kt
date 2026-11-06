package qdu.campus.data.remote.baoxiu

import kotlinx.serialization.Serializable

/**
 * 报修系统的外壳与 DTO。
 *
 * 除图片上传外，所有接口都只有 `{msg, code}` 这一层，业务数据挂在
 * `user` / `info` / `Option` / `page` / `repairInfo` 这些**顶层键**上，没有 `data` 包装
 * （2026-11-05 在真机内嵌页里逐条录得，见 `docs/interfaces/baoxiu.md` 第五节）。
 * 外壳按业务键分开写，是因为不同接口挂的键不一样，用一个泛型 T 套所有键会拿不到值。
 */
interface BaoxiuAck {
    val msg: String?
    val code: Int

    val isSuccess: Boolean get() = code == CODE_OK

    companion object {
        const val CODE_OK = 0
        /** 令牌失效：页面侧表现为整页跳回登录。 */
        const val CODE_UNAUTHORIZED = 401
    }
}

@Serializable
data class BaoxiuSimpleAck(
    override val msg: String? = null,
    override val code: Int = -1,
) : BaoxiuAck

@Serializable
data class BaoxiuUserEnvelope(
    override val msg: String? = null,
    override val code: Int = -1,
    val user: BaoxiuUserDto? = null,
) : BaoxiuAck

@Serializable
data class BaoxiuInfoEnvelope(
    override val msg: String? = null,
    override val code: Int = -1,
    val info: BaoxiuTicketDto? = null,
) : BaoxiuAck

@Serializable
data class BaoxiuPageEnvelope(
    override val msg: String? = null,
    override val code: Int = -1,
    val page: BaoxiuPageDto? = null,
) : BaoxiuAck

@Serializable
data class BaoxiuRepairEnvelope(
    override val msg: String? = null,
    override val code: Int = -1,
    val repairInfo: BaoxiuRepairInfoDto? = null,
) : BaoxiuAck

@Serializable
data class BaoxiuOptionEnvelope(
    override val msg: String? = null,
    override val code: Int = -1,
    val Option: List<BaoxiuOptionDto> = emptyList(),
) : BaoxiuAck

/** 下拉项。`value` 是 32 位十六进制 ID，`label` 是给人看的名字。 */
@Serializable
data class BaoxiuOptionDto(
    val value: String? = null,
    val label: String? = null,
    val disabled: Boolean? = null,
)

@Serializable
data class BaoxiuUserDto(
    val userId: String? = null,
    val username: String? = null,
    val realName: String? = null,
    val mobile: String? = null,
    val personname: String? = null,
)

/** 「我的报修」一行。字段多于客户端用到的部分，多余的不声明。 */
@Serializable
data class BaoxiuTicketDto(
    val id: String? = null,
    val applicantId: String? = null,
    val applicantName: String? = null,
    val applicantPhone: String? = null,
    val campus: String? = null,
    val building: String? = null,
    val detailAddress: String? = null,
    val maintenanceGroup: String? = null,
    val maintenanceItem: String? = null,
    val maintenanceDesc: String? = null,
    val photoUrl: String? = null,
    val applyTime: String? = null,
    val state: String? = null,
    val stateStr: String? = null,
    val evaluateState: Int? = null,
    val completeTime: String? = null,
    val appointmentTime: String? = null,
    val caogao: Boolean? = null,
) {
    /** 草稿不该出现在「我的报修」里，服务端没过滤时客户端补一刀。 */
    val isDraft: Boolean get() = caogao == true
}

@Serializable
data class BaoxiuPageDto(
    val totalCount: Int = 0,
    val pageSize: Int = 0,
    val totalPage: Int = 0,
    val currPage: Int = 0,
    val list: List<BaoxiuTicketDto> = emptyList(),
)

/** 工单主档。比列表多出 ID 与一批流程字段。 */
@Serializable
data class BaoxiuRepairInfoDto(
    val id: String? = null,
    val applicantId: String? = null,
    val applicantName: String? = null,
    val applicantPhone: String? = null,
    val campusId: String? = null,
    val buildingId: String? = null,
    val detailAddress: String? = null,
    val maintenanceGroupId: String? = null,
    val maintenanceItemId: String? = null,
    val maintenanceDesc: String? = null,
    val applyTime: String? = null,
    val appointmentTime: String? = null,
    val dispatchTime: String? = null,
    val state: String? = null,
    val evaluateState: Int? = null,
    val evaluateLevel: Int? = null,
    val evaluateRemark: String? = null,
    val refuseReason: String? = null,
    val stopReason: String? = null,
    val needRepairAgain: Int? = null,
    val videoUrl: String? = null,
)

/** 进度流水的一跳。`operateDetail` 是动作名，`remark` 是说明。 */
@Serializable
data class BaoxiuFlowDto(
    val id: String? = null,
    val lastState: String? = null,
    val nowState: String? = null,
    val operateDetail: String? = null,
    val remark: String? = null,
    val updateTime: String? = null,
)

@Serializable
data class BaoxiuFlowResponse(
    val msg: String? = null,
    val repairFlow: List<BaoxiuFlowDto> = emptyList(),
    val completePhotoUrl: String? = null,
    val completeUrlList: List<String>? = null,
)

/** 图片上传走另一套外壳：`resCode=1` 表示成功，业务数据在 `resData`。 */
@Serializable
data class BaoxiuUploadDto(
    val resCode: Int = 0,
    val resData: BaoxiuUploadData? = null,
    val resMsg: List<String>? = null,
)

@Serializable
data class BaoxiuUploadData(val fileUrl: String? = null)
