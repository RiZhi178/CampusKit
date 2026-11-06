package qdu.campus.data.local.assets

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import qdu.campus.feature.electricity.BuildingCatalog
import qdu.campus.feature.electricity.CatalogCampus
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 随包发布的电费楼栋字典（报修系统 2026-11-05 全量采集，见
 * `docs/interfaces/baoxiu.md` 的「校区与楼栋字典」节）。
 *
 * 与 KcapAssetReader 同一条路：只读随包内容，不进 Room，进程内缓存一份。
 */
@Singleton
class DormCatalogReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = AtomicReference<List<CatalogCampus>?>(null)

    suspend fun campuses(): List<CatalogCampus> =
        cache.get() ?: withContext(Dispatchers.IO) {
            val text = runCatching {
                context.assets.open("zzmp/buildings.json").bufferedReader().use { it.readText() }
            }.getOrDefault("")
            BuildingCatalog.parse(text).also { cache.set(it) }
        }
}
