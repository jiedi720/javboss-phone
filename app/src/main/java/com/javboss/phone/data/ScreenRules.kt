package com.javboss.phone.data

/**
 * 屏幕尺寸 / 方向相关的纯逻辑，刻意不依赖任何 Android 类，方便在 JVM 单测里锁死规则。
 *
 * 两处使用：
 * - 列数：设置里存的是「竖屏基准列数」（[COLUMNS_AUTO] = 自动，或 2..6 手选），
 *   真正画网格时再按当前屏幕的 smallestWidth / 方向解析成实际列数；
 *   **横屏一律在竖屏基准上翻倍**（上限 [COLUMNS_LANDSCAPE_MAX]）；
 * - 播放页：平板横屏点封面右下角播放钮时直接全屏，不再强制竖屏小窗。
 */
object ScreenRules {

    /** Material 的平板判定阈值：最小宽度 ≥ 600dp */
    const val TABLET_SMALLEST_WIDTH_DP = 600

    /** 列数设置里 0 表示「自动」，其余 2..6 是用户手选的竖屏列数 */
    const val COLUMNS_AUTO = 0
    const val COLUMNS_MIN = 2
    const val COLUMNS_MAX = 6

    /** 横屏翻倍后的上限（自动档平板竖屏 4 → 横屏 8） */
    const val COLUMNS_LANDSCAPE_MAX = 8

    fun isTablet(smallestWidthDp: Int): Boolean =
        smallestWidthDp >= TABLET_SMALLEST_WIDTH_DP

    /** 竖屏基准列数：自动档按设备给（手机 2 / 平板 4），手选值钳制在 2..6 */
    fun portraitColumns(choice: Int, isTablet: Boolean): Int =
        if (choice == COLUMNS_AUTO) (if (isTablet) 4 else 2)
        else choice.coerceIn(COLUMNS_MIN, COLUMNS_MAX)

    /** 实际列数：横屏在竖屏基准上翻倍，最高 [COLUMNS_LANDSCAPE_MAX] 列 */
    fun resolveColumns(choice: Int, isTablet: Boolean, landscape: Boolean): Int {
        val portrait = portraitColumns(choice, isTablet)
        return if (landscape) (portrait * 2).coerceAtMost(COLUMNS_LANDSCAPE_MAX) else portrait
    }

    /** 自动档的便捷入口：等价于 choice = [COLUMNS_AUTO] 的 [resolveColumns] */
    fun autoColumns(isTablet: Boolean, landscape: Boolean): Int =
        resolveColumns(COLUMNS_AUTO, isTablet, landscape)

    /** 只有「平板 + 横屏」才直接全屏；手机、平板竖屏仍走原来的竖屏小窗 */
    fun startFullscreen(isTablet: Boolean, landscape: Boolean): Boolean =
        isTablet && landscape
}
