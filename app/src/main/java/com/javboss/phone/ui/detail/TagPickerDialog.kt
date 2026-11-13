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
import com.javboss.phone.data.TagOrderRules
import com.javboss.phone.data.api.dto.TagDto
import com.javboss.phone.ui.components.TagChip
import com.javboss.phone.ui.components.adaptiveDialogWidth
import kotlinx.coroutines.launch

/**
 * 服务端「视频标签」与「JAV 自定义标签」是两套互不相通的数据，
 * 这里按 mode 分别走各自的读取 / 新建 / 保存接口：
 *  - VIDEO：GET /tags、POST /tags、POST /videos/tags/replace
 *  - JAV  ：GET /jav/tags、POST /jav/tags、PUT /jav/items/{id}（只传 tag_ids）
 *
 * v0.3.71：
 * - 标签用彩底白字胶囊（[TagChip]）展示，与标签菜单 / 封面角标一致（自定义绿色、刮削橙色）；
 * - **自定义标签排在最前**并可勾选；刮削标签排在后面、只读展示（服务端 tag_ids 不接受它们）；
 * - 保存时只提交 provider=3 且 id>0 的标签，避免 `invalid tag_id` 导致整单失败。
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
    // 只有用户自定义标签（provider=3）能提交：PUT /jav/items/{id} 的 tag_ids 要求全部
    // is_user=true，混入刮削标签会被判 invalid tag_id 整单失败。
    var selected by remember {
        mutableStateOf(
            if (isJav) TagOrderRules.userTags(currentTags).map { it.id }.toSet()
            else currentTags.map { it.id }.toSet(),
        )
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

    // JAV：可勾选的只有自定义标签；刮削标签在下半区只读展示，让用户能看到全貌
    val editableTags = if (isJav) TagOrderRules.userTags(allTags) else allTags
    val readOnlyTags = if (isJav) TagOrderRules.scrapedTags(allTags) else emptyList()

    AlertDialog(
        modifier = Modifier.adaptiveDialogWidth(),
        onDismissRequest = onDismiss,
        title = { Text(if (isJav) "编辑标签" else "编辑视频标签") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    if (isJav) "编辑该作品的 JAV 自定义标签；与视频标签是两套互不相通的数据。"
                    else "只修改这个视频文件的标签；作品标签请在操作栏「编辑」里修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (allTags.isEmpty()) {
                    Text("加载标签中…", style = MaterialTheme.typography.bodySmall)
                } else {
                    if (isJav && editableTags.isNotEmpty()) {
                        Text(
                            "自定义标签",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        editableTags.forEach { tag ->
                            TagChip(
                                label = tag.name,
                                custom = tag.isCustom,
                                count = tag.count.takeIf { it > 0 },
                                selected = tag.id in selected,
                                onClick = {
                                    selected = if (tag.id in selected) selected - tag.id
                                    else selected + tag.id
                                },
                            )
                        }
                    }
                    if (readOnlyTags.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "刮削标签（只读，不在此处编辑）",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            readOnlyTags.forEach { tag ->
                                TagChip(
                                    label = tag.name,
                                    custom = false,
                                    count = tag.count.takeIf { it > 0 },
                                    enabled = false,
                                    onClick = {},
                                )
                            }
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
                                    selected = selected + tag.id
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
                    if (saving) return@TextButton
                    saving = true
                    if (isJav) {
                        // 只提交 provider=3 且 id>0 的标签，避免服务端整单拒绝
                        val ids = TagOrderRules.userTags(allTags)
                            .filter { it.id in selected }
                            .map { it.id }
                        viewModel.saveJavTags(entityId, ids)
                    } else {
                        viewModel.saveVideoTags(entityId, selected.toList())
                    }
                    onDismiss()
                },
                enabled = !saving,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
