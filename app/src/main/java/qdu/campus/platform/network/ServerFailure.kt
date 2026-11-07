package qdu.campus.platform.network

import okhttp3.Response
import qdu.campus.data.remote.ServerMessage
import qdu.campus.platform.common.RemoteDataException

/**
 * 非 2xx 时把服务端写的原因带进异常，而不是只留一个状态码。
 *
 * 只能在**尚未读过 body** 的分支调用：OkHttp 的 body 是一次性的，读过再调这里会拿到空串。
 * 路径一并带上，同一个系统多个接口报错时才分得清是哪一条。
 */
fun Response.serverFailure(system: String): RemoteDataException {
    val rawBody = runCatching { body.string() }.getOrDefault("")
    val reason = ServerMessage.extract(rawBody)
    return RemoteDataException(
        buildString {
            append(system).append("返回 ").append(code)
            if (!reason.isNullOrBlank()) append("：").append(reason)
            append("（").append(request.url.encodedPath).append("）")
        },
    )
}