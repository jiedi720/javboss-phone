package com.javboss.phone.ui.javlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.data.api.dto.JavDto
import com.javboss.phone.ui.components.CenterMessage
import com.javboss.phone.ui.components.GridFooter
import com.javboss.phone.ui.components.VideoPickDialog
import com.javboss.phone.ui.components.WorkCard
import com.javboss.phone.ui.components.rememberResolvedColumns

/** 演员/标签/片商/系列点击落地页：对应过滤条件的作品瀑布流 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: JavListViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val gridState = rememberLazyGridState()
    var playPickerTarget by remember { mutableStateOf<JavDto?>(null) }

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
                    columns = GridCells.Fixed(rememberResolvedColumns()),
                    state = gridState,
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { jav ->
                        val playAction: (() -> Unit)? =
                            if (jav.videos.isEmpty()) null else ({ onWorkPlay(jav) })
                        WorkCard(
                            coverUrl = viewModel.coverUrl(jav.code),
                            code = jav.code,
                            title = jav.title,
                            onClick = { onWorkClick(jav) },
                            cropPosition = jav.coverCropPosition,
                            watchedMs = jav.watchedMs,
                            onPlay = playAction,
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        GridFooter(state.loadingMore, state.endReached, state.total)
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
}
