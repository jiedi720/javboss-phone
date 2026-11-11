package com.javboss.phone.ui.works

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.data.WindowSizeRules
import com.javboss.phone.data.api.dto.JavDto
import com.javboss.phone.ui.components.CenterMessage
import com.javboss.phone.ui.components.PageToolsState
import com.javboss.phone.ui.components.CompactIconButton
import com.javboss.phone.ui.components.EntityCard
import com.javboss.phone.ui.components.GridFooter
import com.javboss.phone.ui.components.IdolCard
import com.javboss.phone.ui.components.RatingDialog
import com.javboss.phone.ui.components.SortEntry
import com.javboss.phone.ui.components.SortList
import com.javboss.phone.ui.components.VideoPickDialog
import com.javboss.phone.ui.components.FavoriteTarget
import com.javboss.phone.ui.components.WorkCard
import com.javboss.phone.ui.components.adaptiveSearchWidth
import com.javboss.phone.ui.components.adaptiveSheetWidth
import com.javboss.phone.ui.components.idolInfoRows
import com.javboss.phone.ui.components.rememberAdaptivePadding
import com.javboss.phone.ui.components.rememberGridColumns
import com.javboss.phone.ui.components.rememberGridSpacing
import com.javboss.phone.ui.components.rememberWindowWidthDp

