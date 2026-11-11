package com.javboss.phone.ui.favorites

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.data.FavoriteRepository
import com.javboss.phone.data.WindowSizeRules
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import com.javboss.phone.data.api.dto.FavoriteItemDto
import com.javboss.phone.ui.components.CenterMessage
import com.javboss.phone.ui.components.RatingDialog
import com.javboss.phone.ui.components.SortEntry
import com.javboss.phone.ui.components.SortList
import com.javboss.phone.ui.components.FavoriteTarget
import com.javboss.phone.ui.components.IdolCard
import com.javboss.phone.ui.components.WorkCard
import com.javboss.phone.ui.components.adaptiveDialogWidth
import com.javboss.phone.ui.components.adaptiveSearchWidth
import com.javboss.phone.ui.components.adaptiveSheetWidth
import com.javboss.phone.ui.components.rememberAdaptivePadding
import com.javboss.phone.ui.components.rememberGridColumns
import com.javboss.phone.ui.components.rememberGridSpacing
import com.javboss.phone.ui.components.rememberWindowWidthDp

/** 收藏夹 tab：作品 / 女优收藏夹列表，支持切换、新建/重命名/删除 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    onGroupClick: (groupId: Long, name: String, kind: String) -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: FavoritesViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FavoriteGroupDto?>(null) }
    var deleteTarget by remember { mutableStateOf<FavoriteGroupDto?>(null) }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建收藏夹")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 服务端按实体类型分端点：作品 / 女优收藏夹
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FilterChip(
                    selected = state.kind == FavoriteRepository.KIND_JAV,
                    onClick = { viewModel.setKind(FavoriteRepository.KIND_JAV) },
                    label = { Text("作品收藏夹") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = state.kind == FavoriteRepository.KIND_IDOL,
                    onClick = { viewModel.setKind(FavoriteRepository.KIND_IDOL) },
                    label = { Text("女优收藏夹") },
                )
            }
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
            ) {
                when {
                    state.groups.isEmpty() && state.loading -> CenterMessage("加载中…")
                    state.groups.isEmpty() && state.error != null -> CenterMessage(state.error)
                    state.groups.isEmpty() -> CenterMessage("还没有收藏夹\n点右下角新建一个")
                    else -> LazyVerticalGrid(
                        // 宽窗口把「收藏夹」文件夹卡排成两列，窄窗口仍是原来的单列
                        columns = GridCells.Fixed(
                            WindowSizeRules.settingsColumns(rememberWindowWidthDp())
                        ),
                        contentPadding = PaddingValues(rememberAdaptivePadding()),
                        horizontalArrangement = Arrangement.spacedBy(rememberGridSpacing()),
                        verticalArrangement = Arrangement.spacedBy(rememberGridSpacing()),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.groups, key = { it.id }) { group ->
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                Card(
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onGroupClick(group.id, group.name, state.kind) },
                                            onLongClick = { menuOpen = true },
                                        ),
                                ) {
                                    Row(
                                        Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Filled.Folder, contentDescription = null)
                                        Spacer(Modifier.padding(horizontal = 8.dp))
                                        Column {
                                            Text(group.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                if (state.kind == FavoriteRepository.KIND_IDOL) {
                                                    "${group.count} 位女优"
                                                } else {
                                                    "${group.count} 部作品"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("重命名") },
                                        onClick = {
                                            menuOpen = false
                                            renameTarget = group
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = {
                                            menuOpen = false
                                            deleteTarget = group
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        NameInputDialog(
            title = "新建收藏夹",
            initial = "",
            onConfirm = {
                showCreate = false
                viewModel.createGroup(it)
            },
            onDismiss = { showCreate = false },
        )
    }
    renameTarget?.let { group ->
        NameInputDialog(
            title = "重命名收藏夹",
            initial = group.name,
            onConfirm = {
                renameTarget = null
                viewModel.renameGroup(group.id, it)
            },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { group ->
        AlertDialog(
            modifier = Modifier.adaptiveDialogWidth(),
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除收藏夹") },
            text = {
                Text(
                    if (state.kind == FavoriteRepository.KIND_IDOL) {
                        "确定删除「${group.name}」吗？收藏的女优不会被删除。"
                    } else {
                        "确定删除「${group.name}」吗？收藏的作品不会被删除。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.deleteGroup(group.id)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

}

@Composable
private fun NameInputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        modifier = Modifier.adaptiveDialogWidth(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 收藏夹详情：条目网格，长按移除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavGroupScreen(
    groupId: Long,
    groupName: String,
    onOpenDetail: (videoId: Long, code: String?) -> Unit,
    /** code 原样带到播放页，保证竖屏背景用的就是这张 JAV 封面 */
    onPlay: (videoId: Long, code: String?) -> Unit,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    /** 收藏夹实体类型：jav（作品网格）/ idol（女优网格） */
    kind: String = FavoriteRepository.KIND_JAV,
    onOpenIdol: (idolId: Long, name: String) -> Unit = { _, _ -> },
    viewModel: FavGroupViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    var removeTarget by remember { mutableStateOf<FavoriteItemDto?>(null) }
    var ratingTarget by remember { mutableStateOf<FavoriteItemDto?>(null) }
    var showSort by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(groupId, kind) { viewModel.load(groupId, kind) }
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(groupName, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
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
                    },
                )
                // 搜索框：点搜索图标才展开（与「作品」页一致）
                if (searchOpen) {
                    OutlinedTextField(
                        value = state.search,
                        onValueChange = viewModel::onSearchChange,
                        placeholder = {
                            Text(
                                if (kind == FavoriteRepository.KIND_IDOL) "搜索女优" else "搜索番号 / 标题"
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
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val shown = state.visibleItems
            when {
                state.items.isEmpty() && state.loading -> CenterMessage("加载中…")
                state.items.isEmpty() && state.error != null -> CenterMessage(state.error)
                state.noMatch -> CenterMessage(
                    if (kind == FavoriteRepository.KIND_IDOL) {
                        "没有匹配「${state.search.trim()}」的女优"
                    } else {
                        "没有匹配「${state.search.trim()}」的作品"
                    }
                )
                state.items.isEmpty() -> CenterMessage("收藏夹是空的")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(rememberGridColumns()),
                    contentPadding = PaddingValues(rememberAdaptivePadding()),
                    horizontalArrangement = Arrangement.spacedBy(rememberGridSpacing()),
                    verticalArrangement = Arrangement.spacedBy(rememberGridSpacing()),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.id }) { item ->
                        if (kind == FavoriteRepository.KIND_IDOL) {
                            IdolCard(
                                // 女优卡封面用 cover_code（与 Web 端女优卡一致）；
                                // 服务端没给时退回 sample_code 的作品封面，避免整张空白
                                coverUrl = viewModel.coverUrl(item.coverCode.ifBlank { item.sampleCode }),
                                name = item.name,
                                secondaryName = item.japaneseName.ifBlank { item.romanName },
                                workCount = item.workCount.toInt(),
                                infoRows = listOf("作品 ${item.workCount}"),
                                onClick = { onOpenIdol(item.id, item.name) },
                                onLongClick = { removeTarget = item },
                                favoriteCount = 1,
                                favoriteTarget = FavoriteTarget.idol(item.id),
                            )
                        } else {
                            WorkCard(
                                coverUrl = viewModel.coverUrl(item.code),
                                code = item.code,
                                title = item.title,
                                onClick = {
                                    viewModel.openItem(item) { videoId, code ->
                                        onOpenDetail(videoId, code)
                                    }
                                },
                                onLongClick = { removeTarget = item },
                                rating = viewModel.ratingOf(item.code),
                                onRatingClick = { ratingTarget = item },
                                watchedMs = viewModel.watchedMsOf(item.code),
                                favoriteCount = 1,
                                // 收藏夹条目只有番号，星级映射里的 javId 才是 /jav/items/{id} 需要的 id
                                favoriteTarget = state.ratings[item.code.uppercase()]
                                    ?.javId
                                    ?.takeIf { it > 0 }
                                    ?.let { FavoriteTarget.jav(it) },
                                onPlay = {
                                    viewModel.playItem(item) { videoId -> onPlay(videoId, item.code) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    ratingTarget?.let { item ->
        RatingDialog(
            code = item.code,
            current = viewModel.ratingOf(item.code),
            onDismiss = { ratingTarget = null },
            onSave = { value ->
                viewModel.updateRating(item.code, value)
                ratingTarget = null
            },
        )
    }

    removeTarget?.let { item ->
        AlertDialog(
            modifier = Modifier.adaptiveDialogWidth(),
            onDismissRequest = { removeTarget = null },
            title = { Text("移出收藏夹") },
            text = {
                Text(
                    "将「${if (kind == FavoriteRepository.KIND_IDOL) item.name else item.code}」从本收藏夹移除？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removeTarget = null
                    viewModel.removeItem(groupId, item, kind)
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("取消") }
            },
        )
    }

    // 排序：与 JAV 界面同一个底部功能菜单（不是 AlertDialog），样式跟服务端 Web 端一致
    if (showSort) {
        ModalBottomSheet(onDismissRequest = { showSort = false }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier
                        .adaptiveSheetWidth()
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                ) {
                    Text("排序", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    SortList(
                        entries = FavItemSortBase.entries.map { base ->
                            val selected = state.sort == base
                            val direction = if (selected) {
                                if (state.sortAsc) base.ascLabel else base.descLabel
                            } else if (base.defaultAsc) {
                                base.ascLabel
                            } else {
                                base.descLabel
                            }
                            SortEntry(base.label, selected, direction)
                        },
                        onPick = { viewModel.onSortClick(FavItemSortBase.entries[it]) },
                    )
                }
            }
        }
    }
}
