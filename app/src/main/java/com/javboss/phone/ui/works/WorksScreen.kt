package com.javboss.phone.ui.works

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.data.api.dto.JavDto
import com.javboss.phone.ui.components.CenterMessage
import com.javboss.phone.ui.components.EntityCard
import com.javboss.phone.ui.components.GridFooter
import com.javboss.phone.ui.components.IdolCard
import com.javboss.phone.ui.components.RatingDialog
import com.javboss.phone.ui.components.SortDialog
import com.javboss.phone.ui.components.SortEntry
import com.javboss.phone.ui.components.VideoPickDialog
import com.javboss.phone.ui.components.WorkCard
import com.javboss.phone.ui.components.idolInfoRows

/**
 * 作品库 tab：作品/女优/片商/系列四个卡片墙视角。
 * 顶栏为四个视角按钮 + 搜索图标按钮（点开才显示搜索框）+ 筛选图标（仅作品视角）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorksScreen(
    onOpenDetail: (videoId: Long, code: String?) -> Unit,
    onPlay: (videoId: Long) -> Unit,
    onOpenJavList: (title: String, idolIds: String, tagIds: String, studioId: Long, seriesId: Long) -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: WorksViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val gridState = rememberLazyGridState()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    // 排序面板（作品 / 女优两个视角共用）
    var showSort by remember { mutableStateOf(false) }
    // 正在改评分的作品
    var ratingTarget by remember { mutableStateOf<JavDto?>(null) }
    // 一部作品有多个视频文件时，只在点播放钮时弹选择框
    var playPickerTarget by remember { mutableStateOf<JavDto?>(null) }

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

    // 点封面：不管挂了几个视频文件都直接进作品详情（详情页自己会列出全部文件版本），
    // 只有点封面右下角的播放钮才需要选具体文件
    val onWorkClick: (JavDto) -> Unit = { jav ->
        onOpenDetail(jav.videos.firstOrNull()?.id ?: 0, jav.code)
    }

    // 封面右下角的播放钮：一个视频直接起播，多个先选，没视频就没有按钮
    val onWorkPlay: (JavDto) -> Unit = { jav ->
        when {
            jav.videos.isEmpty() -> Unit
            jav.videos.size == 1 -> onPlay(jav.videos.first().id)
            else -> playPickerTarget = jav
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：四个视角按钮（窄屏可横滑）+ 排序 / 搜索 / 筛选 三个图标
        // 每行卡片数已挪到「设置」页，这里不再占位置，图标才不会被挤出屏幕外
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryTab.entries.forEach { tab ->
                    FilterChip(
                        selected = state.tab == tab,
                        onClick = { viewModel.onTabChange(tab) },
                        label = { Text(tab.label) },
                    )
                }
            }
            // 排序：作品 / 女优两个视角各有一套维度（与 Web 端一致）
            if (state.tab == LibraryTab.WORKS || state.tab == LibraryTab.IDOLS) {
                IconButton(onClick = { showSort = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                }
            }
            IconButton(onClick = {
                searchOpen = !searchOpen
                if (!searchOpen && state.search.isNotEmpty()) viewModel.onSearchChange("")
            }) {
                Icon(
                    if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (searchOpen) "收起搜索" else "搜索",
                )
            }
            if (state.tab == LibraryTab.WORKS) {
                IconButton(onClick = {
                    viewModel.loadFilterOptions()
                    showFilter = true
                }) {
                    Icon(Icons.Filled.FilterAlt, contentDescription = "筛选")
                }
            }
        }

        // 搜索框：点搜索图标才展开
        if (searchOpen) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::onSearchChange,
                placeholder = {
                    Text(
                        when (state.tab) {
                            LibraryTab.WORKS -> "搜索番号 / 标题"
                            LibraryTab.IDOLS -> "搜索女优"
                            LibraryTab.STUDIOS -> "搜索片商"
                            LibraryTab.SERIES -> "搜索系列"
                        },
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp),
            )
        }

        PullToRefreshBox(
            isRefreshing = when (state.tab) {
                LibraryTab.WORKS -> state.loading && state.items.isNotEmpty()
                LibraryTab.IDOLS -> state.idols.loading && state.idols.items.isNotEmpty()
                LibraryTab.STUDIOS -> state.studios.loading && state.studios.items.isNotEmpty()
                LibraryTab.SERIES -> state.series.loading && state.series.items.isNotEmpty()
            },
            onRefresh = viewModel::refreshCurrent,
            modifier = Modifier.fillMaxSize(),
        ) {
            // 用等高行的 LazyVerticalGrid，不用瀑布流：
            // 瀑布流各列独立堆叠，卡片高度只要差半个像素（列宽除不尽时的取整误差）
            // 就会越差越多，最后出现"最后一列整体偏上、网格对不齐"。
            LazyVerticalGrid(
                columns = GridCells.Fixed(state.columns),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (state.tab) {
                    LibraryTab.WORKS -> {
                        if (state.items.isEmpty() && !state.loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                CenterMessage(
                                    if (state.error != null) state.error
                                    else if (state.loading) "加载中…" else "没有作品",
                                )
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
                                cropPosition = jav.coverCropPosition,
                                rating = jav.favoriteRating,
                                onRatingClick = { ratingTarget = jav },
                                onPlay = playAction,
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GridFooter(state.loadingMore, state.endReached, state.total)
                        }
                    }
                    LibraryTab.IDOLS -> {
                        if (state.idols.items.isEmpty() && !state.idols.loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) { CenterMessage("没有女优") }
                        }
                        items(state.idols.items, key = { it.id }) { idol ->
                            IdolCard(
                                coverUrl = viewModel.idolCoverUrl(idol),
                                name = idol.displayName,
                                secondaryName = idol.secondaryName,
                                workCount = idol.workCount,
                                infoRows = idolInfoRows(
                                    birthDate = idol.birthDate,
                                    heightCm = idol.heightCm,
                                    bust = idol.bust,
                                    waist = idol.waist,
                                    hips = idol.hips,
                                    cup = idol.cup,
                                ),
                                onClick = {
                                    onOpenJavList("演员：${idol.displayName}", idol.id.toString(), "", 0, 0)
                                },
                                cropLeft = idol.coverCropLeft ?: 0.47,
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GridFooter(state.idols.loadingMore, state.idols.endReached, state.idols.total)
                        }
                    }
                    LibraryTab.STUDIOS -> {
                        if (state.studios.items.isEmpty() && !state.studios.loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) { CenterMessage("没有片商") }
                        }
                        items(state.studios.items, key = { it.id }) { studio ->
                            EntityCard(
                                coverUrl = viewModel.sampleCoverUrl(studio.sampleCode),
                                name = studio.name,
                                subtitle = "${studio.workCount} 部作品",
                                onClick = {
                                    onOpenJavList("片商：${studio.name}", "", "", studio.id, 0)
                                },
                                aspectRatio = 800f / 538f,
                                cropLeft = null,
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GridFooter(state.studios.loadingMore, state.studios.endReached, state.studios.total)
                        }
                    }
                    LibraryTab.SERIES -> {
                        if (state.series.items.isEmpty() && !state.series.loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) { CenterMessage("没有系列") }
                        }
                        items(state.series.items, key = { it.id }) { series ->
                            EntityCard(
                                coverUrl = viewModel.sampleCoverUrl(series.sampleCode),
                                name = series.name,
                                subtitle = "${series.workCount} 部作品",
                                onClick = {
                                    onOpenJavList("系列：${series.name}", "", "", 0, series.id)
                                },
                                aspectRatio = 800f / 538f,
                                cropLeft = null,
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GridFooter(state.series.loadingMore, state.series.endReached, state.series.total)
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        ModalBottomSheet(onDismissRequest = { showFilter = false }) {
            FilterSheet(
                state = state,
                onPrefixChange = viewModel::onPrefixChange,
                onTagToggle = viewModel::onTagToggle,
                onSoloChange = viewModel::onSoloChange,
                onRandomChange = viewModel::onRandomChange,
                onClear = viewModel::clearFilters,
            )
        }
    }

    playPickerTarget?.let { jav ->
        VideoPickDialog(
            title = jav.code,
            hint = "选择要播放的视频文件",
            options = jav.videos.map { it.id to it.filename },
            onPick = { videoId ->
                playPickerTarget = null
                onPlay(videoId)
            },
            onDismiss = { playPickerTarget = null },
        )
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

    if (showSort) {
        when (state.tab) {
            LibraryTab.WORKS -> SortDialog(
                title = "排序 · JAV",
                entries = JavSortBase.entries.map { base ->
                    val selected = state.sort == base && !state.random
                    SortEntry(base.label, selected, if (state.sortAsc) base.ascLabel else base.descLabel)
                },
                onPick = { viewModel.onSortClick(JavSortBase.entries[it]) },
                onDismiss = { showSort = false },
            )
            LibraryTab.IDOLS -> SortDialog(
                title = "排序 · 女优",
                entries = IdolSortBase.entries.map { base ->
                    val selected = state.idolsSort == base
                    SortEntry(base.label, selected, if (state.idolsSortAsc) base.ascLabel else base.descLabel)
                },
                onPick = { viewModel.onIdolSortClick(IdolSortBase.entries[it]) },
                onDismiss = { showSort = false },
            )
            else -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    state: WorksUiState,
    onPrefixChange: (String) -> Unit,
    onTagToggle: (Long) -> Unit,
    onSoloChange: (Boolean) -> Unit,
    onRandomChange: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    // 番号前缀/标签列表一次只展开一个，避免面板过长
    var openSection by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("筛选", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClear) { Text("清空") }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = openSection == "prefix",
                onClick = { openSection = if (openSection == "prefix") null else "prefix" },
                label = {
                    Text(
                        "番号前缀" + if (state.prefix.isNotEmpty()) "（${state.prefix}）" else "",
                    )
                },
            )
            FilterChip(
                selected = openSection == "tags",
                onClick = { openSection = if (openSection == "tags") null else "tags" },
                label = {
                    Text(
                        "标签" + if (state.tagIds.isNotEmpty()) "（已选 ${state.tagIds.size}）" else "",
                    )
                },
            )
        }

        when (openSection) {
            "prefix" -> {
                Spacer(Modifier.height(8.dp))
                if (state.allPrefixes.isEmpty()) {
                    Text("没有可选前缀", style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.prefix.isEmpty(),
                        onClick = { onPrefixChange("") },
                        label = { Text("全部") },
                    )
                    state.allPrefixes.forEach { p ->
                        FilterChip(
                            selected = state.prefix == p.prefix,
                            onClick = { onPrefixChange(p.prefix) },
                            label = { Text("${p.prefix} (${p.workCount})") },
                        )
                    }
                }
            }
            "tags" -> {
                Spacer(Modifier.height(8.dp))
                if (state.allTags.isEmpty()) {
                    Text("没有标签", style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.allTags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in state.tagIds,
                            onClick = { onTagToggle(tag.id) },
                            label = { Text("${tag.name} (${tag.count})") },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("仅单体作品")
            Switch(checked = state.solo, onCheckedChange = onSoloChange)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("随机排序")
            Switch(checked = state.random, onCheckedChange = onRandomChange)
        }
    }
}
