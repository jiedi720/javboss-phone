package com.javboss.phone.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.53：Compact 竖屏底部 NavigationBar 高度规则。
 * M3 默认 80dp，收紧到 64dp，但仍需保证 >= 48dp 的可点区域。
 * 该值只用于 NavigationBar；Medium / Expanded 的 NavigationRail 不受影响。
 */
class NavBarHeightTest {

    @Test
    fun `compact bottom bar is shorter than material3 default`() {
        assertTrue(WindowSizeRules.NAV_BAR_COMPACT_HEIGHT_DP < 80)
    }

    @Test
    fun `compact bottom bar keeps at least 48dp touch target`() {
        assertTrue(WindowSizeRules.NAV_BAR_COMPACT_HEIGHT_DP >= 48)
    }
}
