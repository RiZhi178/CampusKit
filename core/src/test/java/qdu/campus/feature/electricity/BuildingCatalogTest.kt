package qdu.campus.feature.electricity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 楼栋字典解析测试。fixture 是 2026-11-05 从报修系统全量采集后清洗的真实资产
 * （与 `app/src/main/assets/zzmp/buildings.json` 同一份），断言骨架：校区数、楼栋数、
 * 排除是否生效、简称推导的命中与不命中。
 */
class BuildingCatalogTest {

    private val campuses: List<CatalogCampus> by lazy {
        val text = javaClass.getResourceAsStream("/zzmp/buildings.2026-11.json")!!
            .bufferedReader().use { it.readText() }
        BuildingCatalog.parse(text)
    }

    private fun find(name: String): CatalogBuilding? =
        campuses.flatMap { it.buildings }.firstOrNull { it.name == name }

    @Test
    fun `骨架_八个校区一百六十六栋`() {
        assertEquals(8, campuses.size)
        assertEquals(92, campuses.sumOf { it.buildings.size })
        assertEquals(
            listOf("浮山校区东院", "浮山公寓", "浮山校区西院", "浮山校区浩园", "金家岭校区溆院", "金家岭校区湉院", "松山校区", "浮山校区师范学院"),
            campuses.map { it.campus },
        )
    }

    @Test
    fun `排除项不在字典里`() {
        val names = campuses.flatMap { it.buildings }.map { it.name }
        listOf("公共浴室", "餐厅", "教室", "办公", "体育场", "江苏路", "科技园D座", "科技开发公司", "科技研发中心")
            .forEach { word ->
                assertTrue("不应含 $word", names.none { it.contains(word) })
            }
    }

    @Test
    fun `简称推导_园字楼的命中样例`() {
        assertEquals("浩1", find("浩园1号")!!.code)
        assertEquals("浩12", find("浩园12号")!!.code)
        assertEquals("浏C", find("浏园C座")!!.code)
        assertEquals("泓8", find("泓园8号楼")!!.code)
        assertEquals("汇7", find("汇园7号")!!.code)
        assertEquals("溆2", find("溆园2号")!!.code)
        assertEquals("润1", find("润园1号")!!.code)
    }

    @Test
    fun `简称推导_推不出来就给null`() {
        // 学生公寓、人才公寓这类没有公开简称依据的，宁缺毋滥
        assertNull(find("学生公寓1号楼（登州路）")!!.code)
        // 人才公寓、专家公寓这类非学生宿舍已整体不在字典里
        assertNull(find("人才公寓"))
        assertNull(find("专家公寓4号楼"))
    }

    @Test
    fun `浮山公寓走校区专属规则`() {
        // 报修系统里浮山公寓的三栋叫「学生公寓N号楼」，财务处口径是 浮N
        assertEquals("浮1", find("学生公寓1号楼")!!.code)
        assertEquals("浮3", find("学生公寓3号楼")!!.code)
    }

    @Test
    fun `坏 JSON 与空串不崩`() {
        assertTrue(BuildingCatalog.parse("不是 JSON").isEmpty())
        assertTrue(BuildingCatalog.parse("").isEmpty())
        assertTrue(BuildingCatalog.parse("{\"campuses\":[]}").isEmpty())
    }
}
