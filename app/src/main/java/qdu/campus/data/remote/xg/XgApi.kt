package qdu.campus.data.remote.xg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import qdu.campus.domain.model.XgAwardPage
import qdu.campus.platform.common.RemoteDataException
import qdu.campus.platform.common.XgAuthException
import qdu.campus.platform.network.HttpClientFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 学工系统（xg）业务页的只读接口。
 *
 * 会话是门户卡片签发的跳票换来的那一份，本机有就直接用：Cookie 由
 * [qdu.campus.platform.network.CookieBridge] 按请求域注入，`Set-Cookie` 再回写给 WebView，
 * 没有额外的令牌。UA 也不用这里操心，拦截器对 xg 域固定用桌面串——手机 UA 加上
 * 已建立的会话会被站点改道到已删除的移动应用（见 `docs/interfaces/xg.md`）。
 *
 * 只做「读页面、认结构」这一件事：申请、提交、撤销一律留在内嵌页，这里连一次 postback 都不发。
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

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml")
            // 站点按来源判这算不算门户流程里的跳转，带上门户地址最贴近真实点击
            .header("Referer", "https://$HOST/userhall/")
            .build()
        val response = runCatching { http.client.newCall(request).execute() }
            .getOrElse { throw RemoteDataException("无法连接学工系统：${it.message}") }
        response.use {
            // 跳票不在时站点会重定向到 authserver：落地的域不是 xg 就说明没有会话
            if (it.request.url.host != HOST) throw XgAuthException()
            if (!it.isSuccessful) throw RemoteDataException("学工系统返回 ${it.code}")
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
    }
}
