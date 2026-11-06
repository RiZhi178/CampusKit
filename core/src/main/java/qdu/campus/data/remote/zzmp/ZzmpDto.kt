package qdu.campus.data.remote.zzmp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * zzmp（上海商联 SLTAS）的统一响应外壳。
 *
 * HTTP 恒 200，成败只看 `code`：`00000000` 成功、`40320001` 未登录、
 * `40310002` 缺 `requestSiteDomain` 头。错误响应里的 `data_json` 是登录地址对象，
 * 所以按调用点给泛型，不在这里假定业务形状。
 */
@Serializable
data class ZzmpEnvelope<T>(
    val code: String? = null,
    val message: String? = null,
    @SerialName("data_json") val data: T? = null,
) {
    val isSuccess: Boolean get() = code == CODE_OK
    val isLoggedOut: Boolean get() = code == CODE_NOT_LOGGED_IN

    companion object {
        const val CODE_OK = "00000000"
        const val CODE_NOT_LOGGED_IN = "40320001"
        const val CODE_NO_DOMAIN = "40310002"
    }
}

// ── 房间编号校验（充值前置查询）────────────────────────────────────────────
// 三个接口都是低代码平台按页面配置串起来的，响应外壳与普通接口不同：
// 成功码是 30300000，业务数据放在 `datajson`。
// 参数名与嵌套形状照 2026-11-05 浏览器实测，见 docs/interfaces/zzmp.md 第六节。

@Serializable
data class ZzmpCreatePageQuery(
    val domain: String,
    val condition: List<String> = emptyList(),
    @SerialName("call_page_id") val callPageId: String,
    @SerialName("title_name") val titleName: String = "",
)

/** `createPage` 的响应：业务字段在顶层，不在 `datajson` 里。 */
@Serializable
data class ZzmpCreatePageDto(
    val code: String? = null,
    val message: String? = null,
    @SerialName("current_merchant_id") val merchantId: Long = 0,
)

@Serializable
data class ZzmpMerMapQuery(
    val rechMerMapId: Int = 1,
    val merchantId: Long,
)

@Serializable
data class ZzmpMerMapDto(
    val clientUserInfoId: Long = 0,
    val rechMerMapId: Int = 1,
    val rechUnitName: String = "",
)

@Serializable
data class ZzmpCheckOrderQuery(
    val merchantId: Long,
    val userInfoId: Long,
    val rechMerMapId: Int = 1,
    val custRechNo: String,
)

@Serializable
data class ZzmpCheckOrderDto(
    val deviceInfo: List<ZzmpCheckDevice> = emptyList(),
)

@Serializable
data class ZzmpCheckDevice(
    val targetName: String = "",
    val infos: List<ZzmpCheckInfo> = emptyList(),
)

/** 页面上的「公寓信息 / 剩余电量 / 当前余额 / 剩余补助」就是这一组键值。 */
@Serializable
data class ZzmpCheckInfo(
    val keyName: String = "",
    val keyValue: String = "",
)

/** `datajson` 外壳（页面类接口专用）。 */
@Serializable
data class ZzmpPageEnvelope<T>(
    val code: String? = null,
    val message: String? = null,
    val type: String? = null,
    val datajson: T? = null,
) {
    val isSuccess: Boolean get() = code == CODE_OK || code == CODE_CREATED
    val isLoggedOut: Boolean get() = code == "40320001"

    companion object {
        const val CODE_OK = "00000000"
        const val CODE_CREATED = "30300000"
    }
}
