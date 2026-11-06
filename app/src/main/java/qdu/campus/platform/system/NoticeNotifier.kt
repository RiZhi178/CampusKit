package qdu.campus.platform.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import qdu.campus.domain.model.Notice
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 本地通知。只在标题命中关键词时发出，不做后台常驻轮询。 */
@Singleton
class NoticeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "校内通知",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "标题命中关注关键词时提醒"
            },
        )
    }

    fun notify(notice: Notice, keyword: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${sourceLabel(notice.source)}：$keyword")
            .setContentText(notice.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.title))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(notice.fingerprint.hashCode(), notification)
        }
    }

    /** 命中任一关键词则返回该关键词。 */
    fun matchKeyword(title: String, keywords: List<String>): String? =
        keywords.firstOrNull { it.isNotBlank() && title.contains(it) }

    fun sourceLabel(source: String): String =
        if (source == "jwc_notice") "教务处" else "研究生院"

    companion object {
        const val CHANNEL_ID = "graduate_notice"
    }
}
