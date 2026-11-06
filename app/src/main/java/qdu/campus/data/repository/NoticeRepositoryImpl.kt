package qdu.campus.data.repository

import qdu.campus.data.local.db.NoticeDao
import qdu.campus.data.local.db.NoticeEntity
import qdu.campus.data.local.db.SyncDao
import qdu.campus.data.local.db.SyncMetadataEntity
import qdu.campus.data.remote.graduate.NoticeParser
import qdu.campus.data.remote.jwc.JwcNoticeParser
import qdu.campus.domain.model.Notice
import qdu.campus.domain.model.SyncState
import qdu.campus.domain.repository.NoticeRepository
import qdu.campus.domain.repository.RefreshOutcome
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.system.NoticeNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** 一个通知数据源：标识、展示名、取数与解析。返回条目与本源内部的失败说明。 */
private class NoticeSource(
    val id: String,
    val label: String,
    val fetch: suspend (HttpClientFactory) -> Pair<List<Notice>, List<String>>,
)

@Singleton
class NoticeRepositoryImpl @Inject constructor(
    private val http: HttpClientFactory,
    private val dao: NoticeDao,
    private val syncDao: SyncDao,
    private val gradParser: NoticeParser,
    private val jwcParser: JwcNoticeParser,
    private val notifier: NoticeNotifier,
    private val settings: SettingsRepository,
) : NoticeRepository {

    /** 研究生院：培养 / 学籍 / 学位三栏合并，每栏取最新一页（2026-11-06 定）。 */
    private val gradSource = NoticeSource(SOURCE_GRAD, "研究生院") { client ->
        val notices = mutableListOf<Notice>()
        val failures = mutableListOf<String>()
        for (column in NoticeParser.COLUMNS) {
            val url = "$BASE_GRAD_URL${column.path}"
            runCatching { gradParser.parse(client.fetchHtml(url, "研究生院"), url) }
                .onSuccess { notices += it }
                .onFailure { failures += "研究生院·${column.label}：${it.message ?: "请求失败"}" }
        }
        notices to failures
    }

    /** 教务处：教务通知第一页（15 条）。分页是倒序编号，契约见 docs/interfaces/jwc-notice.md。 */
    private val jwcSource = NoticeSource(SOURCE_JWC, "教务处") { client ->
        val url = "$BASE_JWC_URL${JwcNoticeParser.FIRST_PAGE}"
        jwcParser.parse(client.fetchHtml(url, "教务处"), url) to emptyList()
    }

    private val sources = listOf(gradSource, jwcSource)

    override fun observeNotices(): Flow<List<Notice>> =
        dao.observeAll().map { list ->
            list.map {
                Notice(
                    fingerprint = it.fingerprint,
                    title = it.title,
                    date = it.date,
                    column = it.column,
                    url = it.url,
                    read = it.read,
                    source = it.source,
                )
            }
        }

    override fun observeSyncStates(): Flow<List<SyncState>> =
        syncDao.observeAll().map { list ->
            list.map { SyncState(it.source, it.lastSuccessAt, it.message) }
        }

    override suspend fun refresh(): RefreshOutcome = withContext(Dispatchers.IO) {
        try {
            // 每个来源独立抓取、解析、入库：一个来源失败只记它的名字与原因，
            // 不清掉任何缓存（AGENTS 1.3 的离线口径）。全部失败才按失败上报。
            val failures = mutableListOf<String>()
            val parsed = mutableListOf<Notice>()
            for (source in sources) {
                runCatching { source.fetch(http) }
                    .onSuccess { (notices, sourceFailures) ->
                        parsed += notices
                        failures += sourceFailures
                    }
                    .onFailure { failures += "${source.label}：${it.message ?: "请求失败"}" }
            }
            val merged = parsed.distinctBy { it.fingerprint }

            if (merged.isEmpty()) {
                val message = if (failures.isNotEmpty()) {
                    "通知更新失败：${failures.joinToString("；")}"
                } else {
                    "未解析到通知，页面结构可能已调整"
                }
                syncDao.upsert(SyncMetadataEntity(SOURCE_GRAD, null, message))
                syncDao.upsert(SyncMetadataEntity(SOURCE_JWC, null, message))
                return@withContext RefreshOutcome.Failure(message)
            }

            val existing = dao.existingFingerprints().toSet()
            val knownSources = dao.existingSources().toSet()
            val read = dao.readFingerprints().toSet()
            val fresh = merged.filterNot { it.fingerprint in existing }

            dao.upsertAll(
                merged.map {
                    NoticeEntity(
                        fingerprint = it.fingerprint,
                        title = it.title,
                        date = it.date,
                        column = it.column,
                        url = it.url,
                        read = it.fingerprint in read,
                        source = it.source,
                    )
                },
            )

            // 首次同步没有「新」的参照，整页条目都会算 fresh；全部推送等于一次轰炸。
            // 基线按来源各自判断：接教务处那一栏时，研究生院的旧数据不该替它背书。
            if (existing.isNotEmpty()) {
                val keywords = settings.notificationKeywords.first()
                fresh.forEach { notice ->
                    if (notice.source !in knownSources) return@forEach
                    notifier.matchKeyword(notice.title, keywords)?.let { keyword ->
                        notifier.notify(notice, keyword)
                    }
                }
            }

            val now = System.currentTimeMillis()
            val gradNote = failures.filter { it.startsWith("研究生院") }
                .joinToString("；") { it.substringAfter("：") }
                .ifEmpty { null }
            val jwcNote = failures.filter { it.startsWith("教务处") }
                .joinToString("；") { it.substringAfter("：") }
                .ifEmpty { null }
            syncDao.upsert(SyncMetadataEntity(SOURCE_GRAD, now, gradNote))
            syncDao.upsert(SyncMetadataEntity(SOURCE_JWC, now, jwcNote))

            when {
                failures.isEmpty() -> RefreshOutcome.Success(merged.size)
                else -> RefreshOutcome.Partial(merged.size, failures)
            }
        } catch (e: Exception) {
            // 来源之外的意外（入库、设置读取等）仍要有兜底，不让协程把异常抛到 UI 之外
            syncDao.upsert(SyncMetadataEntity(SOURCE_GRAD, null, e.message ?: "通知更新失败"))
            syncDao.upsert(SyncMetadataEntity(SOURCE_JWC, null, e.message ?: "通知更新失败"))
            RefreshOutcome.Failure(e.message ?: "通知更新失败")
        }
    }

    override suspend fun markRead(fingerprint: String) = dao.markRead(fingerprint)

    override suspend fun clear() {
        dao.clear()
        syncDao.upsert(SyncMetadataEntity(SOURCE_GRAD, null, null))
        syncDao.upsert(SyncMetadataEntity(SOURCE_JWC, null, null))
    }

    private suspend fun HttpClientFactory.fetchHtml(url: String, site: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("$site 页面返回 ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    companion object {
        const val BASE_GRAD_URL = "https://grad.qdu.edu.cn/"
        const val BASE_JWC_URL = "https://jwc.qdu.edu.cn/"
        const val SOURCE_GRAD = "grad_notice"
        const val SOURCE_JWC = "jwc_notice"
    }
}
