package qdu.campus.platform.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * 形态诊断的底线是「绝不输出值」。这里除了断言结构描述正确，
 * 还专门断言输出里不含令牌原文与任何 claim 的值。
 */
class CredentialShapeTest {

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val head = encoder.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$head.$body.signature-part"
    }

    @Test
    fun `没有令牌时报无令牌`() {
        assertEquals("无令牌", CredentialShape.jwt(null))
        assertEquals("无令牌", CredentialShape.jwt(""))
        assertEquals("无令牌", CredentialShape.jwt("   "))
    }

    @Test
    fun `不透明串报段数一且载荷读不出`() {
        val shape = CredentialShape.jwt("opaque-token-not-jwt")
        assertTrue(shape.contains("段数=1"))
        assertTrue(shape.contains("载荷字段=读不出"))
        assertTrue(shape.contains("含exp=false"))
        assertFalse(shape.contains("opaque-token-not-jwt"))
    }

    @Test
    fun `三段 JWT 报段数长度与载荷字段名`() {
        val token = jwt("""{"sub":"subject-value","scope":"read write","exp":1893456000}""")
        val shape = CredentialShape.jwt(token, nowMillis = 1_893_456_000_000L - 600_000L)
        assertTrue(shape.contains("段数=3"))
        assertTrue(shape.contains("长度=${token.length}"))
        assertTrue(shape.contains("含exp=true"))
        assertTrue(shape.contains("sub"))
        assertTrue(shape.contains("scope"))
        assertTrue(shape.contains("exp"))
        assertTrue(shape.contains("剩余10分钟"))
    }

    @Test
    fun `输出不含令牌原文也不含 claim 的值`() {
        val token = jwt("""{"sub":"secret-subject","name":"secret-name","exp":1893456000}""")
        val shape = CredentialShape.jwt(token)
        assertFalse(shape.contains(token))
        assertFalse(shape.contains("secret-subject"))
        assertFalse(shape.contains("secret-name"))
        assertFalse(shape.contains("signature-part"))
        assertFalse(shape.contains("1893456000"))
    }

    @Test
    fun `exp 已过报已过期而不是剩余`() {
        val token = jwt("""{"exp":1893456000}""")
        val shape = CredentialShape.jwt(token, nowMillis = 1_893_456_000_000L + 120_000L)
        assertTrue(shape.contains("已过期2分钟"))
        assertFalse(shape.contains("剩余"))
    }

    @Test
    fun `缺 exp 的 JWT 不报剩余时间`() {
        val shape = CredentialShape.jwt(jwt("""{"sub":"u-1"}"""))
        assertTrue(shape.contains("含exp=false"))
        assertFalse(shape.contains("剩余"))
        assertFalse(shape.contains("已过期"))
    }

    @Test
    fun `载荷字段最多报十二个`() {
        val payload = (1..20).joinToString(",", "{", "}") { "\"k$it\":$it" }
        val shape = CredentialShape.jwt(jwt(payload))
        assertTrue(shape.contains("k12"))
        assertFalse(shape.contains("k13"))
    }

    @Test
    fun `Cookie 只报名字与条目数不报值`() {
        val header = "CASTGC=tgt-secret-value; JSESSIONID=session-secret; flagged"
        val shape = CredentialShape.cookies(header)
        assertTrue(shape.contains("条目数=2"))
        assertTrue(shape.contains("CASTGC"))
        assertTrue(shape.contains("JSESSIONID"))
        assertTrue(shape.contains("总长度=${header.length}"))
        assertFalse(shape.contains("tgt-secret-value"))
        assertFalse(shape.contains("session-secret"))
        // 没有等号的片段不算条目
        assertFalse(shape.contains("flagged"))
    }

    @Test
    fun `没有 Cookie 时报无 Cookie`() {
        assertEquals("无 Cookie", CredentialShape.cookies(null))
        assertEquals("无 Cookie", CredentialShape.cookies(""))
    }

    @Test
    fun `不透明令牌只报长度`() {
        val shape = CredentialShape.opaque("abcdef123456")
        assertEquals("长度=12（不透明，非 JWT）", shape)
        assertFalse(shape.contains("abcdef"))
        assertEquals("无令牌", CredentialShape.opaque(null))
    }
}