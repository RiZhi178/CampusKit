package qdu.campus.data.remote.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GitHub Releases 的数据源（检查更新用）。
 *
 * 端点是公开仓库的 `releases/latest`，无需鉴权；未限流时返回最新一个正式 release，
 * 没有 release 时返回 404。校方无关、形状由 GitHub 文档锁定，随时可能加字段，
 * 解析一律忽略未知键；GitHub 侧改版时只动本文件。
 */
@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("prerelease") val isPrerelease: Boolean = false,
)

/** 与 platform/network 同一配置口径：忽略未知键、容忍脏数据。 */
private val releaseJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** 解析 releases/latest 的响应体；形状对不上返回 null，由调用方按失败处理。 */
fun parseGithubRelease(text: String): GithubReleaseDto? = runCatching {
    releaseJson.decodeFromString<GithubReleaseDto>(text)
}.getOrNull()

/**
 * 版本号比较：`[v]主.次.修订[-阶段]`，阶段按 alpha < beta < rc < 正式 排序。
 * 返回负数表示 remote 更旧，0 表示相同，正数表示 remote 更新。
 * 首段数字解析失败的（乱写的 tag）按最旧处理：宁可漏提示，不把垃圾内容当新版弹出。
 */
fun compareVersion(current: String, remote: String): Int {
    val a = parseVersion(current)
    val b = parseVersion(remote)
    for (i in 0..3) {
        if (a[i] != b[i]) return b[i] - a[i]
    }
    return 0
}

/** remote 比 current 新（有可升级的版本）时为 true。 */
fun isNewerVersion(current: String, remote: String): Boolean = compareVersion(current, remote) > 0

private fun parseVersion(text: String): IntArray {
    val cleaned = text.trim().removePrefix("v").removePrefix("V")
    val core = cleaned.substringBefore('-')
    val stageRank = when {
        cleaned.endsWith("-alpha") -> 0
        cleaned.endsWith("-beta") -> 1
        cleaned.endsWith("-rc") -> 2
        else -> 3
    }
    val parts = core.split('.').map { it.trim() }
    // 主版本号都解析不出来就是无效 tag，压到最低档
    val major = parts.getOrNull(0)?.toIntOrNull()
        ?: return intArrayOf(Int.MIN_VALUE, 0, 0, 0)
    return intArrayOf(
        major,
        parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0,
        parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0,
        stageRank,
    )
}
