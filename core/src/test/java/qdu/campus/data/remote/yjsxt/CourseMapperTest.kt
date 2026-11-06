package qdu.campus.data.remote.yjsxt

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `pksj 解析为星期与大节`() {
        val single = ScheduleCodeParser.parse("40304")
        assertEquals(4, single?.day)
        assertEquals(listOf(3, 4), single?.sections)

        val long = ScheduleCodeParser.parse("405060708")
        assertEquals(4, long?.day)
        assertEquals(listOf(5, 6, 7, 8), long?.sections)
    }

    @Test
    fun `非法 pksj 返回 null`() {
        assertNull(ScheduleCodeParser.parse(null))
        assertNull(ScheduleCodeParser.parse(""))
        assertNull(ScheduleCodeParser.parse("9"))
        assertNull(ScheduleCodeParser.parse("8" + "0304"))
    }

    @Test
    fun `周次以 pkzcmx 为准`() {
        assertEquals(setOf(3, 4, 5, 6), ScheduleCodeParser.parseWeeks("3,4,5,6"))
        assertEquals(emptySet<Int>(), ScheduleCodeParser.parseWeeks(""))
        assertEquals(setOf(1, 3, 5), ScheduleCodeParser.parseWeeks("1,3,5,99"))
    }

    @Test
    fun `课表条目映射到领域模型`() {
        val dto = CourseDto(
            id = "10490775",
            name = "习近平总书记教育重要论述研究",
            code = "4122103",
            classNo = "1班",
            teachers = "孙振,闫芳",
            room = "博学楼103",
            timeCode = "40304",
            weekDetail = "3,4,5,6,7,8,9,10",
            startTime = "10:10",
            endTime = "12:00",
            category = "A公共学位课",
            hours = 16,
            credits = 1.0,
            department = "马克思主义学院",
            semesterId = "2026-2027-1",
        )

        val course = CourseMapper.toCourse(dto, "2026-2027-1")!!
        assertEquals(4, course.day)
        assertEquals(listOf(3, 4), course.sections)
        assertEquals(8, course.weeks.size)
        assertEquals(1.0, course.credits, 0.0)
        assertEquals("孙振,闫芳", course.teachers)
    }

    @Test
    fun `缺少课程名的条目被丢弃`() {
        val dto = CourseDto(id = "1", name = null, timeCode = "10102")
        assertNull(CourseMapper.toCourse(dto, "2026-2027-1"))
    }

    @Test
    fun `大节配置映射出节次时间表`() {
        val semester = SemesterDto(
            name = "2026-2027-1",
            sections = listOf(
                BigSectionDto(
                    name = "第一大节",
                    startTime = "08:00",
                    endTime = "09:50",
                    sectionIndexes = "01,02",
                    period = "上午",
                    bigSectionId = "j13526fa0000WH",
                ),
            ),
        )
        val sections = CourseMapper.toSections(semester)
        assertEquals(1, sections.size)
        assertEquals(1, sections.first().index)
        assertEquals(listOf(1, 2), sections.first().covers)
        assertEquals("08:00", sections.first().startTime)
    }

    /**
     * 上面的用例直接构造 DTO，改错了 `@SerialName` 也不会失败，所以这里再从真实的
     * 报文字段名走一遍反序列化。报文按 searchOneXskbList 的实际返回结构手抄，
     * 只保留用得到的键，另外塞两个服务端多出来的键验证忽略未知字段。
     */
    @Test
    fun `课表报文的字段名解析到领域模型`() {
        val raw = """
            {
              "errorCode": "success",
              "data": [
                {
                  "id": "10490775",
                  "kc_name": "习近平总书记教育重要论述研究",
                  "kcbh": "4122103",
                  "ktmc_name": "1班",
                  "teachernames": "孙振,闫芳",
                  "js_name": "博学楼103",
                  "xq_name": "浮山校区",
                  "pksj": "40304",
                  "pkzcmx": "3,4,5,6,7,8,9,10",
                  "pkzc": "3-10",
                  "djkssj": "10:10",
                  "djjssj": "12:00",
                  "kclb_name": "A公共学位课",
                  "zxs": 16,
                  "zxf": 1.0,
                  "kkdw_name": "马克思主义学院",
                  "jczy013id": "2026-2027-1",
                  "pkzt_name": "正常",
                  "bjrs": "0",
                  "xfzkjb": {"id": "irrelevant"}
                }
              ]
            }
        """.trimIndent()

        val envelope = json.decodeFromString(
            ApiEnvelope.serializer(ListSerializer(CourseDto.serializer())),
            raw,
        )
        assertTrue(envelope.isSuccess)
        val course = CourseMapper.toCourse(envelope.data!!.first(), "2026-2027-1")!!
        assertEquals("博学楼103", course.room)
        assertEquals("浮山校区", course.campus)
        assertEquals(4, course.day)
        assertEquals(listOf(3, 4), course.sections)
        assertEquals(setOf(3, 4, 5, 6, 7, 8, 9, 10), course.weeks)
        assertEquals("A公共学位课", course.category)
        assertEquals("2026-2027-1", course.semesterId)
    }

    /** 节次配置的报文键（pkgl00201List / djkssj / zyxjs）同样要能从 JSON 走通。 */
    @Test
    fun `节次报文解析成大节时间表`() {
        val raw = """
            {
              "errorCode": "success",
              "data": [
                {
                  "sjms_name": "2026-2027-1",
                  "pkgl00201List": [
                    {"dyname": "第一大节", "djkssj": "08:00", "djjssj": "09:50", "zyxjs": "01,02", "sjjdname": "上午"},
                    {"dyname": "第二大节", "djkssj": "10:10", "djjssj": "12:00", "zyxjs": "03,04", "sjjdname": "上午"}
                  ]
                }
              ]
            }
        """.trimIndent()

        val envelope = json.decodeFromString(
            ApiEnvelope.serializer(ListSerializer(SemesterDto.serializer())),
            raw,
        )
        val sections = CourseMapper.toSections(envelope.data!!.first())
        assertEquals(listOf(1, 3), sections.map { it.index })
        assertEquals(listOf("08:00", "10:10"), sections.map { it.startTime })
        assertEquals("上午", sections.first().period)
    }

    /** 会话失效与业务失败都只由外壳表态，上层据此决定是重新登录还是报错误。 */
    @Test
    fun `外壳区分成功、业务失败与会话失效`() {
        val ok = json.decodeFromString(
            ApiEnvelope.serializer(ListSerializer(CourseDto.serializer())),
            """{"errorCode":"success","data":[]}""",
        )
        assertTrue(ok.isSuccess)
        assertFalse(ok.isTokenExpired)

        val expired = json.decodeFromString(
            ApiEnvelope.serializer(ListSerializer(CourseDto.serializer())),
            """{"errorCode":"security.httpstatu.401.1006","errorMessage":"token invalid"}""",
        )
        assertFalse(expired.isSuccess)
        assertTrue(expired.isTokenExpired)

        val failed = json.decodeFromString(
            ApiEnvelope.serializer(ListSerializer(CourseDto.serializer())),
            """{"errorCode":"other.error","errorMessage":"查询失败"}""",
        )
        assertFalse(failed.isSuccess)
        assertFalse(failed.isTokenExpired)
    }
}