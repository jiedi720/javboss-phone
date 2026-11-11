package com.javboss.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.TagGroupRules
import com.javboss.phone.data.TagManageRules
import com.javboss.phone.data.TagScope
import com.javboss.phone.data.api.dto.TagCategoryDto
import com.javboss.phone.data.api.dto.TagDto
import kotlinx.coroutines.launch

/**
 * 服务端标签管理弹层（v0.3.62，v0.3.63 按服务端分类分组显示）。
 *
 * Web 端 JAV 侧 / 视频侧各有一个「标签」按钮，打开的都是标签管理：
 *  - JAV  ：GET/POST `jav/tags`、PATCH/DELETE `jav/tags/{id}`（[TagScope.JAV]）
 *  - 视频 ：GET/POST `tags`、PATCH/DELETE `tags/{id}`（[TagScope.VIDEO]）
 * 两套数据是独立的两张表，不能混用，所以端点全部按 [scope] 分派。
 *
 * 提供「新建 / 重命名 / 删除」三项编辑能力（对齐 Web 端标签管理面板 `mq`），
 * 点标签本身则回调 [onApplyFilter] 按标签筛选。
 *
 * v0.3.63：按服务端的标签分类分组显示（[TagGroupRules] 对齐 Web 端 `Ge` / `Ze`）：
 * 分类列表 GET `/jav/tag-categories` 或 `/tags/categories`，按分类 sort_order 排序，
 * 无分类的标签归「默认分类」。分类的增删改、批量调整标签分类属于 Web 端额外能力，
 * 本轮只做分组显示，不实现（不要过度发挥）。
 */
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

    // 单个标签行的三种状态（普通 / 行内重命名 / 删除确认）在各分组内复用
    val tagEntry: @Composable (TagDto) -> Unit = { tag ->
        when {
            renameTarget?.id == tag.id -> {
                // 行内重命名，不用嵌套 Dialog（避免两个弹层叠在一起）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it; error = null },
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
            deleteTarget?.id == tag.id -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "确定删除「${tag.name}」？",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                    TextButton(onClick = delete, enabled = !busy) { Text("删除") }
                }
            }
            else -> {
                TagManageRow(
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
                Text(
                    if (scope == TagScope.JAV) {
                        "管理 JAV 标签：按服务端分类分组显示；点击标签按该标签筛选作品；可新建、重命名、删除。"
                    } else {
                        "管理视频标签：按服务端分类分组显示；点击标签按该标签筛选视频；可新建、重命名、删除。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))

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

                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(8.dp))
                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                } else if (tags.isEmpty()) {
                    Text("暂无标签", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        groups.forEach { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // 分组标题：与 Web 端一致，空分类显示「默认分类」
                                Text(
                                    text = group.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 2.dp, top = 2.dp),
                                )
                                group.tags.forEach { tag -> tagEntry(tag) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun TagManageRow(
    tag: TagDto,
    enabled: Boolean,
    onFilter: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onFilter)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 配色与详情页 / 卡片角标一致：自定义标签蓝绿、刮削标签橘色
        Text(
            text = tag.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (tag.isCustom) CustomTagLabel else ScrapedTagLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(if (tag.isCustom) CustomTagContainer else ScrapedTagContainer)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${tag.count}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SmallIconButton(Icons.Filled.Edit, "重命名标签 ${tag.name}", enabled, onRename)
        SmallIconButton(Icons.Filled.Delete, "删除标签 ${tag.name}", enabled, onDelete)
    }
}

@Composable
private fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
