package qdu.campus.platform.system

/**
 * 校园网相关的外部地址。这里只有地址，没有协议实现。
 *
 * 认证不自己做：连上 QDU-Web 后由系统弹「需要登录 WLAN」，登录在系统的捕获门户页里完成。
 * [AUTH_PAGE] 只是系统没弹提示时的兜底，交给系统浏览器打开：它是内网 http 地址，
 * 本应用不放行该主机的明文（见 `res/xml/network_security_config.xml`），
 * 那套深澜协议也从未真机实测过，所以既不在应用内加载，也不在这里发请求。
 */
object CampusPortal {

    /** 网页认证入口，未经真机核对；只在用户主动点击时递给系统浏览器。 */
    const val AUTH_PAGE = "http://10.145.255.21/srun_portal_pc?ac_id=4&theme=pro"

    /** 自助服务：查看与下线在线设备、修改套餐。公网 https，移动数据下也能打开。 */
    const val SELF_SERVICE = "https://accounts.qdunet.com:8800/"
}
