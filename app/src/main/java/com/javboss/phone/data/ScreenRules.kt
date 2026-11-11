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
 * 播放页的方向锁定决策（不依赖 Android 的 ActivityInfo，便于 JVM 单测）。
 * Compose 侧再把它映射到 `ActivityInfo.SCREEN_ORIENTATION_*` 常量。
 */
enum class PlayerOrientation {
    /** 竖屏小窗：强制 PORTRAIT */
    PORTRAIT,

    /** 点全屏后：强制感应横屏（允许 180° 翻转，但不会自己回到竖屏） */
    SENSOR_LANDSCAPE,

    /** 宽窗口 / 平板：不强制方向，交给系统（分屏、自由窗口不被拧成竖屏） */
    UNSPECIFIED,
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

    /**
     * v0.3.55：Compact（手机竖屏）底部 NavigationBar 的高度。
     * M3 默认 80dp；v0.3.53 收到 64dp 后图标上方几乎没有留白、显得被裁，
     * v0.3.54 放宽到 72dp 后用户仍觉得不够：栏顶距「一页显示不下的那行封面的
     * 上边缘」（用户截图标出的红线）还差约 35px ≈ 10dp（按选中态 indicator
     * 64×32dp = 223×111px 标定出该设备约 3.48 px/dp）。
     * 这里再放宽到 82dp，新增的 10dp 通过 [NAV_BAR_TOP_PADDING_DP] 全部加在
     * 图标上方，使栏顶正好到红线、把露出的半截封面盖住；
     * 图标 / 文字 / 下方留白保持原样。仅作用于 NavigationBar，
     * Medium / Expanded 的 NavigationRail 不受影响。
     */
    const val NAV_BAR_COMPACT_HEIGHT_DP = 82

    /**
     * v0.3.55：新增高度全部落在图标上方，内容区仍是 82 - 18 = 64dp，
     * 与 v0.3.53 / v0.3.54 完全一致，下方留白不变；触控目标仍 >= 48dp。
     * 18dp = v0.3.54 的 8dp + 本轮补到红线的 10dp。
     */
    const val NAV_BAR_TOP_PADDING_DP = 18

    /** 网格列数硬上限：防止自由窗口被拖到超宽时卡片过密（仅用于手选档） */
    const val GRID_MAX_COLUMNS = 8

    /**
     * v0.3.56：视频 / JAV 各自独立设定列数，下拉去掉「自动」档。
     * 竖屏候选 2 / 3（默认 3），横屏候选 5 / 6 / 7（默认 5）。
     * 选择值本身就是要排的目标列数，不再有 0 = 自动这一层解析。
     */
    const val DEFAULT_PORTRAIT_COLUMNS = 3
    const val DEFAULT_LANDSCAPE_COLUMNS = 5

    /** 设置页 4 个下拉共用的候选值（JAV 与视频各一组，选项相同、取值互不联动） */
    val PORTRAIT_COLUMN_CHOICES = listOf(2, 3)
    val LANDSCAPE_COLUMN_CHOICES = listOf(5, 6, 7)

    /** 某个方向的初始值 / 非法值回落值：竖屏 3 列、横屏 5 列 */
    fun defaultColumns(landscape: Boolean): Int =
        if (landscape) DEFAULT_LANDSCAPE_COLUMNS else DEFAULT_PORTRAIT_COLUMNS

    /** 校验某个方向的选择值合法（必须是该方向候选值之一，不再有 0 = 自动） */
    fun isValidColumnChoice(choice: Int, landscape: Boolean): Boolean =
        choice in (if (landscape) LANDSCAPE_COLUMN_CHOICES else PORTRAIT_COLUMN_CHOICES)

    /**
     * 把任意历史值归一化成当前候选值之一：合法值原样保留，
     * 旧版 0 = 自动、越界值（如旧横屏 8 列）统一回落到该方向默认值。
     * DataStore restore 与 setter 都走这里，保证 4 个列数 Flow 永远落在合法集合里。
     */
    fun normalizeColumnChoice(value: Int, landscape: Boolean): Int =
        if (isValidColumnChoice(value, landscape)) value else defaultColumns(landscape)

    /**
     * 旧版单个 `grid_columns`（0 = 自动 / 2..8 = 固定列数）迁移到当前方向候选值：
     * 就近取该方向合法值；0 / 负数直接回落到默认值。
     */
    fun migrateColumnChoice(old: Int, landscape: Boolean): Int {
        if (old <= 0) return defaultColumns(landscape)
        val choices = if (landscape) LANDSCAPE_COLUMN_CHOICES else PORTRAIT_COLUMN_CHOICES
        return choices.minByOrNull { kotlin.math.abs(it - old) } ?: defaultColumns(landscape)
    }

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
     *  v0.3.56 起视频列数由用户独立设定，不再单独按卡宽算、也不再联动 JAV；
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

