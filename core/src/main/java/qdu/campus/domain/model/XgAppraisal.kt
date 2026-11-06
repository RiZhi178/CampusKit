package qdu.campus.domain.model

/**
 * 学工系统（xg）评奖评优的当前批次窗口。
 *
 * 取自 PJPY 申请页顶部的批次表，字段一律保持服务端给的原文（`2025-2026学年`、
 * `2026-10-27至2026-11-15`、`已开始,可以提交申请`），不在这里改写成结构化日期：
 * 站点的写法会变，改写就要跟着维护，界面直接印原文更稳。
 */
data class XgAwardBatch(
    val name: String,
    /** 同一页面下拉里的其它批次。切换要 postback，原生只读，所以只作为提示列出。 */
    val otherNames: List<String>,
    val year: String,
    val dateRange: String,
    val status: String,
    /** 当前批次下可选的奖项名。 */
    val awardOptions: List<String>,
)

/**
 * 学工 WebForms 列表页里的一张表格。
 *
 * 列名由服务端给（有数据时表头行才存在），所以这里用「表头 + 字符串行」而不是固定字段：
 * 校方增减列不用改代码，界面按表头排。
 */
data class XgGrid(
    val id: String,
    val headers: List<String>,
    val rows: List<List<String>>,
) {
    /** 行宽不齐时按表头补齐，界面就不必处理错位。 */
    val normalizedRows: List<List<String>>
        get() {
            val width = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
            if (width == 0) return emptyList()
            return rows.map { row -> (0 until width).map { row.getOrElse(it) { "" } } }
        }
}

/** 一个学工申请页读回来的全部内容。 */
data class XgAwardPage(
    val batch: XgAwardBatch?,
    /** 表格上方的分区标题，实测是「学校奖学金」。 */
    val caption: String,
    /** 分页控件报的条数；这句提示没认出来时为 null，界面就不报数，不当成 0 条。 */
    val totalRecords: Int?,
    val grids: List<XgGrid>,
) {
    /** 认不出批次表也没有表格时，按「这一页不是业务页」处理，通常是会话没建立。 */
    val hasContent: Boolean get() = batch != null || grids.isNotEmpty()

    val records: List<XgGrid> get() = grids.filter { it.rows.isNotEmpty() }
}
