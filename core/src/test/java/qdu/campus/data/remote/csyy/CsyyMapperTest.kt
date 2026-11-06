package qdu.campus.data.remote.csyy

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CsyyMapperTest {

    /** 与 app 侧 appJson 同一套宽松配置，测试才能反映真实反序列化行为。 */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val menuSerializer =
        CsyyEnvelope.serializer(ListSerializer(SeatMenuNodeDto.serializer()))

    @Test
    fun `外壳按业务码判定成功与掉线`() {
        val ok = json.decodeFromString(
            CsyyEnvelope.serializer(CsyyAccountDto.serializer()),
            """{"code":0,"message":null,"data":{"accNo":"2099000001","trueName":"测试"},"count":1}""",
        )
        assertTrue(ok.isSuccess)
        assertFalse(ok.isLoggedOut)
        assertEquals("2099000001", ok.data?.accNo)

        val loggedOut = json.decodeFromString(
            CsyyEnvelope.serializer(CsyyAccountDto.serializer()),
            """{"code":300,"message":"未登录"}""",
        )
        assertFalse(loggedOut.isSuccess)
        assertTrue(loggedOut.isLoggedOut)
        assertNull(loggedOut.data)
    }

    @Test
    fun `展平三层房间树，叶子才是房间`() {
        // 取自真实 /ic-web/seatMenu：校区 → 楼 → 房间，spanDay 等字段一律忽略。
        val raw = """
        {"code":0,"data":[
          {"id":1,"name":"浮山校区","totalCount":1042,"remainCount":1042,"spanDay":false,"children":[
            {"id":18,"name":"泓园8号楼南区一楼","totalCount":99,"remainCount":99,"spanDay":false,"children":[
              {"id":34,"name":"“一站式”学生社区学习共享空间1","totalCount":52,"remainCount":52,"spanDay":false},
              {"id":35,"name":"“一站式”学生社区学习共享空间2","totalCount":47,"remainCount":47,"spanDay":false}
            ]},
            {"id":19,"name":"泓园8号楼南区二楼","totalCount":60,"remainCount":58,"spanDay":false,"children":[
              {"id":36,"name":"学习共享空间3","totalCount":60,"remainCount":58,"spanDay":false}
            ]}
          ]},
          {"id":2,"name":"金家岭校区","totalCount":200,"remainCount":180,"spanDay":false,"children":[
            {"id":30,"name":"图书馆一楼","totalCount":200,"remainCount":180,"spanDay":false,"children":[
              {"id":50,"name":"自习区A","totalCount":200,"remainCount":180,"spanDay":false}
            ]}
          ]}
        ]}
        """.trimIndent()
        val nodes = json.decodeFromString(menuSerializer, raw).data.orEmpty()

        val rooms = nodes.toRooms()
        assertEquals(4, rooms.size)

        val first = rooms.first()
        assertEquals(34, first.id)
        assertEquals("浮山校区", first.campus)
        assertEquals("泓园8号楼南区一楼", first.building)
        assertEquals(52, first.totalCount)
        assertEquals(52, first.remainCount)

        val jinjialing = rooms.last()
        assertEquals(50, jinjialing.id)
        assertEquals("金家岭校区", jinjialing.campus)
        assertEquals("图书馆一楼", jinjialing.building)
        assertEquals(180, jinjialing.remainCount)
    }

    @Test
    fun `缺少 id 的叶子被丢弃`() {
        val rooms = listOf(
            SeatMenuNodeDto(id = null, name = "无 id 房间"),
            SeatMenuNodeDto(id = 7, name = "正常房间", totalCount = 10, remainCount = 3),
        ).toRooms()
        assertEquals(1, rooms.size)
        assertEquals(7, rooms.first().id)
        // 顶层叶子没有祖先，校区与楼为空串
        assertEquals("", rooms.first().campus)
    }

    @Test
    fun `空闲座位按真实响应映射`() {
        // devId 1994，房间 34，2026-11-04 的真实数据
        val raw = """
        {"devId":1994,"devSn":3,"devName":"003","onlyView":false,
         "coordinate":"90.743155,66.472868","openStart":"07:00","openEnd":"22:00",
         "openTimes":[{"openStartTime":"07:00","openEndTime":"22:00","openLimit":1}],
         "resvRule":{"minResvTime":0,"maxResvTime":360,"timeInterval":30,
                     "earliestResvTime":1440,"latestResvTime":0,"cancelTime":30},
         "resvInfo":[]}
        """.trimIndent()
        val seat = json.decodeFromString(CsyySeatDto.serializer(), raw).toSeat()!!

        assertEquals(1994, seat.devId)
        assertEquals(3, seat.devSn)
        assertEquals("003", seat.devName)
        assertFalse(seat.onlyView)
        assertEquals(90.743155f, seat.position!!.x, 0.0001f)
        assertEquals(66.472868f, seat.position!!.y, 0.0001f)
        assertEquals(listOf("07:00" to "22:00"), seat.openTimes.map { it.startTime to it.endTime })
        assertEquals(30, seat.rule!!.timeInterval)
        assertEquals(360, seat.rule!!.maxResvTime)
        assertEquals(1440, seat.rule!!.earliestResvTime)
        assertEquals(0, seat.rule!!.latestResvTime)
        assertTrue(seat.occupancies.isEmpty())
    }

    @Test
    fun `已有预约的毫秒时间戳转成本地时间`() {
        // devId 2187 的真实占用：1793752200000 = 2026-11-04 08:30，1793773800000 = 14:30
        val raw = """
        {"devId":2187,"devSn":1,"devName":"001","onlyView":false,"openStart":"07:00","openEnd":"22:00",
         "openTimes":[{"openStartTime":"07:00","openEndTime":"22:00","openLimit":1}],
         "resvRule":{"minResvTime":0,"maxResvTime":360,"timeInterval":30,
                     "earliestResvTime":1440,"latestResvTime":0,"cancelTime":30},
         "resvInfo":[{"startTime":1793752200000,"endTime":1793773800000,
                      "resvId":3038919,"resvStatus":1027,
                      "uuid":"a188d103a8154608a1e7d3f71ddfe7c3"}]}
        """.trimIndent()
        val seat = json.decodeFromString(CsyySeatDto.serializer(), raw).toSeat()!!

        assertEquals(1, seat.occupancies.size)
        val occupancy = seat.occupancies.first()
        assertEquals("a188d103a8154608a1e7d3f71ddfe7c3", occupancy.uuid)
        assertEquals(LocalDateTime.of(2026, 11, 4, 8, 30), occupancy.start)
        assertEquals(LocalDateTime.of(2026, 11, 4, 14, 30), occupancy.end)
    }

    @Test
    fun `坐标与占用缺字段时降级而不是崩`() {
        val seat = CsyySeatDto(
            devId = 1,
            coordinate = "坏坐标",
            resvInfo = listOf(
                CsyyResvInfoDto(uuid = null, startTime = 1793752200000),
                CsyyResvInfoDto(uuid = "u", startTime = 0, endTime = null),
            ),
        ).toSeat()!!
        assertNull(seat.position)
        assertEquals(1, seat.occupancies.size)
        assertNull(seat.occupancies.first().start)
        assertNull(seat.occupancies.first().end)
    }

    @Test
    fun `没有 devId 的座位映射为 null`() {
        assertNull(CsyySeatDto(devName = "001").toSeat())
    }

    @Test
    fun `预约记录取第一个设备条目`() {
        val reservation = CsyyReservationDto(
            uuid = "u-1",
            testName = "自习",
            resvBeginTime = 1793752200000,
            resvEndTime = 1793773800000,
            resvStatus = 1027,
            resvDevInfoList = listOf(CsyyResvDevDto(devName = "003", roomName = "学习共享空间1")),
        ).toReservation()!!

        assertEquals("u-1", reservation.uuid)
        assertEquals("003", reservation.seatName)
        assertEquals("学习共享空间1", reservation.roomName)
        assertEquals("自习", reservation.theme)
        assertEquals(1027, reservation.status)
        assertEquals(LocalDateTime.of(2026, 11, 4, 8, 30), reservation.start)
        assertNull(reservation.realEnd)
    }

    @Test
    fun `没有 uuid 的预约与没有账号的账户都丢弃`() {
        assertNull(CsyyReservationDto(testName = "x").toReservation())
        assertNull(CsyyAccountDto(accNo = "  ").toAccount())
        assertNull(CsyyAccountDto(accNo = null, trueName = "测试").toAccount())
        // trueName 缺失时降级为空串，UI 不显示姓名不影响预约
        assertEquals("", CsyyAccountDto(accNo = "2099000001").toAccount()!!.name)
    }

    @Test
    fun `预约请求体与网页端 seatForm 一致`() {
        val payload = CsyyReserveRequest(
            sysKind = 8,
            appAccNo = "2099000001",
            memberKind = 1,
            resvMember = listOf("2099000001"),
            resvBeginTime = "2026-11-04 09:00:00",
            resvEndTime = "2026-11-04 12:00:00",
            testName = "",
            captcha = "",
            resvProperty = 32,
            resvDev = listOf(1994),
            memo = "",
        )
        val encoded = json.encodeToString(CsyyReserveRequest.serializer(), payload)
        // resvCode="0"，青大不开验证码，但字段仍必须存在
        assertTrue(encoded.contains("\"captcha\":\"\""))
        assertTrue(encoded.contains("\"resvProperty\":32"))
        assertTrue(encoded.contains("\"resvDev\":[1994]"))
        assertTrue(encoded.contains("\"resvMember\":[\"2099000001\"]"))
        assertTrue(encoded.contains("\"resvBeginTime\":\"2026-11-04 09:00:00\""))
    }
}
