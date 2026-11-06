package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.49：JAV 网格列数按「竖屏 / 横屏」分开设定的纯逻辑单测。
 *
 * 用户诉求：竖屏最多 3 列、横屏最多 6 列，自动档默认竖 3 / 横 6；
 * 竖屏下拉里不许出现 5/6 这种放不下的选项，横屏下拉里不许出现 2/3；
 * 下拉里能选 N 列，就必须真的排 N 列，不能被收敛成别的值。
 */
class ColumnChoiceTest {

    @Test
    fun `auto defaults are three portrait and six landscape`() {
        assertEquals(3, WindowSizeRules.AUTO_PORTRAIT_COLUMNS)
        assertEquals(6, WindowSizeRules.AUTO_LANDSCAPE_COLUMNS)
        assertEquals(3, WindowSizeRules.autoColumns(landscape = false))
        assertEquals(6, WindowSizeRules.autoColumns(landscape = true))
        assertEquals(
            WindowSizeRules.AUTO_PORTRAIT_COLUMNS * 2,
            WindowSizeRules.AUTO_LANDSCAPE_COLUMNS,
        )
    }

    @Test
    fun `choices are split by orientation and never offer unusable counts`() {
        assertEquals(listOf(0, 2, 3), WindowSizeRules.PORTRAIT_COLUMN_CHOICES)
        assertEquals(listOf(0, 5, 6), WindowSizeRules.LANDSCAPE_COLUMN_CHOICES)
        // 竖屏下拉里没有 5/6，横屏下拉里没有 2/3
        WindowSizeRules.PORTRAIT_COLUMN_CHOICES.forEach {
            assertTrue("竖屏不该出现 $it 列", it <= 3)
        }
        WindowSizeRules.LANDSCAPE_COLUMN_CHOICES.forEach {
            assertTrue("横屏不该出现 $it 列", it == 0 || it >= 5)
        }
    }

    @Test
    fun `resolve column choice maps auto to the orientation default`() {
        assertEquals(3, WindowSizeRules.resolveColumnChoice(0, false))
        assertEquals(6, WindowSizeRules.resolveColumnChoice(0, true))
        assertEquals(2, WindowSizeRules.resolveColumnChoice(2, false))
        assertEquals(3, WindowSizeRules.resolveColumnChoice(3, false))
        assertEquals(5, WindowSizeRules.resolveColumnChoice(5, true))
        assertEquals(6, WindowSizeRules.resolveColumnChoice(6, true))
    }

    @Test
    fun `valid choices are orientation specific`() {
        // 自动档两个方向都合法
        assertTrue(WindowSizeRules.isValidColumnChoice(0, false))
        assertTrue(WindowSizeRules.isValidColumnChoice(0, true))
        // 竖屏 2/3
        assertTrue(WindowSizeRules.isValidColumnChoice(2, false))
        assertTrue(WindowSizeRules.isValidColumnChoice(3, false))
        assertFalse(WindowSizeRules.isValidColumnChoice(2, true))
        assertFalse(WindowSizeRules.isValidColumnChoice(3, true))
        // 横屏 5/6
        assertTrue(WindowSizeRules.isValidColumnChoice(5, true))
        assertTrue(WindowSizeRules.isValidColumnChoice(6, true))
        assertFalse(WindowSizeRules.isValidColumnChoice(5, false))
        assertFalse(WindowSizeRules.isValidColumnChoice(6, false))
        // 越界值一律非法（旧版单列数设置里的 4/7/8 等）
        listOf(-1, 1, 4, 7, 8, 99).forEach {
            assertFalse("$it 不该合法(竖屏)", WindowSizeRules.isValidColumnChoice(it, false))
            assertFalse("$it 不该合法(横屏)", WindowSizeRules.isValidColumnChoice(it, true))
        }
    }

