package com.javboss.phone.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.BuildConfig
import com.javboss.phone.data.LaunchTab
import com.javboss.phone.data.api.dto.DirectoryDto

/**
 * 设置页：账号 / 关于 / 读取路径。
 * 读取路径与服务端设置页是同一份数据（/directories），App 里增删会直接影响服务端扫描范围。
 */
@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<DirectoryDto?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.notice, state.error) {
        val message = state.notice ?: state.error
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // ---------- 账号 ----------
        SectionTitle("账号")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("当前服务器", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    state.serverUrl.ifBlank { "未登录" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("退出登录", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // ---------- 显示 ----------
        Spacer(Modifier.height(16.dp))
        SectionTitle("显示")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("启动显示界面", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                LaunchTab.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setLaunchTab(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.launchTab == option,
                            onClick = { viewModel.setLaunchTab(option) },
                        )
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "从桌面冷启动 App 时默认进入的页面（默认 JAV）；修改只在下次启动生效，当前不会跳转",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text("每行卡片数", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (2..4).forEach { n ->
                        FilterChip(
                            selected = state.gridColumns == n,
                            onClick = { viewModel.setGridColumns(n) },
                            label = { Text("$n 列") },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "影响作品墙、女优/片商/系列和收藏夹的列数；列数越多，一屏看到的越多，单张卡片越小",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("封面毛玻璃", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "作品和女优的封面用毛玻璃模糊显示",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.coverMask, onCheckedChange = viewModel::setCoverMask)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "另外：App 使用过程中手机锁屏，会自动给封面打码并在解锁后保持；解锁后点列表顶部的「显示封面」即可恢复" +
                        "（彻底退出 App 再打开则不会自动打码）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------- 关于 ----------
        Spacer(Modifier.height(16.dp))
        SectionTitle("关于")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                InfoRow("版本", "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）")
                Spacer(Modifier.height(6.dp))
                InfoRow("打包时间", BuildConfig.BUILD_TIME)
                Spacer(Modifier.height(6.dp))
                InfoRow("包名", BuildConfig.APPLICATION_ID)
            }
        }

        // ---------- 读取路径 ----------
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("读取路径（${state.directories.size}）", Modifier.weight(1f))
            IconButton(onClick = viewModel::refreshDirectories) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "刷新",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 6.dp)) {
                when {
                    state.loading && state.directories.isEmpty() -> Text(
                        "加载中…",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    state.directories.isEmpty() -> Text(
                        "还没有读取路径，点下面添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    else -> state.directories.forEach { dir ->
                        DirectoryRow(
                            dir = dir,
                            enabled = !state.busy,
                            onScan = { viewModel.scanDirectory(dir.id, dir.path) },
                            onDelete = { deleteTarget = dir },
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TextButton(
                    onClick = { showAddDialog = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("+ 添加读取路径")
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "与服务端设置页是同一份数据：这里的增删会直接改变服务端扫描的目录；路径是服务端容器内的绝对路径（如 /media/JAV）。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showAddDialog) {
        AddDirectoryDialog(
            busy = state.busy,
            onDismiss = { showAddDialog = false },
            onConfirm = { path -> viewModel.addDirectory(path) { showAddDialog = false } },
        )
    }

    deleteTarget?.let { dir ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("移除读取路径") },
            text = {
                Text("将从服务端设置中移除「${dir.path}」吗？已入库的视频不会删除，只是不再扫描这个目录。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDirectory(dir.id, dir.path)
                    deleteTarget = null
                }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("将清除本机的登录状态，需要重新输入服务器地址和密码。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout()
                }) { Text("退出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DirectoryRow(
    dir: DirectoryDto,
    enabled: Boolean,
    onScan: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(dir.path, style = MaterialTheme.typography.bodyMedium)
            Text(
                buildString {
                    append("${dir.scannedVideoCount} 个视频")
                    if (dir.isScanning) append(" · 扫描中")
                    if (!dir.enabled) append(" · 已停用")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onScan, enabled = enabled) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "扫描此路径",
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "移除",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AddDirectoryDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加读取路径") },
        text = {
            Column {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("服务端目录绝对路径") },
                    placeholder = { Text("/media/JAV") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "写容器内能看到的路径，添加后点放大镜即可触发扫描",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(path) }, enabled = !busy) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
