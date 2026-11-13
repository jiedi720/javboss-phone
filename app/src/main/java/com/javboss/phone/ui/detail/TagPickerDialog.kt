package com.javboss.phone.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.javboss.phone.data.TagOrderRules
import com.javboss.phone.data.api.dto.TagDto
import com.javboss.phone.ui.components.TagChip
import com.javboss.phone.ui.components.adaptiveTagDialogWidth
import kotlinx.coroutines.launch

/**
 * 服务端「视频标签」与「JAV 标签」是两套互不相通的数据，这里按 mode 分别走各自的
 * 读取 / 新建 / 保存接口：
 *  - VIDEO：GET /tags、POST /tags、POST /videos/tags/replace
 *  - JAV  ：GET /jav/tags、POST /jav/tags、PUT /jav/items/{id}
 *
 * v0.3.72：
 * - 标签一律用彩底白字胶囊（[TagChip]），与标签菜单 / 封面角标一致（自定义绿、刮削橙）；
 * - **自定义标签排最前**、刮削标签紧随其后，**两段都可以勾选 / 取消**：
 *   自定义提交到 `tag_ids`（服务端要求 is_user=true），刮削提交到 `scraped_tag_ids`
 *   （要求 is_user=false），两者分开提交、互不覆盖——所以不再有「刮削只能看不能改」的限制；
 * - 弹层宽度改用 [adaptiveTagDialogWidth]，与主页「标签管理」弹层同宽。
 */
enum class TagPickerMode { VIDEO, JAV }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagPickerDialog(
    mode: TagPickerMode,
    entityId: Long,
    currentTags: List<TagDto>,
    viewModel: DetailViewModel,
    onDismiss: () -> Unit,
) {
    val isJav = mode == TagPickerMode.JAV
    var allTags by remember { mutableStateOf<List<TagDto>>(emptyList()) }
    // 两套勾选集合：自定义标签与刮削标签分别提交到不同字段，不能混
    var selectedCustom by remember {
        mutableStateOf(TagOrderRules.userTags(currentTags).map { it.id }.toSet())
    }
    var selectedScraped by remember {
        mutableStateOf(TagOrderRules.scrapedTags(currentTags).map { it.id }.toSet())
    }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(mode, entityId) {
        val loaded = if (isJav) viewModel.loadJavTagOptions() else viewModel.loadVideoTagOptions()
        // 当前已打上的标签若不在可选列表里（例如被服务端按 hide_jav 过滤掉），
        // 也补进来，避免一打开就因为列表缺项而把已有标签误删
        val merged = (loaded + currentTags).distinctBy { it.id }
        // 服务端 GET /jav/tags 是「刮削在前、自定义在后」，这里按自定义在前重排
        allTags = if (isJav) TagOrderRules.customFirst(merged) else merged
    }

    val customTags = if (isJav) TagOrderRules.userTags(allTags) else allTags
    val scrapedTags = if (isJav) TagOrderRules.scrapedTags(allTags) else emptyList()

    AlertDialog(
        modifier = Modifier.adaptiveTagDialogWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = { Text(if (isJav) "编辑标签" else "编辑视频标签") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    if (isJav) "点击胶囊增删标签：自定义标签与刮削标签分开保存，互不覆盖。"
                    else "只修改这个视频文件的标签；作品标签请在操作栏「编辑」里修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (allTags.isEmpty()) {
                    Text("加载标签中…", style = MaterialTheme.typography.bodySmall)
                } else {
                    if (customTags.isNotEmpty()) {
                        SectionTitle(if (isJav) "自定义标签" else "标签")
                        TagChipFlow(customTags, selectedCustom) { id ->
                            selectedCustom = if (id in selectedCustom) selectedCustom - id
                            else selectedCustom + id
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (scrapedTags.isNotEmpty()) {
                        SectionTitle("刮削标签")
                        TagChipFlow(scrapedTags, selectedScraped) { id ->
                            selectedScraped = if (id in selectedScraped) selectedScraped - id
                            else selectedScraped + id
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("新建标签") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val name = newName.trim()
                            if (name.isEmpty()) return@TextButton
                            scope.launch {
                                try {
                                    val tag = if (isJav) viewModel.createTag(name)
                                    else viewModel.createVideoTag(name)
                                    allTags = if (isJav) TagOrderRules.customFirst(allTags + tag)
                                    else allTags + tag
                                    selectedCustom = selectedCustom + tag.id
                                    newName = ""
                                    error = null
                                } catch (e: Exception) {
                                    error = "创建标签失败：名称可能已存在"
                                }
                            }
                        },
                        enabled = newName.isNotBlank(),
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (saving || allTags.isEmpty()) return@TextButton
                    saving = true
                    if (isJav) {
                        // 自定义 → tag_ids（is_user=true）；刮削 → scraped_tag_ids（is_user=false）
                        val customIds = customTags.filter { it.id in selectedCustom }.map { it.id }
                        val scrapedIds = scrapedTags.filter { it.id in selectedScraped }.map { it.id }
                        viewModel.saveJavTags(entityId, customIds, scrapedIds)
                    } else {
                        viewModel.saveVideoTags(entityId, selectedCustom.toList())
                    }
                    onDismiss()
                },
                enabled = !saving && allTags.isNotEmpty(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 小节标题（自定义 / 刮削） */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
}

/** 彩底胶囊流式排布；选中项带 2dp 白描边（与标签菜单一致） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChipFlow(
    tags: List<TagDto>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            TagChip(
                label = tag.name,
                custom = tag.isCustom,
                count = tag.count.takeIf { it > 0 },
                selected = tag.id in selected,
                onClick = { onToggle(tag.id) },
            )
        }
    }
}
