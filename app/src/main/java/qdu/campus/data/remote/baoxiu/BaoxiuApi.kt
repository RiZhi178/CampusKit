package qdu.campus.data.remote.baoxiu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import qdu.campus.domain.model.RepairDetail
import qdu.campus.domain.model.RepairDraft
import qdu.campus.domain.model.RepairOption
import qdu.campus.domain.model.RepairTicket
import qdu.campus.platform.common.RemoteDataException
import qdu.campus.platform.network.CookieBridge
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.network.appJson
import qdu.campus.platform.network.serverFailure
import javax.inject.Inject
import javax.inject.Singleton

/** 报修会话失效：换票过期或令牌被清，调用方据此回内嵌页重新登录。 */
class BaoxiuAuthException : Exception("报修登录已过期")

/**
 * 宿舍报修（苏迪智慧报修）接口。
 *
 * 与其它校内系统最大的不同：**除 Cookie 外每个请求还要带一个 `token` 头**，
 * 值就是 Cookie 里的 `token`（页面那边从 localStorage 读同一个值）。
 * 实测只带 Cookie 会回 `{"msg":"invalid token","code":401}`，
 * 补上该头立刻 200（2026-11-05）。所以这里统一从 CookieBridge 取，不另存令牌。
 *
 * 接口清单、字段与证据见 `docs/interfaces/baoxiu.md` 第五节。
 */
