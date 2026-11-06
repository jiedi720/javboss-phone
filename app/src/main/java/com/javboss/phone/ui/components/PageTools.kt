package com.javboss.phone.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * 页面工具条（视角切换 / 排序 / 搜索）的开关状态。
 *
 * v0.3.50 起这套状态统一提到 MainScreen：
 * - Compact（手机竖屏）由页面顶部那一栏按钮读写；
 * - Rail（平板 / 手机横屏 / 宽自由窗口）由原左侧 NavigationRail 里的工具项读写。
 *
 * 两处读写的是同一份状态，所以旋转、分屏、自由窗口在两种导航形式之间切换时，
 * 搜索框是否展开、搜索词、排序菜单是否打开都能原样保留，不会闪一下重置。
 * 状态用 remember 挂在 MainScreen 上：项目已声明 configChanges 不重建 Activity，
 * 转屏时整棵 composition 不销毁，remember 已足够。
 */
class PageToolsState {
    /** JAV 页搜索框是否展开 */
    var worksSearchOpen by mutableStateOf(false)

    /** JAV 页排序 / 筛选底部弹层是否打开 */
    var worksSortOpen by mutableStateOf(false)

    /** 视频页搜索框是否展开 */
    var videosSearchOpen by mutableStateOf(false)

    /** 视频页排序底部弹层是否打开 */
    var videosSortOpen by mutableStateOf(false)

    /** 切换主 tab 时关掉瞬时弹层，避免切回来莫名弹出一个排序菜单 */
    fun closeSheets() {
        worksSortOpen = false
        videosSortOpen = false
    }
}

@Composable
fun rememberPageToolsState(): PageToolsState = remember { PageToolsState() }
