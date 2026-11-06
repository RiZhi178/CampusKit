package qdu.campus.data.repository

import qdu.campus.data.remote.zzmp.ZzmpApi
import qdu.campus.domain.model.RoomAccount
import qdu.campus.domain.repository.ZzmpRepository
import javax.inject.Inject
import javax.inject.Singleton

/** 实时查、不落库：余额每次进来重新取，不拿旧快照当新数。 */
@Singleton
class ZzmpRepositoryImpl @Inject constructor(
    private val api: ZzmpApi,
) : ZzmpRepository {

    override suspend fun checkRoomCode(code: String): RoomAccount = api.checkRoomCode(code)
}
