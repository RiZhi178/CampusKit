package qdu.campus.feature.electricity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * 电费房间码用的校区-楼栋字典。
 *
 * 数据来自报修系统的两级下拉（`getCampusByRoleId` + `getBuildingByRoleId?type=houqin`，
 * 2026-11-05 全量采集，见 `docs/interfaces/baoxiu.md` 的「校区与楼栋字典」节），
 * 清洗后随包发布在 `app/src/main/assets/zzmp/buildings.json`，与本科生课程总表同一条
 * 「随包发布、永不拉」的口径。校方改楼栋表时重新采集生成即可，不用改代码。
 */
data class CatalogBuilding(
    val buildingId: String,
    val name: String,
    /** 财务处口径的楼简称（浩1 / 浏C / 溆2），推不出来时为 null，绑定页退回手填。 */
    val code: String?,
)

data class CatalogCampus(
    val campusId: String,
    val campus: String,
    val buildings: List<CatalogBuilding>,
)

@Serializable
private data class CatalogDto(val campuses: List<CampusDto> = emptyList())

@Serializable
private data class CampusDto(
    val campusId: String = "",
    val campus: String = "",
    val buildings: List<BuildingDto> = emptyList(),
)

@Serializable
private data class BuildingDto(val id: String = "", val name: String = "")

object BuildingCatalog {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 解析随包 JSON。解析失败给空列表，让绑定页走「其他楼栋手填」的旧路，不崩。 */
    fun parse(text: String): List<CatalogCampus> =
        runCatching { json.decodeFromString(CatalogDto.serializer(), text) }
            .getOrDefault(CatalogDto())
            .campuses
            .map { campus ->
                CatalogCampus(
                    campusId = campus.campusId,
                    campus = campus.campus,
                    buildings = campus.buildings.mapNotNull { building ->
                        if (building.id.isBlank() || building.name.isBlank()) return@mapNotNull null
                        CatalogBuilding(
                            buildingId = building.id,
                            name = building.name,
                            code = shortCode(building.name, campus.campus),
                        )
                    },
                )
            }
            .filter { it.campus.isNotBlank() && it.buildings.isNotEmpty() }

    /**
     * 楼简称推导。财务处示例（浩1 / 浏A / 汇1 / 泓5 / 滢1 / 浮1）全是
     * 「园字前的那一个字 + 号数或字母」，所以只认「X园 + 数字/字母 + 号/号楼/栋/座」这一种形态；
     * 推不出来（学生公寓、人才公寓这类）返回 null，**不猜**——猜错会被服务端
     * 「对应房间没有账户」拒掉。
     */
    fun shortCode(name: String, campus: String? = null): String? {
        val clean = name.trim()
            .substringBefore("（")
            .substringBefore("(")
            .trim()
        // 浮山公寓在报修系统里叫「学生公寓N号楼」，对应财务处的 浮N
        if (campus == "浮山公寓") {
            Regex("^学生公寓(\\d+)号楼?$").find(clean)?.let {
                return "浮${it.groupValues[1]}"
            }
        }
        val match = Regex("^(.+园)(\\d+|[A-Z])号?(?:号楼|楼|栋|座)?$").find(clean) ?: return null
        // group1 含「园」字，简称里不带它：浩园1号 → 浩1
        return match.groupValues[1].dropLast(1) + match.groupValues[2]
    }
}
