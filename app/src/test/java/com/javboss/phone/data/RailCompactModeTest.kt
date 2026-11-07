package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.51：左侧栏「紧凑模式」显示文字的纯规则单测。
 * 紧凑模式开启后一律只画图标；关闭时沿用「Expanded 才常驻文字」的旧规则。
 */
class RailCompactModeTest {

    @Test
    fun `compact mode hides labels at every window width`() {
        listOf(400, 599, 600, 839, 840, 959, 1200, 1600).forEach { width ->
            assertFalse(
                "紧凑模式在 ${width}dp 仍显示文字",
                WindowSizeRules.railShowLabel(width, true),
            )
        }
    }

    @Test
    fun `normal mode keeps the width based rule`() {
        assertFalse(WindowSizeRules.railShowLabel(400, false))
        assertFalse(WindowSizeRules.railShowLabel(600, false))
        assertFalse(WindowSizeRules.railShowLabel(839, false))
        assertTrue(WindowSizeRules.railShowLabel(840, false))
        assertTrue(WindowSizeRules.railShowLabel(1200, false))
    }

    @Test
    fun `label boundary stays at 840dp`() {
        assertFalse(WindowSizeRules.railShowLabel(839, false))
        assertTrue(WindowSizeRules.railShowLabel(840, false))
        assertEquals(false, WindowSizeRules.railShowsLabel(839))
        assertEquals(true, WindowSizeRules.railShowsLabel(840))
    }

    @Test
    fun `compact mode never turns labels back on`() {
        assertFalse(WindowSizeRules.railShowLabel(2000, true))
        assertFalse(WindowSizeRules.railShowLabel(Int.MAX_VALUE, true))
    }

    // v0.3.52：紧凑模式不只是隐藏文字，还必须把按钮高度真正压下来，
    // 让图标上移、一屏放得下全部按钮；非紧凑模式保持 44 / 40dp 旧值。
    @Test
    fun `compact mode collapses item height for tabs and tools alike`() {
        assertEquals(
            WindowSizeRules.RAIL_COMPACT_ITEM_DP,
            WindowSizeRules.railItemHeightDp(true, true),
        )
        assertEquals(
            WindowSizeRules.RAIL_COMPACT_ITEM_DP,
            WindowSizeRules.railItemHeightDp(false, true),
        )
    }

    @Test
    fun `normal mode keeps the original item heights`() {
        assertEquals(44, WindowSizeRules.railItemHeightDp(true, false))
        assertEquals(40, WindowSizeRules.railItemHeightDp(false, false))
    }

    @Test
    fun `compact item is always shorter than the normal one`() {
        assertTrue(
            WindowSizeRules.railItemHeightDp(true, true) <
                WindowSizeRules.railItemHeightDp(true, false),
        )
        assertTrue(
            WindowSizeRules.railItemHeightDp(false, true) <
                WindowSizeRules.railItemHeightDp(false, false),
        )
        // 32dp 足够小：横屏 640dp 窗口里 4 个主 tab + 6 个工具项也远放得下
        assertTrue(WindowSizeRules.RAIL_COMPACT_ITEM_DP * 10 < 640)
    }
}
