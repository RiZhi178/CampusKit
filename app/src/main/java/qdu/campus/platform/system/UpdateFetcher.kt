package qdu.campus.platform.system

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Request
import qdu.campus.data.remote.github.GithubReleaseDto
import qdu.campus.data.remote.github.parseGithubRelease
import qdu.campus.platform.common.CrashLogger
import qdu.campus.platform.network.HttpClientFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 检查更新的取数端。拉 GitHub 公开仓库的 releases/latest（无鉴权，无需 Cookie），
 * 用全局客户端即可；GitHub API 对匿名请求限 60 次/小时，本入口只由用户手动触发，
 * 不会摸到限流。失败一律返回 null 由调用方统一提示，不向 UI 抛异常。
 */
@Singleton
class UpdateFetcher @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val http: HttpClientFactory,
) {
    data class LatestRelease(
        val version: String,
        val notes: String,
        val url: String,
    )

    /**
     * 仓库还没有发布过 release 时 GitHub 的 `releases/latest` 返回 404，
     * 这是正常状态（视同已是最新），与网络失败分开表达。
     */
    sealed interface Outcome {
        data object NoRelease : Outcome
        data class Success(val release: LatestRelease) : Outcome
        data object Failed : Outcome
    }

    fun fetchLatest(): Outcome {
        val request = Request.Builder()
            .url("https://api.github.com/repos/RiZhi178/QDUCampus/releases/latest")
            .build()
        return runCatching {
            http.client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    // 404 = 仓库还没有 release；其余非 2xx 按失败处理
                    response.code == 404 -> Outcome.NoRelease
                    !response.isSuccessful -> {
                        CrashLogger.log(appContext, "检查更新 HTTP ${response.code}", IOException(text.take(200)))
                        Outcome.Failed
                    }
                    else -> {
                        val release = parseGithubRelease(text)
                        if (release == null || release.tagName.isBlank()) {
                            CrashLogger.log(appContext, "检查更新响应解析失败", IOException(text.take(200)))
                            Outcome.Failed
                        } else {
                            Outcome.Success(
                                LatestRelease(
                                    version = release.tagName,
                                    notes = release.body.orEmpty(),
                                    url = release.htmlUrl.ifBlank { REPO_RELEASES_URL },
                                ),
                            )
                        }
                    }
                }
            }
        }.onFailure {
            CrashLogger.log(appContext, "检查更新请求异常", it)
        }.getOrDefault(Outcome.Failed)
    }

    fun currentVersion(): String = runCatching {
        val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("未知")

    companion object {
        const val REPO_RELEASES_URL = "https://github.com/RiZhi178/QDUCampus/releases"
    }
}
