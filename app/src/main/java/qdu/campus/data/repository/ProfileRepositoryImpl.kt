package qdu.campus.data.repository

import qdu.campus.domain.repository.ProfileRepository
import qdu.campus.domain.repository.SeatRepository
import qdu.campus.platform.security.CredentialStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 姓名只在 csyy `account/info` 给得出来，所以这里不自己造来源：
 * 成功一次就落盘，之后账号卡直接读缓存，离线或校外也照常显示。
 *
 * 取不到时不写空串盖掉旧值——上次已知姓名比「没有姓名」更有用。
 */
@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val seats: SeatRepository,
    private val credentials: CredentialStore,
) : ProfileRepository {

    override fun cachedName(): String? = credentials.userName()

    override suspend fun syncName(): String? {
        val name = seats.account()?.name?.trim().orEmpty()
        if (name.isNotBlank()) credentials.saveUserName(name)
        return name.ifBlank { credentials.userName() }
    }
}
