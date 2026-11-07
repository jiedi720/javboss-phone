package com.javboss.phone.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.data.NavigationForm
import com.javboss.phone.data.PrivacyMask
import com.javboss.phone.data.WindowSizeRules
import com.javboss.phone.ui.components.LocalCoverMask
import com.javboss.phone.ui.components.adaptiveContentWidth
import com.javboss.phone.ui.components.rememberNavigationForm
import com.javboss.phone.ui.components.rememberPageToolsState
import com.javboss.phone.ui.components.rememberRailCompactMode
import com.javboss.phone.ui.components.rememberWindowWidthDp
import com.javboss.phone.ui.favorites.FavoritesScreen
import com.javboss.phone.ui.settings.SettingsScreen
import com.javboss.phone.ui.videos.VideosScreen
import com.javboss.phone.ui.videos.VideosViewModel
import com.javboss.phone.ui.works.LibraryTab
import com.javboss.phone.ui.works.WorksScreen
import com.javboss.phone.ui.works.WorksViewModel

private data class MainTab(val label: String, val icon: ImageVector)

private val mainTabs = listOf(
    MainTab("视频", Icons.Filled.PlayCircle),
    MainTab("JAV", Icons.Filled.Collections),
    MainTab("收藏夹", Icons.Filled.Favorite),
    MainTab("设置", Icons.Filled.Settings),
)

/** 四个视角在原左侧栏里的紧凑图标，只做映射，不改变视角语义 */
private fun libraryTabIcon(tab: LibraryTab): ImageVector = when (tab) {
    LibraryTab.WORKS -> Icons.Filled.Movie
    LibraryTab.IDOLS -> Icons.Filled.Person
    LibraryTab.STUDIOS -> Icons.Filled.Business
    LibraryTab.SERIES -> Icons.Filled.CollectionsBookmark
}

/**
 * 主页：4 个 tab（视频 / JAV / 收藏夹 / 设置）。
 *
 * 导航形式只由「当前窗口实际宽度」决定：
 * - Compact → 底部 NavigationBar（手机竖屏、窄分屏，布局与旧版完全一致）；
 * - Medium / Expanded → 竖向 NavigationRail（平板、手机横屏达到阈值、宽自由窗口）。
 *   Rail 固定 64dp 宽，绝不横向拉伸占满平板。
 *
 * v0.3.50：横屏 / Rail 模式下，JAV 与视频页原来顶部那一栏功能（视角 / 排序 / 搜索）
 * 全部并入这条**原左侧栏**，内容区顶部不再有工具条；不新增第二条竖向工具条，
 * 也不加宽 rail。Compact 手机竖屏仍走原来的页面顶栏 + 底部导航。
 *
 * Scaffold 与页面内容的组合位置在两种形式下都保持稳定：切换导航形式时
 * `when (tab)` 这棵子树不会被销毁，配合 configChanges 不重建 Activity，
 * 当前 tab、滚动位置、已加载数据与进行中的操作都能保留。
 */
