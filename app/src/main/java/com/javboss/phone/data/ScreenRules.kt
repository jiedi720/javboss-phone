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

    /** NavigationRail 占位宽度，算网格可用宽度时要扣掉；收窄到 64dp 给网格多留 16dp */
    const val RAIL_WIDTH_DP = 64

    /**
     * v0.3.52 左侧栏紧凑模式：单个按钮的高度。
     * 普通模式主 tab 约 60dp、工具项 44dp；紧凑后统一压到 32dp，
     * 隐藏文字腾出的垂直空间被真正回收，图标整体上移，一屏放得下全部按钮不用滚动。
     */
    const val RAIL_COMPACT_ITEM_DP = 32

    /** 紧凑模式下分隔线的垂直 padding（普通模式主分隔线 6dp / 工具区内部分隔线 4dp） */
    const val RAIL_COMPACT_DIVIDER_PADDING_DP = 2

    /** 网格列数硬上限：防止自由窗口被拖到超宽时卡片过密（仅用于手选档） */
    const val GRID_MAX_COLUMNS = 8

    /**
     * 自动档 JAV 列数：竖屏 3 列、横屏 6 列（横屏翻倍），同时也是各自方向的上限。
     * 设置页按方向分开设定后，自动档不再按可用宽度动态增减，而是固定取这两个值；
     * 只有窗口实在放不下时才由 [gridColumns] 按卡片硬下限收敛。
     */
    const val AUTO_PORTRAIT_COLUMNS = 3
    const val AUTO_LANDSCAPE_COLUMNS = 6

    /** 设置页两个下拉各自的候选值；0 = 自动（取对应方向的默认值） */
    val PORTRAIT_COLUMN_CHOICES = listOf(0, 2, 3)
    val LANDSCAPE_COLUMN_CHOICES = listOf(0, 5, 6)

    /** 自动档：竖屏 3 列、横屏 6 列 */
    fun autoColumns(landscape: Boolean): Int =
        if (landscape) AUTO_LANDSCAPE_COLUMNS else AUTO_PORTRAIT_COLUMNS

    /** 把某个方向的选择值解析成实际目标列数（0 = 自动） */
    fun resolveColumnChoice(choice: Int, landscape: Boolean): Int =
        if (choice > 0) choice else autoColumns(landscape)

    /**
     * 旧版单个 `grid_columns`（0 = 自动 / 2..8 = 固定列数）迁移到「竖屏 / 横屏」两个新值。
     * 旧值超出该方向允许范围时钳到边界，避免出现「竖屏选 6 列却只显示 3 列」的无效选项。
     */
    fun migrateColumnChoice(old: Int, landscape: Boolean): Int {
        if (old <= 0) return 0
        val choices = if (landscape) LANDSCAPE_COLUMN_CHOICES else PORTRAIT_COLUMN_CHOICES
        val fixed = choices.filter { it > 0 }
        return fixed.minByOrNull { kotlin.math.abs(it - old) } ?: autoColumns(landscape)
    }

    /** 校验某个方向的选择值合法（0 或该方向候选值之一） */
    fun isValidColumnChoice(choice: Int, landscape: Boolean): Boolean =
        choice == 0 || choice in (if (landscape) LANDSCAPE_COLUMN_CHOICES else PORTRAIT_COLUMN_CHOICES)

    /** 竖版封面卡（2:3 / 100:143.1）的最小宽度 */
    const val CARD_MIN_WIDTH_DP = 150

    /**
     * 手选固定列数时允许的卡片硬下限。
     * 自动档用 150dp 保证「看着舒服」，但用户明确选了 6/7/8 列时若还按 150dp 算，
     * 平板横屏 959dp 只够排 5 列，设置里的 5 / 6 列就形同虚设。
     * 手选是显式意图，放宽到 88dp：959dp 平板横屏扣掉 64dp 侧栏、24dp×2 边距、
     * 16dp 间距后约 847dp 可用，刚好排得下 8 列（每卡约 92dp），
     * 手选 8 列不再被收敛成 7 列；窗口真放不下才收敛，否则按用户选的列数排。
     */
    const val MANUAL_CARD_MIN_WIDTH_DP = 88

    /** 横向视频卡（16:9）的最小宽度：更宽才看得清标题与角标。
     *  视频页不再用它单独算列数，改由 [videoGridColumns] 恒比 JAV 少一列；
     *  此值保留给 [gridColumns] 的通用参数能力与对应单测。 */
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

    /**
     * v0.3.51：左侧栏最终是否显示文字标签。
     * 设置里的「左侧栏紧凑模式」开启后一律只画图标（主 tab 与页面工具项都适用）；
     * 关闭时沿用 [railShowsLabel] 的宽度规则，保持旧行为。
     */
    fun railShowLabel(widthDp: Int, compactMode: Boolean): Boolean =
        !compactMode && railShowsLabel(widthDp)

    /**
     * v0.3.52：左侧栏单个按钮的高度（dp）。
     * - 紧凑模式：主 tab 与工具项一律压到 [RAIL_COMPACT_ITEM_DP]，文字腾出的空间被回收；
     * - 非紧凑且有文字：44dp（旧值）；
     * - 非紧凑但宽度规则本就隐藏文字（Medium）：40dp（旧值，不受紧凑模式影响）。
     */
    fun railItemHeightDp(showLabel: Boolean, compactMode: Boolean): Int = when {
        compactMode -> RAIL_COMPACT_ITEM_DP
        showLabel -> 44
        else -> 40
    }

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
     * @param choice 用户在设置里手选的列数（0 = 自动）；手选值是**目标列数**（选 N 就排 N 列），
     *   不再只当上限。仅当窗口按 [MANUAL_CARD_MIN_WIDTH_DP] 也放不下 N 列时才收敛，
     *   否则平板横屏自动只有 5 列时选 6 列仍被钳成 5，设置项没有意义。
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
        if (choice <= 0) return dynamic
        // 手选：按更宽松的硬下限算出窗口最多能塞几列，取「用户选的列数」与它的较小值。
        // 自动档仍用 minCardWidthDp（150dp）算，保持原来的动态观感。
        val maxFitting = ((available + spacing) / (MANUAL_CARD_MIN_WIDTH_DP + spacing))
            .coerceIn(1, GRID_MAX_COLUMNS)
        return choice.coerceIn(1, GRID_MAX_COLUMNS).coerceAtMost(maxFitting)
    }

    /**
     * 视频网格列数：恒等于同一窗口下 JAV 网格列数减一（至少 1 列）。
     *
     * 视频是 16:9 横版封面、JAV 是 2:3 竖版封面，同样宽度下横版卡更宽，
     * 每行少放一个才舒服。以 JAV 列数为基准（同一个窗口宽度、同一份手选上限），
     * 而不是另取一套「横版卡最小宽度」独立计算——那样取整或手选钳制会出现
     * 「跟 JAV 一样多」或「一下少了两列」，差值不稳定。
     * 极窄窗口 JAV 只剩 1 列时，视频保底 1 列（不能为 0）。
     */
    fun videoGridColumns(javColumns: Int): Int = (javColumns - 1).coerceAtLeast(1)

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