@Singleton
class BaoxiuApi @Inject constructor(
    private val http: HttpClientFactory,
    private val cookies: CookieBridge,
) {

    suspend fun userInfo(): BaoxiuUserDto? = withContext(Dispatchers.IO) {
        val ack = appJson.decodeFromString(
            BaoxiuUserEnvelope.serializer(),
            execute(Request.Builder().url(url("/appsys/sys/user/info"))),
        )
        ack.require()
        ack.user
    }

    /** 「我的报修」一页。返回列表与是否还有下一页。 */
    suspend fun list(applicantId: String, page: Int = 1, size: Int = PAGE_SIZE): Pair<List<RepairTicket>, Int> =
        withContext(Dispatchers.IO) {
            val raw = execute(
                Request.Builder().url(
                    url("/appor/wx/repair/list") {
                        addQueryParameter("applicantId", applicantId)
                        addQueryParameter("queryMyRepair", "1")
                        addQueryParameter("page", page.toString())
                        addQueryParameter("size", size.toString())
                    },
                ).post(EMPTY_JSON.toRequestBody(JSON_MEDIA)),
            )
            val ack = appJson.decodeFromString(BaoxiuPageEnvelope.serializer(), raw)
            ack.require()
            val dto = ack.page ?: return@withContext emptyList<RepairTicket>() to 0
            dto.toTickets() to dto.totalCount
        }

    /**
     * 工单详情。主档只给校区/楼栋 ID，名字要靠两张下拉表反查，
     * 所以这里顺手把校区与楼栋列表取回来做一次映射（都是只读、缓存里没有也很快）。
     */
    suspend fun detail(ticketId: String): RepairDetail = withContext(Dispatchers.IO) {
        val infoRaw = execute(Request.Builder().url(url("/appor/repairmanage/applicationManage/query/$ticketId")))
        val info = appJson.decodeFromString(BaoxiuRepairEnvelope.serializer(), infoRaw)
        info.require()
        val dto = info.repairInfo ?: throw RemoteDataException("工单不存在")

        val flowRaw = execute(Request.Builder().url(url("/appor/repairmanage/applicationManage/queryFlow/$ticketId")))
        val flow = runCatching {
            appJson.decodeFromString(BaoxiuFlowResponse.serializer(), flowRaw)
        }.getOrNull()

        val campusName = runCatching { campuses().firstOrNull { it.id == dto.campusId }?.label }.getOrNull()
        val buildingName = runCatching {
            val campusId = dto.campusId ?: return@runCatching null
            buildings(campusId).firstOrNull { it.id == dto.buildingId }?.label
        }.getOrNull()

        // 两级事项同样只给 ID，名字从下拉表反查；取不到就让映射退回 ID。
        val groupName = runCatching {
            groups().firstOrNull { it.id == dto.maintenanceGroupId }?.label
        }.getOrNull()
        val itemName = runCatching {
            val groupId = dto.maintenanceGroupId ?: return@runCatching null
            items(groupId).firstOrNull { it.id == dto.maintenanceItemId }?.label
        }.getOrNull()

        dto.toDetail(flow, campusName, buildingName, groupName, itemName)
    }

    /** 进度流水单独取，详情页的「撤销」判定与刷新都用它。 */
    suspend fun flow(ticketId: String): BaoxiuFlowResponse = withContext(Dispatchers.IO) {
        appJson.decodeFromString(
            BaoxiuFlowResponse.serializer(),
            execute(Request.Builder().url(url("/appor/repairmanage/applicationManage/queryFlow/$ticketId"))),
        )
    }

    suspend fun campuses(): List<RepairOption> = options(
        path = "/appor/baseset/campus/getCampusByRoleId",
    )

    suspend fun buildings(campusId: String): List<RepairOption> = options(
        path = "/appor/baseset/building/getBuildingByRoleId",
    ) {
        addQueryParameter("campusId", campusId)
        addQueryParameter("type", HOUQIN)
    }

    suspend fun groups(): List<RepairOption> = options(
        path = "/appor/baseset/maintenanceItemGroup/getMaintenanceItemGroupByRoleId",
    ) {
        addQueryParameter("type", HOUQIN)
    }

    suspend fun items(groupId: String): List<RepairOption> = options(
        path = "/appor/baseset/maintenanceItem/getMaintenanceItem",
    ) {
        addQueryParameter("groupId", groupId)
    }

    /** 上一次提交过的表单；没有历史时为 null。用来预填校区与地址。 */
    suspend fun lastForm(applicantId: String): BaoxiuTicketDto? = withContext(Dispatchers.IO) {
        val raw = execute(
            Request.Builder().url(
                url("/appor/repairmanage/applicationManage/queryLastForm") {
                    addQueryParameter("applicantId", applicantId)
                },
            ),
        )
        val ack = appJson.decodeFromString(BaoxiuInfoEnvelope.serializer(), raw)
        ack.require()
        ack.info
    }

    /**
     * 图片上传。字段名固定为 `file`，回 `resData.fileUrl`。
     * 注意这套外壳是 `resCode=1` 表示成功，与其它接口的 `code=0` 不同。
     */
    suspend fun upload(bytes: ByteArray, fileName: String, mime: String): String =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, bytes.toRequestBody(mime.toMediaType()))
                .build()
            val raw = execute(
                Request.Builder()
                    .url(url("/fdfs/upload"))
                    .post(body)
                    .header("sysCode", "appteam")
                    .header("moduleId", "test"),
            )
            val ack = appJson.decodeFromString(BaoxiuUploadDto.serializer(), raw)
            val fileUrl = ack.resData?.fileUrl
            if (ack.resCode != 1 || fileUrl.isNullOrBlank()) {
                throw RemoteDataException(ack.resMsg?.firstOrNull() ?: "图片上传失败")
            }
            fileUrl
        }

    /** 提交报修。成功只回 `{msg:"success", code:0}`，工单号要重新拉列表才有。 */
    suspend fun submit(
        draft: RepairDraft,
        applicantId: String,
        applicantName: String,
    ): Unit = withContext(Dispatchers.IO) {
        val payload = BaoxiuAddRepairRequest(
            t = System.currentTimeMillis(),
            applicantId = applicantId,
            applicantName = applicantName,
            applicantPhone = draft.phone,
            campusId = draft.campusId,
            buildingId = draft.buildingId,
            detailAddress = draft.detailAddress,
            maintenanceGroupId = draft.groupId,
            maintenanceItemId = draft.itemId,
            maintenanceDesc = draft.desc,
            photoUrl = draft.photoUrl.orEmpty(),
        )
        val raw = execute(
            Request.Builder()
                .url(url("/appor/repairmanage/applicationManage/addRepair"))
                .post(appJson.encodeToString(BaoxiuAddRepairRequest.serializer(), payload).toRequestBody(JSON_MEDIA)),
        )
        appJson.decodeFromString(BaoxiuSimpleAck.serializer(), raw).require()
    }

    /** 撤销。**原因必填**（网页端不填只弹提示，不发请求）。 */
    suspend fun cancel(ticketId: String, reason: String): Unit = withContext(Dispatchers.IO) {
        val payload = appJson.encodeToString(
            BaoxiuCancelRequest.serializer(),
            BaoxiuCancelRequest(id = ticketId, cancelReason = reason),
        )
        val raw = execute(
            Request.Builder()
                .url(url("/appor/repairmanage/applicationManage/cancel"))
                .post(payload.toRequestBody(JSON_MEDIA)),
        )
        appJson.decodeFromString(BaoxiuSimpleAck.serializer(), raw).require()
    }

    private suspend fun options(path: String, block: HttpUrl.Builder.() -> Unit = {}): List<RepairOption> =
        withContext(Dispatchers.IO) {
            val raw = execute(
                Request.Builder()
                    .url(url(path, block))
                    .post(EMPTY_JSON.toRequestBody(JSON_MEDIA)),
            )
            val ack = appJson.decodeFromString(BaoxiuOptionEnvelope.serializer(), raw)
            ack.require()
            ack.Option.toOptions()
        }

    private fun url(path: String, block: HttpUrl.Builder.() -> Unit = {}): String =
        (ORIGIN + path).toHttpUrl().newBuilder().apply(block).build().toString()

    private fun execute(builder: Request.Builder): String {
        val request = builder
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "$ORIGIN/wxweb/")
            .apply { tokenHeader()?.let { header("token", it) } }
            .build()
        val response = runCatching { http.client.newCall(request).execute() }
            .getOrElse { throw RemoteDataException("无法连接报修系统：${it.message}") }
        response.use {
            if (!it.isSuccessful) throw it.serverFailure("报修系统")
            val text = it.body?.string().orEmpty()
            if (text.isBlank()) throw RemoteDataException("报修系统返回空内容")
            if (text.trimStart().startsWith("<")) throw BaoxiuAuthException()
            return text
        }
    }

    /** 令牌就在 Cookie 里，取出来当请求头用；取不到时交给服务端回 401。 */
    private fun tokenHeader(): String? = cookies.cookieHeader(ORIGIN)
        ?.split(';')
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("token=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }

    private fun <T : BaoxiuAck> T.require(): T {
        if (code == BaoxiuAck.CODE_UNAUTHORIZED) throw BaoxiuAuthException()
        if (!isSuccess) throw RemoteDataException(msg ?: "报修系统操作失败")
        return this
    }

    companion object {
        /** 报修只有 http，白名单见 res/xml/network_security_config.xml。 */
        const val ORIGIN = "http://baoxiu.qdu.edu.cn"

        /** 事项与楼栋都按后勤口径过滤。 */
        private const val HOUQIN = "houqin"
        private const val PAGE_SIZE = 10
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val EMPTY_JSON = "{}"
    }
}

/** `addRepair` 请求体。常量项（source / isOpen / persontype）照抄网页端。 */
@Serializable
private data class BaoxiuAddRepairRequest(
    val t: Long,
    val applicantId: String,
    val applicantName: String,
    val applicantPhone: String,
    val source: Int = 1,
    val campusId: String,
    val buildingId: String,
    val detailAddress: String,
    val maintenanceGroupId: String,
    val maintenanceItemId: String,
    val maintenanceDesc: String,
    val photoUrl: String,
    val isOpen: Int = 1,
    val videoUrl: String = "",
    val appointmentTime: String = "",
    val appointmentTimeEnd: String = "",
    val persontype: String = "X",
)

@Serializable
private data class BaoxiuCancelRequest(val id: String, val cancelReason: String)
