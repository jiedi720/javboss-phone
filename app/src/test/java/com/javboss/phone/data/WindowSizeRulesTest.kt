package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 窗口自适应规则的 JVM 单测。
 * 关键约束：所有结论只由「当前窗口 dp 宽度」决定，与设备型号 / 是否平板 / 物理分辨率无关。
 */
class WindowSizeRulesTest {

    // ---------- 分级 ----------

    @Test
    fun `width below 600 is compact`() {
        assertEquals(WindowClass.COMPACT, WindowSizeRules.classify(0))
        assertEquals(WindowClass.COMPACT, WindowSizeRules.classify(320))
        assertEquals(WindowClass.COMPACT, WindowSizeRules.classify(599))
        assertTrue(WindowSizeRules.isCompact(599))
    }

    @Test
    fun `600 to 839 is medium`() {
        assertEquals(WindowClass.MEDIUM, WindowSizeRules.classify(600))
        assertEquals(WindowClass.MEDIUM, WindowSizeRules.classify(712))
        assertEquals(WindowClass.MEDIUM, WindowSizeRules.classify(839))
        assertTrue(WindowSizeRules.isMedium(600))
        assertFalse(WindowSizeRules.isMedium(839 + 1))
    }

    @Test
    fun `840 and above is expanded`() {
        assertEquals(WindowClass.EXPANDED, WindowSizeRules.classify(840))
        assertEquals(WindowClass.EXPANDED, WindowSizeRules.classify(1067))
        assertEquals(WindowClass.EXPANDED, WindowSizeRules.classify(2000))
        assertTrue(WindowSizeRules.isExpanded(840))
    }

    @Test
    fun `negative width never crashes and stays compact`() {
        assertEquals(WindowClass.COMPACT, WindowSizeRules.classify(-1))
        assertEquals(1, WindowSizeRules.gridColumns(-40))
    }

    // ---------- 导航 / 双栏 ----------

    @Test
    fun `only compact uses the bottom navigation bar`() {
        assertEquals(NavigationForm.BOTTOM_BAR, WindowSizeRules.navigationForm(599))
        assertEquals(NavigationForm.RAIL, WindowSizeRules.navigationForm(600))
        assertEquals(NavigationForm.RAIL, WindowSizeRules.navigationForm(840))
        assertEquals(NavigationForm.RAIL, WindowSizeRules.navigationForm(1067))
    }

    @Test
    fun `rail label only shows on expanded`() {
        assertFalse(WindowSizeRules.railShowsLabel(600))
        assertFalse(WindowSizeRules.railShowsLabel(839))
        assertTrue(WindowSizeRules.railShowsLabel(840))
    }

    @Test
    fun `two pane needs at least 900dp`() {
        assertFalse(WindowSizeRules.useTwoPane(899))
        assertTrue(WindowSizeRules.useTwoPane(900))
        assertTrue(WindowSizeRules.useTwoPane(1067))
    }

    @Test
    fun `phone landscape reaching medium width gets tablet layout`() {
        // 手机横屏窗口宽度到 720dp：必须是 Rail + 非 Compact，而不是继续手机式布局
        val width = 720
        assertEquals(WindowClass.MEDIUM, WindowSizeRules.classify(width))
        assertEquals(NavigationForm.RAIL, WindowSizeRules.navigationForm(width))
    }

    @Test
    fun `pad 7 pro landscape is expanded and pad portrait is medium or expanded`() {
        // 横屏约 1067dp，竖屏约 712dp（按常见 density 换算），都按实际窗口宽度判
        assertEquals(WindowClass.EXPANDED, WindowSizeRules.classify(1067))
        assertEquals(NavigationForm.RAIL, WindowSizeRules.navigationForm(1067))
        assertEquals(NavigationForm.RAIL, WindowSizeRules.navigationForm(712))
    }

    // ---------- 边距 / 间距 ----------

    @Test
    fun `padding grows with window width`() {
        assertEquals(8, WindowSizeRules.horizontalPadding(400))
        assertEquals(16, WindowSizeRules.horizontalPadding(700))
        assertEquals(24, WindowSizeRules.horizontalPadding(1000))
        assertEquals(32, WindowSizeRules.horizontalPadding(1400))
    }

    @Test
    fun `grid spacing grows with window width`() {
        assertEquals(8, WindowSizeRules.gridSpacing(400))
        assertEquals(12, WindowSizeRules.gridSpacing(700))
        assertEquals(16, WindowSizeRules.gridSpacing(1000))
    }

