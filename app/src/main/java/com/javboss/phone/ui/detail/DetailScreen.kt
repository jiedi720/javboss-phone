package com.javboss.phone.ui.detail

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import com.javboss.phone.data.api.dto.VideoDto
import com.javboss.phone.ui.components.CenterMessage
import com.javboss.phone.ui.components.LabeledValue
import com.javboss.phone.ui.components.LocalCoverMask
import com.javboss.phone.ui.components.TagChip
import com.javboss.phone.ui.components.coverCropAlignment
import com.javboss.phone.ui.components.coverFrost
import com.javboss.phone.ui.components.coverImageModelMasked
import com.javboss.phone.ui.components.formatDuration
import com.javboss.phone.ui.components.formatFileSize
import com.javboss.phone.ui.components.adaptiveDialogWidth
import com.javboss.phone.ui.components.adaptiveImageViewerWidth
import com.javboss.phone.ui.components.formatWatchedTime
import com.javboss.phone.ui.components.rememberUseTwoPane
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 大图查看器里的一张图：完整封面 / 样品图像 / 视频截图共用 */
private data class PreviewItem(val label: String, val url: String)

private data class PreviewState(val items: List<PreviewItem>, val index: Int)

private fun formatReleaseDate(releaseUnix: Long): String =
    if (releaseUnix <= 0) ""
    else Instant.ofEpochSecond(releaseUnix).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT)

