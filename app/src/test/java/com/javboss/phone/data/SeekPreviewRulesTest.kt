package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekPreviewRulesTest {

    @Test
    fun `time from fraction at middle of duration`() {
        assertEquals(50_000L, SeekPreviewRules.timeMs(0.5f, 100_000L))
    }

    @Test
    fun `time from fraction clamps out of range fractions`() {
        assertEquals(0L, SeekPreviewRules.timeMs(-0.5f, 100_000L))
        assertEquals(100_000L, SeekPreviewRules.timeMs(1.5f, 100_000L))
    }

    @Test
    fun `unknown duration yields zero so caller skips frame extraction`() {
        assertEquals(0L, SeekPreviewRules.timeMs(0.5f, 0L))
        assertEquals(0L, SeekPreviewRules.timeMs(0.5f, -1L))
    }

    @Test
    fun `time never exceeds duration`() {
        assertEquals(100_000L, SeekPreviewRules.timeMs(1f, 100_000L))
    }

    @Test
    fun `bucket groups times within one throttle window`() {
        val a = SeekPreviewRules.bucket(10_100L)
        val b = SeekPreviewRules.bucket(10_900L)
        assertEquals(a, b)
        assertEquals(10L, a)
    }

    @Test
    fun `bucket changes across throttle window boundary`() {
        assertEquals(10L, SeekPreviewRules.bucket(10_999L))
        assertEquals(11L, SeekPreviewRules.bucket(11_000L))
    }

    @Test
    fun `bucket honours custom interval`() {
        assertEquals(2L, SeekPreviewRules.bucket(10_000L, 5_000L))
        // 无效间隔退化为「不量化」，避免除零
        assertEquals(10_000L, SeekPreviewRules.bucket(10_000L, 0L))
        assertEquals(10_000L, SeekPreviewRules.bucket(10_000L, -1L))
    }

    @Test
    fun `should fetch only when bucket changed`() {
        assertTrue(SeekPreviewRules.shouldFetch(null, 10L))
        assertFalse(SeekPreviewRules.shouldFetch(10L, 10L))
        assertTrue(SeekPreviewRules.shouldFetch(10L, 11L))
    }

    @Test
    fun `dragging inside one second reuses the same preview frame`() {
        val duration = 2_100_000L
        val f1 = SeekPreviewRules.timeMs(0.4710f, duration)
        val f2 = SeekPreviewRules.timeMs(0.4713f, duration)
        assertEquals(
            SeekPreviewRules.bucket(f1),
            SeekPreviewRules.bucket(f2),
        )
    }
}
