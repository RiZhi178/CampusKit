package qdu.campus.platform.security

import qdu.campus.domain.model.SessionState
import qdu.campus.domain.model.WebDomain
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话形态诊断：回答「本机存了什么形态的凭据」，不回答「值是什么」。
 *
 * 界面说未登录、用户却确信登过时，光看状态位分不出是没存到、存的不是 JWT、
 * 还是 JWT 已经过期。这里把四种凭据的形态各报一行，全部只含结构与名字。
 * 不做网络探测，也不触发回灌——诊断不该改变它要诊断的东西。
 */
@Singleton
class SessionDiagnostics @Inject constructor(
    private val credentials: CredentialStore,
) {

    fun snapshot(session: SessionState): List<String> = listOf(
        "统一认证：" + when (session) {
            SessionState.Valid -> "已登录"
            SessionState.Expired -> "已过期"
            SessionState.Unknown -> "未校验或无凭据"
        } + "（最近一次校验的结论，不是实时探测）",
        "authserver Cookie：" + CredentialShape.cookies(credentials.cookie(WebDomain.AUTH.host)),
        "研究生系统令牌：" + CredentialShape.jwt(credentials.yjsxtToken()),
        "图书馆检索 Cookie：" + CredentialShape.cookies(credentials.cookie(WebDomain.LIB_SEARCH.host)),
        "电费 sessionToken：" + CredentialShape.opaque(credentials.zzmpToken()),
    )
}