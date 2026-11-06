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
    fun `automatic columns give tablets and landscape more columns`() {
        assertEquals(2, ScreenRules.autoColumns(isTablet = false, landscape = false))
        assertEquals(3, ScreenRules.autoColumns(isTablet = false, landscape = true))
        assertEquals(4, ScreenRules.autoColumns(isTablet = true, landscape = false))
        assertEquals(5, ScreenRules.autoColumns(isTablet = true, landscape = true))
    }

    @Test
    fun `choice 0 resolves to automatic columns`() {
        assertEquals(2, ScreenRules.resolveColumns(ScreenRules.COLUMNS_AUTO, false, false))
        assertEquals(3, ScreenRules.resolveColumns(ScreenRules.COLUMNS_AUTO, false, true))
        assertEquals(4, ScreenRules.resolveColumns(ScreenRules.COLUMNS_AUTO, true, false))
        assertEquals(5, ScreenRules.resolveColumns(ScreenRules.COLUMNS_AUTO, true, true))
    }

    @Test
    fun `manual choice is kept regardless of screen size or orientation`() {
        for (n in ScreenRules.COLUMNS_MIN..ScreenRules.COLUMNS_MAX) {
            assertEquals(n, ScreenRules.resolveColumns(n, false, false))
            assertEquals(n, ScreenRules.resolveColumns(n, true, true))
        }
    }

    @Test
    fun `out of range manual choice is clamped instead of producing zero columns`() {
        assertEquals(2, ScreenRules.resolveColumns(-5, true, true))
        assertEquals(2, ScreenRules.resolveColumns(1, true, true))
        assertEquals(6, ScreenRules.resolveColumns(9, false, false))
        assertEquals(6, ScreenRules.resolveColumns(99, true, true))
    }

    @Test
    fun `automatic columns always stay inside the manual range`() {
        for (tablet in listOf(false, true)) {
            for (landscape in listOf(false, true)) {
                val columns = ScreenRules.autoColumns(tablet, landscape)
                assertTrue(columns >= ScreenRules.COLUMNS_MIN)
                assertTrue(columns <= ScreenRules.COLUMNS_MAX)
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
