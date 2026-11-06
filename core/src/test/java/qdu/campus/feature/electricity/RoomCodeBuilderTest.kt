package qdu.campus.feature.electricity

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomCodeBuilderTest {

    @Test
    fun `无单元楼栋单元位填 1`() {
        assertEquals("浩1-1-101", RoomCodeBuilder.build("浩1", "1", "101"))
        assertEquals("浩1-1-101", RoomCodeBuilder.build("浩1", "", "101"))
    }

    @Test
    fun `分单元按楼简称-单元-房号`() {
        assertEquals("浏A-1-101", RoomCodeBuilder.build("浏A", "1", "101"))
        assertEquals("汇1-A-101", RoomCodeBuilder.build("汇1", "A", "101"))
        assertEquals("泓5-北-101", RoomCodeBuilder.build("泓5", "北", "101"))
    }

    @Test
    fun `带人数与内分房间依次追加`() {
        assertEquals("滢1-1-101-4", RoomCodeBuilder.build("滢1", "1", "101", occupants = "4"))
        assertEquals("浮1-1-201-1", RoomCodeBuilder.build("浮1", "1", "201", inner = "1"))
    }

    @Test
    fun `缺少楼栋或房号时返回空串`() {
        assertEquals("", RoomCodeBuilder.build("", "1", "101"))
        assertEquals("", RoomCodeBuilder.build("浩1", "1", ""))
    }
}