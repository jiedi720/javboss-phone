package com.javboss.phone.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.javboss.phone.data.NavigationForm
import com.javboss.phone.data.PrivacyMask
import com.javboss.phone.data.WindowSizeRules
import com.javboss.phone.ui.components.LocalCoverMask
import com.javboss.phone.ui.components.adaptiveContentWidth
import com.javboss.phone.ui.components.rememberNavigationForm
import com.javboss.phone.ui.components.rememberWindowWidthDp
import com.javboss.phone.ui.favorites.FavoritesScreen
import com.javboss.phone.ui.settings.SettingsScreen
import com.javboss.phone.ui.videos.VideosScreen
import com.javboss.phone.ui.works.WorksScreen

private data class MainTab(val label: String, val icon: ImageVector)

private val mainTabs = listOf(
    MainTab("视频", Icons.Filled.PlayCircle),
    MainTab("JAV", Icons.Filled.Collections),
    MainTab("收藏夹", Icons.Filled.Favorite),
    MainTab("设置", Icons.Filled.Settings),
)

/**
 * 主页：4 个 tab（视频 / JAV / 收藏夹 / 设置）。
 *
 * 导航形式只由「当前窗口实际宽度」决定：
 * - Compact → 底部 NavigationBar（手机竖屏、窄分屏，布局与旧版完全一致）；
 * - Medium / Expanded → 竖向 NavigationRail（平板、手机横屏达到阈值、宽自由窗口）。
 *   Rail 固定 80dp 宽，绝不横向拉伸占满平板。
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
    val railShowsLabel = WindowSizeRules.railShowsLabel(widthDp)

    Scaffold(
        bottomBar = {
            if (!rail) {
                NavigationBar {
                    mainTabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
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
                ) {
                    mainTabs.forEachIndexed { index, item ->
                        val itemLabel: @Composable (() -> Unit)? =
                            if (railShowsLabel) { { Text(item.label) } } else null
                        NavigationRailItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = itemLabel,
                            alwaysShowLabel = railShowsLabel,
                        )
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
                                )
                                1 -> WorksScreen(
                                    onOpenDetail = onOpenDetail,
                                    onPlay = onPlay,
                                    onOpenJavList = onOpenJavList,
                                    onSessionExpired = onSessionExpired,
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
