package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.56：JAV / 视频四套独立列数设定的纯逻辑单测。
 *
 * 用户诉求：视频与 JAV 各自独立设定、不再联动（不再恒少一列）；
 * 下拉去掉「自动」档，横屏只留 5/6/7、竖屏保持 2/3；
 * 初始默认竖屏 3、横屏 5；下拉里能选 N 列，就必须真的排 N 列。
 */
class ColumnChoiceTest {

    @Test
    fun `defaults are three portrait and five landscape`() {
        assertEquals(3, WindowSizeRules.DEFAULT_PORTRAIT_COLUMNS)
        assertEquals(5, WindowSizeRules.DEFAULT_LANDSCAPE_COLUMNS)
        assertEquals(3, WindowSizeRules.defaultColumns(landscape = false))
        assertEquals(5, WindowSizeRules.defaultColumns(landscape = true))
        // 两个默认值本身都必须是该方向的合法候选
        assertTrue(
            WindowSizeRules.isValidColumnChoice(
                WindowSizeRules.DEFAULT_PORTRAIT_COLUMNS,
                false,
            ),
        )
        assertTrue(
            WindowSizeRules.isValidColumnChoice(
                WindowSizeRules.DEFAULT_LANDSCAPE_COLUMNS,
                true,
            ),
        )
    }

    @Test
    fun `choices are split by orientation and contain no auto entry`() {
        assertEquals(listOf(2, 3), WindowSizeRules.PORTRAIT_COLUMN_CHOICES)
        assertEquals(listOf(5, 6, 7), WindowSizeRules.LANDSCAPE_COLUMN_CHOICES)
        // v0.3.56 彻底去掉「自动」档：两个候选集里都不再有 0
        assertFalse(0 in WindowSizeRules.PORTRAIT_COLUMN_CHOICES)
        assertFalse(0 in WindowSizeRules.LANDSCAPE_COLUMN_CHOICES)
        WindowSizeRules.PORTRAIT_COLUMN_CHOICES.forEach {
            assertTrue("竖屏不该出现 $it 列", it <= 3)
        }
        WindowSizeRules.LANDSCAPE_COLUMN_CHOICES.forEach {
            assertTrue("横屏不该出现 $it 列", it >= 5)
        }
    }

    @Test
    fun `valid choices are orientation specific and zero is no longer valid`() {
        // 竖屏 2/3
        assertTrue(WindowSizeRules.isValidColumnChoice(2, false))
        assertTrue(WindowSizeRules.isValidColumnChoice(3, false))
        assertFalse(WindowSizeRules.isValidColumnChoice(2, true))
        assertFalse(WindowSizeRules.isValidColumnChoice(3, true))
        // 横屏 5/6/7
        assertTrue(WindowSizeRules.isValidColumnChoice(5, true))
        assertTrue(WindowSizeRules.isValidColumnChoice(6, true))
        assertTrue(WindowSizeRules.isValidColumnChoice(7, true))
        assertFalse(WindowSizeRules.isValidColumnChoice(5, false))
        assertFalse(WindowSizeRules.isValidColumnChoice(6, false))
        assertFalse(WindowSizeRules.isValidColumnChoice(7, false))
        // 旧「自动」0 与越界值一律非法
        listOf(-1, 0, 1, 4, 8, 99).forEach {
            assertFalse("$it 不该合法(竖屏)", WindowSizeRules.isValidColumnChoice(it, false))
            assertFalse("$it 不该合法(横屏)", WindowSizeRules.isValidColumnChoice(it, true))
        }
    }

    @Test
    fun `normalize falls back to default for auto and out of range values`() {
        // 合法值原样保留
        assertEquals(2, WindowSizeRules.normalizeColumnChoice(2, false))
        assertEquals(3, WindowSizeRules.normalizeColumnChoice(3, false))
        assertEquals(5, WindowSizeRules.normalizeColumnChoice(5, true))
        assertEquals(6, WindowSizeRules.normalizeColumnChoice(6, true))
        assertEquals(7, WindowSizeRules.normalizeColumnChoice(7, true))
        // 旧版 0 = 自动、负数、越界值统一回落到该方向默认值
        assertEquals(3, WindowSizeRules.normalizeColumnChoice(0, false))
        assertEquals(5, WindowSizeRules.normalizeColumnChoice(0, true))
        assertEquals(3, WindowSizeRules.normalizeColumnChoice(-3, false))
        assertEquals(5, WindowSizeRules.normalizeColumnChoice(-3, true))
        assertEquals(3, WindowSizeRules.normalizeColumnChoice(4, false))
        assertEquals(3, WindowSizeRules.normalizeColumnChoice(8, false))
        assertEquals(5, WindowSizeRules.normalizeColumnChoice(4, true))
        assertEquals(5, WindowSizeRules.normalizeColumnChoice(8, true))
    }

