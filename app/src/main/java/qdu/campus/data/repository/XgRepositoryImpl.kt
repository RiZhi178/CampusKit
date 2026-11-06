package qdu.campus.data.repository

import qdu.campus.data.remote.xg.XgApi
import qdu.campus.domain.model.XgAwardPage
import qdu.campus.domain.repository.XgRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学工的页面每次进来现读，不落库。
 *
 * 落库要等表格列的真面目录到样本再说：现在只有批次头是实测过的，
 * 记录行还没有一条真实样本，为了缓存给一个没验证过的结构建表不划算。
 */
@Singleton
class XgRepositoryImpl @Inject constructor(
    private val api: XgApi,
) : XgRepository {

    override suspend fun schoolAwardApply(): XgAwardPage = api.schoolAwardApply()
}
