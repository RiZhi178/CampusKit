package qdu.campus.platform.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 外部入口的统一出口：scheme、应用商店与复制兜底。 */
@Singleton
class ExternalLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun openUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /** 先按包名直达，失败后跳应用商店搜索，再失败返回 false 由调用方提示。 */
    fun openAppOrStore(packageName: String?, appName: String): Boolean {
        if (!packageName.isNullOrBlank()) {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(launch) }.isSuccess) return true
            }
        }
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://search?q=$appName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(market) }.isSuccess) return true
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://appgallery.huawei.com/search/$appName"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(web) }.isSuccess
    }

    fun openWeChat(): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage("com.tencent.mm")
            ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launch) }.isSuccess
    }

    /**
     * 带包名把一个 URI 交给指定应用处理（扫码转交用），失败返回 false 由调用方兜底。
     *
     * 必须带 `CATEGORY_BROWSABLE`：http(s) 链接能进微信/支付宝内嵌浏览器的匹配项
     * 都声明了这个类别，缺了它定向转交永远 ActivityNotFoundException（2026-11-06
     * 真机实测踩坑，见 docs/scan-slice.md 第三节），导致白白掉进内嵌 WebView 兜底。
     */
    fun openUriWithApp(uri: String, packageName: String): Boolean {
        if (uri.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(packageName)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * 系统应用选择器：列出设备上所有能打开该 URI 的应用（微信、支付宝、各浏览器），
     * 由用户点选。扫码页不再内嵌 WebView（明文 http 被 cleartext 拦、厂商短链被
     * UA 门禁拦，内嵌打开设备码没有一条走得通），定向转交都失败时落到这里。
     */
    fun openChooser(uri: String, title: String): Boolean {
        if (uri.isBlank()) return false
        val target = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return runCatching {
            context.startActivity(
                Intent.createChooser(target, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    fun openAlipay(): Boolean {
        val launch = context.packageManager
            .getLaunchIntentForPackage("com.eg.android.AlipayGphone")
            ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launch) }.isSuccess
    }

    /**
     * 拉起指定应用的主界面（学习类码转交用：扫码结果的签到动作必须发生在
     * 对方应用的登录会话里，能给的只是「回到那个 App」这一步）。
     */
    fun openApp(packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launch) }.isSuccess
    }

    fun copyToClipboard(text: String, label: String) {
        if (text.isBlank()) return
        val manager = context.getSystemService(ClipboardManager::class.java)
        manager?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制 $label", Toast.LENGTH_SHORT).show()
    }

    fun shareText(text: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
