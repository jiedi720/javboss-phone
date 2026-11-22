package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 男优/女优口径与端点映射（v0.3.82） */
class PerformerRulesTest {
    @Test
    fun `male gender is recognized case insensitively`() {
        assertTrue(PerformerRules.isMale("male"))
        assertTrue(PerformerRules.isMale("MALE"))
        assertFalse(PerformerRules.isMale("female"))
    }

    @Test
    fun `missing or unknown gender falls back to actress`() {
        assertFalse(PerformerRules.isMale(null))
        assertFalse(PerformerRules.isMale(""))
        assertFalse(PerformerRules.isMale("unknown"))
    }

    @Test
    fun `list path follows Web cn helper`() {
        assertEquals("jav/actors", PerformerRules.listPath("male"))
        assertEquals("jav/idols", PerformerRules.listPath("female"))
        assertEquals("jav/idols", PerformerRules.listPath(null))
    }
}
