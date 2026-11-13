package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.73：主页长按 JAV 封面 →「调整封面」弹窗的纯规则回归。
 * 对齐 Web bundle 的 `aT`（normalize）/ `oT`（cropWindow）/ 拖动反解。
 */
class CoverCropRulesTest {

    private val eps = 1e-9

    @Test
    fun `null and invalid positions fall back to the server default`() {
        assertEquals(1.0, CoverCropRules.normalize(null), eps)
        assertEquals(1.0, CoverCropRules.normalize(Double.NaN), eps)
        assertEquals(1.0, CoverCropRules.normalize(Double.POSITIVE_INFINITY), eps)
        assertEquals(1.0, CoverCropRules.normalize(Double.NEGATIVE_INFINITY), eps)
    }

    @Test
    fun `positions are clamped into 0 to 1`() {
        assertEquals(0.0, CoverCropRules.normalize(-0.4), eps)
        assertEquals(1.0, CoverCropRules.normalize(3.2), eps)
        assertEquals(0.42, CoverCropRules.normalize(0.42), eps)
    }

    @Test
    fun `wide 800x538 cover crops exactly 47 percent of its width`() {
        val window = CoverCropRules.cropWindow(800, 538, null)!!
        // iT/i = (800*0.47/538) / (800/538) = 0.47
        assertEquals(0.47, window.width, eps)
        assertEquals(1.0, window.height, eps)
        assertEquals(0.0, window.top, eps)
        assertTrue(window.adjustable)
    }

    @Test
    fun `position 1 puts the crop window against the right edge`() {
        val window = CoverCropRules.cropWindow(800, 538, 1.0)!!
        assertEquals(1.0 - 0.47, window.left, eps)
        assertEquals(1.0, window.left + window.width, eps)
    }

    @Test
    fun `position 0 puts the crop window against the left edge`() {
        val window = CoverCropRules.cropWindow(800, 538, 0.0)!!
        assertEquals(0.0, window.left, eps)
    }

    @Test
    fun `half position centers the crop window`() {
        val window = CoverCropRules.cropWindow(800, 538, 0.5)!!
        assertEquals((1.0 - 0.47) / 2.0, window.left, eps)
        assertEquals(0.5, window.left + window.width / 2.0, eps)
    }

    @Test
    fun `narrow image has no horizontal crop room and is vertically centered`() {
        val window = CoverCropRules.cropWindow(400, 800, 0.2)!!
        assertEquals(1.0, window.width, eps)
        assertTrue(window.height < 1.0)
        assertEquals((1.0 - window.height) / 2.0, window.top, eps)
        assertFalse(window.adjustable)
    }

    @Test
    fun `invalid image size returns null`() {
        assertNull(CoverCropRules.cropWindow(0, 538, null))
        assertNull(CoverCropRules.cropWindow(800, 0, null))
        assertNull(CoverCropRules.cropWindow(-1, 100, null))
    }

    @Test
    fun `drag from the crop window center reproduces the original position`() {
        val window = CoverCropRules.cropWindow(800, 538, 0.37)!!
        val center = window.left + window.width / 2.0
        assertEquals(0.37, CoverCropRules.positionFromDrag(center, window, 0.37), eps)
    }

    @Test
    fun `drag is clamped at both edges`() {
        val window = CoverCropRules.cropWindow(800, 538, 0.5)!!
        assertEquals(0.0, CoverCropRules.positionFromDrag(-5.0, window, 0.5), eps)
        assertEquals(1.0, CoverCropRules.positionFromDrag(5.0, window, 0.5), eps)
    }

    @Test
    fun `drag on a non adjustable window keeps the current position`() {
        val window = CoverCropRules.cropWindow(400, 800, 0.2)!!
        assertEquals(0.2, CoverCropRules.positionFromDrag(0.9, window, 0.2), eps)
    }

    @Test
    fun `percent label matches the Web slider label`() {
        assertEquals(0, CoverCropRules.percent(0.0))
        assertEquals(50, CoverCropRules.percent(0.5))
        assertEquals(100, CoverCropRules.percent(1.0))
        assertEquals(100, CoverCropRules.percent(null))
        assertEquals(37, CoverCropRules.percent(0.366))
        assertEquals(100, CoverCropRules.percent(4.0))
    }

    @Test
    fun `reset returns the server default position`() {
        assertEquals(1.0, CoverCropRules.DEFAULT_POSITION, eps)
        val window = CoverCropRules.cropWindow(800, 538, CoverCropRules.DEFAULT_POSITION)!!
        assertEquals(1.0, window.left + window.width, eps)
    }
}
