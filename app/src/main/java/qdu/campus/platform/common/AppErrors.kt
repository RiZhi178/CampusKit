package qdu.campus.platform.common

import java.io.IOException

/**
 * 研究生系统（yjsxt）自己的账号会话缺失。
 *
 * 该系统实测 `loginType=LOCAL`，不在统一认证覆盖内，它的失败只能说明研究生系统
 * 没登录，绝不能拿去判统一认证会话失效——否则课表每同步一次，
 * 图书馆、学工、座位这些本来好好的入口会一起被拦成「未登录」。
 */
class YjsxtAuthException(message: String = "研究生系统需要单独登录") : IOException(message)

/** 远端返回了业务错误或结构异常。 */
class RemoteDataException(message: String) : IOException(message)

/**
 * 场所管理系统（csyy）会话缺失，业务码 300。
 *
 * csyy 由统一认证放行，但它的会话是登录后自己种的 Cookie，
 * 与 authserver 的 TGT 不同寿命。TGT 还在而 csyy 掉线是常态，
 * 所以这里只表示「去 csyy 页面走一次」，不代表统一认证失效。
 */
class CsyyAuthException(message: String = "座位系统需要重新登录") : IOException(message)

/** 网络不可达，区别于业务失败。 */
class NetworkUnavailableException(message: String = "网络不可用") : IOException(message)

/**
 * 办事大厅（ehall，苏迪 AMP）会话缺失。
 *
 * 它不返回 401，也没有业务码：HTTP 恒 200，只在 JSON 里给 `hasLogin:false`。
 * 判掉线只能读这个字段，见 [qdu.campus.data.remote.ehall.EhallAppListDto]。
 */
class EhallAuthException(message: String = "办事大厅需要重新登录") : IOException(message)

/**
 * 图书馆统一检索（超星 libsp）的借阅会话缺失。
 *
 * 检索与馆藏本来就不需要登录，所以这个异常只会从「我的借阅」来：
 * 要么 `jwtHeader` / `jwt` 这对 Cookie 没了，要么服务端说「登录信息过期，请重新登录」。
 * 它和统一认证无关——TGT 还在也可能掉，青大这条链是 authserver 放行后由超星自己种的会话。
 */
class LibraryAuthException(message: String = "图书馆借阅记录需要重新登录") : IOException(message)

/**
 * 电费与综合缴费（zzmp，上海商联 SLTAS）的域会话缺失。
 *
 * 它和 csyy 一样由统一认证放行后自己种会话：TGT 还在而 zzmp 掉线是常态。
 * 业务码 `40320001` 只说明这一域要再走一次登录桥，不代表统一认证失效。
 */
class ZzmpAuthException(message: String = "电费系统需要重新登录") : IOException(message)

/**
 * 电费系统明确回答「这个编号没有对应账户」。
 *
 * 单独一个类型，是因为绑定要不要落盘得看这是「定论」还是「没查成」：
 * 会话过期、连不上都属于后者，编号不存在才是前者，只有后者该拦住用户绑定。
 */
class RoomCodeRejectedException(message: String) : IOException(message)

/**
 * 学工系统（xg）的业务会话缺失。
 *
 * 学工的会话由门户页面上的跳票签发（`Omniselector.aspx?p=…` 是唯一写会话的地方），
 * 统一认证的 TGT 在也不能替它建会话，直达子页只会复用现有会话。
 * 所以这个异常的意思是「去学工门户点一次对应的服务卡片」，不代表统一认证失效。
 */
class XgAuthException(message: String = "学工系统需要先进一次门户建立会话") : IOException(message)
