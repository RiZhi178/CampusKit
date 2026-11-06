package qdu.campus.feature.web

import qdu.campus.domain.model.WebDomain
import qdu.campus.navigation.Routes
import qdu.campus.platform.network.XG_DESKTOP_UA
import java.net.URLEncoder

/**
 * zzmp 的统一入口。qdsso 把 CAS 委托给 authserver，已有 TGT 时静默换票，
 * 不再出现 qdsso 自己的账号表单。
 *
 * ⚠️ 只带 `domain` 时，桥落地的是 zzmp 首页，不是任何一个业务页；要进具体功能
 * 必须再带 `call_page_id`（平台页面码）。综合缴费与电费各用一个页面码。
 */
private const val ZZMP_BRIDGE =
    "https://qdsso.qdu.edu.cn/commonThirdAuthorize/CAS?domain=zzmp.qdu.edu.cn"

/**
 * 电费充值首页。
 *
 * 实测的完整链（2026-11-05）：统一认证 →
 * `qdsso…/CAS?domain=zzmp.qdu.edu.cn&call_page_id=SL320000` → 两次跳转 →
 * `https://zzmp.qdu.edu.cn/SL320000?ssoType=true&timeStamp=…`（标题「校园充值」）。
 * 页面里的路径：「空调电费充值」SL321000（学校列表）→「青岛大学」→ SL321100
 * （充值编号输入，粘房间编码 + 下一步）→ SL321110（提交后的充值页）。
 *
 * 深一层不能直达：单独带 SL321000 / SL321100 / SL321110 打开时，页面只剩标题或搜索框，
 * 学校与卡类型存在前序页面的运行态里。所以入口固定落 SL320000，由用户点那两下。
 */
private const val ZZMP_ELECTRICITY_BRIDGE = ZZMP_BRIDGE + "&call_page_id=SL320000"

/**
 * 办事大厅的直达地址。实测直接打开 `ehall.qdu.edu.cn/` 只是门户壳，
 * 页面上还要点一次「登录」；带着统一认证的票走 CAS 的 service 入口，
 * 会静默换票并落在 `/new/index.html?ticket=ST-…`，进去就是已登录状态。
 */
private val EHALL_BRIDGE =
    "https://${WebDomain.AUTH.host}/authserver/login?service=" +
        URLEncoder.encode(WebDomain.EHALL.homeUrl, "UTF-8")

/**
 * 宿舍报修的 OAuth2 授权入口，走站点的**移动端**回跳。
 *
 * 报修首页是 SPA，未登录时一律落在 `#/login`，页面上还要再点一次「统一身份认证登录」。
 * 那一下点开的地址不在前端包里，而是站点配置接口 `POST /appsys/sys/config/listAll`
 * 返回的 `CAS_URL`（PC）与 `MOBILE_CAS_URL`（移动）：authserver 的 OAuth2 授权码流程，
 * 两者 `client_id` 同为 `5OLwnr9h`，差别只在回跳地址。
 *
 * 实测（2026-11-05，真机内嵌 WebView）两个回跳的表现不一致：
 * PC 的 `/appsys/wiseduOAuthLogin/oauthLogin` 换票成功后，站点直接 alert
 * 「该账号没有系统使用权限」；移动的 `…/mobileLogin` 落到 `/wxweb/`，
 * 是带「报修大厅 / 我要报修 / 我的报修」三个页签的移动版，可以正常用。
 * 所以这里取移动端回跳。
 *
 * 站点把这两条地址都写成 `http://`，这里换成 `https://`：明文地址会被
 * `network_security_config` 拦掉，而站点脚本自己跳转时 `shouldOverrideUrlLoading`
 * 的升级并不可靠。`client_id` 或回跳地址变了，重新取一次配置整体替换，不要只改一段。
 */
