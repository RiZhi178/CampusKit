package qdu.campus.data.repository

import qdu.campus.data.remote.xg.XgApi
import qdu.campus.domain.model.XgAwardPage
import qdu.campus.domain.model.XgLeaveFormPage
import qdu.campus.domain.model.XgLeaveListPage
import qdu.campus.domain.model.XgLeaveRecord
import qdu.campus.domain.model.XgLeaveWriteResult
import qdu.campus.domain.repository.XgRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学工的页面每次进来现读，不落库。
 *
 * 评奖评优的记录行还没有一条真实样本，请假记录也随审批状态变动，
 * 为缓存给没验证过的结构建表不划算；写操作更是以服务端重读为准，本地不存草稿。
 */
@Singleton
class XgRepositoryImpl @Inject constructor(
    private val api: XgApi,
) : XgRepository {

    override suspend fun schoolAwardApply(): XgAwardPage = api.schoolAwardApply()

    override suspend fun leaveList(): XgLeaveListPage = api.leaveList()

    override suspend fun leaveForm(recordId: String): XgLeaveFormPage = api.leaveForm(recordId)

    override suspend fun submitLeave(
        page: XgLeaveFormPage,
        edits: Map<String, String>,
        existingIds: Set<String>,
    ): XgLeaveWriteResult = api.submitLeave(page, edits, existingIds)

    override suspend fun revokeLeave(record: XgLeaveRecord, page: XgLeaveListPage): XgLeaveWriteResult =
        api.revokeLeave(record, page)

    override suspend fun revokeLeaveDetail(page: XgLeaveFormPage): XgLeaveWriteResult =
        api.revokeLeaveDetail(page)

    override suspend fun cascadeArea(
        page: XgLeaveFormPage,
        selectName: String,
        edits: Map<String, String>,
    ): XgLeaveFormPage = api.cascadeArea(page, selectName, edits)
}
