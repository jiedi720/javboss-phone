package com.javboss.phone.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 锁屏触发的「临时打码」开关。
 *
 * 只活在内存里，刻意不持久化：
 * - App 用着用着手机锁屏 → 打码；解锁后**继续保持**打码，避免一解锁就看到满屏作品封面；
 * - 用户点列表顶部「显示封面」即恢复；
 * - 从桌面冷启动 App（`MainActivity.onCreate` 且 `savedInstanceState == null`）不继承，
 *   所以「普通打开」永远不会莫名其妙打码。
 *
 * 与设置页的「封面毛玻璃」是或的关系：设置常开时无视本状态，一直打码。
 */
object PrivacyMask {

    private val _locked = MutableStateFlow(false)

    /** true = 因为刚刚锁过屏，封面正在自动打码 */
    val locked: StateFlow<Boolean> = _locked

    /** 手机锁屏（屏幕关闭）时调用 */
    fun onScreenLocked() {
        _locked.value = true
    }

    /** 用户主动恢复显示，或从桌面重新冷启动 App */
    fun reveal() {
        _locked.value = false
    }
}
