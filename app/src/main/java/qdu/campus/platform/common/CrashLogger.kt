package qdu.campus.platform.common

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃日志只写本机文本文件，不上传。
 * 由设置页手动导出，符合「无统计 SDK、无崩溃上报」的约束。
 */
object CrashLogger {

    private const val FILE_NAME = "crash.log"
    private const val MAX_BYTES = 256 * 1024

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val file = file(context)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        val header = buildString {
            append("\n===== ").append(timestamp).append(" =====\n")
            append("thread: ").append(thread.name).append('\n')
            append("device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append(" / Android ").append(Build.VERSION.RELEASE)
                .append(" / SDK ").append(Build.VERSION.SDK_INT).append('\n')
        }
        val body = throwable.stackTraceToString()
        file.appendText(header + body)
        if (file.length() > MAX_BYTES) {
            val trimmed = file.readText().takeLast(MAX_BYTES / 2)
            file.writeText(trimmed)
        }
    }

    /** 手动记录一条非致命错误（相机绑定失败这类被捕获的异常），进同一份日志供导出。 */
    fun log(context: Context, message: String, throwable: Throwable) {
        val file = file(context)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        runCatching {
            file.appendText(
                "\n===== $timestamp =====\n" +
                    "thread: " + Thread.currentThread().name + '\n' +
                    "device: " + Build.MANUFACTURER + ' ' + Build.MODEL + " / Android " +
                    Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT + '\n' +
                    message + '\n' +
                    throwable.stackTraceToString(),
            )
            if (file.length() > MAX_BYTES) {
                val trimmed = file.readText().takeLast(MAX_BYTES / 2)
                file.writeText(trimmed)
            }
        }
    }

    fun read(context: Context): String {
        val file = file(context)
        if (!file.exists()) return ""
        return runCatching { file.readText() }.getOrDefault("")
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)
}