/**
 * 作品库 tab：作品/女优/片商/系列四个卡片墙视角。
 * 顶栏为四个视角按钮 + 搜索图标按钮（点开才显示搜索框）+ 筛选图标（仅作品视角）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorksScreen(
    onOpenDetail: (videoId: Long, code: String?) -> Unit,
    /** code 原样带到播放页，保证竖屏背景用的就是这张 JAV 封面 */
    onPlay: (videoId: Long, code: String?) -> Unit,
    onOpenJavList: (title: String, idolIds: String, tagIds: String, studioId: Long, seriesId: Long) -> Unit,
    onSessionExpired: () -> Unit,
    /** v0.3.50：横屏（Rail）时顶栏功能并入原左侧栏，开关由 MainScreen 统一下发 */
    tools: PageToolsState,
    toolsInRail: Boolean,
    viewModel: WorksViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val gridState = rememberLazyGridState()
    val adaptivePadding = rememberAdaptivePadding()
    // 两个开关提到 MainScreen：顶部按钮和原左侧栏写的是同一份状态，
    // 切换导航形式 / 旋转时搜索框与排序菜单不会重置。
    val searchOpen = tools.worksSearchOpen
    val showSortFilter = tools.worksSortOpen
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
            jav.videos.size == 1 -> onPlay(jav.videos.first().id, jav.code)
            else -> playPickerTarget = jav
        }
    }

    Column(Modifier.fillMaxSize()) {
        // v0.3.50：Rail 模式下这一栏功能并入原左侧 NavigationRail，
        // 内容区顶部不再画工具条；Compact 手机竖屏保持原样。
        if (!toolsInRail) {
            // 顶栏：四个紧凑视角按钮 + 排序 / 搜索 / 筛选 三个紧凑图标，
            // 不再横滑，也不再被 48dp 的 IconButton 挤掉，窄屏也能一行完整显示
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LibraryTab.entries.forEach { tab ->
                    CompactTabChip(
                        label = tab.label,
                        selected = state.tab == tab,
                        onClick = { viewModel.onTabChange(tab) },
                    )
                }
                Spacer(Modifier.weight(1f))
                // 排序 / 筛选：只留一个按钮，打开后是同一个菜单（作品视角多一段筛选区）
                if (state.tab == LibraryTab.WORKS || state.tab == LibraryTab.IDOLS) {
                    CompactIconButton(Icons.AutoMirrored.Filled.Sort, "排序 / 筛选") {
                        if (state.tab == LibraryTab.WORKS) viewModel.loadFilterOptions()
                        tools.worksSortOpen = true
                    }
                }
                CompactIconButton(
                    if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                    if (searchOpen) "收起搜索" else "搜索",
                ) {
                    val next = !tools.worksSearchOpen
                    tools.worksSearchOpen = next
                    if (!next && state.search.isNotEmpty()) viewModel.onSearchChange("")
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
                    .adaptiveSearchWidth()
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
                columns = GridCells.Fixed(rememberGridColumns()),
                state = gridState,
                // v0.3.51：Rail 模式内容区顶部不再留白（用户红框处），封面紧贴状态栏下方；
                // Compact 手机竖屏仍保留四周统一留白，避免卡片贴边。
                contentPadding = PaddingValues(
                    start = adaptivePadding,
                    top = if (toolsInRail) 0.dp else adaptivePadding,
                    end = adaptivePadding,
                    bottom = adaptivePadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(rememberGridSpacing()),
                verticalArrangement = Arrangement.spacedBy(rememberGridSpacing()),
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
                                favoriteCount = jav.favoriteCount,
                                favoriteTarget = FavoriteTarget.jav(jav.id),
                                watchedMs = jav.watchedMs,
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
                                favoriteCount = idol.favoriteCount,
                                favoriteTarget = FavoriteTarget.idol(idol.id),
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

    // 排序 + 筛选共用一个菜单：上半是与 Web 端一样的「维度: 方向 + ↑↓」列表，
    // 作品视角下半接原来的筛选区（番号前缀 / 标签 / 仅单体 / 随机）
    if (showSortFilter) {
        ModalBottomSheet(onDismissRequest = { tools.worksSortOpen = false }) {
            // 宽窗口里收窄居中，排序项不横跨整块平板屏
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .adaptiveSheetWidth()
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                ) {
                Text(
                    if (state.tab == LibraryTab.WORKS) "排序 / 筛选" else "排序 · 女优",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "排序",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                when (state.tab) {
                    LibraryTab.WORKS -> SortList(
                        entries = JavSortBase.entries.map { base ->
                            val selected = state.sort == base && !state.random
                            // 未选中的维度也按 Web 端显示自己的默认方向，而不是全局方向
                            val direction = if (state.sort == base) {
                                if (state.sortAsc) base.ascLabel else base.descLabel
                            } else if (base.defaultAsc) {
                                base.ascLabel
                            } else {
                                base.descLabel
                            }
                            SortEntry(base.label, selected, direction)
                        },
                        onPick = { viewModel.onSortClick(JavSortBase.entries[it]) },
                    )
                    LibraryTab.IDOLS -> SortList(
                        entries = IdolSortBase.entries.map { base ->
                            val selected = state.idolsSort == base
                            val direction = if (selected) {
                                if (state.idolsSortAsc) base.ascLabel else base.descLabel
                            } else if (base.defaultAsc) {
                                base.ascLabel
                            } else {
                                base.descLabel
                            }
                            SortEntry(base.label, selected, direction)
                        },
                        onPick = { viewModel.onIdolSortClick(IdolSortBase.entries[it]) },
                    )
                    else -> Unit
                }
                if (state.tab == LibraryTab.WORKS) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    FilterSection(
                        state = state,
                        onPrefixChange = viewModel::onPrefixChange,
                        onTagToggle = viewModel::onTagToggle,
                        onSoloChange = viewModel::onSoloChange,
                        onRandomChange = viewModel::onRandomChange,
                        onClear = viewModel::clearFilters,
                    )
                }
                }
            }
        }
    }
}

/** 顶栏紧凑视角按钮：手机 30dp，平板/宽窗口放大到 40dp 触控目标 */
@Composable
private fun CompactTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val compact = WindowSizeRules.isCompact(rememberWindowWidthDp())
    Box(
        Modifier
            .height(if (compact) 30.dp else 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 9.dp else 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = content, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    state: WorksUiState,
    onPrefixChange: (String) -> Unit,
    onTagToggle: (Long) -> Unit,
    onSoloChange: (Boolean) -> Unit,
    onRandomChange: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    // 番号前缀/标签列表一次只展开一个，避免面板过长
    // 滚动与左右留白由外层合并菜单负责，这里不要再套一层 verticalScroll
    var openSection by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
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
