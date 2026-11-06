package qdu.campus.data.remote.lib

import qdu.campus.domain.model.LibraryBorrowing
import qdu.campus.domain.model.LibraryHolding
import qdu.campus.domain.model.LibraryReader
import qdu.campus.domain.model.LibrarySearchPage
import qdu.campus.domain.model.WebDomain
import qdu.campus.platform.common.LibraryAuthException
import qdu.campus.platform.common.RemoteDataException
import qdu.campus.platform.network.CookieBridge
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.network.appJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图书馆统一检索（`findqdu.libsp.cn`，超星 libsp）接口。
 *
 * 会话形态与校内其他系统都不同：除了 Cookie，还要一个**名字不固定**的请求头——
 * 服务端把「头名」写进 `jwtHeader` Cookie、把「头值」写进 `jwt` Cookie，
 * 网页端的拦截器做的就是 `headers[cookie("jwtHeader")] = cookie("jwt")`。
 * 头名每次登录都可能换，所以不能硬编码，只能成对读取。
 *
 * 检索与馆藏：没有这对头时服务端不报错，而是回 `200 + success:true + numFound:0`
 * 的静默空结果（2026-11-04 补测）——所以「检索匿名可读」是错的，页面必须先在入口
 * 确认会话，缺了就静默补一次再查。借阅记录缺头时直接判未登录。
 */