private val BAOXIU_OAUTH_BRIDGE =
    "https://${WebDomain.AUTH.host}/authserver/oauth2.0/authorize" +
        "?response_type=code&client_id=5OLwnr9h" +
        "&redirect_uri=http%3A%2F%2Fbaoxiu.qdu.edu.cn%2Fappsys%2FwiseduOAuthLogin%2FmobileLogin" +
        // 站点的 CAS_URL / MOBILE_CAS_URL 里这个参数写了两遍，原样保留
        "&response_type=code"

/**
 * 对分易的学生中心页。站点根路径只是宣传页，会话有效时也不会自动跳转，
 * 每次都要手点一次；中心页直接就是课程列表。
 */
private const val DUIFENE_CENTER = "https://www.duifene.com/_UserCenter/PC/CenterStudent.aspx"

/**
 * 对分易是独立账号，密码一律由用户自己输。中心页在会话失效时只渲染一个
 * 连登录入口都没有的空壳（实测「欢迎您，」后面没有名字，课程条目为 0），
 * 所以检测到这种情况就自动回到登录页。判据只在中心页生效，免得在登录页打转。
 */
private const val DUIFENE_LOGIN_GUARD =
    "(function(){if(location.pathname.indexOf('CenterStudent')<0)return;" +
        "if(!/欢迎您，[^\\s]/.test(document.body.innerText))" +
        "location.href='https://www.duifene.com/';})();"

/**
 * 学工系统的直达地址：每次从统一认证的 service 回跳进去。
 *
 * 站点在没有 xg 会话时自己会把用户送去 authserver，登录后回跳；从 authserver 起
 * 就把这一次换票走满，不留「先打开一个未登录门户页」这种中间态。
 *
 * 门户页在手机 UA 且会话已建立时一律坏：`/userhall/`、`/userhall/Sec/page/index`、
 * `/userhall/Sec/page/index?version=ture` 甚至 `/cas` 都被改道到
 * `/XGPhone/PhoneCas/account/Menu`，那个 IIS 应用已经删除，落在 404 页上。
 * `version=ture`（站点自己的拼写）只挡「浏览器版本过低」那页，挡不住改道，
 * 所以这条入口必须和 XG_DESKTOP_UA 一起用。
 * 子系统也不能当入口：`/apps/应用/Application.aspx` 不带跳票同样回 200、外壳也印得出姓名，
 * 但「登录层次 / 登录时间」两格是空的。直达 URL 只复用会话，不建立会话。
 * 证据与对照表见 `docs/interfaces/xg.md`。
 */
private val XG_CAS_BRIDGE =
    "https://${WebDomain.AUTH.host}/authserver/login?service=" +
        URLEncoder.encode("https://${WebDomain.XG.host}/cas", "UTF-8")

/**
 * 门户里每个服务卡片都是 `window.open(…)` 打开的新窗口，WebView 默认不开多窗口，
 * 于是点卡片没有反应。这里把 `window.open` 改成本页跳转：目标地址是门户签发的
 * 一次性跳票，必须立刻在同一个页面里消费，稍后再导航会拿到 `NoAuth.html`。
 */
private const val XG_POPUP_SHIM =
    "(function(){if(window.__qduShim)return;window.__qduShim=1;" +
        "var o=window.open;window.open=function(u){if(u)location.assign(String(u));return null;};})();";

/**
 * 图书馆借阅会话的建立入口。
 *
 * 检索与馆藏公开，但借阅记录要超星自己的会话：这条 init 地址带着统一认证的票走
 * `tyrzfw.chaoxing.com/auth3/qdu/cas/init → cas/index → passport2-api 的 casreceive
 * → findqdu.libsp.cn/find/sso/login/qdu/0`，落地后网页端才把 `jwtHeader` / `jwt` 写进 Cookie。
 * 中间那页 `cas/index` 是否自动跳转还没测过，所以入口文案不承诺「一次都不用点」。
 */
private const val LIB_SEARCH_CAS_BRIDGE =
    "https://tyrzfw.chaoxing.com/auth3/qdu/cas/init?isLogout=0" +
        "&refer=https%3A%2F%2Ffindqdu.libsp.cn%2Ffind%2Fsso%2Flogin%2Fqdu%2F0%3FopacRoute%3D%2FHome"

