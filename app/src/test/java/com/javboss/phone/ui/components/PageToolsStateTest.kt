package com.javboss.phone.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.50：页面工具开关提升到 MainScreen 后的状态机单测。
 * 顶部按钮（Compact）与原左侧栏（Rail）读写同一份 PageToolsState，
 * 这里锁死「切主 tab 只关弹层、不动搜索框」这条规则。
 */
class PageToolsStateTest {

    @Test
    fun `all tools start closed`() {
        val tools = PageToolsState()
        assertFalse(tools.worksSearchOpen)
        assertFalse(tools.worksSortOpen)
        assertFalse(tools.videosSearchOpen)
        assertFalse(tools.videosSortOpen)
    }

    @Test
    fun `works and videos switches are independent`() {
        val tools = PageToolsState()
        tools.worksSearchOpen = true
        tools.videosSortOpen = true
        assertTrue(tools.worksSearchOpen)
        assertTrue(tools.videosSortOpen)
        assertFalse(tools.worksSortOpen)
        assertFalse(tools.videosSearchOpen)
    }

    @Test
    fun `closing sheets keeps search fields open`() {
        val tools = PageToolsState()
        tools.worksSortOpen = true
        tools.videosSortOpen = true
        tools.worksSearchOpen = true
        tools.videosSearchOpen = true
        tools.closeSheets()
        assertFalse(tools.worksSortOpen)
        assertFalse(tools.videosSortOpen)
        assertTrue(tools.worksSearchOpen)
        assertTrue(tools.videosSearchOpen)
    }

    @Test
    fun `sheets can be opened again after closeSheets`() {
        val tools = PageToolsState()
        tools.worksSortOpen = true
        tools.closeSheets()
        tools.worksSortOpen = true
        assertTrue(tools.worksSortOpen)
    }
}
