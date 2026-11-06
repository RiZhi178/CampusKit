package qdu.campus.data.remote.csyy

import kotlinx.serialization.Serializable
import qdu.campus.domain.model.Seat
import qdu.campus.domain.model.SeatAccount
import qdu.campus.domain.model.SeatOccupancy
import qdu.campus.domain.model.SeatOpenTime
import qdu.campus.domain.model.SeatPosition
import qdu.campus.domain.model.SeatReservation
import qdu.campus.domain.model.SeatRoom
import qdu.campus.domain.model.SeatRule
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 场所管理系统（csyy）的响应外壳。
 *
 * `code` 是业务码不是 HTTP 码：0 成功，300 未登录，10010 需要刷新页面。
 * 服务端字段远多于这里列的，反序列化必须忽略未知键。
 */
@Serializable
data class CsyyEnvelope<T>(
    val code: Int = CODE_UNKNOWN,
    val message: String? = null,
    val data: T? = null,
    val count: Int? = null,
) {
    val isSuccess: Boolean get() = code == CODE_OK
    val isLoggedOut: Boolean get() = code == CODE_LOGGED_OUT

    companion object {
        const val CODE_OK = 0
        const val CODE_LOGGED_OUT = 300
        const val CODE_UNKNOWN = -1
    }
}

/** seatMenu 的树节点。三层：校区 → 楼 → 房间，房间是叶子。 */
@Serializable
data class SeatMenuNodeDto(
    val id: Int? = null,
    val name: String? = null,
    val totalCount: Int? = null,
    val remainCount: Int? = null,
    val children: List<SeatMenuNodeDto>? = null,
)

@Serializable
data class CsyyOpenTimeDto(
    val openStartTime: String? = null,
    val openEndTime: String? = null,
)

@Serializable
data class CsyyResvRuleDto(
    val minResvTime: Int? = null,
    val maxResvTime: Int? = null,
    val timeInterval: Int? = null,
    val earliestResvTime: Int? = null,
    val latestResvTime: Int? = null,
    val cancelTime: Int? = null,
)

/** 座位已有的一条预约。startTime / endTime 是 epoch 毫秒。 */
@Serializable
data class CsyyResvInfoDto(
    val uuid: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val resvStatus: Int? = null,
)

@Serializable
data class CsyySeatDto(
    val devId: Int? = null,
    val devSn: Int? = null,
    val devName: String? = null,
    val onlyView: Boolean? = null,
    val coordinate: String? = null,
    val openStart: String? = null,
    val openEnd: String? = null,
    val openTimes: List<CsyyOpenTimeDto>? = null,
    val resvRule: CsyyResvRuleDto? = null,
    val resvInfo: List<CsyyResvInfoDto>? = null,
)

@Serializable
data class CsyyResvDevDto(
    val devName: String? = null,
    val roomName: String? = null,
)

@Serializable
data class CsyyReservationDto(
    val uuid: String? = null,
    val testName: String? = null,
    val resvBeginTime: Long? = null,
    val resvEndTime: Long? = null,
    val resvEndRealTime: Long? = null,
    val resvStatus: Int? = null,
    val signTime: Long? = null,
    val latestCheckInTime: Long? = null,
    val endEarly: Boolean? = null,
    val resvDevInfoList: List<CsyyResvDevDto>? = null,
)

@Serializable
data class CsyyAccountDto(
    val accNo: String? = null,
    val trueName: String? = null,
)

/**
 * 预约请求体。字段与网页端 seatForm 提交的完全一致，
 * `resvProperty=32`、`memberKind=1` 是座位（sysKind=8）固定值。
 */
@Serializable
data class CsyyReserveRequest(
    val sysKind: Int,
    val appAccNo: String,
    val memberKind: Int,
    val resvMember: List<String>,
    val resvBeginTime: String,
    val resvEndTime: String,
    val testName: String,
    val captcha: String,
    val resvProperty: Int,
    val resvDev: List<Int>,
    val memo: String,
)

