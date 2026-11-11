package com.javboss.phone.ui.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import kotlinx.coroutines.launch

/**
 * 卡片右上角星标对应要修改归属的实体。
 * kind = "jav" | "idol"（决定 /jav/{kind}-favorite-groups），
 * entity = "items" | "idols"（决定 /jav/{entity}/{id}/favorite-groups）。
 */
data class FavoriteTarget(
    val kind: String,
    val entity: String,
    val id: Long,
    val title: String,
) {
    companion object {
        /** 作品：/jav/jav-favorite-groups + /jav/items/{id}/favorite-groups */
        fun jav(id: Long) = FavoriteTarget("jav", "items", id, "作品收藏夹")

        /** 女优：/jav/idol-favorite-groups + /jav/idols/{id}/favorite-groups */
        fun idol(id: Long) = FavoriteTarget("idol", "idols", id, "女优收藏夹")
    }
}

/**
 * 点击封面星标后的「选择收藏夹」悬浮窗（Web 端 QC「选择收藏夹」的移动端形态）。
 *
 * 用居中 Popup 而不是全屏遮罩 Dialog：视觉是浮在内容上的一张卡片，同时避免窄窗口
 * 里锚定在右上角会向左溢出屏幕。数据全部走服务端：
 *  GET  /jav/{kind}-favorite-groups              全部收藏夹
 *  GET  /jav/{entity}/{id}/favorite-groups      当前已勾选
 *  PUT  /jav/{entity}/{id}/favorite-groups      全量替换（保存）
 *  POST /jav/{kind}-favorite-groups             新建
 *
 * 保存成功后把新的收藏夹个数回传角标，封面星标即时刷新，不必等列表重新拉数据。
 */
@Composable
fun FavoritePickerPopup(
    target: FavoriteTarget,
    onDismiss: () -> Unit,
    onSaved: (count: Int) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember {
        (context.applicationContext as JavBossApp).container.favoriteRepository
    }
    val scope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<FavoriteGroupDto>?>(null) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(target) {
        try {
            val g = repository.listGroups(target.kind)
            val s = repository.selectedGroupIds(target.id, target.entity)
            groups = g
            selected = s.toSet()
            error = null
        } catch (e: Exception) {
            error = e.message ?: "加载收藏夹失败"
        }
    }

    Popup(
        popupPositionProvider = CenteredPopupPositionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.9f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        target.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
                Text(
                    "选择收藏夹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                when {
                    groups == null && error == null -> {
                        Box(
                            Modifier.fillMaxWidth().height(80.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                    error != null -> {
                        Text(
                            error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            val list = groups.orEmpty()
                            list.forEach { group ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = if (group.id in selected) {
                                                selected - group.id
                                            } else {
                                                selected + group.id
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = group.id in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) {
                                                selected + group.id
                                            } else {
                                                selected - group.id
                                            }
                                        },
                                    )
                                    Text(
                                        group.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "${group.count}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (list.isEmpty()) {
                                Text(
                                    "暂无收藏夹，可在下方新建",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 12.dp),
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
                        placeholder = { Text("新建收藏夹") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    TextButton(
                        enabled = newName.isNotBlank() && !creating,
                        onClick = {
                            creating = true
                            scope.launch {
                                try {
                                    val created = repository.createGroup(newName.trim(), target.kind)
                                    groups = groups.orEmpty() + created
                                    selected = selected + created.id
                                    newName = ""
                                    error = null
                                } catch (e: Exception) {
                                    error = e.message ?: "新建失败"
                                } finally {
                                    creating = false
                                }
                            }
                        },
                    ) { Text("新建") }
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        enabled = !saving && groups != null,
                        onClick = {
                            saving = true
                            scope.launch {
                                try {
                                    repository.replaceGroups(target.id, selected.toList(), target.entity)
                                    onSaved(selected.size)
                                    onDismiss()
                                } catch (e: Exception) {
                                    error = e.message ?: "保存失败"
                                } finally {
                                    saving = false
                                }
                            }
                        },
                    ) { Text(if (saving) "保存中…" else "保存") }
                }
            }
        }
    }
}

/** 让悬浮窗在窗口内居中，避免锚定在卡片右上角时向左溢出屏幕 */
private object CenteredPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        ((windowSize.width - popupContentSize.width) / 2).coerceAtLeast(0),
        ((windowSize.height - popupContentSize.height) / 2).coerceAtLeast(0),
    )
}
