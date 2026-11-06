package com.javboss.phone.data

/**
 * 屏幕尺寸 / 方向相关的纯逻辑，刻意不依赖任何 Android 类，方便在 JVM 单测里锁死规则。
 *
 * 两处使用：
 * - 列数：设置里存的是「选择值」（[COLUMNS_AUTO] = 自动，或 2..6 手选），
 *   真正画网格时再按当前屏幕的 smallestWidth / 方向解析成实际列数；
 * - 播放页：平板横屏点封面右下角播放钮时直接全屏，不再强制竖屏小窗。
 */
object ScreenRules {

    /** Material 的平板判定阈值：最小宽度 ≥ 600dp */
    const val TABLET_SMALLEST_WIDTH_DP = 600

    /** 列数设置里 0 表示「自动」，其余 2..6 是用户手选的固定列数 */
    const val COLUMNS_AUTO = 0
    const val COLUMNS_MIN = 2
    const val COLUMNS_MAX = 6

    fun isTablet(smallestWidthDp: Int): Boolean =
        smallestWidthDp >= TABLET_SMALLEST_WIDTH_DP

    /**
     * 自动列数：平板比手机多，横屏比竖屏多。
     * 手机竖屏 2 / 手机横屏 3 / 平板竖屏 4 / 平板横屏 5。
     */
    fun autoColumns(isTablet: Boolean, landscape: Boolean): Int = when {
        isTablet && landscape -> 5
        isTablet -> 4
        landscape -> 3
        else -> 2
    }

    /** 把设置里的选择值解析成实际列数；手选值一律钳制在 2..6，非法值退回 2 列 */
    fun resolveColumns(choice: Int, isTablet: Boolean, landscape: Boolean): Int =
        if (choice == COLUMNS_AUTO) {
            autoColumns(isTablet, landscape)
        } else {
            choice.coerceIn(COLUMNS_MIN, COLUMNS_MAX)
        }

    /** 只有「平板 + 横屏」才直接全屏；手机、平板竖屏仍走原来的竖屏小窗 */
    fun startFullscreen(isTablet: Boolean, landscape: Boolean): Boolean =
        isTablet && landscape
}
