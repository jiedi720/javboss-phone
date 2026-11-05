package com.javboss.phone.ui.favorites

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import com.javboss.phone.data.api.dto.FavoriteItemDto
import com.javboss.phone.ui.components.CenterMessage
import com.javboss.phone.ui.components.RatingDialog
import com.javboss.phone.ui.components.SortDialog
import com.javboss.phone.ui.components.SortEntry
import com.javboss.phone.ui.components.WorkCard

/** 收藏夹 tab：作品收藏夹列表，支持新建/重命名/删除 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    onGroupClick: (groupId: Long, name: String) -> Unit,
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
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.groups.isEmpty() && state.loading -> CenterMessage("加载中…")
                state.groups.isEmpty() && state.error != null -> CenterMessage(state.error)
                state.groups.isEmpty() -> CenterMessage("还没有收藏夹\n点右下角新建一个")
                else -> LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.groups, key = { it.id }) { group ->
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onGroupClick(group.id, group.name) },
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
                                            "${group.count} 部作品",
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
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除收藏夹") },
            text = { Text("确定删除「${group.name}」吗？收藏的作品不会被删除。") },
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
    viewModel: FavGroupViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    var removeTarget by remember { mutableStateOf<FavoriteItemDto?>(null) }
    var ratingTarget by remember { mutableStateOf<FavoriteItemDto?>(null) }
    var showSort by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(groupId) { viewModel.load(groupId) }
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
                        placeholder = { Text("搜索番号 / 标题") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
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
                state.noMatch -> CenterMessage("没有匹配「${state.search.trim()}」的作品")
                state.items.isEmpty() -> CenterMessage("收藏夹是空的")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(state.columns),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { item ->
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
                            onPlay = {
                                viewModel.playItem(item) { videoId -> onPlay(videoId, item.code) }
                            },
                        )
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
            onDismissRequest = { removeTarget = null },
            title = { Text("移出收藏夹") },
            text = { Text("将「${item.code}」从本收藏夹移除？") },
            confirmButton = {
                TextButton(onClick = {
                    removeTarget = null
                    viewModel.removeItem(groupId, item)
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) { Text("取消") }
            },
        )
    }

    if (showSort) {
        SortDialog(
            title = "排序 · 收藏夹",
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
            onDismiss = { showSort = false },
        )
    }
}
