package qdu.campus.domain.model

/**
 * 需要参与会话管理的域。Cookie 按域读取与注入，禁止跨域复用。
 * `tokenBased` 表示该域额外使用 Authorization / TOKEN 头。
 * `clearTextOnly` 表示该域只提供 http，443 无响应，不得强制升级到 https。
 * `cookiePath` 是读写 Cookie 时使用的路径：`getCookie` 按路径前缀过滤，
 * 票挂在哪个前缀下就必须用哪个前缀读。统一认证的 `CASTGC` 挂在 `/authserver/`，
 * 用根地址读不到；yjsxt 的业务页在 `/Njw2017/main/` 下，读取时同样要带上。
 * `needsLogin` 表示该域的页面凭统一认证会话放行，未登录时先用门禁拦住。
 */
enum class WebDomain(
    val host: String,
    val tokenBased: Boolean = false,
    val clearTextOnly: Boolean = false,
    val cookiePath: String = "/",
    val needsLogin: Boolean = false,
) {
    AUTH("authserver.qdu.edu.cn", cookiePath = "/authserver/"),
    SSO("qdsso.qdu.edu.cn"),
    YJSXT("yjsxt.qdu.edu.cn", tokenBased = true, cookiePath = "/Njw2017/main/"),
    ZZMP("zzmp.qdu.edu.cn"),
    EHALL("ehall.qdu.edu.cn", needsLogin = true),
    XG("xg.qdu.edu.cn", needsLogin = true),
    CSYY("csyy.qdu.edu.cn", clearTextOnly = true, needsLogin = true),
    BAOXIU("baoxiu.qdu.edu.cn", clearTextOnly = true, needsLogin = true),
    LIB("lib.qdu.edu.cn"),
    // 超星统一检索。检索与馆藏公开，借阅要会话：会话凭据是这条域自己种的
    // `jwtHeader` + `jwt` 一对 Cookie（网页端用 document.cookie 写的，path=/），
    // 进程被杀即丢，所以要靠本条目参与 Cookie 回灌。
    LIB_SEARCH("findqdu.libsp.cn", needsLogin = true),
    DUIFENE("www.duifene.com"),
    PORTAL_SELF("accounts.qdunet.com"),
    ;

    val homeUrl: String get() = "${if (clearTextOnly) "http" else "https"}://$host/"

    /** Cookie 的读写地址，注入与提取都用它，避免读写不同路径造成会话丢失。 */
    val cookieUrl: String get() = "${if (clearTextOnly) "http" else "https"}://$host$cookiePath"

    companion object {
        fun ofHost(host: String?): WebDomain? =
            entries.firstOrNull { it.host.equals(host, ignoreCase = true) }
    }
}
