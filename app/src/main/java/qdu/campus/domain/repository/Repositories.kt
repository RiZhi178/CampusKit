package qdu.campus.domain.repository

import qdu.campus.domain.model.AppearanceMode
import qdu.campus.domain.model.AppearanceScheme
import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.EhallApp
import qdu.campus.domain.model.EhallTaskCounts
import qdu.campus.domain.model.LibraryBorrowing
import qdu.campus.domain.model.LibraryHolding
import qdu.campus.domain.model.RoomAccount
import qdu.campus.domain.model.LibraryReader
import qdu.campus.domain.model.LibrarySearchPage
import qdu.campus.domain.model.NoScheduleCourse
import qdu.campus.domain.model.Notice
import qdu.campus.data.remote.notice.NoticeDetail
import qdu.campus.domain.model.RepairDetail
import qdu.campus.domain.model.RepairDraft
import qdu.campus.domain.model.RepairOption
import qdu.campus.domain.model.RepairTicket
import qdu.campus.domain.model.Seat
import qdu.campus.domain.model.SeatAccount
import qdu.campus.domain.model.SeatReservation
import qdu.campus.domain.model.SeatRoom
import qdu.campus.domain.model.SemesterOption
import qdu.campus.domain.model.SessionState
import qdu.campus.domain.model.SyncState
import qdu.campus.domain.model.TimetableLevel
import qdu.campus.domain.model.XgAwardPage
import qdu.campus.domain.model.XgLeaveFormPage
import qdu.campus.domain.model.XgLeaveListPage
import qdu.campus.domain.model.XgLeaveRecord
import qdu.campus.domain.model.XgLeaveWriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.LocalDateTime

/** 一次刷新的结果。失败不清空已缓存内容，只把原因交给页面做非阻断提示。 */
sealed interface RefreshOutcome {
    data class Success(val itemCount: Int) : RefreshOutcome

    /**
     * 多数据源的部分成功：[itemCount] 是本次入库的条数，[failures] 是没抓到的来源与原因。
     * 已入库的部分照常生效，失败交给页面做非阻断提示。
     */
    data class Partial(val itemCount: Int, val failures: List<String>) : RefreshOutcome

    /** [loginRequired] 为真时页面给「去登录」而不是「知道了」：只有重新登录能解决。 */
    data class Failure(val message: String, val loginRequired: Boolean = false) : RefreshOutcome
}

interface TimetableRepository {
    fun observeCourses(semesterId: String): Flow<List<Course>>
    fun observeSections(termId: String): Flow<List<ClassSection>>
    fun observeNoSchedule(semesterId: String): Flow<List<NoScheduleCourse>>
    fun observeSyncState(): Flow<SyncState?>
    fun observeLatestTerm(): Flow<String?>

    /** 这一路数据源能给出的学期，供「学期与节次」里的学期选择框。 */
    fun observeSemesterOptions(): Flow<List<SemesterOption>>
    suspend fun refresh(): RefreshOutcome
}

interface NoticeRepository {
    fun observeNotices(): Flow<List<Notice>>

    /** 各数据源各自的同步状态，通知页按页签分别标「数据更新于」。 */
    fun observeSyncStates(): Flow<List<SyncState>>
    suspend fun refresh(): RefreshOutcome
    suspend fun markRead(fingerprint: String)
    suspend fun clear()

    /**
     * 抓取并解析一条通知的详情页（博达 CMS 正文 + 附件清单）。
     * 进程内按 URL 缓存；失败返回 null，由页面给「重试 / 打开原网页」。
     */
    suspend fun fetchDetail(url: String): NoticeDetail?
}

/**
 * 自习室座位（csyy）。房间列表免登录，其余都要会话。
 *
 * 状态不在服务端：可约与否由 [qdu.campus.domain.usecase.SeatAvailability]
 * 按规则与已有占用算出，仓储只负责取数与下单。
 */
interface SeatRepository {
    suspend fun rooms(): List<SeatRoom>
    suspend fun seats(roomId: Int, date: LocalDate): List<Seat>
    suspend fun reservations(from: LocalDate, to: LocalDate): List<SeatReservation>

    /**
     * 一卡通账号与姓名。`account/info` 是统一认证链上唯一稳定给出全名的只读接口，
     * 账号卡上的姓名取这里；会话缺失或网络不可达时返回 null，不抛。
     */
    suspend fun account(): SeatAccount?

    /** 返回服务端消息，失败抛异常。 */
    suspend fun book(seat: Seat, start: LocalDateTime, end: LocalDateTime, memo: String): String
    suspend fun cancel(uuid: String): String
    suspend fun endEarly(uuid: String): String
}

/**
 * 账号卡上的身份信息。
 *
 * 姓名不单独发一次请求就能变准：它只在 csyy `account/info` 给得出来，
 * 所以本机存一份缓存，登录态成立时补一次，取不到就沿用缓存或留空由卡片退回「已登录」。
 */
interface ProfileRepository {
    fun cachedName(): String?

