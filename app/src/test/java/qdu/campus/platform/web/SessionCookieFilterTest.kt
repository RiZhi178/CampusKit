package qdu.campus.platform.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 口令类 Cookie 挡在入库与回灌之外。样本名取自 yjsxt 实测下发的条目，
 * 其余是同名变体：只按精确名过滤会漏掉它们。
 */
class SessionCookieFilterTest {

    private fun names(raw: String?): Set<String> =
        filterSessionCookies(raw)
            ?.split(';')
            ?.map { it.trim().substringBefore('=').trim() }
            ?.toSet()
            .orEmpty()

    @Test
    fun `yjsxt 实测串只留下票与令牌`() {
        val stored = filterSessionCookies(
            "username=2000000000; password=Zm9vYmFyLXNlY3JldA==; rememberMe=true; " +
                "QZKJ-YJSBB-TOEKN=eyJhbGciOiJIUzI1NiJ9.x.y; JSESSIONID=1AB2C3",
        )
        assertEquals(setOf("QZKJ-YJSBB-TOEKN", "JSESSIONID"), names(stored))
    }

    @Test
    fun `同名变体按关键词一并丢弃`() {
        listOf("userPass", "login_pwd", "MY_SECRET", "Remember-Me", "UserName").forEach {
            assertTrue(it, isCredentialCookie(it))
        }
    }

    @Test
    fun `会话票与令牌一律保留`() {
        assertEquals(
            setOf("CASTGC", "JSESSIONID", "PHPSESSID_8800", "QZKJ-YJSBB-RETOEKN", "SESSION"),
            names(
                "CASTGC=TGT-1-abc; JSESSIONID=1AB2C3; PHPSESSID_8800=01d36; " +
                    "QZKJ-YJSBB-RETOEKN=eyJ.rr; SESSION=deadbeef",
            ),
        )
    }

    @Test
    fun `滤空与空值条目`() {
        assertNull(filterSessionCookies("username=a; password=b"))
        assertNull(filterSessionCookies("   "))
        assertNull(filterSessionCookies(null))
        assertNull(filterSessionCookies("JSESSIONID=; x="))
        assertEquals(setOf("JSESSIONID"), names("JSESSIONID=v; ；"))
    }

    @Test
    fun `过滤是幂等的，自愈写回不会越滤越少`() {
        val once = filterSessionCookies("password=x; CASTGC=TGT-1-abc; username=u")
        assertEquals(once, filterSessionCookies(once))
    }
}
