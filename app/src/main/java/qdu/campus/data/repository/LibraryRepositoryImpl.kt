package qdu.campus.data.repository

import qdu.campus.data.remote.lib.LibraryApi
import qdu.campus.domain.model.LibraryBorrowing
import qdu.campus.domain.model.LibraryHolding
import qdu.campus.domain.model.LibraryReader
import qdu.campus.domain.model.LibrarySearchPage
import qdu.campus.domain.repository.LibraryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 实时查、不落 Room（AGENTS 1.6 的 C 类）：在架状态每次都要现问服务端，
 * 缓存一份「在架」反而会把人骗到书架前。
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val api: LibraryApi,
) : LibraryRepository {

    override fun hasSession(): Boolean = api.hasSession()

    override suspend fun search(keyword: String, page: Int): LibrarySearchPage =
        api.search(keyword, page)

    override suspend fun holdings(recordId: Long): List<LibraryHolding> = api.holdings(recordId)

    override suspend fun loans(): LibraryBorrowing = api.loans()

    override suspend fun loanHistory(): LibraryBorrowing = api.loanHistory()

    override suspend fun reader(): LibraryReader? = api.reader()
}
