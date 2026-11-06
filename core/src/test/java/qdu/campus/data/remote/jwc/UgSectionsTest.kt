package qdu.campus.data.remote.jwc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 节次表对着教务处作息通知断言。换档日期、每节起止都是抄来的事实，
 * 哪天学校改了作息，这里应当红，而不是等学生在课表上看出来。
 */
class UgSectionsTest {

    private val springSummer = UgSections.Season.SpringSummer
    private val autumnWinter = UgSections.Season.AutumnWinter

    @Test
    fun `换档落在五月与十月的第一天`() {
        assertEquals(springSummer, UgSections.seasonOf(LocalDate.of(2026, 5, 1)))
        assertEquals(springSummer, UgSections.seasonOf(LocalDate.of(2026, 9, 30)))
        assertEquals(autumnWinter, UgSections.seasonOf(LocalDate.of(2026, 10, 1)))
        assertEquals(autumnWinter, UgSections.seasonOf(LocalDate.of(2027, 4, 30)))
        // 上午四节两档一样，下午从第 5 节起差半小时
        assertEquals("08:00", UgSections.timeRange(springSummer, listOf(1)).first)
        assertEquals("08:00", UgSections.timeRange(autumnWinter, listOf(1)).first)
        assertEquals("14:00", UgSections.timeRange(springSummer, listOf(5)).first)
        assertEquals("13:30", UgSections.timeRange(autumnWinter, listOf(5)).first)
    }

    @Test
    fun `六个大节按两小节一成，第十一款落单`() {
        val sections = UgSections.sections(autumnWinter)

        assertEquals(listOf(1, 2, 3, 4, 5, 6), sections.map { it.index })
        assertEquals(
            listOf(listOf(1, 2), listOf(3, 4), listOf(5, 6), listOf(7, 8), listOf(9, 10), listOf(11)),
            sections.map { it.covers },
        )
        assertEquals(listOf("第1-2节", "第3-4节", "第11节"), listOf(sections[0].label, sections[1].label, sections[5].label))
        assertEquals("上午", sections[0].period)
        assertEquals("晚上", sections[5].period)
    }

    @Test
    fun `大节的起止覆盖它旗下每一小节`() {
        val sections = UgSections.sections(autumnWinter)

        assertEquals("08:00" to "09:50", sections[0].startTime to sections[0].endTime)
        assertEquals("10:10" to "12:00", sections[1].startTime to sections[1].endTime)
        assertEquals("13:30" to "15:20", sections[2].startTime to sections[2].endTime)
        assertEquals("15:30" to "17:20", sections[3].startTime to sections[3].endTime)
        assertEquals("18:30" to "20:20", sections[4].startTime to sections[4].endTime)
        assertEquals("20:30" to "21:20", sections[5].startTime to sections[5].endTime)
    }

    @Test
    fun `连堂四节的课同时落进两个大节`() {
        val sections = UgSections.sections(springSummer)

        assertTrue(sections[0].contains(listOf(1, 2, 3, 4)))
        assertTrue(sections[1].contains(listOf(1, 2, 3, 4)))
        assertFalse(sections[2].contains(listOf(1, 2, 3, 4)))
        // 表里的节次不会超过 11，越界的号谁都不挨着
        assertFalse(sections.any { it.contains(listOf(12)) })
    }

    @Test
    fun `整段节次的起止用于课程详情上的时间`() {
        assertEquals("08:00" to "12:00", UgSections.timeRange(autumnWinter, listOf(1, 2, 3, 4)))
        assertEquals("19:00" to "20:50", UgSections.timeRange(springSummer, listOf(9, 10)))
        assertEquals("" to "", UgSections.timeRange(autumnWinter, emptyList()))
    }
}