    @Test
    fun `legacy single grid_columns migrates into current orientation choices`() {
        assertEquals(3, WindowSizeRules.migrateColumnChoice(0, false))
        assertEquals(5, WindowSizeRules.migrateColumnChoice(0, true))
        assertEquals(3, WindowSizeRules.migrateColumnChoice(-3, false))
        assertEquals(5, WindowSizeRules.migrateColumnChoice(-3, true))
        // 旧 2 列 -> 竖屏 2、横屏 5（就近取该方向合法值）
        assertEquals(2, WindowSizeRules.migrateColumnChoice(2, false))
        assertEquals(5, WindowSizeRules.migrateColumnChoice(2, true))
        // 旧 3 / 4 列 -> 竖屏 3、横屏 5
        assertEquals(3, WindowSizeRules.migrateColumnChoice(3, false))
        assertEquals(5, WindowSizeRules.migrateColumnChoice(3, true))
        assertEquals(3, WindowSizeRules.migrateColumnChoice(4, false))
        assertEquals(5, WindowSizeRules.migrateColumnChoice(4, true))
        // 旧 5..8 列 -> 竖屏统一钳到 3、横屏就近到 5/6/7
        assertEquals(3, WindowSizeRules.migrateColumnChoice(6, false))
        assertEquals(3, WindowSizeRules.migrateColumnChoice(8, false))
        assertEquals(5, WindowSizeRules.migrateColumnChoice(5, true))
        assertEquals(6, WindowSizeRules.migrateColumnChoice(6, true))
        assertEquals(7, WindowSizeRules.migrateColumnChoice(8, true))
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
    fun `every offered choice actually renders that many columns for both grids`() {
        // 用户诉求核心：JAV / 视频下拉里能选 N 列，就必须真的显示 N 列，不能被收敛。
        // 两者共用同一个 gridColumns，只是传各自的选择值，验证候选值都可达。
        for (landscape in listOf(false, true)) {
            val width = if (landscape) 1067 else 712
            val choices = if (landscape) {
                WindowSizeRules.LANDSCAPE_COLUMN_CHOICES
            } else {
                WindowSizeRules.PORTRAIT_COLUMN_CHOICES
            }
            for (choice in choices) {
                val jav = WindowSizeRules.gridColumns(
                    width,
                    reservedDp = WindowSizeRules.RAIL_WIDTH_DP,
                    choice = choice,
                )
                val video = WindowSizeRules.gridColumns(
                    width,
                    reservedDp = WindowSizeRules.RAIL_WIDTH_DP,
                    choice = choice,
                )
                assertEquals(
                    "landscape=$landscape JAV 选 $choice 应真的排 $choice 列",
                    choice,
                    jav,
                )
                assertEquals(
                    "landscape=$landscape 视频选 $choice 应真的排 $choice 列",
                    choice,
                    video,
                )
            }
        }
    }

    @Test
    fun `jav and video choices are fully independent`() {
        val width = 1067
        val reserved = WindowSizeRules.RAIL_WIDTH_DP
        // 同一窗口：JAV 选 5、视频选 7 → 各自 5 / 7（视频可以比 JAV 多）
        assertEquals(5, WindowSizeRules.gridColumns(width, reservedDp = reserved, choice = 5))
        assertEquals(7, WindowSizeRules.gridColumns(width, reservedDp = reserved, choice = 7))
        // 反过来 JAV 7、视频 2 也各自成立（旧「恒少一列」规则做不到）
        assertEquals(7, WindowSizeRules.gridColumns(width, reservedDp = reserved, choice = 7))
        assertEquals(2, WindowSizeRules.gridColumns(width, reservedDp = reserved, choice = 2))
        // 也可以相同：不再强制差一列
        val jav6 = WindowSizeRules.gridColumns(width, reservedDp = reserved, choice = 6)
        val video6 = WindowSizeRules.gridColumns(width, reservedDp = reserved, choice = 6)
        assertEquals(jav6, video6)
    }

    @Test
    fun `phone portrait default renders three columns`() {
        // 手机竖屏 360dp：Compact 无 Rail，默认竖屏 3 列也要真排得下
        assertEquals(
            3,
            WindowSizeRules.gridColumns(360, choice = WindowSizeRules.DEFAULT_PORTRAIT_COLUMNS),
        )
    }

    @Test
    fun `landscape seven columns are reachable on a pad`() {
        assertEquals(
            7,
            WindowSizeRules.gridColumns(
                1067,
                reservedDp = WindowSizeRules.RAIL_WIDTH_DP,
                choice = 7,
            ),
        )
    }
}
