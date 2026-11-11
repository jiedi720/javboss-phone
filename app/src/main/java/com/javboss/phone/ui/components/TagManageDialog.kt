package com.javboss.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.TagCategoryRules
import com.javboss.phone.data.TagGroupRules
import com.javboss.phone.data.TagManageRules
import com.javboss.phone.data.TagScope
import com.javboss.phone.data.api.dto.TagCategoryDto
import com.javboss.phone.data.api.dto.TagDto
import kotlinx.coroutines.launch

/**
 * 服务端标签管理弹层（v0.3.62 新增；v0.3.63 分组；v0.3.64 流式胶囊；
 * v0.3.65 补齐服务端同款「分类管理 / 编辑 / 多选查找」三功能、右上 × 关闭、加宽）。
 *
 * Web 端面板 `mq` 的工具条反查结果：
 *  - 「分类管理」打开独立的分类管理弹层（新建 / 重命名 / 删除 / 排序 / 批量调整标签分类）；
 *  - 「编辑 / 退出编辑」切换编辑模式，编辑模式下标签可重命名 / 删除 / 新增；
 *  - 「多选查找 / 退出多选」切换多选模式，勾选多个标签后「查找」把它们一起交给
 *    [onApplyFilter] 并关闭弹层；编辑 + 多选时改为「调整分类」（批量归类）。
 *
 * 两套标签表（JAV `/jav/tags`、视频 `/tags`）互不相通，端点全部按 [scope] 分派。
 * 分类分组与顺序沿用 [TagGroupRules] 对齐 Web 端 `Ge / Ze / Xe`。
 *
 * v0.3.65 布局：关闭按钮移到标题栏右上角 ×，底部不再放「关闭」按钮，把腾出来的
 * 空间留给胶囊列表；弹层用 [adaptiveTagDialogWidth] 加宽。标签胶囊统一用 [TagChip]。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManageDialog(
    scope: TagScope,
    onDismiss: () -> Unit,
    /** 点标签 / 多选查找：JAV 页跳作品列表按 tag_id 筛；视频页在 /videos 按标签名筛 */
    onApplyFilter: (List<TagDto>) -> Unit,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as JavBossApp).container.videoRepository
    val coroutineScope = rememberCoroutineScope()

    var tags by remember { mutableStateOf<List<TagDto>>(emptyList()) }
    var categories by remember { mutableStateOf<List<TagCategoryDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<TagDto?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<TagDto?>(null) }

    var editMode by remember { mutableStateOf(false) }
    var multiSelect by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var newTagOpen by remember { mutableStateOf(false) }
    var categoryManagerOpen by remember { mutableStateOf(false) }
    var assignCategoryOpen by remember { mutableStateOf(false) }

    val reload: suspend () -> Unit = {
        loading = true
        error = null
        try {
            tags = when (scope) {
                TagScope.JAV -> repository.listJavTags()
                TagScope.VIDEO -> repository.listVideoTags()
            }
            // 分类列表失败不阻断标签：降级为「默认分类」+ 按标签自带分类名分组
            categories = try {
                repository.listTagCategories(scope)
            } catch (e: Exception) {
                emptyList()
            }
        } catch (e: Exception) {
            error = e.message ?: "加载标签失败"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(scope) { reload() }

    val groups = remember(tags, categories) { TagGroupRules.groupTags(tags, categories) }

    val create: () -> Unit = {
        val name = newName.trim()
        if (TagManageRules.validName(name) && !busy) {
            coroutineScope.launch {
                busy = true
                error = null
                try {
                    when (scope) {
                        TagScope.JAV -> repository.createJavTag(name)
                        TagScope.VIDEO -> repository.createVideoTag(name)
                    }
                    newName = ""
                    reload()
                } catch (e: Exception) {
                    error = "新建失败：${e.message ?: "名称可能已存在"}"
                } finally {
                    busy = false
                }
            }
        }
    }

    val rename: () -> Unit = {
        val target = renameTarget
        val name = renameDraft.trim()
        if (target != null && TagManageRules.validName(name) && !busy) {
            coroutineScope.launch {
                busy = true
                error = null
                try {
                    when (scope) {
                        TagScope.JAV -> repository.renameJavTag(target.id, name)
                        TagScope.VIDEO -> repository.renameVideoTag(target.id, name)
                    }
                    renameTarget = null
                    renameDraft = ""
                    reload()
                } catch (e: Exception) {
                    error = "重命名失败：${e.message ?: "名称可能已存在"}"
                } finally {
                    busy = false
                }
            }
        }
    }

    val delete: () -> Unit = {
        val target = deleteTarget
        if (target != null && !busy) {
            coroutineScope.launch {
                busy = true
                error = null
                try {
                    when (scope) {
                        TagScope.JAV -> repository.deleteJavTag(target.id)
                        TagScope.VIDEO -> repository.deleteVideoTag(target.id)
                    }
                    deleteTarget = null
                    selectedIds = selectedIds - target.id
                    reload()
                } catch (e: Exception) {
                    error = "删除失败：${e.message ?: "标签可能仍被使用"}"
                } finally {
                    busy = false
                }
            }
        }
    }

    /** 多选查找：把选中的标签整体交给筛选回调，然后关闭弹层（Web 端 `i(Qe),t()`） */
    val applyMultiFilter: () -> Unit = {
        val selected = tags.filter { it.id in selectedIds }
        if (selected.isNotEmpty()) {
            onApplyFilter(selected)
            onDismiss()
        }
    }

    AlertDialog(
        modifier = Modifier.adaptiveTagDialogWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    scope.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // v0.3.65：关闭移到右上角 ×，底部不再占一行「关闭」按钮
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    // 底部按钮去掉后把空间让给胶囊列表
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 服务端同款工具条：分类管理 / 编辑 / 多选查找 / 新建标签
                // v0.3.66：FlowRow 防窄屏溢出；新建标签改按钮点击才展开输入框
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToolButton("分类管理", Icons.Filled.Folder, active = false) {
                        categoryManagerOpen = true
                    }
                    ToolButton(
                        if (editMode) "退出编辑" else "编辑",
                        Icons.Filled.Edit,
                        active = editMode,
                    ) {
                        editMode = !editMode
                        selectedIds = emptySet()
                    }
                    ToolButton(
                        if (multiSelect) "退出多选" else "多选查找",
                        Icons.Filled.Search,
                        active = multiSelect,
                    ) {
                        multiSelect = !multiSelect
                        selectedIds = emptySet()
                        renameTarget = null
                        deleteTarget = null
                    }
                    ToolButton("新建标签", Icons.Filled.Add, active = newTagOpen) {
                        newTagOpen = !newTagOpen
                        error = null
                    }
                }

                // 多选操作条：非编辑＝查找；编辑＋多选＝批量调整分类
                if (multiSelect) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "已选 ${selectedIds.size} 个标签",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (editMode) {
                            Button(
                                onClick = { assignCategoryOpen = true },
                                enabled = selectedIds.isNotEmpty() && !busy,
                            ) { Text("调整分类") }
                        } else {
                            Button(
                                onClick = applyMultiFilter,
                                enabled = selectedIds.isNotEmpty() && !busy,
                            ) { Text("查找 (${selectedIds.size})") }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // 新建标签：v0.3.66 改为点工具条按钮才显示输入框，不再常驻占一行
                if (newTagOpen) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it; error = null },
                            label = { Text("新建标签") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        TextButton(
                            onClick = create,
                            enabled = TagManageRules.validName(newName) && !busy,
                        ) { Text("新建") }
                    }
                }

                // 重命名 / 删除确认条放在内容区顶部，不嵌套弹层、不破坏流式布局
                renameTarget?.let { target ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = renameDraft,
                            onValueChange = { renameDraft = it; error = null },
                            label = { Text("重命名「${target.name}」") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { renameTarget = null; renameDraft = "" }) {
                            Text("取消")
                        }
                        TextButton(
                            onClick = rename,
                            enabled = TagManageRules.validName(renameDraft) && !busy,
                        ) { Text("保存") }
                    }
                }
                deleteTarget?.let { target ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "确定删除「${target.name}」？",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                        TextButton(onClick = delete, enabled = !busy) { Text("删除") }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(10.dp))
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                } else if (tags.isEmpty()) {
                    Text("暂无标签", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        groups.forEach { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 2.dp),
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    group.tags.forEach { tag ->
                                        ManageTagChip(
                                            tag = tag,
                                            editMode = editMode,
                                            multiSelect = multiSelect,
                                            selected = tag.id in selectedIds,
                                            enabled = !busy,
                                            onFilter = {
                                                onApplyFilter(listOf(tag))
                                                onDismiss()
                                            },
                                            onToggleSelect = {
                                                selectedIds = if (tag.id in selectedIds) {
                                                    selectedIds - tag.id
                                                } else {
                                                    selectedIds + tag.id
                                                }
                                            },
                                            onRename = {
                                                deleteTarget = null
                                                renameTarget = tag
                                                renameDraft = tag.name
                                                error = null
                                            },
                                            onDelete = {
                                                renameTarget = null
                                                deleteTarget = tag
                                                error = null
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        // 底部按钮区留空：关闭已移到标题栏右上角，腾出下方空间显示更多标签
        confirmButton = {},
    )

    if (categoryManagerOpen) {
        CategoryManageDialog(
            scope = scope,
            categories = categories,
            onDismiss = { categoryManagerOpen = false },
            onChanged = { coroutineScope.launch { reload() } },
        )
    }
    if (assignCategoryOpen) {
        AssignCategoryDialog(
            scope = scope,
            categories = categories,
            selectedIds = selectedIds,
            onDismiss = { assignCategoryOpen = false },
            onAssigned = {
                assignCategoryOpen = false
                selectedIds = emptySet()
                multiSelect = false
                coroutineScope.launch { reload() }
            },
        )
    }
}

/** 服务端同款紧凑工具条按钮：激活态用主色实心，未激活用 surfaceVariant。 */
@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    val background = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        // v0.3.66：字号加大到与分组标题（labelLarge）相近
        Text(label, color = foreground, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * 标签胶囊 + 长按菜单 + 编辑/多选交互。胶囊外观统一走 [TagChip]（彩底白字）。
 * 非编辑非多选：短按筛选、长按重命名/删除；编辑：短按重命名、尾部 × 删除；
 * 多选：短按切换选中（白色描边）。
 */
@Composable
private fun ManageTagChip(
    tag: TagDto,
    editMode: Boolean,
    multiSelect: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onFilter: () -> Unit,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        TagChip(
            label = tag.name,
            custom = tag.isCustom,
            count = tag.count,
            selected = selected,
            enabled = enabled,
            onClick = when {
                multiSelect -> onToggleSelect
                editMode -> onRename
                else -> onFilter
            },
            onLongClick = if (!editMode && !multiSelect) ({ menuOpen = true }) else null,
            trailing = {
                if (editMode && !multiSelect) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除标签",
                        tint = Color.White,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable(enabled = enabled) { onDelete() },
                    )
                }
            },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = { menuOpen = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = { menuOpen = false; onDelete() },
            )
        }
    }
}

/**
 * 分类管理弹层（v0.3.65，对齐 Web 端 `mq` 的 `_e` 面板）：
 * 新建 / 重命名 / 删除分类、上移下移排序，排序结果 PUT `.../categories/order`。
 * 合成的默认分类（id=0）只用于展示/归类，不参与编辑与排序请求。
 */
@Composable
private fun CategoryManageDialog(
    scope: TagScope,
    categories: List<TagCategoryDto>,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as JavBossApp).container.videoRepository
    val coroutineScope = rememberCoroutineScope()

    val initial = remember(categories) {
        TagGroupRules.orderedCategories(categories).filter { it.id > 0 }
    }
    var order by remember(categories) { mutableStateOf(initial.map { it.id }) }
    var names by remember(categories) { mutableStateOf(initial.associate { it.id to it.name }) }
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Long?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runOp(block: suspend () -> Unit) {
        if (busy) return
        coroutineScope.launch {
            busy = true
            error = null
            try {
                block()
                onChanged()
            } catch (e: Exception) {
                error = e.message ?: "操作失败"
            } finally {
                busy = false
            }
        }
    }

    val create: () -> Unit = {
        val name = newName.trim()
        if (TagManageRules.validName(name)) runOp {
            val created = repository.createTagCategory(scope, name)
            order = order + created.id
            names = names + (created.id to created.name)
            newName = ""
        }
    }

    val rename: () -> Unit = {
        val id = renameTarget
        val name = renameDraft.trim()
        if (id != null && TagManageRules.validName(name)) runOp {
            repository.renameTagCategory(scope, id, name)
            names = names + (id to name)
            renameTarget = null
            renameDraft = ""
        }
    }

    val delete: () -> Unit = {
        val id = deleteTarget
        if (id != null) runOp {
            repository.deleteTagCategory(scope, id)
            order = order - id
            names = names - id
            deleteTarget = null
        }
    }

    // v0.3.66：服务端同款拖拽排序（三横线把手），替代「上移/下移」箭头逐个移动。
    // 拖动过程只改本地 order 视觉顺序，松手才 PUT .../categories/order 提交一次。
    val rowHeight = 46.dp
    val rowHeightPx = with(LocalDensity.current) { rowHeight.toPx() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragDelta by remember { mutableStateOf(0f) }
    var dragStartOrder by remember { mutableStateOf<List<Long>?>(null) }

    AlertDialog(
        modifier = Modifier.adaptiveTagDialogWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("分类管理", Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭分类管理")
                }
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it; error = null },
                        label = { Text("新建分类") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        onClick = create,
                        enabled = TagManageRules.validName(newName) && !busy,
                    ) { Text("新建") }
                }

                renameTarget?.let { id ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = renameDraft,
                            onValueChange = { renameDraft = it; error = null },
                            label = { Text("重命名「${names[id].orEmpty()}」") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { renameTarget = null; renameDraft = "" }) {
                            Text("取消")
                        }
                        TextButton(
                            onClick = rename,
                            enabled = TagManageRules.validName(renameDraft) && !busy,
                        ) { Text("保存") }
                    }
                }
                deleteTarget?.let { id ->
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "确定删除分类「${names[id].orEmpty()}」？该分类下的标签会回到默认分类。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                        TextButton(onClick = delete, enabled = !busy) { Text("删除") }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(8.dp))
                if (order.isEmpty()) {
                    Text("暂无分类", style = MaterialTheme.typography.bodySmall)
                } else {
                    order.forEachIndexed { _, id ->
                        val isDragging = draggingId == id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeight)
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragDelta else 0f
                                }
                                .background(
                                    if (isDragging) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        Color.Transparent
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 三横线拖拽把手（和 Web 端一致）；拖动越过半行就换位
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = "拖动排序",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .pointerInput(id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingId = id
                                                dragDelta = 0f
                                                dragStartOrder = order
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragDelta += dragAmount.y
                                                val cur = order.indexOf(id)
                                                if (dragDelta > rowHeightPx / 2 && cur < order.lastIndex) {
                                                    order = TagCategoryRules.moveCategory(order, cur, cur + 1)
                                                    dragDelta -= rowHeightPx
                                                } else if (dragDelta < -rowHeightPx / 2 && cur > 0) {
                                                    order = TagCategoryRules.moveCategory(order, cur, cur - 1)
                                                    dragDelta += rowHeightPx
                                                }
                                            },
                                            onDragEnd = {
                                                val start = dragStartOrder
                                                val next = order
                                                draggingId = null
                                                dragDelta = 0f
                                                dragStartOrder = null
                                                if (start != null && next != start) runOp {
                                                    repository.reorderTagCategories(scope, next)
                                                }
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                dragDelta = 0f
                                                dragStartOrder = null
                                            },
                                        )
                                    },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                names[id].orEmpty().ifBlank { "分类 $id" },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            IconButton(
                                onClick = {
                                    deleteTarget = null
                                    renameTarget = id
                                    renameDraft = names[id].orEmpty()
                                },
                                enabled = !busy,
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = "重命名")
                            }
                            IconButton(onClick = { renameTarget = null; deleteTarget = id }, enabled = !busy) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/** 批量调整标签分类：列出分类（含默认分类），点一个即把选中的标签全部归入它。 */
@Composable
private fun AssignCategoryDialog(
    scope: TagScope,
    categories: List<TagCategoryDto>,
    selectedIds: Set<Long>,
    onDismiss: () -> Unit,
    onAssigned: () -> Unit,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as JavBossApp).container.videoRepository
    val coroutineScope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val options = remember(categories) { TagGroupRules.orderedCategories(categories) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整分类（${selectedIds.size} 个标签）") },
        text = {
            Column {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                options.forEach { category ->
                    TextButton(
                        onClick = {
                            if (!busy) {
                                coroutineScope.launch {
                                    busy = true
                                    error = null
                                    try {
                                        repository.assignTagsCategory(
                                            scope,
                                            selectedIds.toList(),
                                            category.id,
                                        )
                                        onAssigned()
                                    } catch (e: Exception) {
                                        error = e.message ?: "调整失败"
                                        busy = false
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (category.isDefault) TagGroupRules.DEFAULT_LABEL else category.name)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
