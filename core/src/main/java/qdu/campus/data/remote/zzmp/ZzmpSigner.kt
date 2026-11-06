package qdu.campus.data.remote.zzmp

import java.security.MessageDigest
import kotlin.random.Random

/** 一次请求要带上的签名头。GET 的 token 是空串，这里只产出 POST 用的值。 */
data class ZzmpSignature(
    val requestId: String,
    val rand: String,
    val token: String,
)

/**
 * zzmp 的请求头签名。
 *
 * 规则照抄前端 bundle（`token` 只对 POST 生效）：
 *
 *     token = MD5( SALT + MD5( rand 后 6 位 + MD5( JSON.stringify(body) ) ) )
 *
 * [SALT] 是打包产物里的公开常量，不是密钥；它只防手改请求体，
 * 真正的会话凭据仍是 Cookie 里的 `SESSION` 与 storage 里的 `sessionToken`。
 *
 * 这里的 `body` 必须是实际发出去的字节串——重新序列化一次会得到同样的 JSON，
 * 但多一个空格签名就对不上了，所以调用方传入序列化后的原文。
 */
object ZzmpSigner {

    const val SALT = "hgf434h767s3r56f"

    private const val RAND_LENGTH = 13
    private const val RAND_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    fun sign(
        body: String,
        now: Long = System.currentTimeMillis(),
        random: Random = Random.Default,
    ): ZzmpSignature {
        val rand = buildString(RAND_LENGTH) {
            repeat(RAND_LENGTH) { append(RAND_ALPHABET[random.nextInt(RAND_ALPHABET.length)]) }
        }
        // 前端是 "403" + 时间戳后 5 位 + 5 位随机数
        val requestId = "403" +
            (now % 100_000).toString().padStart(5, '0') +
            random.nextInt(100_000).toString().padStart(5, '0')
        return ZzmpSignature(requestId = requestId, rand = rand, token = token(rand, body))
    }

    fun token(rand: String, body: String): String =
        md5(SALT + md5(rand.takeLast(6) + md5(body)))

    fun md5(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
