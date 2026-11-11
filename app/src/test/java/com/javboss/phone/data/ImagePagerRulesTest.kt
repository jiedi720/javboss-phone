package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大图查看器翻页手势规则的 JVM 单测（v0.3.68）。
 *
 * 修复目标：翻页阈值以前按「容器宽度 × 18%」算，横版容器被限到 1100dp 后
 * 需要拖动近 200dp 才能翻一张，明显比竖版（约 65dp）吃力。
 * 新规则按容器**短边**算并钳制在 56..84dp，横竖版手感一致。
 */
class ImagePagerRulesTest {

    private val eps = 0.01f

    @Test
    fun `portrait phone keeps the old feel`() {
        // 竖屏 360 x 650：短边 360 → 360 * 0.18 = 64.8dp，与旧规则完全一致
        assertEquals(64.8f, ImagePagerRules.swipeThresholdDp(360f, 650f), eps)
    }

    @Test
    fun `landscape phone needs the same drag as portrait`() {
        val portrait = ImagePagerRules.swipeThresholdDp(360f, 650f)
        val landscape = ImagePagerRules.swipeThresholdDp(780f, 360f)
        assertEquals("横版不应比竖版难翻", portrait, landscape, eps)
    }

    @Test
    fun `wide landscape container is capped instead of growing with width`() {
        // 平板横屏容器 1100 x 704：旧规则 198dp，新规则被上限收到 84dp
        val threshold = ImagePagerRules.swipeThresholdDp(1100f, 704f)
        assertEquals(ImagePagerRules.MAX_SWIPE_DP, threshold, eps)
        assertTrue("必须明显小于旧的按宽度算的 18%", threshold < 1100f * 0.18f)
        // 容器再宽也不增加难度
        assertEquals(threshold, ImagePagerRules.swipeThresholdDp(2000f, 704f), eps)
    }

    @Test
    fun `tiny container falls back to the lower bound`() {
        assertEquals(ImagePagerRules.MIN_SWIPE_DP, ImagePagerRules.swipeThresholdDp(120f, 160f), eps)
    }

    @Test
    fun `unmeasured container never yields a zero threshold`() {
        assertEquals(ImagePagerRules.MIN_SWIPE_DP, ImagePagerRules.swipeThresholdDp(0f, 0f), eps)
        assertEquals(ImagePagerRules.MIN_SWIPE_DP, ImagePagerRules.swipeThresholdDp(-10f, -10f), eps)
    }

    @Test
    fun `threshold always stays inside the configured bounds`() {
        val widths = listOf(200f, 360f, 480f, 720f, 900f, 1100f, 1600f, 2400f)
        for (w in widths) {
            for (h in widths) {
                val t = ImagePagerRules.swipeThresholdDp(w, h)
                assertTrue(
                    "阈值越界: ${w}x$h -> $t",
                    t >= ImagePagerRules.MIN_SWIPE_DP && t <= ImagePagerRules.MAX_SWIPE_DP,
                )
            }
        }
    }

    @Test
    fun `page animation durations are positive and short`() {
        assertTrue(ImagePagerRules.PAGE_OUT_MS > 0)
        assertTrue(ImagePagerRules.PAGE_IN_MS > 0)
        assertTrue(ImagePagerRules.PAGE_OUT_MS + ImagePagerRules.PAGE_IN_MS <= 600)
    }
}
