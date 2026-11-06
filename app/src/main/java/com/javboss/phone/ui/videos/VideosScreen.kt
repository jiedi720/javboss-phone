package com.javboss.phone.ui.videos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.ui.components.CenterMessage
import com.javboss.phone.ui.components.PageToolsState
import com.javboss.phone.ui.components.CompactIconButton
import com.javboss.phone.ui.components.GridFooter
import com.javboss.phone.ui.components.VideoCard
import com.javboss.phone.ui.components.adaptiveSearchWidth
import com.javboss.phone.ui.components.adaptiveSheetWidth
import com.javboss.phone.ui.components.rememberAdaptivePadding
import com.javboss.phone.ui.components.rememberGridSpacing
import com.javboss.phone.ui.components.rememberVideoGridColumns

/** 视频 tab：自适应列网格 + 顶栏（与 JAV 页同款排序 / 搜索按钮）+ 下拉刷新 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    onVideoClick: (videoId: Long, code: String?) -> Unit,
    onPlay: (videoId: Long, code: String?) -> Unit,
    onSessionExpired: () -> Unit,
    /** v0.3.50：横屏（Rail）时顶栏功能并入原左侧栏，开关由 MainScreen 统一下发 */
    tools: PageToolsState,
    toolsInRail: Boolean,
    viewModel: VideosViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val gridState = rememberLazyGridState()
    // 两个开关提到 MainScreen：顶部按钮和原左侧栏写的是同一份状态，
    // 切换导航形式 / 旋转时搜索框与排序菜单不会重置。
    val searchOpen = tools.videosSearchOpen
    val showSort = tools.videosSortOpen

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
        // v0.3.50：Rail 模式下这一栏功能并入原左侧 NavigationRail，
        // 内容区顶部不再画工具条；Compact 手机竖屏保持原样。
        if (!toolsInRail) {
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
                    tools.videosSortOpen = true
                }
                CompactIconButton(
                    if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                    if (searchOpen) "收起搜索" else "搜索",
                ) {
                    val next = !tools.videosSearchOpen
                    tools.videosSearchOpen = next
                    if (!next && state.search.isNotEmpty()) viewModel.onSearchChange("")
                }
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
                    // widthIn 必须在 fillMaxWidth 之前：先限宽，再在限宽范围内铺满，
                    // 反过来 fillMaxWidth 会把约束钉死成整屏宽，widthIn 就失效了
                    .adaptiveSearchWidth()
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
            )
        }

        // 目录筛选提示：点卡片第二行文件夹路径后显示，可一键清除回到全部视频
        if (state.directoryId != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "目录：${state.subdirectory}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::clearDirectoryFilter) { Text("清除") }
            }
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
                // 等高行网格：视频是 16:9 横版封面，每行恒比 JAV / 作品 / 收藏夹少一列
                else -> LazyVerticalGrid(
                    // 以 JAV 列数为基准减一，同一窗口 / 同一份手选上限，差值稳定；
                    // 极窄窗口保底 1 列，不按设备或方向另算一套最小宽度
                    columns = GridCells.Fixed(rememberVideoGridColumns()),
                    state = gridState,
                    contentPadding = PaddingValues(rememberAdaptivePadding()),
                    horizontalArrangement = Arrangement.spacedBy(rememberGridSpacing()),
                    verticalArrangement = Arrangement.spacedBy(rememberGridSpacing()),
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
                            // 第二行文件夹路径：点它按 directory_id + subdirectory 筛该目录全部视频
                            subdirectory = video.subdirectory,
                            onSubdirectoryClick = if (video.effectiveDirectoryId > 0) {
                                {
                                    viewModel.filterByDirectory(
                                        video.effectiveDirectoryId,
                                        video.subdirectory,
                                    )
                                }
                            } else null,
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
        ModalBottomSheet(onDismissRequest = { tools.videosSortOpen = false }) {
            // 宽窗口里把内容收窄并居中，避免排序项横跨整块平板屏
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .adaptiveSheetWidth()
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
                                    tools.videosSortOpen = false
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
}
