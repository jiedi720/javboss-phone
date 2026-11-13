package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WebSliderRulesTest {

    @Test
    fun `fraction returns middle of range`() {
        assertEquals(0.5f, WebSliderRules.fraction(50f, 0f, 100f), 1e-6f)
    }

    @Test
    fun `fraction clamps below and above range`() {
        assertEquals(0f, WebSliderRules.fraction(-10f, 0f, 100f), 1e-6f)
        assertEquals(1f, WebSliderRules.fraction(120f, 0f, 100f), 1e-6f)
    }

    @Test
    fun `fraction handles non-zero start`() {
        assertEquals(0.25f, WebSliderRules.fraction(15f, 10f, 30f), 1e-6f)
    }

    @Test
    fun `fraction degenerate range returns zero`() {
        assertEquals(0f, WebSliderRules.fraction(5f, 5f, 5f), 1e-6f)
    }

    @Test
    fun `pointer at track start returns start`() {
        assertEquals(0f, WebSliderRules.valueFromPointer(8f, 200f, 16f, 0f, 100f), 1e-4f)
    }

    @Test
    fun `pointer at track end returns end`() {
        assertEquals(100f, WebSliderRules.valueFromPointer(192f, 200f, 16f, 0f, 100f), 1e-4f)
    }

    @Test
    fun `pointer at middle returns middle`() {
        assertEquals(50f, WebSliderRules.valueFromPointer(100f, 200f, 16f, 0f, 100f), 1e-4f)
    }

    @Test
    fun `pointer clamps outside track`() {
        assertEquals(0f, WebSliderRules.valueFromPointer(-50f, 200f, 16f, 0f, 100f), 1e-4f)
        assertEquals(100f, WebSliderRules.valueFromPointer(999f, 200f, 16f, 0f, 100f), 1e-4f)
    }

    @Test
    fun `pointer maps to custom value range`() {
        assertEquals(20f, WebSliderRules.valueFromPointer(100f, 200f, 16f, 10f, 30f), 1e-4f)
    }

    @Test
    fun `pointer on narrower-than-thumb width does not crash`() {
        assertEquals(0f, WebSliderRules.valueFromPointer(0f, 4f, 16f, 0f, 1f), 1e-4f)
    }
}
