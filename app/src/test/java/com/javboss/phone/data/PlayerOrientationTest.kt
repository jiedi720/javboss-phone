package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.57 播放页方向锁定规则。
 *
 * 关键回归：方向只看「进入播放页那一刻」冻结的 Compact 判定，函数签名里
 * 根本没有「当前窗口宽度」这个入参，因此旋转 / 分屏 / 自由窗口让宽度变大后
 * 也不可能把方向改回 UNSPECIFIED，竖屏点全屏不再反复拧回竖屏。
 */
class PlayerOrientationTest {

    @Test
    fun `compact portrait window locks to portrait`() {
        assertEquals(
            PlayerOrientation.PORTRAIT,
            WindowSizeRules.playerOrientation(fullscreen = false, compactAtEntry = true),
        )
    }

    @Test
    fun `compact fullscreen locks to sensor landscape`() {
        assertEquals(
            PlayerOrientation.SENSOR_LANDSCAPE,
            WindowSizeRules.playerOrientation(fullscreen = true, compactAtEntry = true),
        )
    }

    @Test
    fun `exiting fullscreen on compact window returns to portrait`() {
        assertEquals(PlayerOrientation.SENSOR_LANDSCAPE,
            WindowSizeRules.playerOrientation(fullscreen = true, compactAtEntry = true))
        assertEquals(PlayerOrientation.PORTRAIT,
            WindowSizeRules.playerOrientation(fullscreen = false, compactAtEntry = true))
    }

    @Test
    fun `wide window never forces orientation either way`() {
        // 平板横屏 / 宽自由窗口：全屏沉浸与竖屏小窗都不强制方向，
        // 分屏、自由窗口缩放因此不会被拧成竖屏。
        assertEquals(
            PlayerOrientation.UNSPECIFIED,
            WindowSizeRules.playerOrientation(fullscreen = true, compactAtEntry = false),
        )
        assertEquals(
            PlayerOrientation.UNSPECIFIED,
            WindowSizeRules.playerOrientation(fullscreen = false, compactAtEntry = false),
        )
    }

    @Test
    fun `orientation stays landscape after rotation because compact is frozen at entry`() {
        // 复现抖环场景：手机竖屏进播放页（400dp → Compact），点全屏转横屏后
        // 窗口变成 900dp（Medium），但方向决策只吃冻结值，仍锁感应横屏。
        val entryWidthDp = 400
        val compactAtEntry = WindowSizeRules.isCompact(entryWidthDp)
        assertTrue(compactAtEntry)

        val rotatedWidthDp = 900
        assertFalse(WindowSizeRules.isCompact(rotatedWidthDp))

        assertEquals(
            PlayerOrientation.SENSOR_LANDSCAPE,
            WindowSizeRules.playerOrientation(fullscreen = true, compactAtEntry = compactAtEntry),
        )
        // 旧实现正是拿旋转后的宽度重新分级：compact=false → UNSPECIFIED → 系统拧回竖屏。
        // 用当前宽度重算会得到 UNSPECIFIED，与冻结值的结果不同，正是被修掉的抖环。
        assertEquals(
            PlayerOrientation.UNSPECIFIED,
            WindowSizeRules.playerOrientation(
                fullscreen = true,
                compactAtEntry = WindowSizeRules.isCompact(rotatedWidthDp),
            ),
        )
    }

    @Test
    fun `compact classification thresholds still hold`() {
        assertTrue(WindowSizeRules.isCompact(WindowSizeRules.COMPACT_MAX_DP - 1))
        assertFalse(WindowSizeRules.isCompact(WindowSizeRules.COMPACT_MAX_DP))
        assertFalse(WindowSizeRules.isCompact(WindowSizeRules.EXPANDED_MIN_DP))
    }
}
