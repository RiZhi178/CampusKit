package qdu.campus.data.remote.ehall

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import qdu.campus.domain.model.EhallTaskCounts

class EhallMapperTest {

    /** 与 app 侧 appJson 同一套宽松配置，测试才能反映真实反序列化行为。 */
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val serializer = EhallAppListDto.serializer()

    @Test
    fun `未登录不发错误码，只留 hasLogin 为假`() {
        // 2026-11-04 匿名请求 /jsonp/serviceRoleApp.json 的真实响应体，HTTP 200
        val loggedOut = json.decodeFromString(serializer, """{"hasLogin":false,"contextPath":""}""")
        assertFalse(loggedOut.hasLogin)
        assertTrue(loggedOut.categoryList.isEmpty())
        assertTrue(loggedOut.hotRecNewAppList.isEmpty())
        assertTrue(loggedOut.toServices().isEmpty())
    }

    @Test
    fun `真实响应的字段骨架映射出一条服务`() {
        // 取自 2026-11-04 匿名 /jsonp/serviceRoleApp.json?serviceRoleId=1__0&type=all，
        // 删掉 appPc/appMobile/appPcCardList 等本项目不读的嵌套对象，其余键名与值原样保留。
        val raw = """
        {"hotRecNewAppList":[
          {"appId":"4714986430127691","appName":"校园文化活动","iconUrl":null,
           "runPlatformType":null,"description":"活动","version":"XYHD_V1.0.7_TR1",
           "deployPrefix":"https://ehall.qdu.edu.cn/publicapp","entranceUrl":null,
           "vendorName":"金智教育","status":1,"isNew":0,"isOpenInNewTab":null,
           "viewAuth":0,"accessAuth":0,"isRecommend":0,"appShortName":"xyhd",
           "hasPermission":true,"openBeginTime":null,"openEndTime":null,
           "categoryList":[{"categoryId":"2__1af936b8","categoryName":"平台基础",
                           "hasUnReadMark":false,"parentId":null,"subCategoryList":null,
                           "appManageInfoList":[],"appList":[]}],
           "appType":3,"middleIcon":"https://ehall.qdu.edu.cn/resources/app/icon_72.png",
           "opening":true,"maintaining":false,"recommend":false,
           "pcOpenUrl":"https://ehall.qdu.edu.cn/publicapp/sys/xyhd/index.do",
           "mobileAppOpenUrl":"https://ehall.qdu.edu.cn/publicapp/sys/xyhd/mobile/index.html"}
        ],
        "categoryList":[
          {"categoryId":"2__1af936b8","categoryName":"平台基础","hasUnReadMark":false,
           "parentId":null,"subCategoryList":null,"appManageInfoList":[],
           "appList":[
             {"appId":"4714986430127691","appName":"校园文化活动","description":"活动",
              "vendorName":"金智教育","hasPermission":true,"maintaining":false,"isNew":0}
           ]}
        ],
        "hasLogin":false,"contextPath":""}
        """.trimIndent()
        val list = json.decodeFromString(serializer, raw)
        assertFalse(list.hasLogin)

        val services = list.toServices()
        assertEquals(1, services.size)
        val app = services.first()
        assertEquals("4714986430127691", app.id)
        assertEquals("校园文化活动", app.name)
        assertEquals("活动", app.description)
        assertEquals("平台基础", app.category)
        assertTrue(app.hasPermission)
        assertFalse(app.maintaining)
    }

    @Test
    fun `推荐位里的重复条目不双计`() {
        // 上面那条真实响应就是同一条目同时出现在 hotRecNewAppList 与 categoryList.appList，
        // 展平只按 appId 去重，与顺序无关。
        val dto = EhallAppDto(appId = "a1", appName = "同一服务")
        val list = EhallAppListDto(
            hasLogin = true,
            hotRecNewAppList = listOf(dto),
            categoryList = listOf(
                EhallCategoryDto(categoryName = "教学", appList = listOf(dto)),
                EhallCategoryDto(categoryName = "科研", appList = listOf(dto.copy())),
            ),
        )
        val services = list.toServices()
        assertEquals(1, services.size)
        assertEquals("教学", services.first().category)
    }

    @Test
    fun `两级分类用叶子分类名，父级自己没条目不占位`() {
        // subCategoryList 在真实样本里是 null（青大当前只有一级），这条按厂商分类树的结构构造。
        val list = EhallAppListDto(
            hasLogin = true,
            categoryList = listOf(
                EhallCategoryDto(
                    categoryName = "师生服务",
                    subCategoryList = listOf(
                        EhallCategoryDto(
                            categoryName = "后勤",
                            appList = listOf(EhallAppDto(appId = "b2", appName = "报修")),
                        ),
                    ),
                ),
            ),
        )
        val services = list.toServices()
        assertEquals(1, services.size)
        assertEquals("后勤", services.first().category)
        assertEquals("报修", services.first().name)
    }

