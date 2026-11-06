package qdu.campus.data.remote.lib

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import qdu.campus.domain.model.LibraryLoan
import qdu.campus.domain.model.LibraryLoanStatus
import qdu.campus.domain.model.parseLibDate
import java.time.LocalDate

class LibraryDtoTest {

    /** 与 app 侧的 json 同一套宽松配置（响应读法），测试才反映真实反序列化行为。 */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** 请求体要保留显式 null，与 app 侧 `appJson.copy(explicitNulls = true)` 一致。 */
    private val requestJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
        encodeDefaults = true
    }

    private val pageSerializer = LibraryEnvelope.serializer(LibrarySearchPageDto.serializer())

    @Test
    fun `真实检索响应映射出一条书目`() {
        // 2026-11-04 从内嵌页被动录得的 /find/unify/search 响应，
        // 只留本项目读的键，值原样保留
        val raw = """
            {"success":true,"message":"操作成功","errCode":200,"errorCode":null,
             "data":{"numFound":23221,"searchResult":[
              {"recordId":246331,"title":"幻情. 第1版","author":"无名氏著","publisher":"吉林人民出版社",
               "isbn":"7-206-02336-3","publishYear":"95-10-01","subjectWord":"长篇小说",
               "chiClassNo":["I247.5"],"callNo":["I247.5/ZYX.2"],"callNoOne":"I247.5/ZYX.2",
               "onShelfCountI":1,"groupPhysicalCount":1,"docCode":1,"price":"18.00","bookPage":"446"}]}}
        """.trimIndent()
        val page = json.decodeFromString(pageSerializer, raw).data!!.toPage(page = 1, rows = 10)

        assertEquals(23221, page.numFound)
        assertTrue(page.hasMore)
        val record = page.records.single()
        assertEquals(246331L, record.recordId)
        assertEquals("幻情. 第1版", record.title)
        assertEquals("无名氏著", record.author)
        assertEquals("吉林人民出版社 · 95-10-01", record.imprint)
        assertEquals("I247.5/ZYX.2", record.callNo)
        assertEquals(1, record.onShelfCount)
        assertEquals(1, record.physicalCount)
    }

    @Test
    fun `最后一页不再提示更多`() {
        val raw = """{"success":true,"data":{"numFound":2,"searchResult":[]}}"""
        assertFalse(json.decodeFromString(pageSerializer, raw).data!!.toPage(1, 10).hasMore)
    }

    @Test
    fun `匿名检索不给在架数时保持未知，不补 0`() {
        // 「0 册」是结论，「没给」是缺数据，两者不能混：补 0 会让人以为无书可借
        val raw = """{"success":true,"data":{"numFound":1,"searchResult":[
            {"recordId":7,"title":"活着","onShelfCountI":null,"groupPhysicalCount":null}]}}"""
        val record = json.decodeFromString(pageSerializer, raw).data!!.toPage(1, 10).records.single()
        assertNull(record.onShelfCount)
        assertNull(record.physicalCount)
        assertTrue(record.hasHoldings)
    }

    @Test
    fun `没有 recordId 或没有题名的条目整条丢弃`() {
        val raw = """{"success":true,"data":{"numFound":3,"searchResult":[
            {"title":"缺编号"},{"recordId":8,"title":"  "},{"recordId":9,"title":"正常"}]}}"""
        val page = json.decodeFromString(pageSerializer, raw).data!!.toPage(1, 10)
        assertEquals(listOf("正常"), page.records.map { it.title })
    }

    @Test
    fun `success 才是成败位，errCode 只是消息码`() {
        // 实测详情页成功返回时 errCode=9000108，仍要按 success 判定
        val raw = """{"success":true,"errCode":9000108,"data":{"numFound":0,"searchResult":[]}}"""
        assertTrue(json.decodeFromString(pageSerializer, raw).success)

        val failed = """{"success":false,"message":"登录信息过期，请重新登录!","data":null}"""
        val envelope = json.decodeFromString(pageSerializer, failed)
        assertFalse(envelope.success)
        assertTrue(envelope.message!!.contains("登录"))
    }

    @Test
    fun `馆藏两册在架原样映射`() {
        // 2026-11-04 /find/physical/groupitems 真实响应，删掉本项目不读的键
        val raw = """
            {"success":true,"errCode":200,"data":{"totalCount":2,"list":[
              {"itemId":1145013,"callNo":"I247.57/LY(2)[J]/:1","barcode":"0979524",
               "locationName":"金家岭西院书库","curLocationName":"金家岭东院书库（原高职）",
               "processType":"在架","processTypeCode":"411","inDate":"2018-04-11","vol":""},
              {"itemId":1145012,"callNo":"I247.57/LY(2)[J]/:1","barcode":"0775777",
               "locationName":"金家岭西院书库","curLocationName":"金家岭西院书库",
               "processType":"在架","processTypeCode":"411","inDate":"2013-11-07","vol":"1"}]}}
        """.trimIndent()
        val holdings = json.decodeFromString(
            LibraryEnvelope.serializer(LibraryItemsPageDto.serializer()),
            raw,
        ).data!!.toHoldings()

        assertEquals(2, holdings.size)
        assertTrue(holdings.all { it.onShelf })
        assertEquals("0979524", holdings.first().barcode)
        // 典藏地与现存放地不同才多说一句
        assertEquals("金家岭西院书库（现存放：金家岭东院书库（原高职））", holdings.first().locationDisplay)
        assertEquals("金家岭西院书库", holdings[1].locationDisplay)
        assertEquals("1", holdings[1].volume)
    }

    @Test
    fun `不在架的册按状态码与文字双重判定`() {
        val raw = """{"success":true,"data":{"totalCount":1,"list":[
            {"itemId":1,"processType":"已借出","processTypeCode":"301"}]}}"""
        val holding = json.decodeFromString(
            LibraryEnvelope.serializer(LibraryItemsPageDto.serializer()),
            raw,
        ).data!!.toHoldings().single()
        assertFalse(holding.onShelf)
        assertEquals("已借出", holding.status)
    }

    @Test
    fun `没有在借时 loanNum 仍是可借上限`() {
        // 2026-11-04 /find/loanInfo/loanList 真实响应（本机账号当日无在借）
        val raw = """{"success":true,"data":{"searchResult":[],"loanNum":20,"numFound":0}}"""
        val borrowing = json.decodeFromString(
            LibraryEnvelope.serializer(LibraryLoanPageDto.serializer()),
            raw,
        ).data!!.toBorrowing()
        assertTrue(borrowing.loans.isEmpty())
        assertEquals(0, borrowing.total)
        assertEquals(20, borrowing.maxLoan)
    }

    @Test
    fun `借阅行按服务端列名映射`() {
        // 列名取自 2026-11-04 前端渲染代码的 myBorrow 表 dataIndex：
        // title/author/loanDate/normReturnDate/locationName/callNo/barcode
        val raw = """{"success":true,"data":{"numFound":1,"loanNum":20,"searchResult":[
            {"title":"平凡的世界","author":"路遥著","callNo":"I247.57/LY","locationName":"金家岭西院书库",
             "barcode":"0979524","loanDate":"2026-10-01","normReturnDate":"2026-11-02"}]}}"""
        val loan = json.decodeFromString(
            LibraryEnvelope.serializer(LibraryLoanPageDto.serializer()),
            raw,
        ).data!!.toBorrowing().loans.single()

        assertEquals("平凡的世界", loan.title)
        assertEquals("2026-11-02", loan.dueDate)
        assertEquals(LocalDate.of(2026, 11, 2), loan.dueDay)
    }

    @Test
    fun `预约表的驼峰条码拼法也认`() {
        val raw = """{"success":true,"data":{"searchResult":[{"title":"书","barCode":"B1"}]}}"""
        val loan = json.decodeFromString(
            LibraryEnvelope.serializer(LibraryLoanPageDto.serializer()),
            raw,
        ).data!!.toBorrowing().loans.single()
        assertEquals("B1", loan.barcode)
    }

    @Test
    fun `应还日期决定逾期标记`() {
        val today = LocalDate.of(2026, 11, 4)
        fun loan(due: String) = LibraryLoan(
            title = "书", author = "", callNo = "", location = "", barcode = "",
            loanDate = "2026-10-01", dueDate = due,
        )

        assertEquals(LibraryLoanStatus.OVERDUE, loan("2026-10-20").statusOf(today))
        assertEquals(15, loan("2026-10-20").overdueDays(today))
        assertEquals(LibraryLoanStatus.DUE_SOON, loan("2026-11-04").statusOf(today))
        assertEquals(LibraryLoanStatus.DUE_SOON, loan("2026-11-08").statusOf(today))
        assertEquals(LibraryLoanStatus.NORMAL, loan("2026-12-01").statusOf(today))
        assertNull(loan("2026-12-01").overdueDays(today))
        // 日期读不出来时不能画成「正常」
        assertEquals(LibraryLoanStatus.UNKNOWN, loan("").statusOf(today))
        assertEquals(LibraryLoanStatus.UNKNOWN, loan("不是日期").statusOf(today))
    }

    @Test
    fun `带时刻的日期只取日部分`() {
        assertEquals(LocalDate.of(2026, 10, 1), parseLibDate("2026-10-01 14:03:22"))
        assertNull(parseLibDate(null))
        assertNull(parseLibDate("  "))
    }

    @Test
    fun `没有 userId 就当没有会话`() {
        assertNull(
            json.decodeFromString(LibraryEnvelope.serializer(LibraryUserDto.serializer()), """{"data":null}""")
                .data?.toReader(),
        )
        val raw = """{"success":true,"data":{"userId":348215,"name":"*智灿","unit":"浮山校区","password":""}}"""
        val reader = json.decodeFromString(
            LibraryEnvelope.serializer(LibraryUserDto.serializer()),
            raw,
        ).data!!.toReader()
        assertEquals("*智灿", reader?.name)
        assertEquals("浮山校区", reader?.unit)
    }

    @Test
    fun `请求体保留网页端的显式 null 与空数组`() {
        // 服务端对「有键但为 null」与「没有这个键」处理不同，这里钉住载荷
        val items = requestJson.encodeToString(LibraryItemsRequest(recordId = "421574"))
        assertTrue(items.contains("\"entrance\":null"))
        assertTrue(items.contains("\"recordId\":\"421574\""))
        assertTrue(items.contains("\"isUnify\":true"))

        val search = requestJson.encodeToString(LibrarySearchRequest("平凡的世界", page = 2, rows = 10))
        assertTrue(search.contains("\"searchFieldContent\":\"平凡的世界\""))
        assertTrue(search.contains("\"searchField\":\"keyWord\""))
        assertTrue(search.contains("\"matchMode\":\"2\""))
        assertTrue(search.contains("\"sortClause\":null"))
        assertTrue(search.contains("\"subject\":[]"))

        val loan = requestJson.encodeToString(LibraryLoanRequest(page = 1, rows = 20))
        assertTrue(loan.contains("\"searchType\":1"))
        assertTrue(loan.contains("\"startDate\":null"))
    }
}