/** 取消与提前结束都只认预约的 uuid。 */
@Serializable
data class CsyyUuidRequest(val uuid: String)

private val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/** epoch 毫秒 → 本地时间。0 与 null 都当作「没有」。 */
internal fun Long?.toLocalDateTime(): LocalDateTime? =
    this?.takeIf { it > 0 }?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZONE) }

/** 展平房间树。叶子即房间，祖先名依次是校区与楼。 */
fun List<SeatMenuNodeDto>.toRooms(): List<SeatRoom> {
    val rooms = mutableListOf<SeatRoom>()

    fun walk(nodes: List<SeatMenuNodeDto>, path: List<String>) {
        nodes.forEach { node ->
            val name = node.name.orEmpty()
            val children = node.children
            if (children.isNullOrEmpty()) {
                val id = node.id ?: return@forEach
                rooms += SeatRoom(
                    id = id,
                    name = name,
                    campus = path.getOrElse(0) { "" },
                    building = path.getOrElse(1) { "" },
                    totalCount = node.totalCount ?: 0,
                    remainCount = node.remainCount ?: 0,
                )
            } else {
                walk(children, path + name)
            }
        }
    }

    walk(this, emptyList())
    return rooms
}

fun CsyySeatDto.toSeat(): Seat? {
    val id = devId ?: return null
    return Seat(
        devId = id,
        devName = devName.orEmpty(),
        devSn = devSn ?: 0,
        position = coordinate?.let { raw ->
            val parts = raw.split(',')
            val x = parts.getOrNull(0)?.trim()?.toFloatOrNull()
            val y = parts.getOrNull(1)?.trim()?.toFloatOrNull()
            if (x != null && y != null) SeatPosition(x, y) else null
        },
        openStart = openStart.orEmpty(),
        openEnd = openEnd.orEmpty(),
        openTimes = openTimes.orEmpty().mapNotNull { segment ->
            val start = segment.openStartTime ?: return@mapNotNull null
            val end = segment.openEndTime ?: return@mapNotNull null
            SeatOpenTime(start, end)
        },
        rule = resvRule?.let {
            SeatRule(
                minResvTime = it.minResvTime ?: 0,
                maxResvTime = it.maxResvTime ?: 0,
                timeInterval = it.timeInterval ?: 0,
                earliestResvTime = it.earliestResvTime ?: 0,
                latestResvTime = it.latestResvTime ?: 0,
                cancelTime = it.cancelTime ?: 0,
            )
        },
        occupancies = resvInfo.orEmpty().mapNotNull { info ->
            val uuid = info.uuid ?: return@mapNotNull null
            SeatOccupancy(
                uuid = uuid,
                start = info.startTime.toLocalDateTime(),
                end = info.endTime.toLocalDateTime(),
            )
        },
        onlyView = onlyView == true,
    )
}

fun CsyyReservationDto.toReservation(): SeatReservation? {
    val id = uuid ?: return null
    val device = resvDevInfoList?.firstOrNull()
    return SeatReservation(
        uuid = id,
        roomName = device?.roomName.orEmpty(),
        seatName = device?.devName.orEmpty(),
        start = resvBeginTime.toLocalDateTime(),
        end = resvEndTime.toLocalDateTime(),
        realEnd = resvEndRealTime.toLocalDateTime(),
        theme = testName.orEmpty(),
        status = resvStatus ?: 0,
        signTime = signTime.toLocalDateTime(),
        latestCheckInTime = latestCheckInTime.toLocalDateTime(),
        endEarly = endEarly == true,
    )
}

fun CsyyAccountDto.toAccount(): SeatAccount? {
    val accNo = accNo?.takeIf { it.isNotBlank() } ?: return null
    return SeatAccount(accNo = accNo, name = trueName.orEmpty())
}
