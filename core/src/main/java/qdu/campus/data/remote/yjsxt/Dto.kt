package qdu.campus.data.remote.yjsxt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 三个课表接口统一的响应外壳。
 * 服务端字段远比这里多，反序列化必须忽略未知键。
 */
@Serializable
data class ApiEnvelope<T>(
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val errorMessageParam: List<String>? = null,
    val data: T? = null,
) {
    val isSuccess: Boolean
        get() = errorCode == null || errorCode.equals("success", ignoreCase = true)

    /**
     * 金智统一认证错误码：`security.httpstatu.401.1006`
     * 代表请求未携带有效会话，应触发重新登录而非报业务错误。
     */
    val isTokenExpired: Boolean
        get() = errorCode?.contains("401") == true ||
            errorCode?.contains("1006") == true
}

@Serializable
data class SemesterDto(
    @SerialName("sjms_name") val name: String? = null,
    @SerialName("pkgl00201List") val sections: List<BigSectionDto>? = null,
)

@Serializable
data class BigSectionDto(
    @SerialName("id") val id: String? = null,
    @SerialName("pkgl002id") val bigSectionId: String? = null,
    @SerialName("djname") val bigName: String? = null,
    @SerialName("dyname") val name: String? = null,
    @SerialName("djkssj") val startTime: String? = null,
    @SerialName("djjssj") val endTime: String? = null,
    /** 占用的小节号，逗号分隔，例如 "01,02" */
    @SerialName("zyxjs") val sectionIndexes: String? = null,
    @SerialName("sjjdname") val period: String? = null,
    @SerialName("sfxscode") val visible: String? = null,
)

@Serializable
data class CourseDto(
    @SerialName("id") val id: String? = null,
    @SerialName("kc_name") val name: String? = null,
    @SerialName("kcbh") val code: String? = null,
    @SerialName("ktmc_name") val classNo: String? = null,
    @SerialName("teachernames") val teachers: String? = null,
    @SerialName("teachernames_1") val teachersAlt: String? = null,
    @SerialName("js_name") val room: String? = null,
    @SerialName("xq_name") val campus: String? = null,
    @SerialName("pksj") val timeCode: String? = null,
    @SerialName("pkzcmx") val weekDetail: String? = null,
    @SerialName("pkzc") val weekRange: String? = null,
    @SerialName("djkssj") val startTime: String? = null,
    @SerialName("djjssj") val endTime: String? = null,
    @SerialName("kclb_name") val category: String? = null,
    @SerialName("kcxz_name") val studyType: String? = null,
    @SerialName("zxs") val hours: Int? = null,
    @SerialName("zxf") val credits: Double? = null,
    @SerialName("kkdw_name") val department: String? = null,
    @SerialName("jczy013id") val semesterId: String? = null,
    @SerialName("pkzt_name") val status: String? = null,
)

/** 未排课课程字段与主课表不同：kcmc_name / skls_name / kcfz。 */
@Serializable
data class NoScheduleDto(
    @SerialName("id") val id: String? = null,
    @SerialName("kcmc_name") val name: String? = null,
    @SerialName("kc_name") val nameAlt: String? = null,
    @SerialName("skls_name") val teachers: String? = null,
    @SerialName("teachernames") val teachersAlt: String? = null,
    @SerialName("kcfz") val credits: Double? = null,
    @SerialName("zxf") val creditsAlt: Double? = null,
    @SerialName("kkdw_name") val department: String? = null,
    @SerialName("kclb_name") val category: String? = null,
    @SerialName("jczy013id") val semesterId: String? = null,
)