package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compact 竖屏底部 NavigationBar 高度规则。
 * v0.3.53 收到 64dp、v0.3.54 补到 72dp、v0.3.55 按用户红线补到 82dp：
 * 栏本身变高，但新增高度全部作为图标上方的顶部 padding，
 * 真正承载图标 / 文字的内容区恒为 64dp，仍比 M3 默认 80dp 紧凑，
 * 且触控目标不低于 48dp。该值只用于 NavigationBar；
 * Medium / Expanded 的 NavigationRail 不受影响。
 */
class NavBarHeightTest {

    @Test
    fun `compact bottom bar content area is shorter than material3 default`() {
        // 栏高 82dp 虽已超过 M3 默认 80dp，但 18dp 全是上方留白，
        // 图标 / 文字所在的内容区仍是 64dp，比默认紧凑。
        assertTrue(
            WindowSizeRules.NAV_BAR_COMPACT_HEIGHT_DP -
                WindowSizeRules.NAV_BAR_TOP_PADDING_DP < 80,
        )
    }

    @Test
    fun `compact bottom bar keeps at least 48dp touch target`() {
        assertTrue(
            WindowSizeRules.NAV_BAR_COMPACT_HEIGHT_DP -
                WindowSizeRules.NAV_BAR_TOP_PADDING_DP >= 48,
        )
    }

    @Test
    fun `v0 3 55 extra height goes entirely above the icons`() {
        // 82dp 栏 - 18dp 顶部 padding = 64dp 内容区，与 v0.3.53 / v0.3.54 完全一致：
        // 图标 / 文字 / 下方留白都不动，只是上方多出 18dp 冗余，栏顶正好到红线。
        assertEquals(
            64,
            WindowSizeRules.NAV_BAR_COMPACT_HEIGHT_DP -
                WindowSizeRules.NAV_BAR_TOP_PADDING_DP,
        )
        // 本轮在 v0.3.54 的 8dp 基础上再补 10dp（按用户截图红线标定）。
        assertTrue(WindowSizeRules.NAV_BAR_TOP_PADDING_DP > 8)
    }
}
