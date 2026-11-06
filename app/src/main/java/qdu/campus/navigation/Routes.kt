package qdu.campus.navigation

import android.net.Uri

/** 路由定义。顶层四页走底部导航，其余为独立全屏路由。 */
object Routes {
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val NOTICE = "notice"
    const val SETTINGS = "settings"

    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"

    /**
     * 研究生系统自己的登录页。它不在统一认证链上（`getToken` 对合法票据也回认证失败，
     * 2026-11-05 实测，见 `docs/interfaces/yjsxt.md`），课表需要它单独登录一次。
     */
    const val YJSXT_LOGIN = "yjsxt-login"
    const val ACCOUNT = "account"
    const val NETWORK = "network"

    /** 宿舍电费：绑定的房号与账户快照，原生页。充值仍进 [web] 的 zzmp 内嵌页。 */
    const val DORM_ELECTRICITY = "dorm-electricity"

    /** 宿舍电费的绑定页：生成编号、核对后写进 [DORM_ELECTRICITY] 用的那个编号。 */
    const val ROOM_CODE = "room-code"
    const val APPEARANCE = "appearance"

    /** 首页「常用」卡片的数量与顺序。 */
    const val HOME_CARDS = "home-cards"

    /** 自习室座位，原生页。首次登录仍走 [web] 的 csyy 内嵌页。 */
    const val SEAT = "seat"

    /** 办事大厅的服务清单，原生页。点开某个服务仍进内嵌 WebView。 */
    const val EHALL = "ehall"

    /** 宿舍报修「我的报修」，原生页。会话过期或要提交时回 [web] 的 baoxiu 内嵌页。 */
    const val BAOXIU = "baoxiu"

    /** 单个工单详情。工单号由列表传入。 */
    const val BAOXIU_DETAIL = "baoxiu-detail/{ticketId}"

    /** 「我要报修」原生表单。 */
    const val BAOXIU_NEW = "baoxiu-new"

    /** 扫码页：识别码内容并按归属转交（微信/支付宝/应用内网页），见 docs/scan-slice.md。 */
    const val SCAN = "scan"

    fun baoxiuDetail(ticketId: String) = "baoxiu-detail/${Uri.encode(ticketId)}"

    /** 图书馆馆藏检索与借阅，原生页。借阅缺会话时走 [web] 的超星免登链补登录。 */
    const val LIBRARY = "library"

    /** 学工评奖评优：批次窗口与本人申请记录，原生页。缺会话时走 [web] 的学工门户手点一次。 */
    const val XG_AWARD = "xg-award"

    const val WEB = "web/{serviceId}"
    const val WEB_URL = "web-url/{url}?title={title}"

    fun web(serviceId: String) = "web/$serviceId"

    /** 任意 URL 内嵌页。URL 与标题都要编码，避免查询串被二次解析。 */
    fun webUrl(url: String, title: String = ""): String =
        "web-url/${Uri.encode(url)}?title=${Uri.encode(title)}"
}
