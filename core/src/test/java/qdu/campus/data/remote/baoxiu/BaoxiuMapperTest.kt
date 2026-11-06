package qdu.campus.data.remote.baoxiu

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 映射测试。样本截自 2026-11-05 真机录到的响应（学号姓名已替换），
 * 断言骨架：条数、状态文案、地点拼接、草稿过滤，不看具体业务值。
 */
class BaoxiuMapperTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val listSample = """
        {"msg":"success","code":0,"page":{"totalCount":1,"pageSize":10,"totalPage":1,"currPage":0,
        "list":[{"id":"BX202611051907021752","applicantId":"0000000000","applicantName":"张三",
        "applicantPhone":"13800000000","campus":"浮山校区师范学院","building":"浏园C座",
        "detailAddress":"103","maintenanceGroup":"木工（门锁窗、家具维修）",
        "maintenanceItem":"门锁窗、家具维修","maintenanceDesc":"锁坏了，无法锁门",
        "photoUrl":"http://baoxiu.qdu.edu.cn/fdfs/down/group1/M00/00/83/x.jpeg",
        "applyTime":"2026-11-05 19:07:03","state":"20","stateStr":"等待调度","evaluateState":0,
        "completeTime":null,"appointmentTime":null,"backFlag":0,"videoUrl":"","caogao":false}]}}
    """.trimIndent()

    @Test
    fun `列表行映射出标题与地点`() {
        val ack = json.decodeFromString(BaoxiuPageEnvelope.serializer(), listSample)
        assertTrue(ack.isSuccess)
        val tickets = ack.page!!.toTickets()
        assertEquals(1, tickets.size)
        val ticket = tickets.first()
        assertEquals("BX202611051907021752", ticket.id)
        assertEquals("门锁窗、家具维修-锁坏了，无法锁门", ticket.title)
        assertEquals("浏园C座-103", ticket.location)
        assertEquals("等待调度", ticket.stateText)
    }

    @Test
    fun `草稿行不进列表`() {
        val draft = listSample.replace("\"caogao\":false", "\"caogao\":true")
        val tickets = json.decodeFromString(BaoxiuPageEnvelope.serializer(), draft).page!!.toTickets()
        assertTrue(tickets.isEmpty())
    }

    @Test
    fun `缺 id 的行直接丢弃`() {
        val broken = listSample.replace("\"id\":\"BX202611051907021752\"", "\"id\":\"\"")
        val tickets = json.decodeFromString(BaoxiuPageEnvelope.serializer(), broken).page!!.toTickets()
        assertTrue(tickets.isEmpty())
    }

    @Test
    fun `下拉项按 value 取 id`() {
        val raw = """
            {"msg":"success","code":0,"Option":[
            {"value":"ee986d7d5d9c394b3a67749bc6bd5fc4","_value":null,"label":"浮山校区师范学院",
             "disabled":null,"text":null},
            {"value":null,"label":"脏数据"}]}
        """.trimIndent()
        val options = json.decodeFromString(BaoxiuOptionEnvelope.serializer(), raw).Option.toOptions()
        assertEquals(1, options.size)
        assertEquals("浮山校区师范学院", options.first().label)
    }

    @Test
    fun `详情用下拉名字补地点`() {
        val info = """
            {"msg":"success","code":0,"repairInfo":{"id":"BX1","applicantName":"张三",
            "applicantPhone":"13800000000","campusId":"c1","buildingId":"b1","detailAddress":"103",
            "maintenanceGroupId":"34","maintenanceItemId":"232","maintenanceDesc":"锁坏了，无法锁门",
            "applyTime":"2026-11-05 19:07:03","state":"20"}}
        """.trimIndent()
        val flow = json.decodeFromString(
            BaoxiuFlowResponse.serializer(),
            """
            {"msg":"success","repairFlow":[{"id":"f1","lastState":"10","nowState":"20",
            "operateDetail":"审核通过","remark":"系统自动审核","updateTime":"2026-11-05 19:07:04"}],
            "completePhotoUrl":null}
            """.trimIndent(),
        )
        val dto = json.decodeFromString(BaoxiuRepairEnvelope.serializer(), info).repairInfo!!
        val detail = dto.toDetail(
            flow,
            campusName = "浮山校区师范学院",
            buildingName = "浏园C座",
            groupName = "木工（门锁窗、家具维修）",
            itemName = "门锁窗、家具维修",
        )
        assertEquals("浮山校区师范学院浏园C座103", detail.location)
        assertEquals("木工（门锁窗、家具维修）-门锁窗、家具维修", detail.itemPath)
        assertEquals("等待调度", detail.stateText)
        assertEquals(1, detail.flow.size)
        assertEquals("审核通过", detail.flow.first().title)
    }

    @Test
    fun `未知状态不猜文案`() {
        assertEquals("状态 99", stateText("99", null))
        assertEquals("等待调度", stateText("20", null))
        assertEquals("已撤销", stateText("12", null))
        assertEquals("服务端原文", stateText("99", "服务端原文"))
    }
}