@Composable
fun MainScreen(
    /** 冷启动时默认落在哪个 tab，来自设置里的「启动显示界面」（默认 JAV = 1） */
    startTab: Int = 1,
    onOpenDetail: (videoId: Long, code: String?) -> Unit,
    onPlay: (videoId: Long, code: String?) -> Unit,
    onOpenJavList: (title: String, idolIds: String, tagIds: String, studioId: Long, seriesId: Long) -> Unit,
    onFavGroupClick: (groupId: Long, name: String) -> Unit,
    onSessionExpired: () -> Unit,
    onLogout: () -> Unit,
) {
    // rememberSaveable：转屏/去详情再回来保持当前 tab；从桌面冷启动没有保存值时才用 startTab
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    val widthDp = rememberWindowWidthDp()
    val rail = rememberNavigationForm() == NavigationForm.RAIL
    // v0.3.51：紧凑模式开启后强制只画图标；关闭时沿用「Expanded 才常驻文字」的旧规则
    val railCompact = rememberRailCompactMode()
    val railShowsLabel = WindowSizeRules.railShowLabel(widthDp, railCompact)

    // 页面工具的开关提到这里：原左侧栏与页面顶栏读写同一份状态，
    // 旋转 / 分屏 / 自由窗口切换导航形式时搜索框、排序菜单不会重置。
    val tools = rememberPageToolsState()
    // 两个列表 ViewModel 提到 MainScreen：rail 里要读当前视角、切视角、打开排序前
    // 加载筛选项、收起搜索时清空关键词。作用域仍是同一个 ViewModelStoreOwner，
    // 和原先在各自 Screen 里 viewModel() 拿到的是同一个实例。
    val worksViewModel: WorksViewModel = viewModel()
    val videosViewModel: VideosViewModel = viewModel()

    Scaffold(
        bottomBar = {
            if (!rail) {
                NavigationBar(
                    // v0.3.54：72dp 仍比 M3 默认 80dp 紧凑，但图标上方要有冗余。
                    // 新增的 8dp 全部作为 item 顶部 padding，图标 / 文字 / 下方留白不动。
                    modifier = Modifier.height(WindowSizeRules.NAV_BAR_COMPACT_HEIGHT_DP.dp),
                ) {
                    mainTabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            modifier = Modifier.padding(top = WindowSizeRules.NAV_BAR_TOP_PADDING_DP.dp),
                            selected = tab == index,
                            onClick = { tools.closeSheets(); tab = index },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (rail) {
                NavigationRail(
                    modifier = Modifier.width(WindowSizeRules.RAIL_WIDTH_DP.dp),
                    // Scaffold 的 padding 已经处理过状态栏；rail 自己再按默认 insets
                    // 加一遍会在主 tab 上方叠出一块空白（用户红框处），这里清零。
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    mainTabs.forEachIndexed { index, item ->
                        if (railCompact) {
                            // v0.3.52：M3 NavigationRailItem 隐藏 label 后仍占约 60dp，
                            // 图标停在原位、下面照旧要滚动。紧凑模式改用和工具项同款的
                            // 极简图标按钮，压到 32dp，文字腾出的空间被回收、图标整体上移。
                            RailToolButton(
                                icon = item.icon,
                                label = item.label,
                                showLabel = false,
                                compact = true,
                                selected = tab == index,
                                onClick = { tools.closeSheets(); tab = index },
                            )
                        } else {
                            val itemLabel: @Composable (() -> Unit)? =
                                if (railShowsLabel) { { Text(item.label) } } else null
                            NavigationRailItem(
                                selected = tab == index,
                                onClick = { tools.closeSheets(); tab = index },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = itemLabel,
                                alwaysShowLabel = railShowsLabel,
                            )
                        }
                    }

                    // 分隔线以下才放「当前页面功能」；收藏夹 / 设置页没有顶栏功能，不占位。
                    // 功能区单独可滚做兜底：普通模式在矮窗口里也够得到全部工具项；
                    // v0.3.52 紧凑模式把每项压到 32dp 后，横屏基本一屏放得下，不必滚动。
                    // 不把主 tab 挤出去，也不需要给 rail 加宽或另起第二条工具条。
                    if (tab == 0 || tab == 1) {
                        HorizontalDivider(
                            Modifier.padding(
                                horizontal = 12.dp,
                                vertical = if (railCompact) {
                                    WindowSizeRules.RAIL_COMPACT_DIVIDER_PADDING_DP.dp
                                } else 6.dp,
                            )
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            when (tab) {
                                0 -> {
                                    RailToolButton(
                                        icon = Icons.AutoMirrored.Filled.Sort,
                                        label = "排序",
                                        showLabel = railShowsLabel,
                                        compact = railCompact,
                                        onClick = { tools.videosSortOpen = true },
                                    )
                                    RailToolButton(
                                        icon = if (tools.videosSearchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                        label = if (tools.videosSearchOpen) "收起" else "搜索",
                                        showLabel = railShowsLabel,
                                        compact = railCompact,
                                        onClick = {
                                            val next = !tools.videosSearchOpen
                                            tools.videosSearchOpen = next
                                            if (!next && videosViewModel.uiState.search.isNotEmpty()) {
                                                videosViewModel.onSearchChange("")
                                            }
                                        },
                                    )
                                }
                                1 -> {
                                    LibraryTab.entries.forEach { libTab ->
                                        RailToolButton(
                                            icon = libraryTabIcon(libTab),
                                            label = libTab.label,
                                            showLabel = railShowsLabel,
                                            compact = railCompact,
                                            selected = worksViewModel.uiState.tab == libTab,
                                            onClick = { worksViewModel.onTabChange(libTab) },
                                        )
                                    }
                                    HorizontalDivider(
                                        Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = if (railCompact) {
                                                WindowSizeRules.RAIL_COMPACT_DIVIDER_PADDING_DP.dp
                                            } else 4.dp,
                                        )
                                    )
                                    // 与原顶栏一致：片商 / 系列视角没有排序入口
                                    if (worksViewModel.uiState.tab == LibraryTab.WORKS ||
                                        worksViewModel.uiState.tab == LibraryTab.IDOLS
                                    ) {
                                        RailToolButton(
                                            icon = Icons.AutoMirrored.Filled.Sort,
                                            label = "排序",
                                            showLabel = railShowsLabel,
                                            compact = railCompact,
                                            onClick = {
                                                if (worksViewModel.uiState.tab == LibraryTab.WORKS) {
                                                    worksViewModel.loadFilterOptions()
                                                }
                                                tools.worksSortOpen = true
                                            },
                                        )
                                    }
                                    RailToolButton(
                                        icon = if (tools.worksSearchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                        label = if (tools.worksSearchOpen) "收起" else "搜索",
                                        showLabel = railShowsLabel,
                                        compact = railCompact,
                                        onClick = {
                                            val next = !tools.worksSearchOpen
                                            tools.worksSearchOpen = next
                                            if (!next && worksViewModel.uiState.search.isNotEmpty()) {
                                                worksViewModel.onSearchChange("")
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                // 超宽窗口把正文限宽居中，避免一米长的文字行；Compact/Medium 不加任何约束
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        Modifier
                            .adaptiveContentWidth()
                            .fillMaxWidth()
                            .fillMaxHeight(),
                    ) {
                        // 锁屏触发的自动打码：给一个明确的恢复入口，否则解锁后满屏毛玻璃没处解
                        AutoMaskBanner()
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                0 -> VideosScreen(
                                    onVideoClick = onOpenDetail,
                                    onPlay = onPlay,
                                    onSessionExpired = onSessionExpired,
                                    viewModel = videosViewModel,
                                    tools = tools,
                                    toolsInRail = rail,
                                )
                                1 -> WorksScreen(
                                    onOpenDetail = onOpenDetail,
                                    onPlay = onPlay,
                                    onOpenJavList = onOpenJavList,
                                    onSessionExpired = onSessionExpired,
                                    viewModel = worksViewModel,
                                    tools = tools,
                                    toolsInRail = rail,
                                )
                                2 -> FavoritesScreen(
                                    onGroupClick = onFavGroupClick,
                                    onSessionExpired = onSessionExpired,
                                )
                                else -> SettingsScreen(onLogout = onLogout)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 原左侧栏里的图标按钮：不额外占 rail 宽度。
 * 普通模式是「图标 + 一行短标签」44dp 高（宽度规则隐藏文字时 40dp）；
 * v0.3.52 紧凑模式把主 tab 与工具项统一压到 32dp、去掉垂直 padding，
 * 文字腾出的空间被真正回收，图标整体上移，一屏放得下全部按钮。
 * 选中态用于标出当前主 tab / JAV 视角；排序 / 搜索这类动作按钮不高亮。
 * 紧凑模式下文字不画，但 [label] 仍作为 contentDescription 保留，语义树仍可读。
 */
@Composable
private fun RailToolButton(
    icon: ImageVector,
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    selected: Boolean = false,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = if (compact) 0.dp else 2.dp)
            .height(WindowSizeRules.railItemHeightDp(showLabel, compact).dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = content)
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
        }
    }
}

/**
 * 「锁屏后已自动打码」提示条。
 * 只在自动打码（而不是设置里手动常开）时出现——手动常开时点「显示封面」也不会变。
 */
@Composable
private fun AutoMaskBanner() {
    // 和封面共用根部下发的同一份状态，保证「看到毛玻璃」和「看到恢复入口」永远同步
    val mask = LocalCoverMask.current
    if (!mask.masked || mask.alwaysOn) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "锁屏后已自动给封面打码",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = PrivacyMask::reveal) { Text("显示封面") }
    }
}
