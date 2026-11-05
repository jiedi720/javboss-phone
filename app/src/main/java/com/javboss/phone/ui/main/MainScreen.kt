package com.javboss.phone.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import com.javboss.phone.data.PrivacyMask
import com.javboss.phone.ui.components.LocalCoverMask
import com.javboss.phone.ui.favorites.FavoritesScreen
import com.javboss.phone.ui.settings.SettingsScreen
import com.javboss.phone.ui.videos.VideosScreen
import com.javboss.phone.ui.works.WorksScreen

/**
 * 主页：底部三 tab（视频 / 作品 / 收藏夹）+ 设置页。
 * tab 切换只是本地状态，ViewModel 挂在 main 路由的 NavBackStackEntry 上，
 * 切 tab 不丢滚动位置和已加载数据。
 * 登录相关操作（退出登录）已收进设置页，不再占底栏位置。
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.PlayCircle, contentDescription = "视频") },
                    label = { Text("视频") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Collections, contentDescription = "JAV") },
                    label = { Text("JAV") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = "收藏夹") },
                    label = { Text("收藏夹") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // 锁屏触发的自动打码：给一个明确的恢复入口，否则解锁后满屏毛玻璃没处解
            AutoMaskBanner()
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> VideosScreen(
                        onVideoClick = onOpenDetail,
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
