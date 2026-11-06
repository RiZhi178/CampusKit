package qdu.campus.data.repository

import qdu.campus.data.remote.csyy.CsyyApi
import qdu.campus.data.remote.csyy.CsyySessionMinter
import qdu.campus.data.remote.csyy.toAccount
import qdu.campus.data.remote.csyy.toReservation
import qdu.campus.data.remote.csyy.toSeat
import qdu.campus.domain.model.Seat
import qdu.campus.domain.model.SeatAccount
import qdu.campus.domain.model.SeatReservation
import qdu.campus.domain.model.SeatRoom
import qdu.campus.domain.repository.SeatRepository
import qdu.campus.platform.common.CsyyAuthException
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeatRepositoryImpl @Inject constructor(
    private val api: CsyyApi,
    private val sessionMinter: CsyySessionMinter,
) : SeatRepository {

    /** `seatMenu` 实测公开，无会话也返回 code 0，不参与会话判定也不值得免登。 */
    override suspend fun rooms(): List<SeatRoom> = api.fetchRooms()

    override suspend fun seats(roomId: Int, date: LocalDate): List<Seat> =
        withRefreshedSession { api.fetchSeats(roomId, date) }
            .mapNotNull { it.toSeat() }
            .sortedBy { it.devSn }

    override suspend fun reservations(from: LocalDate, to: LocalDate): List<SeatReservation> =
        withRefreshedSession { api.fetchReservations(from, to) }.mapNotNull { it.toReservation() }

    override suspend fun account(): SeatAccount? =
        runCatching { withRefreshedSession { api.fetchAccount() }?.toAccount() }.getOrNull()

    override suspend fun book(
        seat: Seat,
        start: LocalDateTime,
        end: LocalDateTime,
        memo: String,
    ): String {
        // 预约体要带一卡通账号，只有 account/info 给得准，不能拿本机存的学号顶替。
        // 免登只做到这一步为止：它发生在 POST 发出之前，下单本身不重放。
        val account = withRefreshedSession { api.fetchAccount() }?.toAccount()
            ?: throw CsyyAuthException()
        return api.reserve(
            accNo = account.accNo,
            devId = seat.devId,
            start = start,
            end = end,
            memo = memo.trim(),
        )
    }

    override suspend fun cancel(uuid: String): String = mintWithoutReplay { api.cancel(uuid) }

    override suspend fun endEarly(uuid: String): String = mintWithoutReplay { api.endEarly(uuid) }

    /**
     * 判成 300 不一定是「没登录」：统一认证票还在时，本域会话过期是可以静默补的。
     * 补完原地重试一次，再失败才交回上层。
     */
    private suspend fun <T> withRefreshedSession(block: suspend () -> T): T =
        try {
            block()
        } catch (auth: CsyyAuthException) {
            if (sessionMinter.mint()) block() else throw auth
        }

    /**
     * 写操作只补会话、不重放请求。
     *
     * 服务端回 300 说明它没处理这一笔，但拿「大概没处理」去赌一次重放的代价是重复预约，
     * 省的那一次点击不值这个钱。补完再点一次就能成。
     */
    private suspend fun <T> mintWithoutReplay(block: suspend () -> T): T =
        try {
            block()
        } catch (auth: CsyyAuthException) {
            sessionMinter.mint()
            throw auth
        }
}
