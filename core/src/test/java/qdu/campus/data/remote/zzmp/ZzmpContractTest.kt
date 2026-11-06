package qdu.campus.data.remote.zzmp

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ZzmpContractTest {

    /** 与 app 侧 appJson 同一套宽松配置，测试才反映真实反序列化行为。 */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun `页面外壳认 30300000 成功并取出账户键值`() {
        val envelope = json.decodeFromString(
            ZzmpPageEnvelope.serializer(ZzmpCheckOrderDto.serializer()),
            """{"code":"30300000","message":"交易成功","datajson":{"deviceInfo":[
                 {"targetName":"怡山1号楼-1-101","infos":[
                   {"keyName":"剩余电量","keyValue":"23.09"},
                   {"keyName":"当前余额","keyValue":"12.34"}]}]}}""",
        )
        assertTrue(envelope.isSuccess)
        assertFalse(envelope.isLoggedOut)

        val infos = requireNotNull(envelope.datajson).deviceInfo.first().infos
        assertEquals(2, infos.size)
        assertEquals("当前余额", infos[1].keyName)
        assertEquals("12.34", infos[1].keyValue)
    }

    @Test
    fun `未登录与业务失败分开，未登录才触发补会话`() {
        val loggedOut = json.decodeFromString(
            ZzmpPageEnvelope.serializer(ZzmpCheckOrderDto.serializer()),
            """{"code":"40320001","message":"未登录或登录超时，请重新登录",
               "datajson":{"ssoAuthorizeUrl":"//qdsso.qdu.edu.cn/oauth2.0/authorize"}}""",
        )
        assertFalse(loggedOut.isSuccess)
        assertTrue(loggedOut.isLoggedOut)

        val rejected = json.decodeFromString(
            ZzmpPageEnvelope.serializer(ZzmpCheckOrderDto.serializer()),
            """{"code":"E99999","message":"第三方接口响应：对应房间没有账户"}""",
        )
        assertFalse(rejected.isSuccess)
        assertFalse(rejected.isLoggedOut)
    }

    @Test
    fun `createPage 的商户号在顶层而不是 datajson`() {
        val page = json.decodeFromString(
            ZzmpCreatePageDto.serializer(),
            """{"code":"00000000","current_merchant_id":3211001,"datajson":{"ignored":true}}""",
        )
        assertEquals(3211001L, page.merchantId)
    }

    @Test
    fun `签名头的形状与常量一致`() {
        // MD5("abc") 的公开测试向量，确认摘要实现没被平台差异带偏
        assertEquals("900150983cd24fb0d6963f7d28e17f72", ZzmpSigner.md5("abc"))

        val signature = ZzmpSigner.sign("""{"type":"0"}""", now = 1_784_000_000_123, random = Random(7))
        assertEquals(13, signature.requestId.length)
        assertTrue(signature.requestId.startsWith("403"))
        assertEquals(13, signature.rand.length)
        assertTrue(signature.rand.all { it in 'a'..'z' || it in '0'..'9' })
        assertEquals(32, signature.token.length)
        assertTrue(signature.token.all { it in '0'..'9' || it in 'a'..'f' })
        // 请求体变了签名必须跟着变，否则服务端校验形同虚设
        assertFalse(signature.token == ZzmpSigner.token(signature.rand, """{"type":"1"}"""))
    }
}