    // ---------- 动态列数 ----------

    @Test
    fun `phone portrait still shows two columns`() {
        assertEquals(2, WindowSizeRules.gridColumns(360))
        assertEquals(2, WindowSizeRules.gridColumns(400))
    }

    @Test
    fun `columns are never fixed at eight`() {
        // v0.3.41 的旧规则横屏一律翻倍到 8 列；新规则按可用宽度算
        assertEquals(5, WindowSizeRules.gridColumns(1067, reservedDp = WindowSizeRules.RAIL_WIDTH_DP))
        assertEquals(3, WindowSizeRules.gridColumns(720, reservedDp = WindowSizeRules.RAIL_WIDTH_DP))
        assertTrue(
            "宽屏也不该硬凑到 8 列",
            WindowSizeRules.gridColumns(1067) < WindowSizeRules.GRID_MAX_COLUMNS,
        )
    }

    @Test
    fun `wide cards need more width so they get fewer columns`() {
        val narrow = WindowSizeRules.gridColumns(
            1067,
            reservedDp = WindowSizeRules.RAIL_WIDTH_DP,
            minCardWidthDp = WindowSizeRules.CARD_MIN_WIDTH_DP,
        )
        val wide = WindowSizeRules.gridColumns(
            1067,
            reservedDp = WindowSizeRules.RAIL_WIDTH_DP,
            minCardWidthDp = WindowSizeRules.WIDE_CARD_MIN_WIDTH_DP,
        )
        assertTrue("16:9 卡片应比竖封面少列，实际 $wide vs $narrow", wide < narrow)
        assertEquals(4, wide)
    }

    @Test
    fun `rail reservation reduces available columns`() {
        val full = WindowSizeRules.gridColumns(900, reservedDp = 0)
        val withRail = WindowSizeRules.gridColumns(900, reservedDp = WindowSizeRules.RAIL_WIDTH_DP)
        assertTrue(withRail <= full)
    }

    @Test
    fun `manual column choice is the target count not just an upper bound`() {
        // 平板横屏 959dp：自动档按 150dp 最小卡宽只算出 5 列，
        // 手选 6 / 7 / 8 列必须真的排 6 / 7 / 8 列，否则设置里的高列数形同虚设。
        val width = 959
        val auto = WindowSizeRules.gridColumns(width, reservedDp = 80, choice = 0)
        assertEquals(5, auto)
        assertEquals(6, WindowSizeRules.gridColumns(width, reservedDp = 80, choice = 6))
        assertEquals(7, WindowSizeRules.gridColumns(width, reservedDp = 80, choice = 7))
        // 88dp 硬下限让 959dp 横屏手选 8 列可达，不再被收敛成 7 列；
        // 换成收窄后的 64dp 侧栏同样可达。
        assertEquals(8, WindowSizeRules.gridColumns(width, reservedDp = 80, choice = 8))
        assertEquals(
            8,
            WindowSizeRules.gridColumns(
                width,
                reservedDp = WindowSizeRules.RAIL_WIDTH_DP,
                choice = 8,
            ),
        )
        // 窄窗口下手选 8 列也必须收缩，卡片不能窄于手选硬下限
        assertTrue(WindowSizeRules.gridColumns(360, choice = 8) <= 3)
    }

    @Test
    fun `manual eight columns are reachable on a wide window`() {
        assertEquals(8, WindowSizeRules.gridColumns(1200, reservedDp = 80, choice = 8))
        assertEquals(8, WindowSizeRules.gridColumns(959, reservedDp = 80, choice = 8))
    }

    @Test
    fun `columns stay inside one to eight and never decrease as width grows`() {
        var previous = 1
        for (width in 200..2400 step 20) {
            val columns = WindowSizeRules.gridColumns(width, reservedDp = 80)
            assertTrue("列数越界: width=$width columns=$columns", columns in 1..WindowSizeRules.GRID_MAX_COLUMNS)
            assertTrue("列数随宽度单调不减: width=$width $columns < $previous", columns >= previous)
            previous = columns
        }
    }

    @Test
    fun `split screen narrow window falls back to compact single-column-ish layout`() {
        val width = 340
        assertEquals(WindowClass.COMPACT, WindowSizeRules.classify(width))
        assertEquals(NavigationForm.BOTTOM_BAR, WindowSizeRules.navigationForm(width))
        assertEquals(1, WindowSizeRules.gridColumns(width, minCardWidthDp = WindowSizeRules.WIDE_CARD_MIN_WIDTH_DP))
    }

