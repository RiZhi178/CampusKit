package qdu.campus.domain.model

/** 列表里的一条请假记录。 cells 保持服务端原文与列顺序，列名由页面的表头行给。 */
data class XgLeaveRecord(
    val id: String,
    val cells: List<String>,
    val detailUrl: String,
    /** 行内那个隐藏 submit 的 name（`GridView1$ctlNN$DelBtn`）；空串表示这一行不能撤。 */
    val revokeButton: String,
    val revokeConfirm: String,
)

/**
 * 我的请假列表页。
 *
 * 顶部二选一：能申请时是 `#AddDiv`，有在途申请时换成 `#NoLeaveDiv` 的提示。
 * 这条互斥是站点自己的规矩——提示不消失，新建表单连字段都不渲染，所以 [blockedMessage]
 * 非空时界面不给「申请」入口，直接印这句话。
 */
data class XgLeaveListPage(
    val url: String,
    val form: XgForm?,
    val canApply: Boolean,
    val applyUrl: String,
    val blockedMessage: String,
    val caption: String,
    val headers: List<String>,
    val records: List<XgLeaveRecord>,
    val totalRecords: Int?,
) {
    /** 认不出表单也认不出门禁与表格，就当这一页不是业务页（通常是会话没建立）。 */
    val hasContent: Boolean
        get() = form != null &&
            (canApply || blockedMessage.isNotEmpty() || headers.isNotEmpty() || records.isNotEmpty())

    val recordIds: Set<String> get() = records.map { it.id }.toSet()

    /** 翻页按钮按 name 后缀认，禁用的那两个就是没有上一页 / 下一页。 */
    val prevPage: XgControl? get() = pager("Prevpage")
    val nextPage: XgControl? get() = pager("Nextpage")

    private fun pager(suffix: String): XgControl? =
        form?.controls?.firstOrNull {
            it.kind == XgControlKind.SUBMIT && it.name.endsWith("$suffix")
        }
}

/** 页面上「标签：值」成对印出来的原文，学生基本信息与说明性文字都用它。 */
data class XgLabelledValue(val label: String, val value: String)

/** 详情页顶部的审批进度。 */
data class XgLeaveApproval(
    val status: String,
    val steps: List<String>,
    val records: List<String>,
)

/** 表单的一个分区：标题来自 `td.TitleTd`，字段按文档顺序。 */
data class XgLeaveSection(
    val title: String,
    val fields: List<XgControl>,
    val notes: List<XgLabelledValue> = emptyList(),
)

/**
 * 新建表单页，也是详情页——站点用同一个 `StuApply_Edit.aspx` 伺服两者，
 * 带 `?Id=` 就是详情：多一段审批信息、多两个流程隐藏字段、`Type` 为空。
 *
 * [message] 是服务端注入的 `layer.alert` 文案。成功与失败都回 200 且留在原页，
 * 所以这句话是唯一的即时反馈，不能拿地址变化当成功。
 */
data class XgLeaveFormPage(
    val url: String,
    val form: XgForm,
    val student: List<XgLabelledValue>,
    val sections: List<XgLeaveSection>,
    val approval: XgLeaveApproval?,
    val recordId: String,
    /** 页面自己声明的提交目标，取自「提交」那个链接的 `__doPostBack('Save','')`。 */
    val submitTarget: String,
    val submitLabel: String,
    val canRevoke: Boolean,
    val revokeConfirm: String,
    val message: String,
) {
    val isNew: Boolean get() = recordId.isEmpty()

    /** 一个控件都没有就不是这一页（跳票不在时站点把人送去统一认证，拿回来的是登录表单）。 */
    val hasContent: Boolean get() = form.controls.isNotEmpty()

    /** 在途互斥时服务端把控件全撤了，只剩外壳：这种页没有可填的东西。 */
    val blocked: Boolean get() = sections.all { it.fields.isEmpty() }

    /** 详情页只有这一个 submit（`DelSave`），列表页那些翻页按钮不会出现在这里。 */
    val revokeSubmit: XgControl?
        get() = form.controls.firstOrNull { it.kind == XgControlKind.SUBMIT && it.enabled }
}

/** 一次写操作的结果：服务端那句话 + 立刻重读的列表 + 到底成没成。 */
data class XgLeaveWriteResult(
    val message: String,
    val list: XgLeaveListPage,
    val ok: Boolean,
    /** 新建出来的记录 id，或者被撤掉的那个；没认定为成功时是空串。 */
    val recordId: String,
)

/** 校验不通过的一条。name 用来定位控件，message 直接印给用户。 */
data class XgFieldError(val name: String, val label: String, val message: String)