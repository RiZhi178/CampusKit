package qdu.campus.data.remote.yjsxt

import android.util.Log
import qdu.campus.BuildConfig
import qdu.campus.platform.common.RemoteDataException
import qdu.campus.platform.common.YjsxtAuthException
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.network.appJson
import qdu.campus.platform.network.serverFailure
import qdu.campus.platform.security.CredentialShape
import qdu.campus.platform.security.CredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 研究生系统（yjsxt）课表接口。
 *
 * 调用链：先 searchPkgl002List 取学期与大节配置，再 searchOneXskbList 取全量课表，
 * 最后 findNoKb 取未排课课程。三个接口各自解析，接口变更只影响本文件。
 */
@Singleton
class YjsxtApi @Inject constructor(
    private val http: HttpClientFactory,
    private val credentials: CredentialStore,
) {

    suspend fun fetchSemester(semesterId: String): SemesterDto? = withContext(Dispatchers.IO) {
        val body = appJson.encodeToString(
            RequestPayload.serializer(),
            RequestPayload(semesterId = semesterId),
        )
        val raw = post(
            path = "/resService/jwxtpt/v1/xsd/xsdjsjygl_info/searchPkgl002List" +
                "?resourceCode=XSMH0702" +
                "&apiCode=jw.xsd.xsdInfo.controller.XsdJsjyglController.searchPkgl002List",
            body = body,
        )
        val envelope = appJson.decodeFromString(
            ApiEnvelope.serializer(ListSerializer(SemesterDto.serializer())),
            raw,
        )
        if (!envelope.isSuccess) {
            if (envelope.isTokenExpired) throw sessionExpired()
            throw RemoteDataException(envelope.errorMessage ?: "学期与大节配置获取失败")
        }
        envelope.data?.firstOrNull()
    }

    suspend fun fetchCourses(semesterId: String, bigSectionId: String): List<CourseDto> =
        withContext(Dispatchers.IO) {
            val body = appJson.encodeToString(
                RequestPayload.serializer(),
                RequestPayload(semesterId = semesterId, bigSectionId = bigSectionId),
            )
            val raw = post(
                path = "/resService/jwxtpt/v1/xsd/xsdqxxkb_info/searchOneXskbList" +
                    "?resourceCode=XSMH0701" +
                    "&apiCode=jw.xsd.xsdInfo.controller.XsdQxxkbController.searchOneXskbList",
                body = body,
            )
            val envelope = appJson.decodeFromString(
                ApiEnvelope.serializer(ListSerializer(CourseDto.serializer())),
                raw,
            )
            if (!envelope.isSuccess) {
                if (envelope.isTokenExpired) throw sessionExpired()
                throw RemoteDataException(envelope.errorMessage ?: "课表获取失败")
            }
            envelope.data.orEmpty()
        }

    suspend fun fetchNoSchedule(semesterId: String): List<NoScheduleDto> =
        withContext(Dispatchers.IO) {
            val body = appJson.encodeToString(
                RequestPayload.serializer(),
                RequestPayload(semesterId = semesterId),
            )
            val raw = post(
                path = "/resService/jwxtpt/v1/xsd/xsdqxxkb_info/findNoKb" +
                    "?resourceCode=XSMH070101" +
                    "&apiCode=jw.xsd.xsdInfo.controller.XsdQxxkbController.findNoKb",
                body = body,
            )
            val envelope = appJson.decodeFromString(
                ApiEnvelope.serializer(ListSerializer(NoScheduleDto.serializer())),
                raw,
            )
            if (!envelope.isSuccess) {
                if (envelope.isTokenExpired) throw sessionExpired()
                throw RemoteDataException(envelope.errorMessage ?: "未排课课程获取失败")
            }
            envelope.data.orEmpty()
        }

    private fun post(path: String, body: String): String {
        val token = credentials.yjsxtToken()
        val builder = Request.Builder()
            .url(BASE_URL + path)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Content-Type", "application/json")
            .header("app", "PCWEB")
            .header("locale", "zh_CN")
            .header("userRoleCode", "bussiness")
            .header("Origin", BASE_URL)
            .header("Referer", "$BASE_URL/Njw2017/main/")
            .header("Accept", "application/json, text/plain, */*")
        if (!token.isNullOrBlank()) {
            // 两个同值头都要带，缺一个后端会当作未授权
            builder.header("Authorization", "Bearer $token")
            builder.header("TOKEN", token)
        }

        if (BuildConfig.DEBUG) {
            val cookie = runCatching { http.cookieBridge.cookieHeader(BASE_URL) }.getOrNull()
            // 令牌前缀也是凭据的一部分，日志只报形态，够分清没存到、不是 JWT、还是已过期
            Log.d(
                TAG,
                "YjsxtApi.post token=${CredentialShape.jwt(token)} " +
                    "cookieLen=${cookie?.length ?: 0} path=$path",
            )
        }

        val response = runCatching { http.client.newCall(builder.build()).execute() }
            .getOrElse { throw RemoteDataException("无法连接研究生系统：${it.message}") }

        response.use {
            if (it.code == 401 || it.code == 403) {
                throw sessionExpired()
            }
            if (it.isRedirect) {
                throw sessionExpired()
            }
            if (!it.isSuccessful) {
                throw it.serverFailure("研究生系统")
            }
            val text = it.body?.string().orEmpty()
            if (text.isBlank()) {
                throw RemoteDataException("研究生系统返回空内容")
            }
            // 未登录时后端可能返回登录页 HTML 而不是 JSON
            if (text.trimStart().startsWith("<")) {
                throw sessionExpired()
            }
            return text
        }
    }

    /**
     * 研究生系统自己的会话缺失。只影响课表同步，
     * 不去动统一认证的状态：那份票还管着图书馆、学工、座位与办事大厅。
     */
    private fun sessionExpired(): YjsxtAuthException = YjsxtAuthException()

    companion object {
        const val BASE_URL = "https://yjsxt.qdu.edu.cn"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val TAG = "YjsxtApi"
    }
}

@kotlinx.serialization.Serializable
internal data class RequestPayload(
    @kotlinx.serialization.SerialName("jczy013id") val semesterId: String,
    @kotlinx.serialization.SerialName("pkgl002id") val bigSectionId: String? = null,
)