    /** 拉一次 `account/info` 并落盘，返回当前可用的姓名；失败时返回既有缓存。 */
    suspend fun syncName(): String?
}

/**
 * 办事大厅（ehall，苏迪 AMP）。
 *
 * 只取服务清单这一层：大厅本身是聚合入口，背后挂几十个第三方系统，逐个原生化不现实，
 * 点条目仍交内嵌 WebView，只是由 [entryUrl] 走服务端解析器补第三方应用的登录握手。
 */
interface EhallRepository {
    suspend fun services(): List<EhallApp>
    suspend fun taskCounts(): EhallTaskCounts?
    fun entryUrl(app: EhallApp): String
}

/**
 * 图书馆统一检索（`findqdu.libsp.cn`，超星 libsp）。
 *
 * 检索与馆藏免登录就能读，借阅记录要 `jwt` 会话，因此 [loans] 会单独抛
 * [qdu.campus.platform.common.LibraryAuthException]：它只关掉「我的借阅」这一页，
 * 检索照常可用。只读取，续借与预约一律不在本模块内。
 */
interface LibraryRepository {
    /** 本机是否已有那对 `jwtHeader`/`jwt` 会话头。有才谈得上检索与借阅。 */
    fun hasSession(): Boolean
    suspend fun search(keyword: String, page: Int): LibrarySearchPage
    suspend fun holdings(recordId: Long): List<LibraryHolding>
    suspend fun loans(): LibraryBorrowing
    /** 借阅历史只取最近一页。[LibraryBorrowing.total] 大于这一页时由页面提示还有更多。 */
    suspend fun loanHistory(): LibraryBorrowing
    suspend fun reader(): LibraryReader?
}

/**
 * 电费与综合缴费（zzmp，上海商联 SLTAS）。
 *
 * 只做房间编号核对与账户快照。充值、下单与支付不在这里，也不应加进来：
 * 资金操作永远是内嵌页或官方渠道的事。
 */
interface ZzmpRepository {
    /**
     * 校验房间编号并取账户信息。编号不存在时抛 [qdu.campus.platform.common.RemoteDataException]，
     * 消息已翻成「对应房间没有账户」这种能看懂的话。
     *
     * 只读：走的是充值页的查询链路（createPage → merMapInfo → selectAndCheckOrder），
     * 2026-11-05 实测不会产生待支付单。
     */
    suspend fun checkRoomCode(code: String): RoomAccount
}

/**
 * 学工系统（xg）各子系统的页面。见 `docs/interfaces/xg.md`。
 *
 * 会话只能由门户页面自己签发，所以这一层假设「本机已经有学工的会话」：
 * 没有就抛 [qdu.campus.platform.common.XgAuthException]，由界面把人送回学工入口手点一次，
 * 不在原生里模拟跳票、也不挂隐藏页自动点。
 *
 * 评奖评优只读；日常请假的读写已授权原生实现，写操作走整表单回发并重读列表核对成败。
 */
interface XgRepository {
    /** 评奖评优的学校奖助页：批次窗口 + 本人申请记录。 */
    suspend fun schoolAwardApply(): XgAwardPage

    /** 我的请假列表：申请入口或在途互斥提示 + 本人记录。 */
    suspend fun leaveList(): XgLeaveListPage

    /** 新建表单（[recordId] 为空）或详情（带记录号）。 */
    suspend fun leaveForm(recordId: String = ""): XgLeaveFormPage

    /** 提交请假。[existingIds] 是提交前列表里的记录号，用来核对是否真的多出一条。 */
    suspend fun submitLeave(
        page: XgLeaveFormPage,
        edits: Map<String, String>,
        existingIds: Set<String>,
    ): XgLeaveWriteResult

    /** 列表行内撤销。[page] 必须是撤销前刚读到的列表，按钮名随行号前移。 */
    suspend fun revokeLeave(record: XgLeaveRecord, page: XgLeaveListPage): XgLeaveWriteResult

    /** 详情内撤销。 */
    suspend fun revokeLeaveDetail(page: XgLeaveFormPage): XgLeaveWriteResult

    /** 省→市→区联动回发，返回重绑下级选项后的整页表单。 */
    suspend fun cascadeArea(
        page: XgLeaveFormPage,
        selectName: String,
        edits: Map<String, String>,
    ): XgLeaveFormPage
}

interface SessionRepository {
    /** 会话状态。用 StateFlow 是为了让 UI 首帧就读到本地判定结果，不出现门禁闪烁。 */
    val state: StateFlow<SessionState>

    /** 登录页拿到 TGT 后直接判有效，不再等前台探测。 */
    fun markValid()

    /**
     * 冷启动回灌完 Cookie 立刻按本机是否已有 TGT 定态。
     * 没有这一步，卡片在探测返回前一直显示「未登录」，看起来像登录没保住。
     */
    fun warmStart()

    suspend fun validate(): SessionState
    suspend fun studentId(): String?

