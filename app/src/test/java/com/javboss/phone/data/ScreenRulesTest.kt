package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRulesTest {

    @Test
    fun `smallest width 600dp is the tablet boundary`() {
        assertFalse(ScreenRules.isTablet(0))
        assertFalse(ScreenRules.isTablet(599))
        assertTrue(ScreenRules.isTablet(600))
        assertTrue(ScreenRules.isTablet(720))
    }

    @Test
    fun `automatic columns double in landscape`() {
        // 手机竖屏 2 → 横屏 4；平板竖屏 4 → 横屏 8
        assertEquals(2, ScreenRules.autoColumns(isTablet = false, landscape = false))
        assertEquals(4, ScreenRules.autoColumns(isTablet = false, landscape = true))
        assertEquals(4, ScreenRules.autoColumns(isTablet = true, landscape = false))
        assertEquals(8, ScreenRules.autoColumns(isTablet = true, landscape = true))
    }

    @Test
    fun `portrait baseline is phone 2 and tablet 4`() {
        assertEquals(2, ScreenRules.portraitColumns(ScreenRules.COLUMNS_AUTO, false))
        assertEquals(4, ScreenRules.portraitColumns(ScreenRules.COLUMNS_AUTO, true))
    }

    @Test
    fun `choice 0 resolves to automatic columns`() {
        assertEquals(2, ScreenRules.resolveColumns(ScreenRules.COLUMNS_AUTO, false, false))
        assertEquals(4, ScreenRules.resolveColumns(ScreenRules.COLUMNS_AUTO, false, true))
        assertEquals(4, ScreenRules.resolveColumns(ScreenRules.COLUMNS_AUTO, true, false))
        assertEquals(8, ScreenRules.resolveColumns(ScreenRules.COLUMNS_AUTO, true, true))
    }

    @Test
    fun `manual choice is the portrait baseline and doubles in landscape`() {
        assertEquals(3, ScreenRules.resolveColumns(3, false, false))
        assertEquals(6, ScreenRules.resolveColumns(3, false, true))
        assertEquals(3, ScreenRules.resolveColumns(3, true, false))
        assertEquals(6, ScreenRules.resolveColumns(3, true, true))
        // 手选 6 列竖屏不变，横屏翻倍后受上限 8 约束
        assertEquals(6, ScreenRules.resolveColumns(6, true, false))
        assertEquals(8, ScreenRules.resolveColumns(6, true, true))
    }

    @Test
    fun `landscape shows exactly double the portrait columns before the cap`() {
        for (choice in listOf(0, 2, 3, 4)) {
            for (tablet in listOf(false, true)) {
                val portrait = ScreenRules.resolveColumns(choice, tablet, false)
                val landscape = ScreenRules.resolveColumns(choice, tablet, true)
                assertEquals(portrait * 2, landscape)
            }
        }
    }

    @Test
    fun `out of range manual choice is clamped to the portrait range`() {
        assertEquals(2, ScreenRules.resolveColumns(-5, true, false))
        assertEquals(2, ScreenRules.resolveColumns(1, true, false))
        assertEquals(6, ScreenRules.resolveColumns(9, false, false))
        assertEquals(6, ScreenRules.resolveColumns(99, true, false))
    }

    @Test
    fun `portrait columns always stay inside the manual range`() {
        for (tablet in listOf(false, true)) {
            val columns = ScreenRules.portraitColumns(ScreenRules.COLUMNS_AUTO, tablet)
            assertTrue(columns >= ScreenRules.COLUMNS_MIN)
            assertTrue(columns <= ScreenRules.COLUMNS_MAX)
        }
    }

    @Test
    fun `landscape columns never exceed the landscape cap`() {
        for (choice in -5..99) {
            for (tablet in listOf(false, true)) {
                val portrait = ScreenRules.portraitColumns(choice, tablet)
                val expected = (portrait * 2).coerceAtMost(ScreenRules.COLUMNS_LANDSCAPE_MAX)
                assertEquals(expected, ScreenRules.resolveColumns(choice, tablet, true))
            }
        }
    }

    @Test
    fun `only tablet landscape starts fullscreen`() {
        assertTrue(ScreenRules.startFullscreen(isTablet = true, landscape = true))
        assertFalse(ScreenRules.startFullscreen(isTablet = true, landscape = false))
        assertFalse(ScreenRules.startFullscreen(isTablet = false, landscape = true))
        assertFalse(ScreenRules.startFullscreen(isTablet = false, landscape = false))
    }
}