/** 详情页：封面 + 元数据 + 可点击的片商/系列/演员/标签 + 外部链接 + 收藏/编辑 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    videoId: Long,
    javCode: String?,
    /** 带番号进播放页，竖屏背景才能用与 JAV 列表同一张封面 */
    onPlay: (videoId: Long, code: String?) -> Unit,
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
    // 标签编辑弹窗：JAV 详情编辑 JAV 自定义标签，普通视频详情编辑视频标签（两套数据互不相通）
    var showTagPicker by remember { mutableStateOf(false) }
    // 长按文件卡片 → 操作面板；再从中进入重命名 / 删除确认
    var fileMenuTarget by remember { mutableStateOf<VideoDto?>(null) }
    var renameTarget by remember { mutableStateOf<VideoDto?>(null) }
    var deleteTarget by remember { mutableStateOf<VideoDto?>(null) }
    // 点击封面 / 样品图 / 视频截图 → 全屏大图，可左右翻页
    var preview by remember { mutableStateOf<PreviewState?>(null) }
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
    // 文件版本由 ViewModel 按 /videos/{id} 的 locations 逐条展开，
    // 每个版本各带自己的完整路径与 location_id（不再按 video.id 合并成同一条主路径）
    val files = state.files
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
            else -> {
                // 当前应用窗口足够宽时左右双栏：左边封面 + 基本信息 + 操作 + 文件版本与路径，
                // 右边元数据 / 标签 / 样品图 / 截图，两栏各自独立滚动。
                // 窄窗口（手机、窄分屏、窄自由窗口）仍走原来的单栏，排版与行为完全不变。
                val body = DetailBodyParams(
                    state = state,
                    viewModel = viewModel,
                    title = title,
                    javCode = javCode,
                    onPlay = onPlay,
                    onOpenJavList = onOpenJavList,
                    onPreview = { preview = it },
                    onFavorite = { showFavorite = true },
                    onEdit = { showEdit = true },
                    onTagPicker = { showTagPicker = true },
                    onFileMenu = { fileMenuTarget = it },
                )
                if (rememberUseTwoPane()) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                    ) {
                        Column(
                            Modifier
                                .width(300.dp)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            // v0.3.59：双栏左栏只有 300dp，操作栏要按等宽按钮排布，
                            // 否则三颗按钮会互相挤压（见 DetailHeader 的 compactActions）。
                            DetailHeader(body, compactActions = true)
                        }
                        Spacer(Modifier.width(20.dp))
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        Spacer(Modifier.width(20.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            DetailExtras(body)
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        DetailHeader(body)
                        DetailExtras(body)
                    }
                }
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
    if (showTagPicker && jav != null) {
        TagPickerDialog(
            mode = TagPickerMode.JAV,
            entityId = jav.id,
            currentTags = jav.tags,
            viewModel = viewModel,
            onDismiss = { showTagPicker = false },
        )
    } else if (showTagPicker && video != null) {
        TagPickerDialog(
            mode = TagPickerMode.VIDEO,
            entityId = video.id,
            currentTags = video.tags,
            viewModel = viewModel,
            onDismiss = { showTagPicker = false },
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
        modifier = Modifier.adaptiveDialogWidth(),
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

    preview?.let { current ->
        ImageViewerDialog(
            state = current,
            onDismiss = { preview = null },
            onSave = { url ->
                Toast.makeText(context, "正在保存到本地…", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val message = viewModel.saveImageToLocal(url)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
        )
    }
}

/**
 * 全屏大图：完整封面用 ContentScale.Fit，不按 2:3 裁切，能看到整张封面。
 * 交互：
 * - **双指捏合缩放**（1x ~ 5x），放大后单指拖动可以平移看细节；
 * - **多张时左右拖拽切换**（也能点箭头）；
 * - **长按图片**弹出「保存图片」确认菜单，确认后才下载到本机「下载/JavBoss」；
 * - 点空白处或右上角 × 关闭。
 */
@Composable
private fun ImageViewerDialog(
    state: PreviewState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val items = state.items
    if (items.isEmpty()) return
    var index by remember(state) { mutableIntStateOf(state.index.coerceIn(0, items.lastIndex)) }
    // 缩放与平移状态：换图时复位
    var scale by remember(state) { mutableFloatStateOf(1f) }
    var offset by remember(state) { mutableStateOf(Offset.Zero) }
    // 横向滑动切图时的实时位移（松手才真正换图）
    var slideX by remember(state) { mutableFloatStateOf(0f) }
    var imageBoxSize by remember(state) { mutableStateOf(IntSize.Zero) }
    // 长按后先弹保存菜单，确认了才真正下载（非 null = 菜单打开，值为待保存的图片 URL）
    var saveMenuUrl by remember { mutableStateOf<String?>(null) }
    // pointerInput 的 key 只有图片数量，块里的 url / 回调要用最新值就得走 rememberUpdatedState
    val currentUrl by rememberUpdatedState(items[index].url)
    val saveAction by rememberUpdatedState(onSave)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val current = items[index]
            Box(
                Modifier
                    .adaptiveImageViewerWidth()
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .onSizeChanged { imageBoxSize = it }
                    // 吞掉图片上的点击：点图片不关闭大图（关闭只留空白处和右上角 ×）
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { }
                    .graphicsLayer {
                        translationX = slideX
                    }
                    .pointerInput(items.size) {
                        coroutineScope {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var totalDx = 0f
                                var multiTouch = false
                                // 长按：到期只弹出保存菜单，由用户确认后才存图；一旦移动就取消
                                val longPressJob = launch {
                                    delay(viewConfiguration.longPressTimeoutMillis)
                                    saveMenuUrl = currentUrl
                                }
                                do {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    if (pressed >= 2) {
                                        multiTouch = true
                                        // 双指：以两指中心为锚点缩放，同时跟着平移
                                        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                        val center = Offset(
                                            imageBoxSize.width / 2f,
                                            imageBoxSize.height / 2f,
                                        )
                                        val focal = event.calculateCentroid()
                                        val anchor = (focal - center) * scale * (zoomChange - 1f)
                                        scale = newScale
                                        offset = clampOffset(
                                            offset + anchor + panChange * scale,
                                            newScale,
                                            imageBoxSize,
                                        )
                                        event.changes.forEach { it.consume() }
                                    } else if (pressed == 1) {
                                        if (scale > 1f) {
                                            // 已放大：单指拖动画面
                                            offset = clampOffset(
                                                offset + panChange,
                                                scale,
                                                imageBoxSize,
                                            )
                                            event.changes.forEach { it.consume() }
                                        } else {
                                            // 未放大：单指横向拖动 → 切换上一张 / 下一张
                                            totalDx += panChange.x
                                            slideX = totalDx
                                        }
                                    }
                                    if (multiTouch || event.changes.any { it.positionChanged() }) {
                                        // 一旦动了就不再算长按
                                        longPressJob.cancel()
                                    }
                                } while (event.changes.any { it.pressed })
                                longPressJob.cancel()

                                if (!multiTouch && scale <= 1f && items.size > 1) {
                                    // 未放大时的单指横向拖动 → 切上一张 / 下一张
                                    val threshold = imageBoxSize.width * 0.18f
                                    if (kotlin.math.abs(totalDx) > threshold) {
                                        val target = if (totalDx < 0) index + 1 else index - 1
                                        if (target in items.indices) {
                                            index = target
                                            scale = 1f
                                            offset = Offset.Zero
                                        }
                                    }
                                }
                                slideX = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = current.url,
                    contentDescription = current.label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }

            Text(
                current.label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (items.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                index = (index - 1 + items.size) % items.size
                                scale = 1f
                                offset = Offset.Zero
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "上一张",
                                tint = Color.White,
                            )
                        }
                        Text(
                            "${index + 1} / ${items.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                        IconButton(
                            onClick = {
                                index = (index + 1) % items.size
                                scale = 1f
                                offset = Offset.Zero
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "下一张",
                                tint = Color.White,
                            )
                        }
                    }
                }
                Text(
                    "双指缩放 · 左右拖动切换 · 长按弹出保存菜单",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }

    // 长按图片后弹出的保存确认菜单：不直接下载，点「保存到本地」才执行
    saveMenuUrl?.let { url ->
        AlertDialog(
        modifier = Modifier.adaptiveDialogWidth(),
            onDismissRequest = { saveMenuUrl = null },
            title = { Text("保存图片") },
            text = { Text("是否将这张图片保存到本地「下载/JavBoss」？") },
            confirmButton = {
                TextButton(onClick = {
                    saveMenuUrl = null
                    saveAction(url)
                }) { Text("保存到本地") }
            },
            dismissButton = {
                TextButton(onClick = { saveMenuUrl = null }) { Text("取消") }
            },
        )
    }
}

/** 放大后限制平移范围，避免图片被拖出屏幕；缩回 1x 时自动归零 */
private fun clampOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    if (scale <= 1f) return Offset.Zero
    val maxX = (size.width * (scale - 1f)) / 2f
    val maxY = (size.height * (scale - 1f)) / 2f
    return Offset(
        offset.x.coerceIn(-maxX, maxX),
        offset.y.coerceIn(-maxY, maxY),
    )
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
        modifier = Modifier.adaptiveDialogWidth(),
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
        modifier = Modifier.adaptiveDialogWidth(),
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
        modifier = Modifier.adaptiveDialogWidth(),
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


/**
 * 详情页正文在「单栏」与「双栏」两种布局之间复用，参数集中在这里避免长参数列表。
 * 所有取值都从 [state] 派生，切换窗口宽度时不会丢数据或状态。
 */
private class DetailBodyParams(
    val state: DetailUiState,
    val viewModel: DetailViewModel,
    val title: String,
    val javCode: String?,
    val onPlay: (Long, String?) -> Unit,
    val onOpenJavList: (String, String, String, Long, Long) -> Unit,
    val onPreview: (PreviewState) -> Unit,
    val onFavorite: () -> Unit,
    val onEdit: () -> Unit,
    val onTagPicker: () -> Unit,
    val onFileMenu: (VideoDto) -> Unit,
)

/**
 * 左栏（双栏）/ 顶部（单栏）：封面 + 基本信息 + 播放 / 收藏 / 编辑 + 文件版本与完整路径
 *
 * [compactActions]：横屏双栏左栏固定 300dp 时，三颗按钮按自然宽度（M3 Button 默认
 * 左右各 24dp 内边距 + 24dp 图标 + 文字）总宽会超出，Row 会把最后一颗挤扁，
 * 造成「编辑」文字看不见、图标被压缩得和前两颗不一样。开启后改为三颗等宽
 * （weight）、收窄内边距、统一图标尺寸；竖屏单栏沿用原来的自然宽度排版不变。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.DetailHeader(
    p: DetailBodyParams,
    compactActions: Boolean = false,
) {
    val state = p.state
    val viewModel = p.viewModel
    val jav = state.javMeta
    val video = state.video
    val files = state.files
    val title = p.title
    fun showPreview(items: List<PreviewItem>, index: Int) =
        p.onPreview(PreviewState(items = items, index = index))
    // 封面毛玻璃与列表卡片同源：设置常开 / 锁屏自动打码时，详情页主封面也一起糊
    val masked = LocalCoverMask.current.masked
    // 封面 + 基本信息
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(140.dp)) {
            AsyncImage(
                model = coverImageModelMasked(viewModel.coverUrl()),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                alignment = coverCropAlignment(jav?.coverCropPosition),
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(2f / 3f)
                    .coverFrost(masked)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        viewModel.fullCoverUrl()?.let { url ->
                            showPreview(
                                items = listOf(PreviewItem("完整封面 · $title", url)),
                                index = 0,
                            )
                        }
                    },
            )
        }
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
    Row(
        modifier = if (compactActions) Modifier.fillMaxWidth() else Modifier,
        horizontalArrangement = Arrangement.spacedBy(if (compactActions) 8.dp else 12.dp),
    ) {
        // weight 是 RowScope 的扩展，必须在这层作用域里取值
        val itemModifier = if (compactActions) Modifier.weight(1f) else Modifier
        val itemPadding = if (compactActions) {
            PaddingValues(horizontal = 6.dp, vertical = 8.dp)
        } else {
            ButtonDefaults.ContentPadding
        }
        val iconModifier = if (compactActions) Modifier.size(20.dp) else Modifier
        Button(
            onClick = {
                video?.let { p.onPlay(it.id, jav?.code ?: it.jav?.code ?: p.javCode) }
            },
            enabled = video != null,
            modifier = itemModifier,
            contentPadding = itemPadding,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = iconModifier)
            Spacer(Modifier.width(4.dp))
            Text("播放", maxLines = 1)
        }
        OutlinedButton(
            onClick = p.onFavorite,
            enabled = jav != null,
            modifier = itemModifier,
            contentPadding = itemPadding,
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, modifier = iconModifier)
            Spacer(Modifier.width(4.dp))
            Text("收藏", maxLines = 1)
        }
        OutlinedButton(
            onClick = p.onEdit,
            enabled = jav != null,
            modifier = itemModifier,
            contentPadding = itemPadding,
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = iconModifier)
            Spacer(Modifier.width(4.dp))
            Text("编辑", maxLines = 1)
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
                            onLongClick = { p.onFileMenu(file) },
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = file.filename.ifBlank { "（无文件名）" },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        // 展开后所有版本的 id 都是同一个 video_id，不能再按 id 判断；
                        // 只有 location_id 或完整路径与当前视频一致时才标「当前」
                        val isCurrent = video != null && (
                            (video.locationId > 0 && file.locationId == video.locationId) ||
                                (video.locationId <= 0 && file.fullPath == video.fullPath)
                            )
                        if (isCurrent) {
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
    }

}

/** 右栏（双栏）/ 下方（单栏）：演员、片商系列、标签、外链、样品图、截图 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.DetailExtras(p: DetailBodyParams) {
    val state = p.state
    val viewModel = p.viewModel
    val jav = state.javMeta
    val video = state.video
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 样品图像也跟随同一套封面毛玻璃状态
    val masked = LocalCoverMask.current.masked
    fun showPreview(items: List<PreviewItem>, index: Int) =
        p.onPreview(PreviewState(items = items, index = index))

    // 演员（可点击）：放在片商 / 系列上面
    if (!jav?.idols.isNullOrEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text("演员", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            jav!!.idols.forEach { idol ->
                AssistChip(
                    onClick = {
                        p.onOpenJavList("演员：${idol.displayName}", idol.id.toString(), "", 0, 0)
                    },
                    label = { Text(idol.displayName) },
                )
            }
        }
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
                        p.onOpenJavList("片商：${studio.name}", "", "", studio.id, 0)
                    },
                    label = { Text(studio.name) },
                )
            }
            if (seriesId > 0 && state.seriesName.isNotBlank()) {
                AssistChip(
                    onClick = {
                        p.onOpenJavList("系列：${state.seriesName}", "", "", 0, seriesId)
                    },
                    label = { Text(state.seriesName) },
                )
            }
        }
    }

    // 标签（可点击）：服务端视频自身 tags + 作品 jav.tags，合并去重后一起展示，
    // 普通视频（没有 JAV 元数据）也能看到服务端给它打的标签。
    // 编辑入口按两套互不相通的标签体系分流：JAV 详情「编辑标签」走 /jav/tags，
    // 普通视频详情「编辑视频标签」走 /tags；空标签时也能新增。
    val tags = (state.video?.tags.orEmpty() + jav?.tags.orEmpty())
        .distinctBy { tag -> if (tag.id > 0) "id:${tag.id}" else "name:${tag.name}" }
    if (video != null) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "标签",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (jav != null) {
                TextButton(onClick = p.onTagPicker) {
                    Text("编辑标签")
                }
            } else {
                TextButton(onClick = p.onTagPicker) {
                    Text("编辑视频标签")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (tags.isEmpty()) {
            Text(
                "暂无标签，点「${if (jav != null) "编辑标签" else "编辑视频标签"}」添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                tags.forEach { tag ->
                    // v0.3.65：与标签菜单 / 封面角标统一为实心彩底白字胶囊
                    TagChip(
                        label = tag.name,
                        custom = tag.isCustom,
                        onClick = {
                            p.onOpenJavList("标签：${tag.name}", "", tag.id.toString(), 0, 0)
                        },
                    )
                }
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

    // 样品图像：服务端 POST /jav/items/{id}/sample-images，按数组下标取 thumbnail / detail
    if (state.sampleImageCount > 0 && state.sampleJavId > 0) {
        Spacer(Modifier.height(16.dp))
        Text(
            "样品图像（${state.sampleImageCount}）",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.sampleImageCount) { index ->
                AsyncImage(
                    model = coverImageModelMasked(viewModel.sampleThumbnailUrl(index)),
                    contentDescription = "样品图像 ${index + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(120.dp)
                        .width(120.dp)
                        .coverFrost(masked)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .clickable {
                            showPreview(
                                items = (0 until state.sampleImageCount).map { i ->
                                    PreviewItem(
                                        "样品图像 ${i + 1}",
                                        viewModel.sampleDetailUrl(i),
                                    )
                                },
                                index = index,
                            )
                        },
                )
            }
        }
        Text(
            "点击可查看大图",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    // 视频截图：服务端 /videos/{id}/screenshots，点开看完整截图
    if (video != null && state.screenshots.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text(
            "视频截图（${state.screenshots.size}）",
            style = MaterialTheme.typography.labelLarge,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(state.screenshots, key = { _, shot -> shot.name }) { index, shot ->
                AsyncImage(
                    model = viewModel.screenshotUrl(video.id, shot.name),
                    contentDescription = shot.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(120.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            showPreview(
                                items = state.screenshots.map { s ->
                                    PreviewItem(s.name, viewModel.screenshotUrl(video.id, s.name))
                                },
                                index = index,
                            )
                        },
                )
            }
        }
        Text(
            "点击可查看完整截图",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
