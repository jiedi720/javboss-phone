package com.javboss.phone.data

/**
 * 屏幕尺寸 / 方向相关的纯逻辑，刻意不依赖任何 Android 类，方便在 JVM 单测里锁死规则。
 *
 * 文件里有两套规则，别混用：
 * - [ScreenRules]：v0.3.40/0.3.41 的旧规则（smallestWidth 判平板 + 横屏列数翻倍）。
 *   保留是为了让已有单测与旧调用点继续编译通过，新代码一律不要用。
 * - [WindowSizeRules]：v0.3.42 起的统一规则。**唯一依据是「当前应用窗口实际可用 dp 宽度」**，
 *   不看设备型号、不看是否平板、不看 smallestScreenWidthDp、不看物理分辨率。
 *   旋转、分屏、自由窗口导致窗口宽度变化时，Compose 会重新读宽度并重新分级，布局随之切换。
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

/** 窗口宽度分级。跟 Material3 WindowSizeClass 的阈值一致，但只吃「当前窗口宽度」这一个入参。 */
enum class WindowClass {
    /** 窄窗口：手机竖屏、窄分屏、窄自由窗口 → 单栏 + 底部导航 */
    COMPACT,

    /** 中等窗口：平板竖屏、较宽分屏、手机横屏达到阈值 → 平板式导航，空间够才双栏 */
    MEDIUM,

    /** 宽窗口：Xiaomi Pad 7 Pro 横屏、宽自由窗口 → 完整平板布局 */
    EXPANDED,
}

/** 主界面导航形式 */
enum class NavigationForm {
    /** 底部 NavigationBar，仅 Compact */
    BOTTOM_BAR,

    /** 竖向 NavigationRail，Medium / Expanded */
    RAIL,
}

/**
 * 统一的窗口自适应规则。
 *
 * 所有函数都只接收「当前应用窗口实际可用宽度（dp）」，返回 dp 数值（Int）或枚举，
 * 不碰 Android / Compose 类型，因此能直接跑在 JVM 单测里。
 * Compose 侧由 `Common.kt` 的 `AdaptiveRoot` 用 `BoxWithConstraints` 测出宽度并下发，
 * 旋转 / 分屏 / 自由窗口改变窗口尺寸时会重新测量、重新分级。
 */
object WindowSizeRules {

    /** width < 600 为 Compact；600..839 为 Medium；>= 840 为 Expanded */
    const val COMPACT_MAX_DP = 600
    const val EXPANDED_MIN_DP = 840

    /** 详情左右双栏所需的最小窗口宽度 */
    const val TWO_PANE_MIN_DP = 900

    /** NavigationRail 占位宽度，算网格可用宽度时要扣掉 */
    const val RAIL_WIDTH_DP = 80

    /** 网格列数硬上限：防止自由窗口被拖到超宽时卡片过密 */
    const val GRID_MAX_COLUMNS = 8

    /** 竖版封面卡（2:3 / 100:143.1）的最小宽度 */
    const val CARD_MIN_WIDTH_DP = 150

    /** 横向视频卡（16:9）的最小宽度：更宽才看得清标题与角标 */
    const val WIDE_CARD_MIN_WIDTH_DP = 200

    /**
     * 「不限宽」哨兵。Compose 侧 `adaptiveMaxWidth()` 见到它就不加 widthIn，
     * 避免把一个巨大的 Int 直接转成 Dp 后参与像素换算。
     */
    const val NO_LIMIT = Int.MAX_VALUE / 4

    fun classify(widthDp: Int): WindowClass = when {
        widthDp < COMPACT_MAX_DP -> WindowClass.COMPACT
        widthDp < EXPANDED_MIN_DP -> WindowClass.MEDIUM
        else -> WindowClass.EXPANDED
    }

    fun isCompact(widthDp: Int): Boolean = classify(widthDp) == WindowClass.COMPACT
    fun isMedium(widthDp: Int): Boolean = classify(widthDp) == WindowClass.MEDIUM
    fun isExpanded(widthDp: Int): Boolean = classify(widthDp) == WindowClass.EXPANDED

    fun navigationForm(widthDp: Int): NavigationForm =
        if (isCompact(widthDp)) NavigationForm.BOTTOM_BAR else NavigationForm.RAIL

    /** Rail 在 Expanded 才常驻显示文字标签；Medium 只留图标，省横向空间 */
    fun railShowsLabel(widthDp: Int): Boolean = isExpanded(widthDp)

    /** 详情 / 设置是否采用左右双栏 */
    fun useTwoPane(widthDp: Int): Boolean = widthDp >= TWO_PANE_MIN_DP

    /** 页面左右边距：窗口越宽留白越多，避免内容顶到屏幕边 */
    fun horizontalPadding(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT -> 8
        WindowClass.MEDIUM -> 16
        WindowClass.EXPANDED -> if (widthDp >= 1200) 32 else 24
    }

