package com.javboss.phone.ui.videos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.ui.components.CenterMessage
import com.javboss.phone.ui.components.CompactIconButton
import com.javboss.phone.ui.components.GridFooter
import com.javboss.phone.ui.components.VideoCard

/** 视频 tab：两列网格 + 顶栏（与 JAV 页同款排序 / 搜索按钮）+ 下拉刷新 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    onVideoClick: (videoId: Long, code: String?) -> Unit,
    onPlay: (videoId: Long, code: String?) -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: VideosViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val gridState = rememberLazyGridState()
    // 搜索框点搜索图标才展开；排序按钮点开底部菜单 —— 与 JAV 页一致
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }

    // 滚动到底部附近时自动加载下一页
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastIndex ->
                val total = gridState.layoutInfo.totalItemsCount
                if (total > 0 && lastIndex >= total - 4) viewModel.loadMore()
            }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：左边保留本页标题（JAV 页左边是四个视角按钮，这里按需求不强行一致），
        // 右边两个紧凑图标按钮与 JAV 页完全一致：排序 / 筛选、搜索（展开后变关闭）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("视频", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            CompactIconButton(Icons.AutoMirrored.Filled.Sort, "排序 / 筛选") {
                showSort = true
            }
            CompactIconButton(
                if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                if (searchOpen) "收起搜索" else "搜索",
            ) {
                searchOpen = !searchOpen
                if (!searchOpen && state.search.isNotEmpty()) viewModel.onSearchChange("")
            }
        }

        // 搜索框：点搜索图标才展开（与 JAV 页一致）
        if (searchOpen) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::onSearchChange,
                placeholder = { Text("搜索视频") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
            )
        }

        PullToRefreshBox(
            isRefreshing = state.loading && state.videos.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.videos.isEmpty() && state.loading -> CenterMessage("加载中…")
                state.videos.isEmpty() && state.error != null -> CenterMessage(state.error)
                state.videos.isEmpty() -> CenterMessage("没有视频")
                // 等高行网格：两列严格对齐，不会像瀑布流那样各列独立堆叠、越差越多
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.videos, key = { it.id }) { video ->
                        VideoCard(
                            thumbnailUrl = viewModel.thumbnailUrl(video.id),
                            title = video.displayTitle,
                            durationSec = video.durationSec,
                            watchedMs = video.watchedMs,
                            // 服务端视频自身 tags + 关联作品 jav.tags，合并去重后给卡片左上角显示
                            tags = (video.tags + video.jav?.tags.orEmpty())
                                .distinctBy { tag ->
                                    if (tag.id > 0) "id:${tag.id}" else "name:${tag.name}"
                                }
                                .map { it.name }
                                .filter { it.isNotBlank() },
                            onPlay = { onPlay(video.id, video.jav?.code) },
                            onClick = { onVideoClick(video.id, video.jav?.code) },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GridFooter(state.loadingMore, state.endReached, state.total)
                    }
                }
            }
        }
    }

    // 排序菜单：与 JAV 页一样用底部弹层承载，选项仍是视频页原有的 8 个排序值，
    // 选中项高亮；点击即应用并关闭，不改变排序逻辑与服务端参数。
    if (showSort) {
        ModalBottomSheet(onDismissRequest = { showSort = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text("排序 / 筛选", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                VideoSort.entries.forEach { sort ->
                    val selected = state.sort == sort
                    val background = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    }
                    val foreground = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(background)
                            .clickable {
                                showSort = false
                                viewModel.onSortChange(sort)
                            }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            sort.label,
                            color = foreground,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
