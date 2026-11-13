package com.javboss.phone.ui.javlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.data.JavPrefixRules
import com.javboss.phone.data.TagColorRules
import com.javboss.phone.data.TagManageRules
import com.javboss.phone.data.api.dto.JavDto
import com.javboss.phone.data.api.dto.TagDto
import com.javboss.phone.ui.components.CenterMessage
import com.javboss.phone.ui.components.CoverAdjustDialog
import com.javboss.phone.ui.components.GridFooter
import com.javboss.phone.ui.components.VideoPickDialog
import com.javboss.phone.ui.components.FavoriteTarget
import com.javboss.phone.ui.components.WorkCard
import com.javboss.phone.ui.components.RatingDialog
import com.javboss.phone.ui.components.rememberAdaptivePadding
import com.javboss.phone.ui.components.rememberGridColumns
import com.javboss.phone.ui.components.rememberGridSpacing

/** 演员/标签/片商/系列点击落地页：对应过滤条件的作品瀑布流 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun JavListScreen(
    title: String,
    idolIds: String,
    tagIds: String,
    studioId: Long,
    seriesId: Long,
    onOpenDetail: (videoId: Long, code: String?) -> Unit,
    /** code 原样带到播放页，保证竖屏背景用的就是这张 JAV 封面 */
    onPlay: (videoId: Long, code: String?) -> Unit,
    /** v0.3.62：封面左下角标签点击，按该标签筛选作品 */
    onOpenJavList: (title: String, idolIds: String, tagIds: String, studioId: Long, seriesId: Long) -> Unit,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: JavListViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val gridState = rememberLazyGridState()
    var playPickerTarget by remember { mutableStateOf<JavDto?>(null) }
    // v0.3.70：女优 / 标签 / 片商 / 系列点进来的也是作品封面，左上角同样要有评分按钮
    var ratingTarget by remember { mutableStateOf<JavDto?>(null) }
    // v0.3.74：这里也是 JAV 封面，长按同样打开服务端「调整封面」弹窗
    var coverAdjustTarget by remember { mutableStateOf<JavDto?>(null) }

    LaunchedEffect(Unit) { viewModel.start(idolIds, tagIds, studioId, seriesId) }
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastIndex ->
                val total = gridState.layoutInfo.totalItemsCount
                if (total > 0 && lastIndex >= total - 4) viewModel.loadMore()
            }
    }
    // v0.3.75：切换番号前缀后回到列表顶部，避免停在上一个前缀的滚动位置
    LaunchedEffect(state.prefix) {
        if (state.items.isNotEmpty()) gridState.scrollToItem(0)
    }

    // 点封面：不管挂了几个视频文件都直接进作品详情（详情页自己会列出全部文件版本），
    // 只有点封面右下角的播放钮才需要选具体文件
    val onWorkClick: (JavDto) -> Unit = { jav ->
        onOpenDetail(jav.videos.firstOrNull()?.id ?: 0, jav.code)
    }

    // 封面右下角播放钮：一个视频直接起播，多个先选
    val onWorkPlay: (JavDto) -> Unit = { jav ->
        when {
            jav.videos.isEmpty() -> Unit
            jav.videos.size == 1 -> onPlay(jav.videos.first().id, jav.code)
            else -> playPickerTarget = jav
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.items.isEmpty() && state.loading -> CenterMessage("加载中…")
                state.items.isEmpty() && state.error != null -> CenterMessage(state.error)
                state.items.isEmpty() && !state.loading -> CenterMessage("没有作品")
                else -> LazyVerticalGrid(
                    // 普通网格是「行优先」：1 2 3 / 4 5 6 从左往右排。
                    // 之前用的 staggered grid 是「列优先」，序号会竖着一列列往下走，
                    // 看上去就不是从左到右的顺序了。
                    columns = GridCells.Fixed(rememberGridColumns()),
                    state = gridState,
                    contentPadding = PaddingValues(rememberAdaptivePadding()),
                    horizontalArrangement = Arrangement.spacedBy(rememberGridSpacing()),
                    verticalArrangement = Arrangement.spacedBy(rememberGridSpacing()),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // v0.3.75：片商落地页顶部显示服务端同款「番号 (N)」分组筛选。
                    // 数据源是 GET /jav/studios/{id} 的 code_prefixes（Web 片商详情弹窗 `Ak`），
                    // 只在 studioId > 0 时加载；女优 / 标签 / 系列落地页不显示这一区。
                    if (studioId > 0L &&
                        (state.prefixes.isNotEmpty() || state.prefixesLoading || state.prefixesError != null)
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            when {
                                state.prefixesLoading -> Text(
                                    "番号加载中…",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                state.prefixesError != null -> Text(
                                    "番号：${state.prefixesError}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                else -> StudioPrefixSection(
                                    state = state,
                                    onPrefixChange = viewModel::onPrefixChange,
                                    onSortToggle = viewModel::onPrefixSortToggle,
                                )
                            }
                        }
                    }
                    items(state.items, key = { it.id }) { jav ->
                        val playAction: (() -> Unit)? =
                            if (jav.videos.isEmpty()) null else ({ onWorkPlay(jav) })
                        WorkCard(
                            coverUrl = viewModel.coverUrl(jav.code),
                            code = jav.code,
                            title = jav.title,
                            onClick = { onWorkClick(jav) },
                            onLongClick = { coverAdjustTarget = jav },
                            cropPosition = jav.coverCropPosition,
                            favoriteCount = jav.favoriteCount,
                            favoriteTarget = FavoriteTarget.jav(jav.id),
                            watchedMs = jav.watchedMs,
                            onPlay = playAction,
                            rating = jav.favoriteRating,
                            onRatingClick = { ratingTarget = jav },
                            customTags = TagColorRules.customTags(jav.tags),
                            onCustomTagClick = { tag: TagDto ->
                                onOpenJavList(
                                    TagManageRules.filterTitle(tag.name),
                                    "",
                                    tag.id.toString(),
                                    0,
                                    0,
                                )
                            },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GridFooter(state.loadingMore, state.endReached, state.total)
                    }
                }
            }
        }
    }

    ratingTarget?.let { jav ->
        RatingDialog(
            code = jav.code,
            current = jav.favoriteRating,
            onDismiss = { ratingTarget = null },
            onSave = { value ->
                viewModel.updateRating(jav.id, value)
                ratingTarget = null
            },
        )
    }

    // v0.3.74：女优 / 标签 / 片商 / 系列点开的列表，长按封面也是「调整封面」
    coverAdjustTarget?.let { jav ->
        CoverAdjustDialog(
            code = jav.code,
            coverUrl = viewModel.coverUrl(jav.code),
            initialPosition = jav.coverCropPosition,
            onDismiss = { coverAdjustTarget = null },
            onSave = { position -> viewModel.updateCoverCropPosition(jav.id, position) },
        )
    }

    playPickerTarget?.let { jav ->
        VideoPickDialog(
            title = jav.code,
            hint = "选择要播放的视频文件",
            options = jav.videos.map { it.id to it.filename },
            onPick = { videoId ->
                playPickerTarget = null
                onPlay(videoId, jav.code)
            },
            onDismiss = { playPickerTarget = null },
        )
    }
}

/**
 * v0.3.75：片商「番号」分组筛选区，对齐 Web 片商详情弹窗 `jk` / `Mk`：
 * 标题「番号 (N)」+ 蓝色排序小胶囊（名称 ↔ 作品数）+ 流式前缀胶囊（前缀 + 作品数）。
 * 点击胶囊把 prefix 与 studioId 一起带到 /jav，实现「查看 xxx 的全部作品」。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioPrefixSection(
    state: JavListUiState,
    onPrefixChange: (String) -> Unit,
    onSortToggle: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("番号 (${state.prefixes.size})", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(10.dp))
            FilterChip(
                selected = true,
                onClick = onSortToggle,
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                label = {
                    Text(
                        JavPrefixRules.sortLabel(state.prefixByName),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.prefix.isEmpty(),
                onClick = { onPrefixChange("") },
                label = { Text("全部") },
            )
            JavPrefixRules.sort(state.prefixes, state.prefixByName).forEach { p ->
                FilterChip(
                    selected = state.prefix == p.prefix,
                    onClick = { onPrefixChange(if (state.prefix == p.prefix) "" else p.prefix) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.prefix,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${p.workCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}
