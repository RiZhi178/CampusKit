package qdu.campus.data.remote.lib

import kotlinx.serialization.Serializable
import qdu.campus.domain.model.LibraryBorrowing
import qdu.campus.domain.model.LibraryHolding
import qdu.campus.domain.model.LibraryLoan
import qdu.campus.domain.model.LibraryReader
import qdu.campus.domain.model.LibraryRecord
import qdu.campus.domain.model.LibrarySearchPage

/**
 * 超星 libsp 统一检索的响应外壳。
 *
 * `success` 才是成败位；`errCode` 只是消息码，实测详情页正常返回时它是 9000108
 * 而不是 200，所以不能拿 `errCode == 200` 判成功。服务端字段远多于这里声明的，
 * 反序列化必须忽略未知键。
 */
@Serializable
data class LibraryEnvelope<T>(
    val success: Boolean = false,
    val message: String? = null,
    val errCode: Int? = null,
    val errorCode: String? = null,
    val data: T? = null,
)

@Serializable
data class LibrarySearchPageDto(
    val numFound: Int = 0,
    val start: Int = 0,
    val searchResult: List<LibraryRecordDto>? = null,
)

/**
 * 检索结果的一条书目。
 *
 * `adstract` 不是笔误，服务端字段名就是这个拼错的写法。
 * `onShelfCountI` 只有数字后缀的 I，是超星按分馆算出来的在架数，匿名访问时为 null。
 */
@Serializable
data class LibraryRecordDto(
    val recordId: Long? = null,
    val title: String? = null,
    val author: String? = null,
    val publisher: String? = null,
    val publishYear: String? = null,
    val isbn: String? = null,
    val subjectWord: String? = null,
    val callNoOne: String? = null,
    val callNo: List<String>? = null,
    val onShelfCountI: Int? = null,
    val physicalCount: Int? = null,
    val groupPhysicalCount: Int? = null,
)

/** 馆藏列表：`data.list` 是一册一条。 */
@Serializable
data class LibraryItemsPageDto(
    val totalCount: Int = 0,
    val list: List<LibraryItemDto>? = null,
)

@Serializable
data class LibraryItemDto(
    val itemId: Long? = null,
    val barcode: String? = null,
    val callNo: String? = null,
    val locationName: String? = null,
    val curLocationName: String? = null,
    val processType: String? = null,
    val processTypeCode: String? = null,
    val inDate: String? = null,
    val vol: String? = null,
    val shelfNo: String? = null,
)

/**
 * 借阅列表外壳。
 *
 * `loanNum` 实测恒为可借上限（青大 20），不是已借数——已借数看 [numFound]。
 * 两个列表（当前借阅 / 借阅历史）共用这一行结构。
 */
@Serializable
data class LibraryLoanPageDto(
    val numFound: Int = 0,
    val loanNum: Int? = null,
    val searchResult: List<LibraryLoanDto>? = null,
)

@Serializable
data class LibraryLoanDto(
    val title: String? = null,
    val author: String? = null,
    val callNo: String? = null,
    val locationName: String? = null,
    /** 服务端两处拼法不同：借阅表用 `barcode`，请求/预约表用 `barCode`，都收下再取非空的那个。 */
    val barcode: String? = null,
    val barCode: String? = null,
    val loanDate: String? = null,
    val normReturnDate: String? = null,
    val returnDate: String? = null,
)

/** `GET /oga/userinfo`：会话有效时给出脱敏姓名与校区，掉线时响应体为空。 */
@Serializable
data class LibraryUserDto(
    val userId: Long? = null,
    val name: String? = null,
    val unit: String? = null,
)

fun LibrarySearchPageDto.toPage(page: Int, rows: Int): LibrarySearchPage {
    val records = searchResult.orEmpty().mapNotNull { it.toRecord() }
    return LibrarySearchPage(
        records = records,
        numFound = numFound,
        page = page,
        hasMore = page.toLong() * rows < numFound.toLong(),
    )
}

fun LibraryRecordDto.toRecord(): LibraryRecord? {
    val id = recordId ?: return null
    val name = title?.trim().orEmpty()
    // 题名是列表里唯一不能空的字段，服务端给了空题名就等于给了一条读不出来的记录
    if (name.isEmpty()) return null
    return LibraryRecord(
        recordId = id,
        title = name,
        author = author?.trim().orEmpty(),
        publisher = publisher?.trim().orEmpty(),
        publishYear = publishYear?.trim().orEmpty(),
        isbn = isbn?.trim().orEmpty(),
        callNo = callNoOne?.trim().orEmpty().ifEmpty { callNo?.firstOrNull()?.trim().orEmpty() },
        subject = subjectWord?.trim().orEmpty(),
        onShelfCount = onShelfCountI,
        physicalCount = groupPhysicalCount ?: physicalCount,
    )
}

