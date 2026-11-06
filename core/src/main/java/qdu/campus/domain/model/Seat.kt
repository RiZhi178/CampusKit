package qdu.campus.domain.model

import java.time.LocalDateTime

/** 自习室房间。campus / building 由 seatMenu 的三层树展平得到。 */
data class SeatRoom(
    val id: Int,
    val name: String,
    val campus: String,
    val building: String,
    val totalCount: Int,
    val remainCount: Int,
)

/** 一天内的一个开放时段，"HH:mm"。 */
data class SeatOpenTime(val startTime: String, val endTime: String)

/**
 * 预约规则，时间单位一律是分钟。
 *
 * `earliestResvTime` / `latestResvTime` 的服务端命名与语义相反：实测房间下发
 * earliest=1440、latest=0，可约窗口是「今天 ~ 今天+1 天」，也就是 latest 才是近端。
 * 换算集中在 [qdu.campus.domain.usecase.SeatAvailability]，别处不要再解释这两个字段。
 */
data class SeatRule(
    val minResvTime: Int,
    val maxResvTime: Int,
    val timeInterval: Int,
    val earliestResvTime: Int,
    val latestResvTime: Int,
    val cancelTime: Int,
)

/** 座位图上的百分比坐标。服务端没给时为 null，按 devSn 顺序排。 */
data class SeatPosition(val x: Float, val y: Float)

/** 一条已存在的占用。服务端下发 epoch 毫秒。 */
data class SeatOccupancy(
    val uuid: String,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
) {
    /** 与 [from, to) 有交集。端点缺失按无限远处理，宁可判占用也不要放出重复预约。 */
    fun overlaps(from: LocalDateTime, to: LocalDateTime): Boolean {
        val s = start ?: LocalDateTime.MIN
        val e = end ?: LocalDateTime.MAX
        return s < to && e > from
    }

    /** 完整覆盖 [from, to]。 */
    fun covers(from: LocalDateTime, to: LocalDateTime): Boolean {
        val s = start ?: return false
        val e = end ?: return false
        return !s.isAfter(from) && !e.isBefore(to)
    }
}

/** 座位在所选时段内的可约状态。服务端不下发，由客户端按规则与占用算出。 */
enum class SeatStatus {
    /** 所选时段完全空闲 */
    Free,

    /** 所选时段与已有预约部分重叠 */
    Partial,

    /** 所选时段被已有预约完全覆盖 */
    Busy,

    /** 不在可约窗口内，或该时段不开放 */
    Closed,

    /** 仅支持现场预约 */
    OnSiteOnly,
}

data class Seat(
    val devId: Int,
    val devName: String,
    val devSn: Int,
    val position: SeatPosition?,
    val openStart: String,
    val openEnd: String,
    val openTimes: List<SeatOpenTime>,
    val rule: SeatRule?,
    val occupancies: List<SeatOccupancy>,
    val onlyView: Boolean,
)

/** 我的一条预约。realEnd 非空表示已提前结束。 */
data class SeatReservation(
    val uuid: String,
    val roomName: String,
    val seatName: String,
    val start: LocalDateTime?,
    val end: LocalDateTime?,
    val realEnd: LocalDateTime?,
    val theme: String,
    val status: Int,
    /** 实际签到时间；null 表示还没签到。 */
    val signTime: LocalDateTime? = null,
    /** 服务端给的签到截止时间，过了它未签到会转违约。 */
    val latestCheckInTime: LocalDateTime? = null,
    /**
     * 服务端自己的「现在能不能提前结束」判定。网页端也只认这个旗标，
     * 签名动作发生在到场签到时，客户端无法只凭时间复现。
     */
    val endEarly: Boolean = false,
)

/** 预约请求要用的账号标识，来自 account/info。 */
data class SeatAccount(val accNo: String, val name: String)
