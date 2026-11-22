package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 男优排序维度口径（v0.3.85），对齐 Web bundle 的 Xe / _t() */
class PerformerSortRulesTest {
    @Test
    fun `male sort keeps only work recent age height`() {
        assertEquals(
            setOf("WORK", "RECENT", "AGE", "HEIGHT"),
            PerformerSortRules.MALE_ALLOWED_NAMES,
        )
        assertTrue(PerformerSortRules.allowedForMale("WORK"))
        assertTrue(PerformerSortRules.allowedForMale("RECENT"))
        assertTrue(PerformerSortRules.allowedForMale("AGE"))
        assertTrue(PerformerSortRules.allowedForMale("HEIGHT"))
    }

    @Test
    fun `measurements are not allowed for male`() {
        PerformerSortRules.MEASUREMENT_NAMES.forEach { name ->
            assertFalse("$name should be female only", PerformerSortRules.allowedForMale(name))
        }
    }

    @Test
    fun `male measurement sort falls back to work like Web _t`() {
        assertEquals("WORK", PerformerSortRules.effectiveSortName("BUST", true))
        assertEquals("WORK", PerformerSortRules.effectiveSortName("CUP", true))
        assertEquals("WORK", PerformerSortRules.effectiveSortName("HIPS", true))
        assertEquals("WORK", PerformerSortRules.effectiveSortName("WAIST", true))
        // 非三围维度原样保留
        assertEquals("HEIGHT", PerformerSortRules.effectiveSortName("HEIGHT", true))
        assertEquals("AGE", PerformerSortRules.effectiveSortName("AGE", true))
    }

    @Test
    fun `female sort is never corrected`() {
        assertEquals("BUST", PerformerSortRules.effectiveSortName("BUST", false))
        assertEquals("CUP", PerformerSortRules.effectiveSortName("CUP", false))
        assertEquals("WORK", PerformerSortRules.effectiveSortName("WORK", false))
    }
}
