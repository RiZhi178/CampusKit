package qdu.campus.feature.account

import qdu.campus.domain.model.SessionState
import qdu.campus.platform.common.Formats
import qdu.campus.platform.designsystem.AccountCardState

/**
 * 会话状态到账号卡的唯一映射。学号在这里脱敏，卡片本身不再处理原文。
 * 姓名来自 csyy 一卡通账号接口（见 ProfileRepositoryImpl），
 * 还没同步到时给 null，卡片退回「已登录」。
 */
fun accountCardState(
    session: SessionState,
    studentId: String?,
    name: String? = null,
): AccountCardState =
    if (session == SessionState.Valid) {
        AccountCardState.SignedIn(
            name = name?.takeIf { it.isNotBlank() },
            studentId = Formats.maskStudentId(studentId),
        )
    } else {
        AccountCardState.SignedOut
    }
