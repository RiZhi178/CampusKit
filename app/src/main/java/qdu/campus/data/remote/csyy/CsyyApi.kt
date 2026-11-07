package qdu.campus.data.remote.csyy

import qdu.campus.domain.model.SeatRoom
import qdu.campus.platform.common.CsyyAuthException
import qdu.campus.platform.common.RemoteDataException
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.network.appJson
import qdu.campus.platform.network.serverFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 场所管理系统（csyy）自习室座位接口。
 *
 * 会话完全靠 Cookie：实测只带 CookieManager 里的 csyy Cookie、不带网页端那个
 * `token` 头，`account/info`、`reserve`、`reserve/resvInfo` 一律返回 code 0，
 * 所以这里不收割 sessionStorage，[qdu.campus.platform.network.CookieBridge] 就够。
 *
 * 它的请求助手形状是 `axios[method](url, params)`：GET 时 params 是 axios 配置
 * （进查询串），POST 时是请求体。三个 POST 逐条核对过，接口变更只影响本文件。
 */
@Singleton
class CsyyApi @Inject constructor(
    private val http: HttpClientFactory,
) {

    suspend fun fetchRooms(): List<SeatRoom> = withContext(Dispatchers.IO) {
        decode<List<SeatMenuNodeDto>>(get("/seatMenu"))
            .requireData(ROOMS_FALLBACK)
            .toRooms()
    }

    suspend fun fetchSeats(roomId: Int, date: LocalDate): List<CsyySeatDto> =
        withContext(Dispatchers.IO) {
            decode<List<CsyySeatDto>>(
                get("/reserve") {
                    addQueryParameter("roomIds", roomId.toString())
                    addQueryParameter("resvDates", date.format(DATE_COMPACT))
                    addQueryParameter("sysKind", SYS_KIND_SEAT.toString())
                },
            ).requireData(SEATS_FALLBACK)
        }

    suspend fun fetchAccount(): CsyyAccountDto? = withContext(Dispatchers.IO) {
        decode<CsyyAccountDto>(get("/account/info")).dataOrNull()
    }

    /**
     * 免登链的入口地址，公开接口、无会话也返回 code 0。
     *
     * 网页端的 axios 拦截器收到 code 300 时就是拿这个结果去跳统一认证的：
     * `data` 里带着服务端为本次握手记下的 `uuid`，后面几跳必须原样跟下去，
     * 自己拼 URL 会换不回来，所以这里只取不解释。
     */
    suspend fun fetchCasEntry(): String? = withContext(Dispatchers.IO) {
        decode<String>(
            get("/auth/address") {
                addQueryParameter("finalAddress", ENTRY_FINAL_ADDRESS)
                addQueryParameter("errPageUrl", ENTRY_ERROR_ADDRESS)
                addQueryParameter("manager", "false")
                addQueryParameter("consoleType", ENTRY_CONSOLE_TYPE)
            },
        ).dataOrNull()?.takeIf { it.startsWith("http") }
    }

    /**
     * 「我的预约」。**不带 `needStatus`**：网页端那个 `null242568192` 是精确状态白名单，
     * 实测会把刚下单的记录（`resvStatus:1027`）整条滤掉，页面显示成「没有预约」。
     */
    suspend fun fetchReservations(from: LocalDate, to: LocalDate): List<CsyyReservationDto> =
        withContext(Dispatchers.IO) {
            decode<List<CsyyReservationDto>>(
                get("/reserve/resvInfo") {
                    addQueryParameter("beginDate", from.format(DATE))
                    addQueryParameter("endDate", to.format(DATE))
                    addQueryParameter("page", "1")
                    addQueryParameter("pageNum", RESERVATION_PAGE_SIZE.toString())
                },
            ).requireData(RESERVATIONS_FALLBACK)
        }

    /** 返回服务端消息，成功时通常是「预约成功」。 */
    suspend fun reserve(
        accNo: String,
        devId: Int,
        start: LocalDateTime,
        end: LocalDateTime,
        memo: String,
    ): String = withContext(Dispatchers.IO) {
        val payload = CsyyReserveRequest(
            sysKind = SYS_KIND_SEAT,
            appAccNo = accNo,
            memberKind = MEMBER_KIND_SELF,
            resvMember = listOf(accNo),
            resvBeginTime = start.format(DATE_TIME),
            resvEndTime = end.format(DATE_TIME),
            testName = "",
            // sysConfig.resvCode 实测为 "0"，青大没开预约验证码，留空即可。
            captcha = "",
            resvProperty = RESV_PROPERTY_SEAT,
            resvDev = listOf(devId),
            memo = memo,
        )
        postAck("/reserve", appJson.encodeToString(CsyyReserveRequest.serializer(), payload))
    }

    suspend fun cancel(uuid: String): String = withContext(Dispatchers.IO) {
        postAck("/reserve/delete", appJson.encodeToString(CsyyUuidRequest.serializer(), CsyyUuidRequest(uuid)))
    }

    suspend fun endEarly(uuid: String): String = withContext(Dispatchers.IO) {
        postAck("/reserve/endAhaed", appJson.encodeToString(CsyyUuidRequest.serializer(), CsyyUuidRequest(uuid)))
    }

    private fun url(path: String, block: HttpUrl.Builder.() -> Unit = {}): String =
        (BASE_URL + path).toHttpUrl().newBuilder().apply(block).build().toString()

    /** GET 只收路径，查询参数在 block 里加：调用方拿到的是同一个拼接口径，不会把裸路径当成完整地址传进来。 */
    private fun get(path: String, block: HttpUrl.Builder.() -> Unit = {}): String =
        execute(Request.Builder().url(url(path, block)).get())

    private fun postAck(path: String, body: String): String {
        val ack = appJson.decodeFromString(
            CsyyAck.serializer(),
            execute(
                Request.Builder()
                    .url(url(path))
                    .post(body.toRequestBody(JSON_MEDIA))
                    .header("Content-Type", "application/json"),
            ),
        )
        if (ack.code == CsyyEnvelope.CODE_LOGGED_OUT) throw CsyyAuthException()
        if (ack.code != CsyyEnvelope.CODE_OK) {
            throw RemoteDataException(ack.message ?: "操作失败")
        }
        return ack.message ?: "操作成功"
    }

    private fun execute(builder: Request.Builder): String {
        val request = builder
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", REFERER)
            .build()

        val response = runCatching { http.client.newCall(request).execute() }
            .getOrElse { throw RemoteDataException("无法连接座位系统：${it.message}") }

        response.use {
            if (!it.isSuccessful) throw it.serverFailure("座位系统")
            val text = it.body?.string().orEmpty()
            if (text.isBlank()) throw RemoteDataException("座位系统返回空内容")
            // 掉线时可能被重定向到登录页，拿到的是 HTML 而不是 JSON
            if (text.trimStart().startsWith("<")) throw CsyyAuthException()
            return text
        }
    }

    private inline fun <reified T> decode(raw: String): CsyyEnvelope<T> =
        appJson.decodeFromString(CsyyEnvelope.serializer(serializer<T>()), raw)

    companion object {
        /** csyy 没有 443，只能明文，白名单见 res/xml/network_security_config.xml。 */
        private const val ORIGIN = "http://csyy.qdu.edu.cn"
        const val BASE_URL = "$ORIGIN/ic-web"
        private const val REFERER = "$ORIGIN/"

        /** 免登入口的三个参数照抄网页端：`addressPull=false`，所以 finalAddress 不带 pathname。 */
        private const val ENTRY_FINAL_ADDRESS = ORIGIN + "/"
        private const val ENTRY_ERROR_ADDRESS = "$ORIGIN/#/error"
        private const val ENTRY_CONSOLE_TYPE = "16"

        /** 座位是 sysKind=8；研修间 1、电子阅览室 2、考研座位 32。 */
        const val SYS_KIND_SEAT = 8
        private const val MEMBER_KIND_SELF = 1
        private const val RESV_PROPERTY_SEAT = 32
        private const val RESERVATION_PAGE_SIZE = 50

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DATE_COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        private const val ROOMS_FALLBACK = "房间列表获取失败"
        private const val SEATS_FALLBACK = "座位获取失败"
        private const val RESERVATIONS_FALLBACK = "预约记录获取失败"
    }
}

/** 写操作只看 code 与 message，不需要 data。 */
@Serializable
private data class CsyyAck(val code: Int = -1, val message: String? = null)

private fun <T> CsyyEnvelope<T>.requireData(fallback: String): T {
    if (isLoggedOut) throw CsyyAuthException()
    if (!isSuccess) throw RemoteDataException(message ?: fallback)
    return data ?: throw RemoteDataException(fallback)
}

private fun <T> CsyyEnvelope<T>.dataOrNull(): T? {
    if (isLoggedOut) throw CsyyAuthException()
    if (!isSuccess) return null
    return data
}
