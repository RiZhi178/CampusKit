package qdu.campus.data.remote.ehall

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import qdu.campus.domain.model.EhallApp
import qdu.campus.domain.model.EhallTaskCounts
import qdu.campus.platform.common.EhallAuthException
import qdu.campus.platform.common.RemoteDataException
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.network.appJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 办事大厅（ehall，苏迪 AMP 2.3.x）接口。
 *
 * 会话靠 Cookie：`/new/index.html?ticket=ST-…` 落地时种的会话 Cookie 与统一认证票同源，
 * 由 [qdu.campus.platform.network.CookieBridge] 注入，网页端没有额外的 token 头。
 * 未登录**不发 401**，HTTP 恒 200、只在 JSON 里留 `hasLogin:false`。
 *
 * 目前接两项：服务清单与侧边栏任务计数。计数在 `/taskcenterapp/` 前缀下，
 * 匿名请求实测 401，而且必须带 JSONP 回调名——不带回调直接请求返回的是一页「系统异常」HTML。
 * 接口变更只影响本文件。
 */
@Singleton
class EhallApi @Inject constructor(
    private val http: HttpClientFactory,
) {

    /** 学生角色能看到的服务清单。 */
    suspend fun fetchServices(): List<EhallApp> = withContext(Dispatchers.IO) {
        val dto = decode(
            get(PATH_APP_LIST) {
                addQueryParameter("serviceRoleId", ROLE_STUDENT)
                addQueryParameter("type", "all")
            },
        )
        if (!dto.hasLogin) throw EhallAuthException()
        dto.toServices()
    }

    /**
     * 侧边栏任务计数。取不到就当作没有计数，不抛异常——它只是列表边上的一行提示，
     * 拿它挡住在办事大厅里找服务没道理。未登录时这个接口回 401（与列表的 `hasLogin:false` 不同）。
     */
    suspend fun fetchTaskCounts(): EhallTaskCounts? = withContext(Dispatchers.IO) {
        val raw = runCatching {
            get(PATH_TASK_COUNT) { addQueryParameter("callback", JSONP_CALLBACK) }
        }.getOrNull()
        raw?.let(::parseEhallTaskCounts)
    }

    /**
     * 磁贴的实际去处。门户自己点服务走的就是这个解析器，它会在跳转时替第三方应用
     * 补一次登录握手；直接用响应里的 `pcOpenUrl` 会跳过握手，多数第三方应用落地就是登录页。
     */
    fun entryUrl(appId: String): String = (BASE_URL + PATH_APP_SHOW)
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("appId", appId)
        .build()
        .toString()

    private fun get(path: String, block: HttpUrl.Builder.() -> Unit = {}): String =
        execute(
            Request.Builder()
                .url((BASE_URL + path).toHttpUrl().newBuilder().apply(block).build().toString())
                .get(),
        )

    private fun execute(builder: Request.Builder): String {
        val request = builder
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Referer", REFERER)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        val response = runCatching { http.client.newCall(request).execute() }
            .getOrElse { throw RemoteDataException("无法连接办事大厅：${it.message}") }

        response.use {
            if (!it.isSuccessful) throw RemoteDataException("办事大厅返回 ${it.code}")
            val text = it.body.string()
            if (text.isBlank()) throw EhallAuthException("办事大厅返回空内容")
            // 被重定向到统一认证时拿到的是登录页 HTML
            if (text.trimStart().startsWith("<")) throw EhallAuthException()
            return text
        }
    }

    private fun decode(raw: String): EhallAppListDto =
        try {
            appJson.decodeFromString(EhallAppListDto.serializer(), raw)
        } catch (e: SerializationException) {
            // 改版多出来的字段由 ignoreUnknownKeys 兜住；真到解不动的程度要说清是哪一层坏的
            throw RemoteDataException("办事大厅返回了无法识别的内容")
        }

    companion object {
        const val BASE_URL = "https://ehall.qdu.edu.cn"
        private const val REFERER = "$BASE_URL/new/index.html"

        /** `school.json` 的 `role_data` 实测：`1__0` 学生办事、`1__1` 教师办事、`1__2` 游客浏览。 */
        private const val ROLE_STUDENT = "1__0"

        private const val PATH_APP_LIST = "/jsonp/serviceRoleApp.json"
        private const val PATH_APP_SHOW = "/appShow"

        /** amp.js 里 `services.getTaskCount` 用的同一条地址，回调名也取自门户自己。 */
        private const val PATH_TASK_COUNT = "/taskcenterapp/sys/taskCenter/portalDataReceive/getTaskCount.do"
        private const val JSONP_CALLBACK = "amp_jsonp_callback"
    }
}
