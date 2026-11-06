package qdu.campus.feature.electricity

/**
 * 房间编码拼接。规则取自财务处示例：
 * 无单元填 1；分单元或单独楼宇写「楼简称-单元-房号」；
 * 带人数追加人数位，内分房间再追加序号。
 */
object RoomCodeBuilder {

    fun build(
        building: String,
        unit: String,
        room: String,
        occupants: String = "",
        inner: String = "",
    ): String {
        if (building.isBlank() || room.isBlank()) return ""
        val unitPart = unit.trim().ifBlank { "1" }
        val parts = mutableListOf(building.trim(), unitPart, room.trim())
        occupants.trim().takeIf { it.isNotEmpty() }?.let(parts::add)
        inner.trim().takeIf { it.isNotEmpty() }?.let(parts::add)
        return parts.joinToString("-")
    }
}