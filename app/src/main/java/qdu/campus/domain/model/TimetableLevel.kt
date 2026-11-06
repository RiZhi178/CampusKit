package qdu.campus.domain.model

/**
 * 课表的数据来源。
 *
 * 研究生走研究生系统（yjsxt）同步，本科生走随包发布的教务处课程总表，两者不共用取数路径。
 * 学校系统认不出「这个人是本科生还是研究生」——统一认证两边都给不出这个结论，
 * 所以由用户自己选一次，记在本机。
 */
enum class TimetableLevel {
    Graduate,
    Undergraduate,
}