@Singleton
class LibraryApi @Inject constructor(
    private val http: HttpClientFactory,
    private val cookieBridge: CookieBridge,
) {

    /** 会话头是否成对存在。页面据此决定是直接查，还是先静默补一次会话。 */
    fun hasSession(): Boolean = jwtHeaders() != null

    suspend fun search(keyword: String, page: Int = 1, rows: Int = PAGE_SIZE): LibrarySearchPage =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(
                LibrarySearchRequest(keyword.trim(), page = page, rows = rows),
            )
            unwrap<LibrarySearchPageDto>(PATH_SEARCH, post(PATH_SEARCH, body), "检索失败")
                .toPage(page, rows)
        }

    /** 某条书目的馆藏册。`recordId` 服务端要字符串。 */
    suspend fun holdings(recordId: Long): List<LibraryHolding> = withContext(Dispatchers.IO) {
        val body = json.encodeToString(LibraryItemsRequest(recordId.toString()))
        unwrap<LibraryItemsPageDto>(PATH_ITEMS, post(PATH_ITEMS, body), "馆藏信息获取失败").toHoldings()
    }

    /** 当前借阅。响应里的 `loanNum` 是可借上限，已借数看 `numFound`。 */
    suspend fun loans(page: Int = 1): LibraryBorrowing = withContext(Dispatchers.IO) {
        loanPage(PATH_LOANS, page)
    }

    suspend fun loanHistory(page: Int = 1): LibraryBorrowing = withContext(Dispatchers.IO) {
        loanPage(PATH_LOAN_HISTORY, page)
    }

    /**
     * 借阅人概况。没有会话时返回 null 而不是抛异常：
     * 这一行只是「确认这是我自己的记录」，登录状态由 [loans] 的成败判定就够了。
     */
    suspend fun reader(): LibraryReader? = withContext(Dispatchers.IO) {
        val raw = runCatching { get(PATH_USER, requireJwt = true) }.getOrNull() ?: return@withContext null
        val envelope = runCatching { json.decodeFromString<LibraryEnvelope<LibraryUserDto>>(raw) }.getOrNull()
        envelope?.takeIf { it.success }?.data?.toReader()
    }

    private fun loanPage(path: String, page: Int): LibraryBorrowing {
        val body = json.encodeToString(LibraryLoanRequest(page = page, rows = LOAN_ROWS))
        return unwrap<LibraryLoanPageDto>(path, post(path, body, requireJwt = true), "借阅记录获取失败")
            .toBorrowing()
    }

    private inline fun <reified T> unwrap(path: String, raw: String, fallback: String): T {
        val envelope = runCatching { json.decodeFromString<LibraryEnvelope<T>>(raw) }
            .getOrElse { throw RemoteDataException("图书馆检索返回了读不懂的内容（$path）") }
        if (!envelope.success) throw businessFailure(envelope.message, fallback)
        return envelope.data ?: throw RemoteDataException(fallback)
    }

    /**
     * 服务端把「登录信息过期」写在 message 里而不是状态码上：实测 HTTP 仍返回 200、
     * `success=false`，所以判掉线只能读这句文案。
     */
    private fun businessFailure(message: String?, fallback: String): Throwable {
        val text = message?.trim().orEmpty()
        return if (text.contains("登录")) LibraryAuthException()
        else RemoteDataException(text.ifEmpty { fallback })
    }

    private fun post(path: String, body: String, requireJwt: Boolean = false): String {
        val builder = Request.Builder()
            .url(ORIGIN + path)
            .post(body.toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .header("Referer", ORIGIN + "/")
        return execute(withJwt(builder, requireJwt))
    }

    private fun get(path: String, requireJwt: Boolean = false): String {
        val builder = Request.Builder()
            .url(ORIGIN + path)
            .get()
            .header("Accept", "application/json")
            .header("Referer", ORIGIN + "/")
        return execute(withJwt(builder, requireJwt))
    }

    private fun withJwt(builder: Request.Builder, requireJwt: Boolean): Request {
        val jwt = jwtHeaders()
        if (requireJwt && jwt == null) throw LibraryAuthException()
        jwt?.let { builder.header(it.first, it.second) }
        return builder.build()
    }

    /**
     * 成对读出 `jwtHeader`（头名）与 `jwt`（头值），缺任何一个都算没有会话。
     *
     * 网页端写 Cookie 时 `encodeURIComponent`、取值时 `decodeURIComponent`，
     * 请求头里要放解码后的原文。用 `URLDecoder` 而不是手写替换时注意：它会把 `+`
     * 解成空格，而 JWT 的 base64 里就有 `+`，所以先把 `+` 保护成 `%2B` 再解。
     */
    private fun jwtHeaders(): Pair<String, String>? {
        val cookie = cookieBridge.cookieHeader(WebDomain.LIB_SEARCH.cookieUrl) ?: return null
        val values = cookie.split(';')
            .map { it.trim() }
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=').decodeCookieValue() }
        val name = values["jwtHeader"]?.takeIf { it.isNotBlank() } ?: return null
        val value = values["jwt"]?.takeIf { it.isNotBlank() } ?: return null
        return name to value
    }

    private fun execute(request: Request): String {
        val response = runCatching { http.client.newCall(request).execute() }
            .getOrElse { throw RemoteDataException("无法连接图书馆检索：${it.message}") }

        response.use {
            if (it.code == 401) throw LibraryAuthException()
            if (!it.isSuccessful) throw RemoteDataException("图书馆检索返回 ${it.code}")
            val text = it.body?.string().orEmpty()
            // 会话没了：/oga/userinfo 回空体，业务接口可能被重定向成登录页 HTML
            if (text.isBlank() || text.trimStart().startsWith("<")) throw LibraryAuthException()
            return text
        }
    }

    private companion object {
        private val ORIGIN: String = "https://${WebDomain.LIB_SEARCH.host}"
        private const val PATH_SEARCH = "/find/unify/search"
        private const val PATH_ITEMS = "/find/physical/groupitems"
        private const val PATH_LOANS = "/find/loanInfo/loanList"
        private const val PATH_LOAN_HISTORY = "/find/loanInfo/loanHistoryList"
        private const val PATH_USER = "/oga/userinfo"

        /** 与网页端一致：检索按 10 条一页。 */
        private const val PAGE_SIZE = 10

        /**
         * 借阅记录一页取 20 条。历史更长的用户由页面提示「只显示最近 20 条」，
         * 个人用途不需要翻页。
         */
        private const val LOAN_ROWS = 20

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * 请求体要保留显式 null：实测网页端发的是 `"entrance":null`、`"sortClause":null`，
         * 服务端对「有键但为 null」和「没有这个键」的处理不同，所以这里覆盖全局的
         * `explicitNulls=false`。
         */
        private val json: Json = Json(from = appJson) { explicitNulls = true }
    }
}

private fun String.decodeCookieValue(): String =
    runCatching { URLDecoder.decode(replace("+", "%2B"), "UTF-8") }.getOrDefault(this)
