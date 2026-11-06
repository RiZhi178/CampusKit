package qdu.campus.data.local.assets

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qdu.campus.data.remote.jwc.KcapParser
import qdu.campus.data.remote.jwc.KcapSemester
import qdu.campus.data.remote.jwc.KcapTimetables
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 随包发布的本科生课程总表（按班级建独立课表）。
 *
 * 数据在 APK 的 assets 里，不进 Room：这是只读的随包内容，不存在「离线要留着看」的问题，
 * 多一份库就多一套表、多一次迁移。全量解析一遍约百来毫秒，缓存在内存里，
 * 进程活着就不重复读。
 */
@Singleton
class KcapAssetReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val indexCache = AtomicReference<List<KcapSemester>?>(null)
    private val timetableCache = ConcurrentHashMap<String, KcapTimetables>()

    suspend fun semesters(): List<KcapSemester> =
        indexCache.get() ?: withContext(Dispatchers.IO) {
            KcapParser.parseIndex(read("kcap/index.json")).also { indexCache.set(it) }
        }

    suspend fun semester(id: String): KcapSemester? = semesters().firstOrNull { it.id == id }

    /** 整学期按班级组织的课表：勾选框的班级列表与每个班的课程都从这里来。 */
    suspend fun classTimetables(id: String): KcapTimetables? =
        timetableCache[id] ?: withContext(Dispatchers.IO) {
            val file = semester(id)?.file.orEmpty()
            if (file.isBlank()) null
            else KcapParser.parseTimetables(read("kcap/$file"))?.also { timetableCache[id] = it }
        }

    private fun read(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }
}
