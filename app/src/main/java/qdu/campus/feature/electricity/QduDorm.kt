package qdu.campus.feature.electricity

/**
 * 浮山校区宿舍楼目录，供房间编码的楼栋下拉使用。
 *
 * 简写取自财务处示例（浩1 / 浏A / 汇1 / 泓5 / 滢1 / 浮1），
 * 单元按公开楼栋结构整理：汇园每栋分 A/B/C 单元，泓园分北/南/东区，
 * 滢园 2–8 号楼分东/西单元，其余楼栋无单元，编码时单元位填 1。
 * 金家岭与松山校区的简写没有公开依据，不猜，列表外楼栋允许手填。
 */
data class DormBuilding(
    val park: String,
    val label: String,
    val code: String,
    val units: List<String> = emptyList(),
) {
    val defaultUnit: String get() = units.firstOrNull() ?: "1"
}

private val HuiUnits = listOf("A", "B", "C")
private val HongUnits = listOf("北", "南", "东")
private val YingUnits = listOf("东", "西")
private val FushanApartmentUnits = listOf("1", "2", "3")

val QduDormBuildings: List<DormBuilding> = buildList {
    listOf(1, 2, 3, 4, 7, 8, 12, 14).forEach {
        add(DormBuilding(park = "浩园", label = "浩园 $it 号楼", code = "浩$it"))
    }
    listOf("A", "B", "C", "D", "E").forEach {
        add(DormBuilding(park = "浏园", label = "浏园 $it 栋", code = "浏$it"))
    }
    (1..7).forEach {
        add(DormBuilding(park = "汇园", label = "汇园 $it 号楼", code = "汇$it", units = HuiUnits))
    }
    (1..11).forEach {
        add(DormBuilding(park = "泓园", label = "泓园 $it 号楼", code = "泓$it", units = HongUnits))
    }
    (1..14).forEach {
        add(
            DormBuilding(
                park = "滢园",
                label = "滢园 $it 号楼",
                code = "滢$it",
                units = if (it in 2..8) YingUnits else emptyList(),
            ),
        )
    }
    (1..4).forEach {
        add(
            DormBuilding(
                park = "浮山公寓",
                label = "浮山公寓 $it 号楼",
                code = "浮$it",
                units = FushanApartmentUnits,
            ),
        )
    }
}
