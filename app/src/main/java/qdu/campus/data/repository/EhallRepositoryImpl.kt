package qdu.campus.data.repository

import qdu.campus.data.remote.ehall.EhallApi
import qdu.campus.domain.model.EhallApp
import qdu.campus.domain.model.EhallTaskCounts
import qdu.campus.domain.repository.EhallRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 实时查、不落 Room（AGENTS 1.6 的 C 类）：清单按学期变动，但用户点开大厅就是要看
 * 当下能办什么，缓存旧清单反而误导。
 */
@Singleton
class EhallRepositoryImpl @Inject constructor(
    private val api: EhallApi,
) : EhallRepository {

    override suspend fun services(): List<EhallApp> = api.fetchServices()

    override suspend fun taskCounts(): EhallTaskCounts? = api.fetchTaskCounts()

    override fun entryUrl(app: EhallApp): String = api.entryUrl(app.id)
}