    /** 网格间距 */
    fun gridSpacing(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT -> 8
        WindowClass.MEDIUM -> 12
        WindowClass.EXPANDED -> 16
    }

    /**
     * 页面最大内容宽度：超宽窗口（如拖到很宽的自由窗口）把正文限宽居中，
     * 避免文字行拉到一米长。网格列数计算也会按这个上限收敛（见 [gridColumns]），
     * 保证列数与真正留给网格的可用宽度一致。
     */
    fun contentMaxWidth(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT, WindowClass.MEDIUM -> NO_LIMIT
        WindowClass.EXPANDED -> 1400
    }

    /**
     * 网格动态列数：按「可用宽度 ÷ 卡片最小宽度」算，不按设备、不按方向、不固定列数。
     *
     * @param windowWidthDp 当前应用窗口实际可用宽度
     * @param reservedDp 被 NavigationRail / 侧栏占掉的宽度
     * @param minCardWidthDp 该网格卡片的最小宽度（竖封面用 [CARD_MIN_WIDTH_DP]，16:9 视频用 [WIDE_CARD_MIN_WIDTH_DP]）
     * @param choice 用户在设置里手选的列数（0 = 自动）；手选值只作为上限，实际列数仍随窗口收缩
     */
    fun gridColumns(
        windowWidthDp: Int,
        reservedDp: Int = 0,
        minCardWidthDp: Int = CARD_MIN_WIDTH_DP,
        choice: Int = ScreenRules.COLUMNS_AUTO,
    ): Int {
        val padding = horizontalPadding(windowWidthDp)
        val spacing = gridSpacing(windowWidthDp)
        val minCard = minCardWidthDp.coerceAtLeast(1)
        // 页面正文在超宽自由窗口会被 adaptiveContentWidth() 限到 contentMaxWidth，
        // 所以列数必须按「真正留给网格的正文宽度」算，否则窗口 > 1400dp 时列数偏多、
        // 卡片被压得比 minCardWidthDp 还窄。Compact/Medium 的 contentMaxWidth 是 NO_LIMIT，
        // 这里等价于 windowWidthDp - reservedDp。
        val effectiveWidth = (windowWidthDp - reservedDp)
            .coerceAtMost(contentMaxWidth(windowWidthDp))
        val available = (effectiveWidth - padding * 2).coerceAtLeast(minCard)
        val dynamic = ((available + spacing) / (minCard + spacing))
            .coerceIn(1, GRID_MAX_COLUMNS)
        return if (choice > 0) dynamic.coerceAtMost(choice.coerceIn(1, GRID_MAX_COLUMNS)) else dynamic
    }

    /** 对话框最大宽度；Compact 交给平台默认，不加限制 */
    fun dialogMaxWidth(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT -> NO_LIMIT
        WindowClass.MEDIUM -> 560
        WindowClass.EXPANDED -> 640
    }

    /** ModalBottomSheet 内容最大宽度 */
    fun sheetMaxWidth(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT -> NO_LIMIT
        WindowClass.MEDIUM -> 640
        WindowClass.EXPANDED -> 720
    }

    /** 搜索输入框最大宽度 */
    fun searchFieldMaxWidth(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT -> NO_LIMIT
        WindowClass.MEDIUM -> 420
        WindowClass.EXPANDED -> 480
    }

    /** 登录表单最大宽度：任何窗口都不拉伸到整屏 */
    fun loginFormMaxWidth(widthDp: Int): Int = 480

    /** 设置页整体最大宽度 */
    fun settingsMaxWidth(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT -> NO_LIMIT
        WindowClass.MEDIUM -> 720
        WindowClass.EXPANDED -> 960
    }

    /** 设置卡片列数：只有足够宽的 Expanded 才两列分组 */
    fun settingsColumns(widthDp: Int): Int =
        if (isExpanded(widthDp) && widthDp >= 1100) 2 else 1

    /** 大图查看器图容器最大宽度：宽屏下翻页阈值按容器宽算，不按整屏宽算 */
    fun imageViewerMaxWidth(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT -> NO_LIMIT
        WindowClass.MEDIUM -> 900
        WindowClass.EXPANDED -> 1100
    }

    /** 非全屏播放区域最大宽度：平板上不让 16:9 画面铺满整屏 */
    fun playerStageMaxWidth(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT -> NO_LIMIT
        WindowClass.MEDIUM -> 720
        WindowClass.EXPANDED -> 960
    }

    /**
     * 播放页首次是否直接全屏沉浸。
     *
     * 只依据「当前应用窗口实际宽高」：窗口达到 Expanded 宽度且是横向窗口（宽 > 高）才直接全屏；
     * 手机横屏、平板竖屏、窄分屏都仍走竖屏小窗。不再用 isTablet / smallestScreenWidthDp /
     * 设备型号判断，旋转与自由窗口缩放会重新测量。
     */
    fun startFullscreen(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= EXPANDED_MIN_DP && widthDp > heightDp
}
