package qdu.campus.data.remote.xg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import qdu.campus.domain.model.XgAwardPage
import qdu.campus.domain.model.XgLeaveFormPage
import qdu.campus.domain.model.XgLeaveListPage
import qdu.campus.domain.model.XgLeaveRecord
import qdu.campus.domain.model.XgLeaveWriteResult
import qdu.campus.platform.common.RemoteDataException
import qdu.campus.platform.common.XgAuthException
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.network.serverFailure
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学工系统（xg）业务页接口。
 *
 * 会话是门户卡片签发的跳票换来的那一份，本机有就直接用：Cookie 由
 * [qdu.campus.platform.network.CookieBridge] 按请求域注入，`Set-Cookie` 再回写给 WebView，
 * 没有额外的令牌。UA 也不用这里操心，拦截器对 xg 域固定用桌面串——手机 UA 加上
 * 已建立的会话会被站点改道到已删除的移动应用（见 `docs/interfaces/xg.md`）。
 *
 * 评奖评优只读页面、认结构。**日常请假例外**：它的提交、撤销、省市区联动已授权原生实现，
 * 走 WebForms 整表单回发（[XgFormPost]），成功与否只读服务端注入的 `layer.alert` 再重读列表核对，
 * 契约与实测证据见 `docs/interfaces/xg.md` 第四节。其余学工应用仍只读，写操作留在内嵌页。
 */
