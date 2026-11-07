package qdu.campus.data.remote.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GitHub Releases 解析与版本比较。锁骨架与排序规则，不断言发布文案细节。
 * fixture 照 API 文档的响应形状构造（公开端点，探测时出口 IP 被限流，
 * 真实样本待有 release 后用未限流网络补采）。
 */
class GithubReleaseTest {

    @Test
    fun `解析 releases 响应的骨架字段`() {
        val text = javaClass.getResourceAsStream("/github/release.latest.2026-11-06.json")!!
            .bufferedReader()
            .readText()
        val release = parseGithubRelease(text)!!
        assertEquals("v0.9.14", release.tagName)
        assertEquals("https://github.com/RiZhi178/CampusKit/releases/tag/v0.9.14", release.htmlUrl)
        assertTrue(release.isPrerelease)
        assertEquals("0.9.14-alpha", release.name)
    }

    @Test
    fun `形状对不上返回 null 不抛异常`() {
        assertNull(parseGithubRelease("<html>not json</html>"))
        assertNull(parseGithubRelease(""))
        assertNull(parseGithubRelease("""["not","an","object"]"""))
    }

    @Test
    fun `字段全缺解出空对象 由取数层用 tagName 兜底`() {
        // coerceInputValues + 全默认值：只含未知键的 JSON 解出一个空 DTO 而不是 null，
        // 「这不是有效 release」的判定交给调用方看 tagName 是否为空。
        val release = parseGithubRelease("""{"url": 12345}""")!!
        assertEquals("", release.tagName)
    }

    @Test
    fun `补发的字段不破坏解析`() {
        val release = parseGithubRelease("""{"tag_name":"v1.0.0","some_new_field":true}""")!!
        assertEquals("v1.0.0", release.tagName)
        assertEquals("", release.htmlUrl)
    }

    @Test
    fun `v 前缀与当前版本比较`() {
        assertTrue(isNewerVersion("0.9.14-alpha", "v0.9.14"))
        assertTrue(isNewerVersion("0.9.14-alpha", "v0.9.15"))
        assertTrue(isNewerVersion("0.9.14-alpha", "v1.0.0"))
    }

    @Test
    fun `同号正式版比 alpha 新`() {
        assertTrue(isNewerVersion("0.9.14-alpha", "0.9.14"))
        assertFalse(isNewerVersion("0.9.14", "0.9.14-alpha"))
    }

    @Test
    fun `同号或更旧不算有更新`() {
        assertFalse(isNewerVersion("0.9.14-alpha", "v0.9.14-alpha"))
        assertFalse(isNewerVersion("0.9.14-alpha", "v0.9.13"))
        assertFalse(isNewerVersion("0.9.14-alpha", "v0.9.14-alpha"))
    }

    @Test
    fun `阶段排序 alpha 小于 beta 小于 rc 小于正式`() {
        assertTrue(isNewerVersion("0.9.14-alpha", "0.9.14-beta"))
        assertTrue(isNewerVersion("0.9.14-beta", "0.9.14-rc"))
        assertTrue(isNewerVersion("0.9.14-rc", "0.9.14"))
    }

    @Test
    fun `乱写的 tag 按最旧处理不误报`() {
        // 远端给了对不上形状的 tag：宁可当旧版不提示，也不崩
        assertFalse(isNewerVersion("0.9.14-alpha", "not-a-version"))
        assertEquals(0, compareVersion("", ""))
    }
}