    /**
     * v0.3.68：详情页双栏左栏宽度。
     *
     * 原来固定 300dp（来自 v0.3.59 的三颗等宽按钮排版），横屏平板上左栏偏窄、
     * 右侧留白过多。改为按窗口宽度取 [DETAIL_LEFT_PANE_RATIO]（34%），
     * 并钳制在 [DETAIL_LEFT_PANE_MIN_DP]..[DETAIL_LEFT_PANE_MAX_DP]：
     * - 900dp（双栏下限）→ 340dp（比原来宽 40dp）
     * - 1067dp（平板横屏）→ 363dp
     * - 1348dp 及以上 → 上限 460dp
     * 右栏是 weight(1f)，左栏变宽后右栏自然收窄，不需要另设规则。
     */
    const val DETAIL_LEFT_PANE_RATIO = 0.34f
    const val DETAIL_LEFT_PANE_MIN_DP = 340
    const val DETAIL_LEFT_PANE_MAX_DP = 460

    fun detailLeftPaneWidthDp(widthDp: Int): Int {
        if (!useTwoPane(widthDp)) return DETAIL_LEFT_PANE_MIN_DP
        val ratio = (widthDp * DETAIL_LEFT_PANE_RATIO + 0.5f).toInt()
        return ratio.coerceIn(DETAIL_LEFT_PANE_MIN_DP, DETAIL_LEFT_PANE_MAX_DP)
    }

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
     * @param choice 目标列数（v0.3.56 起直接来自 JAV / 视频各自竖屏或横屏的选择值）；
     *   choice<=0 仍保留「按 [minCardWidthDp] 动态算」的通用能力（纯函数测试用），
     *   但设置层不再产生 0。手选值是**目标列数**（选 N 就排 N 列），不再只当上限：
     *   仅当窗口按 [MANUAL_CARD_MIN_WIDTH_DP] 也放不下 N 列时才收敛。
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

    // v0.3.56：视频列数改为用户独立设定（video_portrait_columns / video_landscape_columns），
    // 不再有「恒比 JAV 少一列」的联动函数。JAV 与视频都直接把各自的选择值传给
    // [gridColumns]，同一窗口下选几列就排几列，互不牵连。

    /** 对话框最大宽度；Compact 交给平台默认，不加限制 */
    fun dialogMaxWidth(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT -> NO_LIMIT
        WindowClass.MEDIUM -> 560
        WindowClass.EXPANDED -> 640
    }

    /**
     * 标签管理弹层专用最大宽度（v0.3.65）：标签多、还要放工具条与流式胶囊，
     * 比通用对话框更宽；Compact 仍交给平台默认（配合 fillMaxWidth 撑满），
     * 宽窗口按窗口宽度收窄，避免胶囊行拉得过长。
     */
    fun tagDialogMaxWidth(widthDp: Int): Int = when (classify(widthDp)) {
        WindowClass.COMPACT -> NO_LIMIT
        WindowClass.MEDIUM -> 720
        WindowClass.EXPANDED -> 900
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
     * 当前窗口是否横向（宽 > 高）。旋转、分屏、自由窗口缩放都会改变它，
     * 一律以窗口实测宽高为准，不看设备方向传感器。
     */
    fun isLandscape(widthDp: Int, heightDp: Int): Boolean = widthDp > heightDp

    /**
     * 播放页首次是否直接全屏沉浸。
     *
     * 只依据「当前应用窗口实际宽高」：窗口达到 Expanded 宽度且是横向窗口（宽 > 高）才直接全屏；
     * 手机横屏、平板竖屏、窄分屏都仍走竖屏小窗。不再用 isTablet / smallestScreenWidthDp /
     * 设备型号判断，旋转与自由窗口缩放会重新测量。
     */
    fun startFullscreen(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= EXPANDED_MIN_DP && isLandscape(widthDp, heightDp)

    /**
     * v0.3.57：播放页方向锁定。
     *
     * **第二个入参是「进入播放页那一刻」冻结的 Compact 判定，不是旋转后的当前宽度。**
     * 旧实现把 `windowWidthDp` 放进方向 `LaunchedEffect` 的 key：竖屏手机点全屏 →
     * `SENSOR_LANDSCAPE` → 转横屏后窗口宽度变大，`isCompact()` 变 false →
     * 方向被改成 `UNSPECIFIED` → 系统按姿态拧回竖屏 → 宽度变小又设回横屏，形成抖环。
     * 方向只看冻结值，旋转 / 分屏 / 自由窗口改变窗口宽度都不会重新判定方向。
     *
     * - Compact（手机竖屏）：竖屏小窗锁 PORTRAIT，点全屏锁 SENSOR_LANDSCAPE；
     * - 宽窗口（平板横屏、宽自由窗口）：一律 UNSPECIFIED，不强制方向。
     */
    fun playerOrientation(fullscreen: Boolean, compactAtEntry: Boolean): PlayerOrientation = when {
        fullscreen && compactAtEntry -> PlayerOrientation.SENSOR_LANDSCAPE
        !fullscreen && compactAtEntry -> PlayerOrientation.PORTRAIT
        else -> PlayerOrientation.UNSPECIFIED
    }
}
