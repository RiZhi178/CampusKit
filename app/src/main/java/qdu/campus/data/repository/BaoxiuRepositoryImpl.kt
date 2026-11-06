package qdu.campus.data.repository

import qdu.campus.data.remote.baoxiu.BaoxiuApi
import qdu.campus.domain.model.RepairDetail
import qdu.campus.domain.model.RepairDraft
import qdu.campus.domain.model.RepairOption
import qdu.campus.domain.model.RepairTicket
import qdu.campus.domain.repository.BaoxiuRepository
import qdu.campus.domain.repository.SessionRepository
import qdu.campus.platform.common.RemoteDataException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 报修仓库。除了取学号与姓名，其余都是转发。
 *
 * 「上次表单」没有做预填：`queryLastForm` 在真实账号上只见过 `info: null`，
 * 非空时的字段形状没录过，按项目口径不猜字段，宁可不预填。
 */
@Singleton
class BaoxiuRepositoryImpl @Inject constructor(
    private val api: BaoxiuApi,
    private val session: SessionRepository,
) : BaoxiuRepository {

    override suspend fun tickets(page: Int): Pair<List<RepairTicket>, Int> {
        val applicantId = requireApplicantId()
        return api.list(applicantId = applicantId, page = page)
    }

    override suspend fun detail(ticketId: String): RepairDetail = api.detail(ticketId)

    override suspend fun campuses(): List<RepairOption> = api.campuses()

    override suspend fun buildings(campusId: String): List<RepairOption> = api.buildings(campusId)

    override suspend fun groups(): List<RepairOption> = api.groups()

    override suspend fun items(groupId: String): List<RepairOption> = api.items(groupId)

    override suspend fun lastDraft(): RepairDraft? = null

    override suspend fun uploadPhoto(bytes: ByteArray, fileName: String, mime: String): String =
        api.upload(bytes, fileName, mime)

    override suspend fun submit(draft: RepairDraft) {
        val applicantId = requireApplicantId()
        val user = api.userInfo()
        val name = user?.realName?.takeIf { it.isNotBlank() }
            ?: user?.personname?.takeIf { it.isNotBlank() }
            ?: throw RemoteDataException("读取报修人姓名失败")
        api.submit(draft = draft, applicantId = applicantId, applicantName = name)
    }

    override suspend fun cancel(ticketId: String, reason: String) {
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) throw RemoteDataException("请填写撤销原因")
        api.cancel(ticketId, trimmed)
    }

    /**
     * 报修的 `applicantId` 就是学号。优先用本机存的（引导页填过就有），
     * 没有时从报修自己的 `user/info` 取——学员在网页里手动输过学号的情况，
     * 本机未必存过，只认本地会让先登录后进来的人直接被挡下。
     */
    private suspend fun requireApplicantId(): String =
        session.studentId()?.takeIf { it.isNotBlank() }
            ?: api.userInfo()?.username?.takeIf { it.isNotBlank() }
            ?: throw RemoteDataException("读取学号失败，请先完成统一认证登录")
}
