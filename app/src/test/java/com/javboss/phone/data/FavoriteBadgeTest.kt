package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteBadgeTest {

    @Test
    fun `positive favorite count shows solid amber star`() {
        assertTrue(CardBadgeRules.showFavoriteBadge(1))
        assertTrue(CardBadgeRules.showFavoriteBadge(7))
    }

    @Test
    fun `zero favorite count shows outlined star`() {
        // v0.3.60 起角标常显：false 不是「不画」，而是画半透明黑底描边星
        assertFalse(CardBadgeRules.showFavoriteBadge(0))
    }

    @Test
    fun `negative favorite count is treated as not favorited`() {
        assertFalse(CardBadgeRules.showFavoriteBadge(-1))
        assertFalse(CardBadgeRules.showFavoriteBadge(Int.MIN_VALUE))
    }

    @Test
    fun `watched badge sits directly below the favorite star`() {
        // 6dp 顶距 + 24dp 星标 + 6dp 间距 = 36dp；视频卡没有星标时回到 6dp
        assertEquals(36, CardBadgeRules.watchedBadgeTopDp(belowFavorite = true))
        assertEquals(6, CardBadgeRules.watchedBadgeTopDp(belowFavorite = false))
    }

    @Test
    fun `badge geometry constants stay consistent`() {
        assertEquals(24, CardBadgeRules.FAVORITE_BADGE_SIZE_DP)
        assertEquals(6, CardBadgeRules.BADGE_EDGE_PADDING_DP)
        assertEquals(
            CardBadgeRules.BADGE_EDGE_PADDING_DP * 2 + CardBadgeRules.FAVORITE_BADGE_SIZE_DP,
            CardBadgeRules.watchedBadgeTopDp(true),
        )
    }
}
