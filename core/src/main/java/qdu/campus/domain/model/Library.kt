package qdu.campus.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 图书馆统一检索（`findqdu.libsp.cn`，超星 libsp）的领域模型。
 *
 * 检索与馆藏是公开的，借阅记录要会话，所以这里的类型按「一本书」和「一条借阅」两条线分开，
 * 不互相依赖：拿不到借阅时检索页照常可用。
 */

/** 一条书目。数字字段保持可空：服务端匿名返回时不给在架数。 */
data class LibraryRecord(
    val recordId: Long,
    val title: String,
    val author: String,
    val publisher: String,
    val publishYear: String,
    val isbn: String,
    val callNo: String,
    val subject: String,
    val onShelfCount: Int? = null,
    val physicalCount: Int? = null,
) {
    /** 「出版社 · 年份」，两者都缺时返回空串，由页面决定不显示这一行。 */
    val imprint: String
        get() = listOf(publisher, publishYear).filter { it.isNotBlank() }.joinToString(" · ")

    val hasHoldings: Boolean get() = physicalCount?.let { it > 0 } ?: true
}

data class LibrarySearchPage(
    val records: List<LibraryRecord>,
    val numFound: Int,
    val page: Int,
    val hasMore: Boolean,
)

/**
 * 一册实体书的馆藏。
 *
 * [status] 是服务端原文（实测「在架」），[statusCode] 是它的码；判在架以码为主，
 * 码缺失时回退到文字，因为青大只观察到 `411` 一个值。
 */
data class LibraryHolding(
    val itemId: Long,
    val barcode: String,
    val callNo: String,
    val shelfLocation: String,
    val currentLocation: String,
    val status: String,
    val statusCode: String,
    val acquiredAt: String,
    val volume: String,
) {
    val onShelf: Boolean
        get() = statusCode.isNotBlank() && statusCode == STATUS_ON_SHELF || status.contains("在架")

    /** 典藏地与实际存放地不同时才有必要多说一句，多数条目两处一致。 */
    val locationDisplay: String
        get() = if (currentLocation.isNotBlank() && currentLocation != shelfLocation) {
            "$shelfLocation（现存放：$currentLocation）"
        } else {
            shelfLocation
        }

    companion object {
        const val STATUS_ON_SHELF = "411"
    }
}

/** 一条借阅记录。日期一律用服务端的原文，格式异常时原样显示，不做二次猜测。 */
data class LibraryLoan(
    val title: String,
    val author: String,
    val callNo: String,
    val location: String,
    val barcode: String,
    val loanDate: String,
    val dueDate: String,
    /** 只有借阅历史才有：实际归还日。 */
    val returnDate: String = "",
) {
    val dueDay: LocalDate? get() = parseLibDate(dueDate)
    val loanDay: LocalDate? get() = parseLibDate(loanDate)

    /**
     * 应还状态。日期读不出来时返回 [LibraryLoanStatus.UNKNOWN]，
     * 页面据此什么标记都不画——「不知道」不能画成「正常」。
     */
    fun statusOf(today: LocalDate): LibraryLoanStatus {
        val due = dueDay ?: return LibraryLoanStatus.UNKNOWN
        return when {
            due.isBefore(today) -> LibraryLoanStatus.OVERDUE
            due.isBefore(today.plusDays(SOON_DAYS)) || due == today -> LibraryLoanStatus.DUE_SOON
            else -> LibraryLoanStatus.NORMAL
        }
    }

    /** 已逾期多少天；未逾期或读不出日期时为 null。 */
    fun overdueDays(today: LocalDate): Int? {
        val due = dueDay ?: return null
        val days = today.toEpochDay() - due.toEpochDay()
        return if (days > 0) days.toInt() else null
    }

    companion object {
        /** 到期前 5 天开始提示。 */
        const val SOON_DAYS = 5L
    }
}

enum class LibraryLoanStatus { OVERDUE, DUE_SOON, NORMAL, UNKNOWN }

/** 借阅列表连同额度。[maxLoan] 是服务端 `loanNum`，实测与可借上限一致，取不到就当没有。 */
data class LibraryBorrowing(
    val loans: List<LibraryLoan>,
    val total: Int,
    val maxLoan: Int? = null,
)

/** 借阅人概况，只用于「这是我的记录」这一行确认。姓名由服务端脱敏下发。 */
data class LibraryReader(
    val name: String,
    val unit: String,
)

/**
 * 服务端日期常见 `yyyy-MM-dd`，也见过带时间的写法。
 * 只截取日部分再解析，解析失败返回 null 而不是抛异常。
 */
fun parseLibDate(raw: String?): LocalDate? {
    val text = raw?.trim()?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { LocalDate.parse(text, LIB_DATE) }.getOrNull()
        ?: runCatching { LocalDate.parse(text) }.getOrNull()
}

private val LIB_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
