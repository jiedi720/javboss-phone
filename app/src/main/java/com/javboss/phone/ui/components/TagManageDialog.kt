package com.javboss.phone.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.TagGroupRules
import com.javboss.phone.data.TagManageRules
import com.javboss.phone.data.TagScope
import com.javboss.phone.data.api.dto.TagCategoryDto
import com.javboss.phone.data.api.dto.TagDto
import kotlinx.coroutines.launch

/**
 * 服务端标签管理弹层（v0.3.62 新增，v0.3.63 按服务端分类分组，v0.3.64 改流式胶囊）。
 *
 * Web 端 JAV 侧 / 视频侧各有一个「标签」按钮，打开的都是标签管理：
 *  - JAV  ：GET/POST `jav/tags`、PATCH/DELETE `jav/tags/{id}`（[TagScope.JAV]）
 *  - 视频 ：GET/POST `tags`、PATCH/DELETE `tags/{id}`（[TagScope.VIDEO]）
 * 两套数据是独立的两张表，不能混用，所以端点全部按 [scope] 分派。
 *
 * 分组与顺序由 [TagGroupRules] 对齐 Web 端标签管理面板 `mq` 的 Ge / Ze：
 * 分类列表 GET `/jav/tag-categories` 或 `/tags/categories`，按分类 sort_order 排序，
 * 无分类的标签归「默认分类」。分类的增删改、批量调整标签分类属于 Web 端额外能力，
 * 本轮不实现（不要过度发挥）。
 *
 * v0.3.64 视觉对齐服务端截图：删掉顶部「管理…」说明文字；分组内改为 [FlowRow]
 * 自动换行的实心圆角胶囊（自定义 `bg-emerald-500` 绿 / 刮削 `bg-orange-500` 橘，
 * 白字 + 深色数量徽标），不再一个标签一行。短按胶囊＝按该标签筛选（沿用 v0.3.62
 * 语义），长按弹出重命名 / 删除菜单；对应的编辑 / 删除确认条放在内容区顶部，
 * 避免嵌套弹层，也不破坏胶囊的流式布局。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManageDialog(
    scope: TagScope,
    onDismiss: () -> Unit,
    /** 点标签：JAV 页跳作品列表按 tag_id 筛；视频页在 /videos 按标签名筛 */
    onApplyFilter: (TagDto) -> Unit,
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
                    reload()
                } catch (e: Exception) {
                    error = "删除失败：${e.message ?: "标签可能仍被使用"}"
                } finally {
                    busy = false
                }
            }
        }
    }

    AlertDialog(
        modifier = Modifier.adaptiveDialogWidth(),
        onDismissRequest = onDismiss,
        title = { Text(scope.label) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 新建标签
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

                // v0.3.64：胶囊布局后，重命名 / 删除确认不再行内展开，统一放到内容区顶部
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
                                // 分组标题：与 Web 端一致，空分类显示「默认分类」
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 2.dp),
                                )
                                // v0.3.64：流式胶囊，标签多时自动换行，不再一个标签一行
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    group.tags.forEach { tag ->
                                        TagChip(
                                            tag = tag,
                                            enabled = !busy,
                                            onFilter = { onApplyFilter(tag); onDismiss() },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/**
 * 服务端同款标签胶囊（v0.3.64）：实心圆角、白字、名称右侧深色数量徽标。
 * 短按＝按标签筛选；长按弹出「重命名 / 删除」菜单（编辑入口不占用胶囊版面）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagChip(
    tag: TagDto,
    enabled: Boolean,
    onFilter: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (tag.isCustom) CustomTagChipBg else ScrapedTagChipBg)
                .combinedClickable(
                    enabled = enabled,
                    onClick = onFilter,
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tag.name,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${tag.count}",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TagChipCountBg)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
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
