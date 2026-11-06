package qdu.campus.domain.model

/**
 * 房间编号校验结果。
 *
 * 服务端给的就是页面上那几行键值（公寓信息、剩余电量、当前余额、剩余补助），
 * 客户端不猜字段含义、也不重算，原样展示——页面改版加一行，这边跟着显示就行。
 */
data class RoomAccount(
    val lines: List<RoomAccountLine>,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

data class RoomAccountLine(val label: String, val value: String)
