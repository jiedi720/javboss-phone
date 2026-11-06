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
import androidx.compose.material3.FilterChip
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
import com.javboss.phone.data.api.dto.TagDto
import kotlinx.coroutines.launch

/**
 * 服务端「视频标签」与「JAV 自定义标签」是两套互不相通的数据，
 * 这里按 mode 分别走各自的读取 / 新建 / 保存接口：
 *  - VIDEO：GET /tags、POST /tags、POST /videos/tags/replace
 *  - JAV  ：GET /jav/tags、POST /jav/tags、PUT /jav/items/{id}（只传 tag_ids）
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
    var selected by remember { mutableStateOf(currentTags.map { it.id }.toSet()) }
    var newName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(mode, entityId) {
        val loaded = if (isJav) viewModel.loadJavTagOptions() else viewModel.loadVideoTagOptions()
        // 当前已打上的标签若不在可选列表里（例如被服务端按 hide_jav 过滤掉），
        // 也补进来，避免一打开就因为列表缺项而把已有标签误删
        allTags = (loaded + currentTags).distinctBy { it.id }
    }

    AlertDialog(
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
                    if (isJav) "编辑该作品的 JAV 自定义标签，与视频标签是两套互不相通的数据。"
                    else "只修改这个视频文件的标签；作品标签请在操作栏「编辑」里修改。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                if (allTags.isEmpty()) {
                    Text("加载标签中…", style = MaterialTheme.typography.bodySmall)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = tag.id in selected,
                                onClick = {
                                    selected = if (tag.id in selected) selected - tag.id
                                    else selected + tag.id
                                },
                                label = { Text(tag.name) },
                            )
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
                                    allTags = allTags + tag
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
                    if (isJav) viewModel.saveJavTags(entityId, selected.toList())
                    else viewModel.saveVideoTags(entityId, selected.toList())
                    onDismiss()
                },
                enabled = !saving,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
