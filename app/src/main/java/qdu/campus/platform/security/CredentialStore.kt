package qdu.campus.platform.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本机敏感凭据存储。Cookie 只存会话票与令牌（口令类条目由
 * `WebSessionCoordinator.filterSessionCookies` 挡在门外），另有 yjsxt token 回退值、
 * zzmp 的 `sessionToken`；不保存统一认证密码，也不保存校园网 Portal 密码。
 * 文件排除于云备份与设备迁移（见 res/xml/data_extraction_rules.xml）。
 */
interface CredentialStore {
    fun cookie(domain: String): String?
    fun saveCookie(domain: String, value: String?)
    fun yjsxtToken(): String?
    fun saveYjsxtToken(token: String?)
    /** zzmp 前端把 `siteInfo.sessionToken` 注入每个业务请求，原生侧复用同一份。 */
    fun zzmpToken(): String?
    fun saveZzmpToken(token: String?)
    fun studentId(): String?
    fun saveStudentId(value: String?)
    /** 账号卡上的姓名。与学号同生命周期：退出保留、切换账号清除。 */
    fun userName(): String?
    fun saveUserName(value: String?)
    fun clear()
}

@Singleton
class EncryptedCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) : CredentialStore {

    private val prefs = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as android.content.SharedPreferences
    }.getOrElse { error ->
        // 密钥库异常时退化为不落盘的存储：功能可用性下降为「每次需重新登录」，
        // 但不会把明文凭据写到磁盘上。
        Log.e(TAG, "加密存储不可用，降级为内存存储", error)
        null
    }

    private val memoryFallback = mutableMapOf<String, String>()

    init {
        // 老版本存过 Portal 密码，原生认证链已移除；升级后把这条无主口令从磁盘上清掉。
        prefs?.let { p ->
            if (p.contains(LEGACY_PORTAL_USERNAME) || p.contains(LEGACY_PORTAL_PASSWORD)) {
                p.edit()
                    .remove(LEGACY_PORTAL_USERNAME)
                    .remove(LEGACY_PORTAL_PASSWORD)
                    .apply()
            }
        }
    }

    private fun read(key: String): String? =
        prefs?.getString(key, null) ?: memoryFallback[key]

    private fun write(key: String, value: String?) {
        if (prefs != null) {
            prefs.edit().apply {
                if (value == null) remove(key) else putString(key, value)
            }.apply()
        } else {
            if (value == null) memoryFallback.remove(key) else memoryFallback[key] = value
        }
    }

    override fun cookie(domain: String): String? = read(KEY_COOKIE_PREFIX + domain)

    override fun saveCookie(domain: String, value: String?) =
        write(KEY_COOKIE_PREFIX + domain, value)

    override fun yjsxtToken(): String? = read(KEY_YJSXT_TOKEN)

    override fun saveYjsxtToken(token: String?) = write(KEY_YJSXT_TOKEN, token)

    override fun zzmpToken(): String? = read(KEY_ZZMP_TOKEN)

    override fun saveZzmpToken(token: String?) = write(KEY_ZZMP_TOKEN, token)

    override fun studentId(): String? = read(KEY_STUDENT_ID)

    override fun saveStudentId(value: String?) = write(KEY_STUDENT_ID, value)

    override fun userName(): String? = read(KEY_USER_NAME)

    override fun saveUserName(value: String?) = write(KEY_USER_NAME, value?.takeIf { it.isNotBlank() })

    override fun clear() {
        memoryFallback.clear()
        prefs?.edit()?.clear()?.apply()
    }

    private companion object {
        const val TAG = "CredentialStore"
        const val FILE_NAME = "secure_prefs"
        const val KEY_COOKIE_PREFIX = "cookie:"
        const val KEY_YJSXT_TOKEN = "yjsxt_token"
        const val KEY_ZZMP_TOKEN = "zzmp_token"
        const val KEY_STUDENT_ID = "student_id"
        const val KEY_USER_NAME = "user_name"
        /** 0.9.2 之前的 Portal 密码，原生认证链移除后不再读写，升级时清掉。 */
        const val LEGACY_PORTAL_USERNAME = "portal_username"
        const val LEGACY_PORTAL_PASSWORD = "portal_password"
    }
}