fun LibraryItemsPageDto.toHoldings(): List<LibraryHolding> =
    list.orEmpty().mapNotNull { item ->
        val id = item.itemId ?: return@mapNotNull null
        LibraryHolding(
            itemId = id,
            barcode = item.barcode?.trim().orEmpty(),
            callNo = item.callNo?.trim().orEmpty(),
            shelfLocation = item.locationName?.trim().orEmpty(),
            currentLocation = item.curLocationName?.trim().orEmpty(),
            status = item.processType?.trim().orEmpty(),
            statusCode = item.processTypeCode?.trim().orEmpty(),
            acquiredAt = item.inDate?.trim().orEmpty(),
            volume = item.vol?.trim().orEmpty(),
        )
    }

fun LibraryLoanPageDto.toBorrowing(): LibraryBorrowing =
    LibraryBorrowing(
        loans = searchResult.orEmpty().mapNotNull { it.toLoan() },
        total = numFound,
        maxLoan = loanNum?.takeIf { it > 0 },
    )

fun LibraryLoanDto.toLoan(): LibraryLoan? {
    val name = title?.trim().orEmpty()
    if (name.isEmpty()) return null
    return LibraryLoan(
        title = name,
        author = author?.trim().orEmpty(),
        callNo = callNo?.trim().orEmpty(),
        location = locationName?.trim().orEmpty(),
        barcode = (barcode ?: barCode)?.trim().orEmpty(),
        loanDate = loanDate?.trim().orEmpty(),
        dueDate = normReturnDate?.trim().orEmpty(),
        returnDate = returnDate?.trim().orEmpty(),
    )
}

/** 只有服务端认了这个会话才会给 userId，姓名缺失时留空由页面决定不显示。 */
fun LibraryUserDto.toReader(): LibraryReader? = userId?.let {
    LibraryReader(name = name?.trim().orEmpty(), unit = unit?.trim().orEmpty())
}

/**
 * 检索请求体，逐字段照抄网页端的关键字检索：`searchField=keyWord` 是「全部字段」，
 * `matchMode="2"` 是模糊匹配，其余筛选项一律留空列表而不是省略——服务端对缺省数组
 * 的处理与空数组不同，实测会返回不同条数。
 */
@Serializable
data class LibrarySearchRequest(
    val searchFieldContent: String,
    val page: Int = 1,
    val rows: Int = DEFAULT_ROWS,
    val searchField: String = "keyWord",
    val matchMode: String = "2",
    val docCode: List<Int?> = listOf(null),
    val litCode: List<String> = emptyList(),
    val resourceType: List<String> = emptyList(),
    val subject: List<String> = emptyList(),
    val discode1: List<String> = emptyList(),
    val publisher: List<String> = emptyList(),
    val libCode: List<String> = emptyList(),
    val locationId: List<String> = emptyList(),
    val eCollectionIds: List<String> = emptyList(),
    val neweCollectionIds: List<String> = emptyList(),
    val curLocationId: List<String> = emptyList(),
    val campusId: List<String> = emptyList(),
    val kindNo: List<String> = emptyList(),
    val collectionName: List<String> = emptyList(),
    val author: List<String> = emptyList(),
    val langCode: List<String> = emptyList(),
    val countryCode: List<String> = emptyList(),
    val coreInclude: List<String> = emptyList(),
    val ddType: List<String> = emptyList(),
    val verifyStatus: List<String> = emptyList(),
    val group: List<String> = emptyList(),
    val keyWord: List<String> = emptyList(),
    val sortField: String = "relevance",
    val sortClause: String? = null,
    val onlyOnShelf: Boolean? = null,
) {
    companion object {
        const val DEFAULT_ROWS = 10
    }
}

/**
 * 馆藏（在架）请求体。`recordId` 是字符串，`isUnify=true` 表示合并各分馆馆藏，
 * 网页端固定这么传；`entrance` 与 `callNo` 是分馆/索书号过滤，这里不做。
 */
@Serializable
data class LibraryItemsRequest(
    val recordId: String,
    val page: Int = 1,
    val rows: Int = 20,
    val isUnify: Boolean = true,
    val sortType: Int = 0,
    val entrance: String? = null,
    val callNo: String = "",
)

/** 借阅列表请求体。`searchType=1` 是书名，`sortType=0` 按借出时间倒序。 */
@Serializable
data class LibraryLoanRequest(
    val page: Int = 1,
    val rows: Int = 20,
    val searchType: Int = 1,
    val searchContent: String = "",
    val sortType: Int = 0,
    val startDate: String? = null,
    val endDate: String? = null,
)
