package qdu.campus.platform.security

import qdu.campus.domain.model.SessionState
import qdu.campus.domain.model.WebDomain
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话诊断：用白话报各系统当前能不能用，不出现凭据值，也不出现 Cookie、令牌这类名词。
 *
 * 某个功能打不开时，用户先看这里是不是「未登录」。只读本机已存的凭据在不在，
 * 不做网络探测，也不触发回灌——诊断不该改变它要诊断的东西。
 */
@Singleton
class SessionDiagnostics @Inject constructor(
    private val credentials: CredentialStore,
) {

    fun snapshot(session: SessionState, electricityRoomBound: Boolean): List<String> = listOf(
        "统一认证：" + when (session) {
            SessionState.Valid -> "已登录"
            SessionState.Expired -> "登录已过期"
            SessionState.Unknown -> "未登录"
        },
        "研究生系统：" + if (credentials.yjsxtToken().isNullOrBlank()) "未登录" else "已登录",
        "图书馆：" + if (credentials.cookie(WebDomain.LIB_SEARCH.host).isNullOrBlank()) "未登录" else "已登录",
        "电费：" + if (electricityRoomBound) "已绑定房间" else "未绑定房间",
    )
}