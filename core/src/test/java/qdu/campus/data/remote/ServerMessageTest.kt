package qdu.campus.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只锁「能不能把服务端那句话取出来」，不锁各家业务语义。
 * 样本形状取自 docs/interfaces 下已实测的响应外壳。
 */
class ServerMessageTest {

    @Test
    fun `研究生系统取 errorMessage`() {
        val body = """{"errorCode":"500","errorMessage":"学期不存在","data":null}"""
        assertEquals("学期不存在", ServerMessage.extract(body))
    }

    @Test
    fun `座位系统取 message`() {
        assertEquals("请重新登录", ServerMessage.extract("""{"code":300,"message":"请重新登录"}"""))
    }

    @Test
    fun `报修系统取 msg`() {
        assertEquals("参数错误", ServerMessage.extract("""{"code":1,"msg":"参数错误"}"""))
    }

    @Test
    fun `金智动态码取 returnMessage`() {
        val body = """{"res":"notmobile","returnMessage":"该用户尚未绑定手机号"}"""
        assertEquals("该用户尚未绑定手机号", ServerMessage.extract(body))
    }

    @Test
    fun `OAuth2 优先取人话 error_description 而不是机器码 error`() {
        val body = """{"error":"client_identification_is_invalid","error_code":"10003","error_description":"客户端标识不合法"}"""
        assertEquals("客户端标识不合法", ServerMessage.extract(body))
    }

    @Test
    fun `只有 error 时退而取机器码`() {
        assertEquals(
            "request_missing_param",
            ServerMessage.extract("""{"error":"request_missing_param","error_code":"10001"}"""),
        )
    }

    @Test
    fun `HTML 错误页取 title`() {
        val body = "<html><head><title>502 Bad Gateway</title></head><body>nginx</body></html>"
        assertEquals("502 Bad Gateway", ServerMessage.extract(body))
    }

    @Test
    fun `空体与纯文本取不出消息`() {
        assertNull(ServerMessage.extract(""))
        assertNull(ServerMessage.extract("   "))
        assertNull(ServerMessage.extract("Bad Gateway"))
        assertNull(ServerMessage.extract("""{"errorCode":"500","data":null}"""))
    }

    @Test
    fun `空字符串消息不算消息`() {
        assertNull(ServerMessage.extract("""{"message":"","msg":"  "}"""))
    }

    @Test
    fun `message 是对象时跳过它取下一个字段`() {
        val body = """{"message":{"detail":"x"},"msg":"备选原因"}"""
        assertEquals("备选原因", ServerMessage.extract(body))
    }

    @Test
    fun `JSON 坏了仍用正则兜住`() {
        val body = """{"message":"磁盘满","data":}"""
        assertEquals("磁盘满", ServerMessage.extract(body))
    }

    @Test
    fun `转义引号还原`() {
        assertEquals("他说\"不行\"", ServerMessage.extract("""{"message":"他说\"不行\""}"""))
    }

    @Test
    fun `换行与多空格压成单行`() {
        val body = "{\"message\":\"第一行\\n第二行   第三行\"}"
        assertEquals("第一行 第二行 第三行", ServerMessage.extract(body))
    }

    @Test
    fun `超长消息截断到一百二十字`() {
        val long = "错".repeat(200)
        val result = ServerMessage.extract("""{"message":"$long"}""")!!
        assertEquals(121, result.length)
        assertTrue(result.endsWith("…"))
        assertTrue(result.startsWith("错错错"))
    }
}