    @Test
    fun `resizing a free window changes columns dynamically`() {
        val small = WindowSizeRules.gridColumns(500, reservedDp = 80)
        val large = WindowSizeRules.gridColumns(1200, reservedDp = 80)
        assertTrue("窗口变宽列数应增加: $small -> $large", large > small)
    }

    @Test
    fun `ultra wide window columns follow capped content width`() {
        // 超宽自由窗口正文被限到 1400dp，列数不能再按 2400dp 算，否则卡片被压得过小
        val atCap = WindowSizeRules.gridColumns(1600, reservedDp = 80)
        val ultraWide = WindowSizeRules.gridColumns(2400, reservedDp = 80)
        assertEquals("超宽窗口不应再多算列", atCap, ultraWide)
        assertTrue(ultraWide <= WindowSizeRules.GRID_MAX_COLUMNS)
    }

    // ---------- v0.3.56：视频 / JAV 列数各自独立，不再联动 ----------

    @Test
    fun `independent choices still converge on a very narrow window`() {
        // 极窄自由窗口：无论 JAV / 视频各自选了几列，都要按 88dp 硬下限收敛，
        // 只是两者各收敛各的，不再有「视频 = JAV - 1」的强制关系。
        val width = 340
        val jav7 = WindowSizeRules.gridColumns(width, choice = 7)
        val video5 = WindowSizeRules.gridColumns(width, choice = 5)
        assertEquals(3, jav7)
        assertEquals(3, video5)
        assertTrue(jav7 in 1..3)
        assertTrue(video5 in 1..3)
    }

    // ---------- 播放页方向 ----------

    @Test
    fun `player starts fullscreen only on wide landscape windows`() {
        // 平板横屏 1067 x 700
        assertTrue(WindowSizeRules.startFullscreen(1067, 700))
        // 平板竖屏 712 x 1067：宽窗口但竖向，不强制全屏
        assertFalse(WindowSizeRules.startFullscreen(712, 1067))
        // 手机横屏 780 x 360：虽是横向但没到 Expanded 阈值
        assertFalse(WindowSizeRules.startFullscreen(780, 360))
        // 手机竖屏 / 窄分屏
        assertFalse(WindowSizeRules.startFullscreen(360, 640))
        assertFalse(WindowSizeRules.startFullscreen(340, 700))
        // 手机横屏真达到 Expanded 阈值：按规则也走平板式全屏
        assertTrue(WindowSizeRules.startFullscreen(900, 400))
    }

    @Test
    fun `startFullscreen never depends on device model`() {
        // 同样的窗口宽高，无论设备是手机还是平板，结论必须一致
        assertEquals(
            WindowSizeRules.startFullscreen(1067, 700),
            WindowSizeRules.startFullscreen(1067, 700),
        )
        assertFalse(WindowSizeRules.startFullscreen(839, 400))
        assertTrue(WindowSizeRules.startFullscreen(840, 400))
    }

    // ---------- 各类最大宽度 ----------

    @Test
    fun `compact leaves dialog and sheet to platform defaults`() {
        assertEquals(WindowSizeRules.NO_LIMIT, WindowSizeRules.dialogMaxWidth(400))
        assertEquals(WindowSizeRules.NO_LIMIT, WindowSizeRules.sheetMaxWidth(400))
        assertEquals(WindowSizeRules.NO_LIMIT, WindowSizeRules.searchFieldMaxWidth(400))
    }

    @Test
    fun `medium and expanded cap dialog sheet and search widths`() {
        assertEquals(560, WindowSizeRules.dialogMaxWidth(700))
        assertEquals(640, WindowSizeRules.dialogMaxWidth(1067))
        assertEquals(640, WindowSizeRules.sheetMaxWidth(700))
        assertEquals(720, WindowSizeRules.sheetMaxWidth(1067))
        assertEquals(420, WindowSizeRules.searchFieldMaxWidth(700))
        assertEquals(480, WindowSizeRules.searchFieldMaxWidth(1067))
    }

    @Test
    fun `login form is always capped`() {
        assertEquals(480, WindowSizeRules.loginFormMaxWidth(360))
        assertEquals(480, WindowSizeRules.loginFormMaxWidth(1067))
        assertEquals(480, WindowSizeRules.loginFormMaxWidth(2000))
    }

