package qdu.campus.platform.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 读取当前 Wi-Fi 名称需要的运行时权限。
 *
 * API 33 起 `NEARBY_WIFI_DEVICES` 只解决扫描类能力，读「当前连接的 SSID」
 * 仍要 `ACCESS_FINE_LOCATION` 且用户选择精确位置；只声明不申请会拿到空 SSID，
 * 校园网模块就会一直显示「未连接校园网」。
 */
object WifiPermission {

    val required: Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }.toTypedArray()

    fun grantStatus(context: Context): GrantState {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine) return GrantState.Missing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearby = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
            if (!nearby) return GrantState.Missing
        }
        return GrantState.Granted
    }

    enum class GrantState { Granted, Missing }
}