    /** 退出登录：只清会话凭据与 WebView 存储，学号留着，下次登录还能预填。 */
    suspend fun clearSession()

    /** 清除全部本机凭据（含学号），用于切换账号与「清除全部数据」。 */
    suspend fun clearAll()
}

interface SettingsRepository {
    val semesterId: Flow<String>
    val termStartDate: Flow<String>
    /** 节次时间覆盖，键为节次序号，值为起止时间。空表示沿用学期配置。 */
    val sectionTimeOverrides: Flow<Map<Int, Pair<String, String>>>
    /** 通知关键词筛选，同时用于新通知里的推送匹配。 */
    val notificationKeywords: Flow<List<String>>
    val onboardingDone: Flow<Boolean>
    val appearanceScheme: Flow<AppearanceScheme>
    val appearanceMode: Flow<AppearanceMode>
    /** 「保留颜色」：深色下表面带上主色色相。默认关闭，浅色下不生效。 */
    val darkTinted: Flow<Boolean>
    /**
     * 首页「常用」显示哪些入口、按什么顺序。存 CampusService.id 的有序列表。
     * 空表示沿用 [qdu.campus.feature.web.CampusService.homeCards] 的默认集合与顺序。
     */
    val homeCards: Flow<List<String>>

    /**
     * 绑定的宿舍电费编号（充值编号，如 浩1-1-101）。空串表示未绑定。
     * 房间号不是凭据，但它属于用户自己的宿舍信息，只在落本机 DataStore。
     */
    val dormElectricityCode: Flow<String>

    /**
     * 课表取哪份数据：研究生系统，还是随包发布的本科生课程总表。
     * 学校两边都给不出「这个人是什么层次」，所以是本机设置，不是登录时推断出来的。
     * null 表示还没选过——第一次进课表时问一下，选完就不再打扰。
     */
    val timetableLevel: Flow<TimetableLevel?>

    /** 本科课表勾选的班级。空集合表示还没选过，页面据此决定要不要给导入引导。 */
    val undergraduateClasses: Flow<Set<String>>

    /** 上一次应用本科课表的时刻，页面用它说「数据更新于」。 */
    val undergraduateAppliedAt: Flow<Long?>

    /**
     * 首页更新横幅已忽略的 release tag（如 `v1.1.0`）。
     * 同版本只提醒一次；空串 = 没有忽略过任何版本。
     */
    val updateDismissedTag: Flow<String>

    suspend fun setUpdateDismissedTag(value: String)

    suspend fun setSemesterId(value: String)
    suspend fun setTermStartDate(value: String)
    suspend fun setSectionTimeOverrides(value: Map<Int, Pair<String, String>>)
    suspend fun setNotificationKeywords(value: List<String>)
    suspend fun setOnboardingDone(value: Boolean)
    suspend fun setAppearanceScheme(value: AppearanceScheme)
    suspend fun setAppearanceMode(value: AppearanceMode)
    suspend fun setDarkTinted(value: Boolean)
    suspend fun setHomeCards(ids: List<String>)
    suspend fun setDormElectricityCode(value: String)

    suspend fun setTimetableLevel(value: TimetableLevel)

    /** 勾选班级即「应用课表」，应用时刻一并记下，不另开一次写。 */
    suspend fun setUndergraduateClasses(value: Set<String>)

    /**
     * 清掉课表相关的全部本机状态：学期、起始日、节次覆盖、层次与本科班级勾选。
     *
     * 只清 Room 的课表表是不够的——本科课表的课程由班级勾选与随包资产实时推导，
     * 勾选不清，课表「清除」后立刻原样回来。
     */
    suspend fun clearTimetableData()

    /** 清掉全部本机设置与偏好（含主题、房号绑定、通知关键词与引导标记），回到首次安装状态。 */
    suspend fun clearAllData()
}

/**
 * 宿舍报修。数据源见 `docs/interfaces/baoxiu.md`；鉴权是 Cookie + `token` 头。
 *
 * 写操作（提交、撤销）都直接落到校方系统，调用方必须先做二次确认。
 */
interface BaoxiuRepository {
    /** 「我的报修」。返回当前页与总数。 */
    suspend fun tickets(page: Int = 1): Pair<List<RepairTicket>, Int>

    /** 工单详情（含进度流水）。 */
    suspend fun detail(ticketId: String): RepairDetail

    suspend fun campuses(): List<RepairOption>
    suspend fun buildings(campusId: String): List<RepairOption>
    suspend fun groups(): List<RepairOption>
    suspend fun items(groupId: String): List<RepairOption>

    /** 上一次提交过的表单，用来预填校区/楼栋/地址。 */
    suspend fun lastDraft(): RepairDraft?

    suspend fun uploadPhoto(bytes: ByteArray, fileName: String, mime: String): String

    suspend fun submit(draft: RepairDraft)

    /** 撤销。原因为空时直接抛 [RemoteDataException]（网页端也是必填）。 */
    suspend fun cancel(ticketId: String, reason: String)
}