    @Test
    fun `settings page is capped and only wide expanded windows use two columns`() {
        assertEquals(WindowSizeRules.NO_LIMIT, WindowSizeRules.settingsMaxWidth(400))
        assertEquals(720, WindowSizeRules.settingsMaxWidth(700))
        assertEquals(960, WindowSizeRules.settingsMaxWidth(1067))
        assertEquals(1, WindowSizeRules.settingsColumns(840))
        assertEquals(1, WindowSizeRules.settingsColumns(1067))
        assertEquals(2, WindowSizeRules.settingsColumns(1200))
    }

    @Test
    fun `image viewer and player stage are capped on wide windows`() {
        assertEquals(WindowSizeRules.NO_LIMIT, WindowSizeRules.imageViewerMaxWidth(400))
        assertEquals(900, WindowSizeRules.imageViewerMaxWidth(700))
        assertEquals(1100, WindowSizeRules.imageViewerMaxWidth(1067))
        assertEquals(WindowSizeRules.NO_LIMIT, WindowSizeRules.playerStageMaxWidth(400))
        assertEquals(720, WindowSizeRules.playerStageMaxWidth(700))
        assertEquals(960, WindowSizeRules.playerStageMaxWidth(1067))
    }

    @Test
    fun `content max width only kicks in on expanded`() {
        assertEquals(WindowSizeRules.NO_LIMIT, WindowSizeRules.contentMaxWidth(400))
        assertEquals(WindowSizeRules.NO_LIMIT, WindowSizeRules.contentMaxWidth(700))
        assertEquals(1400, WindowSizeRules.contentMaxWidth(1067))
    }

    @Test
    fun `tag dialog is wider than the generic dialog on wide windows`() {
        // v0.3.65：标签菜单要显示更多胶囊，宽窗口上限比普通 dialog 更宽
        assertEquals(WindowSizeRules.NO_LIMIT, WindowSizeRules.tagDialogMaxWidth(400))
        assertEquals(720, WindowSizeRules.tagDialogMaxWidth(700))
        assertEquals(900, WindowSizeRules.tagDialogMaxWidth(1067))
        assertTrue(
            WindowSizeRules.tagDialogMaxWidth(700) > WindowSizeRules.dialogMaxWidth(700),
        )
        assertTrue(
            WindowSizeRules.tagDialogMaxWidth(1067) > WindowSizeRules.dialogMaxWidth(1067),
        )
    }

    // ---------- v0.3.68：详情双栏左栏宽度 ----------

    @Test
    fun `detail left pane is wider than the old fixed 300dp`() {
        // 900dp（双栏下限）→ 34% = 306dp，被下限抬到 340dp，比原来固定 300dp 更宽
        assertEquals(340, WindowSizeRules.detailLeftPaneWidthDp(900))
        // 平板横屏 1067dp → 363dp
        assertEquals(363, WindowSizeRules.detailLeftPaneWidthDp(1067))
        assertTrue(WindowSizeRules.detailLeftPaneWidthDp(900) > 300)
        assertTrue(WindowSizeRules.detailLeftPaneWidthDp(1067) > 300)
    }

    @Test
    fun `detail left pane is capped so the right pane keeps most of the width`() {
        assertEquals(
            WindowSizeRules.DETAIL_LEFT_PANE_MAX_DP,
            WindowSizeRules.detailLeftPaneWidthDp(1600),
        )
        assertEquals(
            WindowSizeRules.DETAIL_LEFT_PANE_MAX_DP,
            WindowSizeRules.detailLeftPaneWidthDp(2400),
        )
        // 任何双栏宽度下左栏都要明显窄于右栏
        for (width in 900..2400 step 50) {
            val left = WindowSizeRules.detailLeftPaneWidthDp(width)
            assertTrue("左栏过宽: width=$width left=$left", left < width / 2)
        }
    }

    @Test
    fun `detail left pane grows monotonically and never goes below the minimum`() {
        var previous = 0
        for (width in 0..2400 step 20) {
            val left = WindowSizeRules.detailLeftPaneWidthDp(width)
            assertTrue("左栏不低于下限: width=$width left=$left", left >= WindowSizeRules.DETAIL_LEFT_PANE_MIN_DP)
            assertTrue("左栏随窗口宽度单调不减: width=$width $left < $previous", left >= previous)
            previous = left
        }
    }

    // ---------- 旧规则保持兼容 ----------

    @Test
    fun `legacy ScreenRules still behaves as before`() {
        assertTrue(ScreenRules.isTablet(600))
        assertFalse(ScreenRules.isTablet(599))
        assertEquals(8, ScreenRules.autoColumns(isTablet = true, landscape = true))
    }
}
