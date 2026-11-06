package qdu.campus.domain.usecase

/**
 * 房间内座位号，用来给用户看、写进回执。
 *
 * 服务端 `devName` 是 `H8-ZXS01-40` 这种正式编号，只有尾段是房间内的座位号；
 * `devSn` 是 188047 这类内部主键，除了请求体以外都不该出现在界面上。
 */
object SeatLabel {

    /** 取尾段数字并去前导零；尾段不是数字（或为空）时给一个占位符，不把整串正式编号上屏。 */
    fun of(devName: String): String {
        val tail = devName.substringAfterLast('-').trim()
        if (tail.isNotEmpty() && tail.all(Char::isDigit)) {
            return tail.trimStart('0').ifEmpty { "0" }
        }
        return "·"
    }

    /** 预约回执里的位置说明。房间名为空时只给座位号，不凭空编一个房间。 */
    fun bookingPlace(roomName: String?, devName: String): String {
        val number = of(devName)
        val room = roomName?.trim().orEmpty()
        return if (room.isEmpty()) "座位 $number" else "$room 座位 $number"
    }
}
