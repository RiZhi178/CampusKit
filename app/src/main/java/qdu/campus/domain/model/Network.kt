package qdu.campus.domain.model

/** 会话状态。不向 UI 暴露 Cookie / Token 本身，只暴露派生状态。 */
enum class SessionState {
    /** 尚未校验或缺少凭据 */
    Unknown,
    Valid,
    Expired,
}

/**
 * 校园 Wi-Fi。学校只有这两个 SSID，两个都算校园网，但认证方式不同：
 * QDU-1X 在无线连接面板里配好账号密码与证书即可，连接时由系统完成 802.1X 认证；
 * QDU-Web 连上之后仍要过网页认证（深澜 Portal），未认证时只有认证区可达。
 */
enum class CampusWifi(val ssid: String, val portalRequired: Boolean) {
    Qdu1x("QDU-1X", false),
    QduWeb("QDU-Web", true),
    ;

    companion object {
        /** 按 SSID 归类。大小写不敏感，名称对不上就是不在校园网。 */
        fun fromSsid(ssid: String?): CampusWifi? {
            val name = ssid?.trim().orEmpty()
            if (name.isEmpty()) return null
            return entries.firstOrNull { it.ssid.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * 当前网络环境快照。地址一律按系统实际分配读，不写死。
 *
 * IPv6 分两类给：校园网同时下发 v6，只显示 v4 容易让人以为没连上；
 * 而 `fe80` 开头的链路本地地址出了本链路就不能用，和可路由的全局地址分开列。
 */
data class CampusNetworkEnvironment(
    /** 活动网络是不是 Wi-Fi。移动数据或没有网络时为 false。 */
    val isWifi: Boolean = false,
    /** 当前 SSID。没给定位权限或系统读不到时为 null，不代表没连 Wi-Fi。 */
    val ssid: String? = null,
    val campusWifi: CampusWifi? = null,
    val localIpv4: String? = null,
    /** 全局 IPv6（含 ULA），可直接对外通信。 */
    val ipv6Global: List<String> = emptyList(),
    /** 链路本地 IPv6（fe80::/10），只在本链路内有效。 */
    val ipv6LinkLocal: List<String> = emptyList(),
) {
    val onCampus: Boolean get() = campusWifi != null
}
