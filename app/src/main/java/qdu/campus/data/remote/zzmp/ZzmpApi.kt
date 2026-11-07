package qdu.campus.data.remote.zzmp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import qdu.campus.domain.model.RoomAccount
import qdu.campus.domain.model.RoomAccountLine
import qdu.campus.platform.common.RemoteDataException
import qdu.campus.platform.common.RoomCodeRejectedException
import qdu.campus.platform.common.ZzmpAuthException
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.network.appJson
import qdu.campus.platform.network.serverFailure
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 电费与综合缴费（zzmp，上海商联 SLTAS）的只读接口。
 *
 * 会话是 Cookie 里的 `SESSION`（Spring Session），由 qdsso 的 CAS 桥落地时种下；
 * Cookie 由 [qdu.campus.platform.network.CookieBridge] 注入，没有 Authorization 头。
 * 每个请求另外要带 [ZzmpSigner] 产出的四个头，否则服务端按「未登录 / 缺域名」拒绝。
 *
 * 只做房间编号核对与账户快照：充值、下单与支付一律留在内嵌页，
 * 见 AGENTS 1.4「碰资金操作」与 `_research/zzmp/CAPTURE-NOTES.txt` 的 OFF-LIMITS 清单。
 */
@Singleton
class ZzmpApi @Inject constructor(
    private val http: HttpClientFactory,
    private val session: ZzmpSession,
) {

    /**
     * 校验房间编号并取账户信息，三步都走充值页自己的接口：
     * `createPage`（拿商户号）→ `merMapInfo`（拿用户与卡类型映射）→ `selectAndCheckOrder`（拿账户）。
     * 2026-11-05 实测这一步不会产生待支付单，所以可以拿来在绑定房号时核对编号、
     * 以及宿舍电费页每次进入取账户快照。
     */
    suspend fun checkRoomCode(code: String): RoomAccount = withContext(Dispatchers.IO) {
        val createRaw = post(
            PATH_CREATE_PAGE,
            appJson.encodeToString(
                ZzmpCreatePageQuery.serializer(),
                ZzmpCreatePageQuery(domain = HOST, callPageId = PAGE_RECHARGE),
            ),
        )
        val page = try {
            appJson.decodeFromString(ZzmpCreatePageDto.serializer(), createRaw)
        } catch (e: SerializationException) {
            throw RemoteDataException("电费系统返回了无法识别的内容")
        }
        if (page.code != ZzmpPageEnvelope.CODE_OK) {
            throw RemoteDataException(page.message?.takeIf { it.isNotBlank() } ?: "电费系统未返回商户信息")
        }
        if (page.merchantId <= 0L) throw RemoteDataException("电费系统未返回商户号")

        val mapRaw = post(
            PATH_MER_MAP_INFO,
            appJson.encodeToString(
                ZzmpMerMapQuery.serializer(),
                ZzmpMerMapQuery(merchantId = page.merchantId),
            ),
        )
        val map = decodePage(mapRaw, ZzmpMerMapDto.serializer())
            ?: throw RemoteDataException("电费系统未返回充值账户")

        val checkRaw = post(
            PATH_CHECK_ORDER,
            appJson.encodeToString(
                ZzmpCheckOrderQuery.serializer(),
                ZzmpCheckOrderQuery(
                    merchantId = page.merchantId,
                    userInfoId = map.clientUserInfoId,
                    rechMerMapId = map.rechMerMapId.takeIf { it > 0 } ?: 1,
                    custRechNo = code,
                ),
            ),
        )
        val check = decodePage(checkRaw, ZzmpCheckOrderDto.serializer())
            ?: throw RemoteDataException("电费系统返回了空内容")
        RoomAccount(
            lines = check.deviceInfo.firstOrNull()?.infos.orEmpty()
                .map { RoomAccountLine(it.keyName, it.keyValue) }
                .filter { it.label.isNotBlank() },
        )
    }

    /** 带 `datajson` 外壳的接口：这里统一判成功/掉线，失败的消息翻成人话。 */
    private fun <T> decodePage(raw: String, serializer: kotlinx.serialization.KSerializer<T>): T? {
        val envelope = try {
            appJson.decodeFromString(ZzmpPageEnvelope.serializer(serializer), raw)
        } catch (e: SerializationException) {
            throw RemoteDataException("电费系统返回了无法识别的内容")
        }
        if (envelope.isLoggedOut) throw ZzmpAuthException()
        if (!envelope.isSuccess) {
            val text = friendlyMessage(envelope.message)
            // 「没有账户」是服务端给的定论，单独一个类型，绑定页才能把它和「没查成」
            // （会话过期、连不上）分开：前者拦住绑定，后者照常落盘、回仪表盘再查。
            throw if (text.contains("没有账户")) RoomCodeRejectedException(text)
            else RemoteDataException(text)
        }
        return envelope.datajson
    }

    /**
     * 服务端的失败消息是「第三方接口响应：对应房间没有账户，接口名称：…，URL：…」，
     * 后面两段是给运维看的，界面上只留能操作的前半句。
     */
    private fun friendlyMessage(raw: String?): String {
        val text = raw.orEmpty()
        if (text.contains("没有账户")) return "对应房间没有账户，请核对楼栋与房号"
        return text.substringAfter("第三方接口响应：", text)
            .substringBefore("，接口名称")
            .ifBlank { "房间编号校验失败" }
    }

    /** 所有 `/api/` 请求共用一套头：域名、四件签名、会话令牌。 */
    private fun post(path: String, body: String): String {
        val signature = ZzmpSigner.sign(body)
        val request = Request.Builder()
            .url(BASE_URL + path)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json, text/plain, */*")
            .header("Content-Type", "application/json;charset=UTF-8")
            // 服务端拿它判租户；缺了直接 40310002，与登录态无关
            .header("requestSiteDomain", HOST)
            .header("requestid", signature.requestId)
            .header("rand", signature.rand)
            .header("token", signature.token)
            .header("sessionToken", session.token().orEmpty())
            .header("Referer", ORIGIN + "/")
            .build()
        return execute(request)
    }

    private fun execute(request: Request): String {
        val response = runCatching { http.client.newCall(request).execute() }
            .getOrElse { throw RemoteDataException("无法连接电费系统：${it.message}") }

        response.use {
            if (!it.isSuccessful) throw it.serverFailure("电费系统")
            val text = it.body?.string().orEmpty()
            if (text.isBlank()) throw RemoteDataException("电费系统返回空内容")
            // 掉线时可能被重定向到登录页，拿到的是 HTML 而不是 JSON
            if (text.trimStart().startsWith("<")) throw ZzmpAuthException()
            return text
        }
    }

    companion object {
        private const val ORIGIN = "https://zzmp.qdu.edu.cn"
        const val HOST = "zzmp.qdu.edu.cn"
        private const val BASE_URL = "$ORIGIN/api"
        private const val PATH_CREATE_PAGE = "/pageService/createPage"
        private const val PATH_MER_MAP_INFO = "/rechargeMobileService/merMapInfo"
        private const val PATH_CHECK_ORDER = "/rechargeMobileService/selectAndCheckOrder"

        /** 电费充值页（SL321110）的页面码，商户号从它的配置里取。 */
        private const val PAGE_RECHARGE = "SL321110"

        private val JSON_MEDIA = "application/json;charset=UTF-8".toMediaType()
    }
}
