package qdu.campus.platform.common

import kotlinx.coroutines.delay

/**
 * 每分钟对齐整点触发一次。
 *
 * 页面上「此刻在上哪节课」「这个时段还能不能约」都只跟分钟有关，本机时钟自己就会走，
 * 不该等网络回包才更新；靠整点对齐还能让多个页面在同一刻一起翻档。
 * 作用域取消时由 [delay] 抛出取消异常退出循环。
 */
suspend fun everyMinute(action: suspend () -> Unit) {
    while (true) {
        delay(Formats.millisUntilNextMinute())
        action()
    }
}
