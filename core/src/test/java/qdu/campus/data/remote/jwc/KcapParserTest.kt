package qdu.campus.data.remote.jwc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KcapParserTest {

    private val timetableJson = """
        {
          "id": "2026-2027-1",
          "classes": [
            { "name": "26小教班", "lessons": [
                { "id": "aaa", "name": "大学英语读写译Ⅲ", "code": "FL102",
                  "teachers": "李晓敏、武爱品、盖静", "day": 2, "periods": [1, 2],
                  "weeks": "4-18", "room": "", "credits": 3.0, "hours": 48 },
                { "id": "bbb", "name": "现代汉语", "code": "ZW201",
                  "teachers": "柳茜", "day": 2, "periods": [3, 4],
                  "weeks": "1-16", "room": "德音楼116", "credits": 3.0, "hours": 48 }
            ]},
            { "name": "26小教G班", "lessons": [
                { "id": "aaa", "name": "大学英语读写译Ⅲ", "code": "FL102",
                  "teachers": "李晓敏、武爱品、盖静", "day": 2, "periods": [1, 2],
                  "weeks": "4-18", "room": "", "credits": 3.0, "hours": 48 }
            ]}
          ]
        }
    """.trimIndent()

    private val timetables = KcapParser.parseTimetables(timetableJson)!!

    private val season = UgSections.seasonOf(LocalDate.of(2026, 11, 6))

    @Test
    fun `解析按班级组织的课表并列出班级`() {
        assertEquals("2026-2027-1", timetables.id)
        assertEquals(listOf("26小教G班", "26小教班"), KcapParser.classNames(timetables))
    }

    @Test
    fun `单班课表转领域模型并保留教师与空地点`() {
        val course = KcapParser.toCourse(
            timetables.classes[0].lessons[0],
            "26小教班",
            "2026-2027-1",
            season,
        )!!
        assertEquals("大学英语读写译Ⅲ", course.name)
        // 平行教学班组：教师全列（任选其一），地点留空不误导
        assertEquals("李晓敏、武爱品、盖静", course.teachers)
        assertEquals("", course.room)
        assertEquals(2, course.day)
        assertEquals(listOf(1, 2), course.sections)
        assertEquals("26小教班", course.classNo)
        // 第 4-18 周
        assertEquals((4..18).toSet(), course.weeks)
    }

    @Test
    fun `合班课跨班同 id，多选班时只显示一次`() {
        val a = KcapParser.toCourse(
            timetables.classes[0].lessons[0], "26小教班", "2026-2027-1", season,
        )!!
        val b = KcapParser.toCourse(
            timetables.classes[1].lessons[0], "26小教G班", "2026-2027-1", season,
        )!!
        // 同一条排课在两个班的课表里 id 相同，仓储 distinctBy 后只显示一次
        assertEquals(a.id, b.id)
        assertEquals(a.name, b.name)
    }

    @Test
    fun `周次区间与明细混排都能解析`() {
        assertEquals(setOf(1, 2, 3, 15, 16), KcapParser.parseWeeks("1-3,15-16"))
        assertEquals(setOf(4, 18), KcapParser.parseWeeks("4,18"))
        assertTrue(KcapParser.parseWeeks(null).isEmpty())
    }

    @Test
    fun `格式对不上时返回空而不抛异常`() {
        // 字段名整体对不上：id/classes 都缺，解出来是空课表而非失败
        val wrongShape = KcapParser.parseTimetables("""{"semesters": []}""")!!
        assertTrue(wrongShape.classes.isEmpty())
        // 彻底的坏 JSON 才返回 null
        assertNull(KcapParser.parseTimetables("not json"))
        assertTrue(KcapParser.parseIndex("not json").isEmpty())
    }
}
