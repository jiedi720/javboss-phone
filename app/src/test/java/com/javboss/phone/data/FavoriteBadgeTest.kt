package com.javboss.phone.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteBadgeTest {

    @Test
    fun `positive favorite count shows the solid heart`() {
        assertTrue(CardBadgeRules.showFavoriteBadge(1))
        assertTrue(CardBadgeRules.showFavoriteBadge(7))
    }

    @Test
    fun `zero favorite count hides the badge`() {
        assertFalse(CardBadgeRules.showFavoriteBadge(0))
    }

    @Test
    fun `negative favorite count is treated as not favorited`() {
        assertFalse(CardBadgeRules.showFavoriteBadge(-1))
        assertFalse(CardBadgeRules.showFavoriteBadge(Int.MIN_VALUE))
    }
}