    @Test
    fun `缺 id 或缺名称的条目被丢弃`() {
        val list = EhallAppListDto(
            hasLogin = true,
            categoryList = listOf(
                EhallCategoryDto(
                    categoryName = "综合",
                    appList = listOf(
                        EhallAppDto(appId = null, appName = "没有 id"),
                        EhallAppDto(appId = "  ", appName = "空白 id"),
                        EhallAppDto(appId = "c3", appName = null),
                        EhallAppDto(appId = "c4", appName = "  "),
                        EhallAppDto(appId = "c5", appName = "有效条目"),
                    ),
                ),
            ),
        )
        val services = list.toServices()
        assertEquals(1, services.size)
        assertEquals("c5", services.first().id)
        // 描述与厂商名都没给时降级为空串，界面据此不显示副标题
        assertEquals("", services.first().description)
    }

    @Test
    fun `描述与名称同名时退回厂商名`() {
        val list = EhallAppListDto(
            hasLogin = true,
            categoryList = listOf(
                EhallCategoryDto(
                    categoryName = "综合",
                    appList = listOf(
                        EhallAppDto(
                            appId = "d6",
                            appName = "校园卡",
                            description = "校园卡",
                            vendorName = "金智教育",
                        ),
                    ),
                ),
            ),
        )
        assertEquals("金智教育", list.toServices().first().description)
    }

    @Test
    fun `权限与维护标志缺失时按可见可用作默认`() {
        // 厂商演示数据里的条目形如 hasPermission:false、maintaining 缺失，
        // 两个标志都不能因为缺字段而变成「不可用」。
        val app = json.decodeFromString(
            EhallAppDto.serializer(),
            """{"appId":"4628660626845040","appName":"域名申请系统1","hasPermission":false,"status":1}""",
        )
        val list = EhallAppListDto(
            hasLogin = true,
            categoryList = listOf(EhallCategoryDto(categoryName = "公共服务", appList = listOf(app))),
        )
        val service = list.toServices().first()
        assertFalse(service.hasPermission)
        assertFalse(service.maintaining)
        assertEquals("", service.description)
    }

    @Test
    fun `厂商演示响应里的未知键不影响解析`() {
        // 苏迪演示环境的一条完整对象（含 categoryList 嵌套、卡片列表、时间戳字符串），
        // 真机改版后多出来的键必须继续被忽略。
        val list = json.decodeFromString(
            serializer,
            """{"hotRecNewAppList":[],"categoryList":[],"hasLogin":true,
               "contextPath":"/","unknownWidget":{"a":[1,2]},"desktopId":"default"}""",
        )
        assertTrue(list.hasLogin)
        assertTrue(list.toServices().isEmpty())
    }

    @Test
    fun `任务计数按真实 JSONP 载荷解析`() {
        // 2026-11-04 登录后在门户里录到的原样响应，三个数依次待办、已办、流程跟踪。
        val counts = parseEhallTaskCounts("amp_jsonp_callback([0,0,0])")
        assertEquals(EhallTaskCounts(pending = 0, done = 0, tracked = 0), counts)
        assertFalse(counts?.hasWork ?: true)
    }

    @Test
    fun `任务计数有值时待办与流程跟踪才算有事`() {
        val counts = parseEhallTaskCounts("amp_jsonp_callback([ 2 , \"3\" , 1 ])")
        assertEquals(2, counts?.pending)
        assertEquals(3, counts?.done)
        assertEquals(1, counts?.tracked)
        assertTrue(counts?.hasWork ?: false)

        // 只有已办时门户不显示角标，hasWork 也必须跟着为假
        assertFalse(parseEhallTaskCounts("amp_jsonp_callback([0,7,0])")?.hasWork ?: true)
    }

    @Test
    fun `任务计数的坏载荷一律当作没取到`() {
        // 不带 callback 请求同一条地址，服务端回的就是一页 HTML，必须解析成 null 而不是 0。
        assertNull(parseEhallTaskCounts("<html><body>系统异常</body></html>"))
        assertNull(parseEhallTaskCounts(""))
        assertNull(parseEhallTaskCounts("amp_jsonp_callback()"))
        assertNull(parseEhallTaskCounts("amp_jsonp_callback({\"a\":1})"))
        assertNull(parseEhallTaskCounts("amp_jsonp_callback([1,2])"))
        assertNull(parseEhallTaskCounts("amp_jsonp_callback([null,2,3])"))
        assertNull(parseEhallTaskCounts("amp_jsonp_callback([[1],2,3])"))
    }
}
