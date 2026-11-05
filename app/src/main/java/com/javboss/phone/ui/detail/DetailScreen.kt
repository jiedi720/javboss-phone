package com.javboss.phone.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import com.javboss.phone.data.api.dto.VideoDto
import com.javboss.phone.ui.components.CenterMessage
import com.javboss.phone.ui.components.LabeledValue
import com.javboss.phone.ui.components.coverCropAlignment
import com.javboss.phone.ui.components.formatDuration
import com.javboss.phone.ui.components.formatFileSize
import com.javboss.phone.ui.components.formatWatchedTime
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun formatReleaseDate(releaseUnix: Long): String =
    if (releaseUnix <= 0) ""
    else Instant.ofEpochSecond(releaseUnix).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT)

/** 详情页：封面 + 元数据 + 可点击的片商/系列/演员/标签 + 外部链接 + 收藏/编辑 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    videoId: Long,
    javCode: String?,
    onPlay: (Long) -> Unit,
    onOpenJavList: (title: String, idolIds: String, tagIds: String, studioId: Long, seriesId: Long) -> Unit,
    onBack: () -> Unit,
    onSessionExpired: () -> Unit,
    viewModel: DetailViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showFavorite by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    // 长按文件卡片 → 操作面板；再从中进入重命名 / 删除确认
    var fileMenuTarget by remember { mutableStateOf<VideoDto?>(null) }
    var renameTarget by remember { mutableStateOf<VideoDto?>(null) }
    var deleteTarget by remember { mutableStateOf<VideoDto?>(null) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(videoId, javCode) { viewModel.load(videoId, javCode) }
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    val jav = state.javMeta
    val video = state.video
    // 一部作品可能挂多个文件版本（不同分辨率/不同目录），详情页要全部列出来。
    // 以 /jav 元数据里内嵌的 videos 为准，当前播放的那条用 /videos/{id} 的新鲜数据替换（它可能带更新的路径）。
    val files: List<VideoDto> = remember(state.javMeta, state.video) {
        val current = state.video
        val fromMeta = state.javMeta?.videos.orEmpty().map { v ->
            if (current != null && v.id == current.id && current.fullPath.isNotBlank()) current else v
        }
        when {
            current == null -> fromMeta
            fromMeta.any { it.id == current.id } -> fromMeta
            else -> listOf(current) + fromMeta
        }
    }
    val title = jav?.let { "${it.code} ${it.title}" }?.trim()?.takeIf { it.isNotBlank() }
        ?: video?.filename ?: javCode ?: "详情"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> CenterMessage("加载中…", Modifier.padding(padding))
            state.error != null -> CenterMessage(state.error, Modifier.padding(padding))
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                // 封面 + 基本信息
                Row(Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = viewModel.coverUrl(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        alignment = coverCropAlignment(jav?.coverCropPosition),
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(2f / 3f),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        jav?.let {
                            Text(it.code, style = MaterialTheme.typography.titleMedium)
                            if (it.title.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(it.title, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            formatReleaseDate(it.releaseUnix).takeIf { d -> d.isNotEmpty() }?.let { d ->
                                LabeledValue("发行日期", d)
                            }
                            if (it.durationMin > 0) LabeledValue("时长", "${it.durationMin} 分钟")
                            if (it.favoriteCount > 0) LabeledValue("被收藏", "${it.favoriteCount} 次")
                            // 服务端统计的累计观看时长（jav.watched_ms，含所有文件版本）
                            formatWatchedTime(it.watchedMs).takeIf { t -> t.isNotEmpty() }?.let { t ->
                                LabeledValue("观看时长", t)
                            }
                        }
                        video?.let {
                            Spacer(Modifier.height(4.dp))
                            if (it.durationSec > 0) LabeledValue("视频时长", formatDuration(it.durationSec))
                            LabeledValue("播放次数", "${it.playCount}")
                        }
                    }
                }

                // 操作栏：播放 / 收藏 / 编辑
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { video?.let { onPlay(it.id) } },
                        enabled = video != null,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("播放")
                    }
                    OutlinedButton(
                        onClick = { showFavorite = true },
                        enabled = jav != null,
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("收藏")
                    }
                    OutlinedButton(
                        onClick = { showEdit = true },
                        enabled = jav != null,
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("编辑")
                    }
                }

                // 文件：一部作品可能对应多个文件版本，逐个列出文件名 + 完整路径（可长按复制）
                if (files.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (files.size > 1) "文件（${files.size} 个版本）" else "文件",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        files.forEach { file ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { fileMenuTarget = file },
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = file.filename.ifBlank { "（无文件名）" },
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (file.id == video?.id) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "当前",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                val fullPath = file.fullPath
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = fullPath.ifBlank { "（服务端未返回路径）" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val extra = listOfNotNull(
                                    file.size.takeIf { it > 0 }?.let { formatFileSize(it) },
                                    file.durationSec.takeIf { it > 0 }?.let { formatDuration(it) },
                                    formatWatchedTime(file.watchedMs).takeIf { it.isNotEmpty() }
                                        ?.let { "已看 $it" },
                                ).joinToString(" · ")
                                if (extra.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = extra,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "长按文件卡片：查看大小、复制路径、重命名或删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                // 片商 / 系列（可点击）
                val studio = jav?.studio
                val seriesId = jav?.seriesId ?: 0
                if ((studio != null && studio.name.isNotBlank()) || (seriesId > 0 && state.seriesName.isNotBlank())) {
                    Spacer(Modifier.height(16.dp))
                    Text("片商 / 系列", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (studio != null && studio.name.isNotBlank()) {
                            AssistChip(
                                onClick = {
                                    onOpenJavList("片商：${studio.name}", "", "", studio.id, 0)
                                },
                                label = { Text(studio.name) },
                            )
                        }
                        if (seriesId > 0 && state.seriesName.isNotBlank()) {
                            AssistChip(
                                onClick = {
                                    onOpenJavList("系列：${state.seriesName}", "", "", 0, seriesId)
                                },
                                label = { Text(state.seriesName) },
                            )
                        }
                    }
                }

                // 演员（可点击）
                if (!jav?.idols.isNullOrEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("演员", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        jav!!.idols.forEach { idol ->
                            AssistChip(
                                onClick = {
                                    onOpenJavList("演员：${idol.displayName}", idol.id.toString(), "", 0, 0)
                                },
                                label = { Text(idol.displayName) },
                            )
                        }
                    }
                }

                // 标签（可点击）
                if (!jav?.tags.isNullOrEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("标签", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        jav!!.tags.forEach { tag ->
                            AssistChip(
                                onClick = {
                                    onOpenJavList("标签：${tag.name}", "", tag.id.toString(), 0, 0)
                                },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }

                // 外部链接
                if (!jav?.code.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text("外部链接", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        scope.launch {
                            val url = viewModel.resolveJavDbUrl(jav!!.code)
                            if (url != null) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                }
                            } else {
                                Toast.makeText(context, "未找到对应的 JavDB 页面", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("JavDB")
                    }
                }

                // 截图
                if (video != null && state.screenshots.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("截图", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.screenshots, key = { it.name }) { shot ->
                            AsyncImage(
                                model = viewModel.screenshotUrl(video.id, shot.name),
                                contentDescription = shot.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .height(120.dp)
                                    .aspectRatio(16f / 9f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showFavorite && jav != null) {
        FavoriteDialog(
            javId = jav.id,
            viewModel = viewModel,
            onDismiss = { showFavorite = false },
        )
    }
    if (showEdit && jav != null) {
        EditDialog(
            jav = jav,
            viewModel = viewModel,
            onDismiss = { showEdit = false },
        )
    }

    fileMenuTarget?.let { file ->
        FileActionDialog(
            file = file,
            onCopyPath = {
                clipboard.setText(AnnotatedString(file.fullPath))
                Toast.makeText(context, "已复制完整路径", Toast.LENGTH_SHORT).show()
                fileMenuTarget = null
            },
            onRename = {
                fileMenuTarget = null
                renameTarget = file
            },
            onDelete = {
                fileMenuTarget = null
                deleteTarget = file
            },
            onDismiss = { fileMenuTarget = null },
        )
    }

    renameTarget?.let { file ->
        RenameFileDialog(
            initial = file.filename.substringAfterLast('/').substringAfterLast('\\'),
            onConfirm = { name ->
                renameTarget = null
                viewModel.renameFile(file, name)
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件") },
            text = {
                Text("确定删除「${file.filename}」吗？服务端会同步删除这个文件（磁盘上的真实文件也会被删除），不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.deleteFile(file)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

/** 长按文件卡片：大小 / 路径 / 时长 / 已看 + 复制路径、重命名、删除 */
@Composable
private fun FileActionDialog(
    file: VideoDto,
    onCopyPath: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.filename.ifBlank { "文件" }, maxLines = 2) },
        text = {
            Column {
                Text(
                    "文件大小：${if (file.size > 0) formatFileSize(file.size) else "服务端未返回"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (file.durationSec > 0) {
                    Text("视频时长：${formatDuration(file.durationSec)}", style = MaterialTheme.typography.bodySmall)
                }
                formatWatchedTime(file.watchedMs).takeIf { it.isNotEmpty() }?.let {
                    Text("已看：$it", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    file.fullPath.ifBlank { "（服务端未返回路径）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                TextButton(onClick = onCopyPath, modifier = Modifier.fillMaxWidth()) { Text("复制完整路径") }
                TextButton(onClick = onRename, modifier = Modifier.fillMaxWidth()) { Text("重命名文件（同步服务端）") }
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("删除文件（同步服务端）", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 只改文件名，不含路径；服务端会同步改磁盘上的真实文件名 */
@Composable
private fun RenameFileDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val valid = name.isNotBlank() && !name.contains('/') && !name.contains('\\')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名文件") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新文件名") },
                    singleLine = true,
                    isError = !valid && name.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "只填文件名（含扩展名），不要带路径。服务端会同步改名磁盘上的真实文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = valid) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 收藏弹窗：勾选收藏夹 + 新建收藏夹，保存时全量替换 */
@Composable
private fun FavoriteDialog(
    javId: Long,
    viewModel: DetailViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var groups by remember { mutableStateOf<List<FavoriteGroupDto>?>(null) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var newName by remember { mutableStateOf("") }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(javId) {
        try {
            val (g, s) = viewModel.loadFavoriteData(javId)
            groups = g
            selected = s
        } catch (e: Exception) {
            loadFailed = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏到…") },
        text = {
            Column {
                when {
                    loadFailed -> Text("加载收藏夹失败", color = MaterialTheme.colorScheme.error)
                    groups == null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Text("加载中…")
                    }
                    else -> {
                        if (groups!!.isEmpty()) {
                            Text("还没有收藏夹，先新建一个", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                        groups!!.forEach { group ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (group.id in selected) selected - group.id
                                        else selected + group.id
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = group.id in selected,
                                    onCheckedChange = null,
                                )
                                Text("${group.name} (${group.count})")
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newName,
                                onValueChange = { newName = it },
                                label = { Text("新建收藏夹") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    val name = newName.trim()
                                    if (name.isEmpty()) return@TextButton
                                    scope.launch {
                                        try {
                                            val created = viewModel.createFavoriteGroup(name)
                                            groups = groups!! + created
                                            selected = selected + created.id
                                            newName = ""
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context, "创建失败：名称可能已存在", Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                                enabled = newName.isNotBlank(),
                            ) { Text("新建") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.saveFavorite(javId, selected)
                    onDismiss()
                },
                enabled = groups != null,
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
