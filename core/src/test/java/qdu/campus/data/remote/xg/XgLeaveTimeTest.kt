package qdu.campus.data.remote.xg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * 时间口径全局只留这一处。站点 laydate 只到小时，服务端回填到分钟，两种都认；
 * 时长按整小时截断，实测 22:00–22:30 列表印「0天0小时」，半小时被抹平。
 */
class XgLeaveTimeTest {

    @Test
    fun `分钟与小时两种写法都认`() {
        assertEquals(LocalDateTime.of(2026, 11, 7, 22, 30), XgLeaveTime.parse("2026-11-07 22:30"))
        assertEquals(LocalDateTime.of(2026, 11, 7, 22, 0), XgLeaveTime.parse("2026-11-07 22"))
        assertEquals(LocalDateTime.of(2026, 11, 7, 22, 0), XgLeaveTime.parse("  2026-11-07 22:00  "))
    }

    @Test
    fun `认不出就返回 null`() {
        assertNull(XgLeaveTime.parse(""))
        assertNull(XgLeaveTime.parse("明天下午"))
        assertNull(XgLeaveTime.parse("2026-13-40 99:99"))
    }

    @Test
    fun `格式化回分钟写法`() {
        assertEquals("2026-11-07 22:30", XgLeaveTime.format(LocalDateTime.of(2026, 11, 7, 22, 30)))
    }

    @Test
    fun `时长按整小时截断`() {
        assertEquals("0天0小时", XgLeaveTime.durationText("2026-11-07 22:00", "2026-11-07 22:30"))
        assertEquals("0天1小时", XgLeaveTime.durationText("2026-11-07 22:00", "2026-11-07 23:00"))
        assertEquals("1天1小时", XgLeaveTime.durationText("2026-11-07 22:00", "2026-11-08 23:00"))
    }

    @Test
    fun `没填全或倒挂时不报时长`() {
        assertEquals("", XgLeaveTime.durationText("", "2026-11-07 23:00"))
        assertEquals("", XgLeaveTime.durationText("2026-11-07 23:00", "2026-11-07 22:00"))
    }
}