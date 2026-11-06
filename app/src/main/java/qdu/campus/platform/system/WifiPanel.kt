package qdu.campus.platform.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * 打开系统的 Wi-Fi 面板。
 *
 * Android 10 起应用不能代用户连接或保存 Wi-Fi 网络，能做的上限就是把系统的面板递到用户面前。
 * `Settings.Panel.ACTION_WIFI` 是 API 29+ 的浮层，部分 ROM 把它阉掉（`resolveActivity` 返回 null
 * 或启动抛异常），这时退回完整的 Wi-Fi 设置页，不假装能自动连上。
 */
object WifiPanel {

    /** 返回是否真的把面板或设置页打开了；两个入口都被系统挡掉时由调用方提示用户。 */
    fun open(context: Context): Boolean {
        val panel = Intent(Settings.Panel.ACTION_WIFI)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolvable = context.packageManager.resolveActivity(panel, 0) != null
        if (resolvable && runCatching { context.startActivity(panel) }.isSuccess) return true

        Log.i(TAG, "Wi-Fi 浮层不可用，退回 Wi-Fi 设置页")
        val settings = Intent(Settings.ACTION_WIFI_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(settings) }
            .onFailure { Log.w(TAG, "无法打开 Wi-Fi 设置页", it) }
            .isSuccess
    }

    private const val TAG = "WifiPanel"
}
