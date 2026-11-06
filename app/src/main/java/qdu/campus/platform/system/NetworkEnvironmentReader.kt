package qdu.campus.platform.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import qdu.campus.domain.model.CampusNetworkEnvironment
import qdu.campus.domain.model.CampusWifi
import java.net.Inet4Address
import java.net.Inet6Address
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当前网络环境读取，只读系统已经分配好的结果，不发任何请求。
 *
 * 「是否在校园网」按 SSID 判定，不用请求超时去推断：认证区在内网，
 * 靠超时判断会把移动数据下的正常状态误报成异常。
 * 地址从 `LinkProperties` 取，系统给多少读多少，不假定网段，也不猜 IPv6 有没有。
 */
@Singleton
class NetworkEnvironmentReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun current(): CampusNetworkEnvironment {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return CampusNetworkEnvironment()
        val network = manager.activeNetwork ?: return CampusNetworkEnvironment()
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return CampusNetworkEnvironment()
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val ssid = if (isWifi) readSsid(capabilities) else null
        val addresses = runCatching {
            manager.getLinkProperties(network)
                ?.linkAddresses
                ?.map { it.address }
                .orEmpty()
        }.getOrDefault(emptyList())

        val ipv4 = addresses.filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        val ipv6 = addresses.filterIsInstance<Inet6Address>()
            .filterNot { it.isLoopbackAddress || it.isMulticastAddress }

        return CampusNetworkEnvironment(
            isWifi = isWifi,
            ssid = ssid,
            campusWifi = CampusWifi.fromSsid(ssid),
            localIpv4 = ipv4?.hostAddress,
            ipv6Global = ipv6.filterNot { it.isLinkLocalAddress }
                .mapNotNull { it.hostAddress?.takeIf(String::isNotBlank) }
                .distinct(),
            ipv6LinkLocal = ipv6.filter { it.isLinkLocalAddress }
                // hostAddress 带 %wlan0 这类 scope id，链路本地地址需要它才能唯一定位，原样保留。
                .mapNotNull { it.hostAddress?.takeIf(String::isNotBlank) }
                .distinct(),
        )
    }

    /** 没给定位权限、或系统刚切网络时 SSID 会是空串或 `<unknown ssid>`，都按读不到处理。 */
    private fun readSsid(capabilities: NetworkCapabilities): String? = runCatching {
        (capabilities.transportInfo as? WifiInfo)
            ?.ssid
            ?.trim('"')
            ?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }
    }.getOrNull()

    private companion object {
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
