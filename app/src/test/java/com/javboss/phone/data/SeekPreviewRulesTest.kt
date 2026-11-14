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

    @Test
    fun `preview bottom follows measured track top not control bar height`() {
        // 滑块顶边距控制条顶边 10px、滑块高 60px、轨道高 8px、安全缝 5px。
        // 轨道在滑块内垂直居中 ⇒ 轨道上沿 = 10 + (60 - 8) / 2 = 36，卡片下沿 = 36 - 5 = 31。
        assertEquals(
            31.0,
            SeekPreviewRules.previewBottomPx(10f, 60f, 8f, 5f).toDouble(),
            0.001,
        )
    }

    @Test
    fun `landscape and portrait share one measured formula`() {
        // 横屏控制条更高 ⇒ 滑块距顶 30px；竖屏更矮 ⇒ 10px。
        // 两者都贴各自轨道上沿，差值正好等于实测顶边差，不再出现「竖屏留缝」。
        val landscape = SeekPreviewRules.previewBottomPx(30f, 60f, 8f, 5f)
        val portrait = SeekPreviewRules.previewBottomPx(10f, 60f, 8f, 5f)
        assertEquals(51.0, landscape.toDouble(), 0.001)
        assertEquals(31.0, portrait.toDouble(), 0.001)
        assertEquals(20.0, (landscape - portrait).toDouble(), 0.001)
    }

    @Test
    fun `unmeasured slider falls back to slider top minus gap`() {
        // 首帧还没量到尺寸时退化为「顶边 - 安全缝」，避免把卡片算到屏幕外
        assertEquals(5.0, SeekPreviewRules.previewBottomPx(10f, 0f, 8f, 5f).toDouble(), 0.001)
        assertEquals(5.0, SeekPreviewRules.previewBottomPx(10f, 60f, 0f, 5f).toDouble(), 0.001)
    }

    @Test
    fun `gap keeps preview off the track`() {
        val noGap = SeekPreviewRules.previewBottomPx(10f, 60f, 8f, 0f)
        val withGap = SeekPreviewRules.previewBottomPx(10f, 60f, 8f, 5f)
        assertEquals(5.0, (noGap - withGap).toDouble(), 0.001)
    }
}