@Singleton
class XgApi @Inject constructor(
    private val http: HttpClientFactory,
) {

    /**
     * 学校奖助的申请页：头部是批次窗口，下面是本人的申请记录。
     *
     * 只认默认选中的那个批次——切换批次与奖项要 postback，不在原生里做。
     */
    suspend fun schoolAwardApply(): XgAwardPage = withContext(Dispatchers.IO) {
        val page = XgAwardParser.parse(get(SCHOOL_AWARD_APPLY), SCHOOL_AWARD_APPLY)
        // 没有会话时站点把人一路送回统一认证，拿回来的是登录表单：批次表和表格都认不出来
        if (!page.hasContent) throw XgAuthException()
        page
    }

    /** 我的请假列表。顶部要么给「申请」入口，要么给在途互斥的提示，两者必居其一才算业务页。 */
    suspend fun leaveList(): XgLeaveListPage = withContext(Dispatchers.IO) {
        val page = XgLeaveParser.list(get(LEAVE_LIST), LEAVE_LIST)
        if (!page.hasContent) throw XgAuthException()
        page
    }

    /** 新建表单（[recordId] 为空）或详情（带记录号）。站点用同一个编辑页伺服两者。 */
    suspend fun leaveForm(recordId: String = ""): XgLeaveFormPage = withContext(Dispatchers.IO) {
        val url = if (recordId.isEmpty()) LEAVE_EDIT else "$LEAVE_EDIT?Id=$recordId"
        val page = XgLeaveParser.form(get(url), url)
        if (!page.hasContent) throw XgAuthException()
        page
    }

    /**
     * 提交请假。整表单回发 `__EVENTTARGET=Save`，用户改动叠在 [edits] 里。
     *
     * 成功与失败都回 200 且停在原页，所以不拿状态码判成败：读服务端注入的 `layer.alert` 当消息，
     * 再重读列表，用「相对 [existingIds] 多出来的记录」认定真的建上了。调用方传它进来之前看到的 id 集。
     */
    suspend fun submitLeave(
        page: XgLeaveFormPage,
        edits: Map<String, String>,
        existingIds: Set<String>,
    ): XgLeaveWriteResult = withContext(Dispatchers.IO) {
        val response = post(
            page.form.action,
            XgFormPost.pairs(page.form, eventTarget = page.submitTarget, overrides = edits),
        )
        val message = XgLeaveParser.message(response)
        val list = leaveList()
        val created = list.recordIds - existingIds
        XgLeaveWriteResult(message, list, ok = created.isNotEmpty(), recordId = created.firstOrNull().orEmpty())
    }

    /**
     * 列表行内撤销。触发项是那个隐藏 submit 的名值对（`GridView1$ctlNN$DelBtn=撤销申请`），不是 EVENTTARGET。
     *
     * `ctlNN` 会随删行前移，[page] 必须是撤销前刚读到的这一份列表，不能缓存上一轮的按钮名。
     * 撤没撤掉以重读列表后该 id 还在不在为准。
     */
    suspend fun revokeLeave(record: XgLeaveRecord, page: XgLeaveListPage): XgLeaveWriteResult =
        withContext(Dispatchers.IO) {
            val form = page.form ?: throw RemoteDataException("请假列表缺少表单，无法撤销")
            val response = post(form.action, XgFormPost.pairs(form, clickedSubmit = record.revokeButton))
            val message = XgLeaveParser.message(response)
            val list = leaveList()
            XgLeaveWriteResult(message, list, ok = record.id !in list.recordIds, recordId = record.id)
        }

    /** 详情内撤销。触发项是 `DelSave=Button`，撤没撤掉同样以重读列表后该 id 还在不在为准。 */
    suspend fun revokeLeaveDetail(page: XgLeaveFormPage): XgLeaveWriteResult = withContext(Dispatchers.IO) {
        val button = page.revokeSubmit ?: throw RemoteDataException("详情页没有撤销按钮")
        val response = post(page.form.action, XgFormPost.pairs(page.form, clickedSubmit = button.name))
        val message = XgLeaveParser.message(response)
        val list = leaveList()
        XgLeaveWriteResult(message, list, ok = page.recordId !in list.recordIds, recordId = page.recordId)
    }

    /**
     * 省→市→区联动。三个下拉没有 onchange，服务端按 `__EVENTTARGET=<下拉名>` 的回发重绑下级选项。
     *
     * 把用户已填的 [edits] 一并回发，服务端重渲染时会带回来，返回的表单即刷新后的整页。
     */
    suspend fun cascadeArea(
        page: XgLeaveFormPage,
        selectName: String,
        edits: Map<String, String>,
    ): XgLeaveFormPage = withContext(Dispatchers.IO) {
        val response = post(
            page.form.action,
            XgFormPost.pairs(page.form, eventTarget = selectName, overrides = edits),
        )
        XgLeaveParser.form(response, page.url)
    }

    private fun get(url: String): String = execute(
        Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            // 站点按来源判这算不算门户流程里的跳转，带上门户地址最贴近真实点击
            .header("Referer", "https://$HOST/userhall/")
            .build(),
    )

    private fun post(url: String, pairs: List<Pair<String, String>>): String {
        val body = FormBody.Builder().apply { pairs.forEach { (name, value) -> add(name, value) } }.build()
        return execute(
            Request.Builder()
                .url(url)
                .post(body)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Origin", "https://$HOST")
                .header("Referer", url)
                .build(),
        )
    }

    private fun execute(request: Request): String {
        val response = runCatching { http.client.newCall(request).execute() }
            .getOrElse { throw RemoteDataException("无法连接学工系统：${it.message}") }
        response.use {
            // 跳票不在时站点会重定向到 authserver：落地的域不是 xg 就说明没有会话
            if (it.request.url.host != HOST) throw XgAuthException()
            if (!it.isSuccessful) throw it.serverFailure("学工系统")
            return it.body?.string().orEmpty()
        }
    }

    companion object {
        const val HOST = "xg.qdu.edu.cn"

        /**
         * 评奖评优（PJPY）学生端的学校奖助申请页。
         *
         * 用不带 `(S…)` 的干净地址：那是 ASP.NET 的无 Cookie 会话号，等于活凭证，
         * 不进代码、日志与文档。带着已有 Cookie 访问，站点给的是同一个会话。
         */
        private const val SCHOOL_AWARD_APPLY =
            "https://$HOST/apps/PJPY/Features/AppraiseAward/XSCenter/StuSchoolApply.aspx"

        /** 日常请假（DailyLeave）的列表与编辑页，同样用不带会话号的干净地址。 */
        private const val LEAVE_LIST =
            "https://$HOST/apps/DailyLeave/Features/DailyLeave/StuApply.aspx"
        private const val LEAVE_EDIT =
            "https://$HOST/apps/DailyLeave/Features/DailyLeave/StuApply_Edit.aspx"
    }
}