    @Test
    fun `legacy single grid_columns migrates into both orientations`() {
        assertEquals(0, WindowSizeRules.migrateColumnChoice(0, false))
        assertEquals(0, WindowSizeRules.migrateColumnChoice(0, true))
        assertEquals(0, WindowSizeRules.migrateColumnChoice(-3, false))
        // 旧 2 列 -> 竖屏 2、横屏 5（就近取该方向合法值）
        assertEquals(2, WindowSizeRules.migrateColumnChoice(2, false))
        assertEquals(5, WindowSizeRules.migrateColumnChoice(2, true))
        // 旧 3 / 4 列 -> 竖屏 3、横屏 5
        assertEquals(3, WindowSizeRules.migrateColumnChoice(3, false))
        assertEquals(5, WindowSizeRules.migrateColumnChoice(3, true))
        assertEquals(3, WindowSizeRules.migrateColumnChoice(4, false))
        assertEquals(5, WindowSizeRules.migrateColumnChoice(4, true))
        // 旧 5..8 列 -> 竖屏统一钳到 3、横屏钳到 5/6
        assertEquals(3, WindowSizeRules.migrateColumnChoice(6, false))
        assertEquals(3, WindowSizeRules.migrateColumnChoice(8, false))
        assertEquals(5, WindowSizeRules.migrateColumnChoice(5, true))
        assertEquals(6, WindowSizeRules.migrateColumnChoice(6, true))
        assertEquals(6, WindowSizeRules.migrateColumnChoice(8, true))
    }

    @Test
    fun `every migrated value is legal for that orientation`() {
        for (old in -2..10) {
            val portrait = WindowSizeRules.migrateColumnChoice(old, false)
            val landscape = WindowSizeRules.migrateColumnChoice(old, true)
            assertTrue(
                "old=$old 竖屏迁移值 $portrait 非法",
                WindowSizeRules.isValidColumnChoice(portrait, false),
            )
            assertTrue(
                "old=$old 横屏迁移值 $landscape 非法",
                WindowSizeRules.isValidColumnChoice(landscape, true),
            )
        }
    }

    @Test
    fun `auto choice really yields three portrait and six landscape columns on a pad`() {
        // Pad 7 Pro 竖屏约 712dp（Medium，Rail 64dp）/ 横屏约 1067dp（Expanded，Rail 64dp）
        val portraitTarget = WindowSizeRules.resolveColumnChoice(0, false)
        val landscapeTarget = WindowSizeRules.resolveColumnChoice(0, true)
        val portrait = WindowSizeRules.gridColumns(
            712,
            reservedDp = WindowSizeRules.RAIL_WIDTH_DP,
            choice = portraitTarget,
        )
        val landscape = WindowSizeRules.gridColumns(
            1067,
            reservedDp = WindowSizeRules.RAIL_WIDTH_DP,
            choice = landscapeTarget,
        )
        assertEquals(3, portrait)
        assertEquals(6, landscape)
        // 视频仍恒少一列：竖屏 3->2，横屏 6->5
        assertEquals(2, WindowSizeRules.videoGridColumns(portrait))
        assertEquals(5, WindowSizeRules.videoGridColumns(landscape))
    }

    @Test
    fun `every offered choice actually renders that many columns`() {
        // 用户诉求核心：下拉里能选 N 列，就必须真的显示 N 列，不能被收敛
        for (landscape in listOf(false, true)) {
            val width = if (landscape) 1067 else 712
            val choices = if (landscape) {
                WindowSizeRules.LANDSCAPE_COLUMN_CHOICES
            } else {
                WindowSizeRules.PORTRAIT_COLUMN_CHOICES
            }
            for (choice in choices) {
                val target = WindowSizeRules.resolveColumnChoice(choice, landscape)
                val actual = WindowSizeRules.gridColumns(
                    width,
                    reservedDp = WindowSizeRules.RAIL_WIDTH_DP,
                    choice = target,
                )
                assertEquals(
                    "landscape=$landscape choice=$choice 应真的排 $target 列",
                    target,
                    actual,
                )
            }
        }
    }

    @Test
    fun `phone portrait auto shows three jav and two video columns`() {
        // 手机竖屏 360dp：Compact 无 Rail
        val target = WindowSizeRules.resolveColumnChoice(0, false)
        val jav = WindowSizeRules.gridColumns(360, choice = target)
        assertEquals(3, jav)
        assertEquals(2, WindowSizeRules.videoGridColumns(jav))
    }
}