/** 入口去向。UI 只按 id 路由，不硬编码 URL。 */
sealed interface ServiceTarget {
    /** 内嵌 WebView，A 组 */
    data class InternalWeb(val url: String, val domain: WebDomain) : ServiceTarget

    /** 跳转外部 App，B 组 */
    data class ExternalApp(
        val appName: String,
        val packageName: String? = null,
        val landingUrl: String? = null,
    ) : ServiceTarget

    /** 跳转外部网页，B 组 */
    data class ExternalWeb(val url: String) : ServiceTarget

    /** 需要经微信进入（公众号 / 小程序），B 组 */
    data class WeChat(val keyword: String) : ServiceTarget
}

/**
 * 服务清单。副标题写明动作与去向：B 组必须写清楚去哪，
 * A 组是内嵌，不写「去」字。
 */
enum class CampusService(
    val id: String,
    val title: String,
    val subtitle: String,
    val target: ServiceTarget,
    val cssInjection: String? = null,
    val scriptInjection: String? = null,
    /**
     * 非空表示这个服务已有原生页，点击入口时走它而不是内嵌 WebView。
     *
     * [target] 仍然保留：原生页在会话缺失时要能把用户送回同一个内嵌页登录，
     * 登录完 Cookie 由 [qdu.campus.platform.network.CookieBridge] 带回原生请求。
     */
    val nativeRoute: String? = null,
    /**
     * 只为个别站点覆盖 WebView 的用户代理。
     *
     * 站点会按 UA 分流页面（学工就是其中一处：手机 UA 加已登录会话被改道到已删除的
     * 移动应用），默认一律跟随系统 WebView 自己的 UA，只有实测证明必需的服务才填。
     */
    val userAgent: String? = null,
) {
    BATH(
        id = "bath",
        title = "洗澡",
        subtitle = "去易校园查看浴室与校园卡",
        target = ServiceTarget.ExternalApp(appName = "易校园"),
    ),
    CHECK_IN(
        id = "checkin",
        title = "签到",
        subtitle = "签到、作业与课程资源",
        target = ServiceTarget.InternalWeb(DUIFENE_CENTER, WebDomain.DUIFENE),
        scriptInjection = DUIFENE_LOGIN_GUARD,
    ),
    ELECTRICITY(
        id = "electricity",
        title = "电费",
        subtitle = "绑定房号，查余额与剩余电量",
        // 必须带页面码：只给 domain 会落在 zzmp 首页，页面上还得自己找「电费充值」。
        // 这条同时是宿舍电费页补会话用的入口（DormElectricityScreen 的 syncUrl）。
        target = ServiceTarget.InternalWeb(ZZMP_ELECTRICITY_BRIDGE, WebDomain.SSO),
        nativeRoute = Routes.DORM_ELECTRICITY,
    ),
    EHALL(
        id = "ehall",
        title = "办事大厅",
        subtitle = "校内服务与办事流程入口",
        target = ServiceTarget.InternalWeb(EHALL_BRIDGE, WebDomain.EHALL),
        nativeRoute = Routes.EHALL,
    ),

    // 以下为校内系统补充入口，见首页「校内系统」区块
    XG(
        id = "xg",
        title = "学工系统",
        subtitle = "请假、测评、三助与宿舍",
        target = ServiceTarget.InternalWeb(XG_CAS_BRIDGE, WebDomain.XG),
        scriptInjection = XG_POPUP_SHIM,
        userAgent = XG_DESKTOP_UA,
    ),
    XG_AWARD(
        id = "xg-award",
        title = "评奖评优",
        subtitle = "查批次窗口与我的申请记录",
        // 原生页读 PJPY 的申请页，但会话不能从这里建立：跳票只由门户页面签发，
        // 直达子页只复用会话。所以回退目标仍是学工门户，进去手点一次「评奖评优管理」。
        target = ServiceTarget.InternalWeb(XG_CAS_BRIDGE, WebDomain.XG),
        scriptInjection = XG_POPUP_SHIM,
        userAgent = XG_DESKTOP_UA,
        nativeRoute = Routes.XG_AWARD,
    ),
    LIBRARY_SEAT(
        id = "csyy",
        title = "预约座位",
        subtitle = "查空闲座位、预约与取消",
        target = ServiceTarget.InternalWeb(WebDomain.CSYY.homeUrl, WebDomain.CSYY),
        nativeRoute = Routes.SEAT,
    ),
    REPAIR(
        id = "baoxiu",
        title = "宿舍报修",
        subtitle = "提交报修与查进度（首次需确认授权）",
        // 不能直接给 homeUrl：那是 SPA 的 `#/login`，页面上还得自己找「统一身份认证登录」。
        target = ServiceTarget.InternalWeb(BAOXIU_OAUTH_BRIDGE, WebDomain.BAOXIU),
        // 列表、详情与撤销已原生化；提交表单暂时仍在内嵌页里做。
        nativeRoute = Routes.BAOXIU,
    ),
    LIBRARY(
        id = "lib",
        title = "图书馆资源",
        subtitle = "查馆藏、在架册数与我的借阅",
        target = ServiceTarget.InternalWeb(LIB_SEARCH_CAS_BRIDGE, WebDomain.LIB_SEARCH),
        nativeRoute = Routes.LIBRARY,
    ),
    PAYMENT(
        id = "payment",
        title = "综合缴费",
        subtitle = "学费、住宿费与空调租赁费",
        target = ServiceTarget.InternalWeb(ZZMP_BRIDGE, WebDomain.SSO),
    ),
    ;

    val isInternal: Boolean get() = target is ServiceTarget.InternalWeb

    /**
     * 是否要先有统一认证（authserver）会话才能用。
     *
     * 登录页直接登录 authserver，建立一份 TGT；`ehall`、`xg`、`csyy`、`baoxiu`
     * 这些由 CAS 放行的域共用同一份票，报修首次进入还要在 OAuth2 页点一次「确认授权」。
     * `lib` 门户本身公开，检索跳到 `findqdu.libsp.cn` 时同样走 authserver，TGT 有效即免登。
     * 办事大厅的首页是门户壳，直接打开还要点一次登录，所以它的入口本身就带 service 参数走 CAS。
     * 电费与综合缴费是 `zzmp` 同域的两个页面，都从财务处公众号共用的 qdsso CAS 桥进入，
     * 桥本身委托 authserver；直接打开 zzmp 首页才会落到 qdsso 自己的账号表单。
     * 对分易是独立账号，不能靠 CAS 免登，因此不拦，交给页面自己的登录页。
     * 研究生系统 `yjsxt` 实测为 `LOCAL` 账号，不在 CAS 内。
     */
    val needsLogin: Boolean
        get() = (target as? ServiceTarget.InternalWeb)?.domain?.needsLogin == true

    companion object {
        fun fromId(id: String?): CampusService? = entries.firstOrNull { it.id == id }

        /**
         * 首页「常用」网格的默认四卡，顺序即展示顺序。
         * 打水、开空调、洗衣服三卡已由首页顶部的通栏扫码大卡取代（PRD F2-6），
         * 老用户 DataStore 里存下的 water / ac / laundry id 会在 [resolveHomeCards] 里被跳过。
         */
        val homeCards: List<CampusService> = listOf(
            BATH, CHECK_IN, ELECTRICITY, EHALL,
        )

        /**
         * 把本机存下的 id 解析成卡片。认不出的 id（入口改过名或已删）跳过；
         * 一个都不剩时回到默认集合——首页不能是空的。
         */
        fun resolveHomeCards(ids: List<String>): List<CampusService> =
            ids.mapNotNull { fromId(it) }.ifEmpty { homeCards }
    }
}
