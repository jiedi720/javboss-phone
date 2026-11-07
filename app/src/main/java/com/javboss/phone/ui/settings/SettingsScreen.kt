package com.javboss.phone.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.BuildConfig
import com.javboss.phone.data.LaunchTab
import com.javboss.phone.data.ServerPreset
import com.javboss.phone.data.WindowSizeRules
import com.javboss.phone.data.api.dto.DirectoryDto
import com.javboss.phone.ui.components.adaptiveDialogWidth
import com.javboss.phone.ui.components.adaptiveMaxWidth
import com.javboss.phone.ui.components.rememberWindowWidthDp

/**
 * 设置页：每行卡片数（置顶）/ 账号 / 读取路径 / 关于（放最下面）。
 * 读取路径照服务端「目录管理」设计，与服务端是同一份数据（/directories）：
 * 增删、启用 / 停用都会直接影响服务端扫描范围与内容展示。
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
    // 探测地址输入框的本地草稿；持久化值变化（保存成功）时同步刷新
    var probeDraft by remember(state.probeUrlOverride) { mutableStateOf(state.probeUrlOverride) }

    LaunchedEffect(state.notice, state.error) {
        val message = state.notice ?: state.error
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    // 平板上整页限宽居中：卡片不再横跨 11 寸屏，分组结构保持不变；
    // 手机 / 窄窗口上限是「不限宽」，与旧版完全一样。
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .adaptiveMaxWidth(WindowSizeRules.settingsMaxWidth(rememberWindowWidthDp()))
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

        // ---------- 每行卡片数（置顶，最常用的设置项） ----------
        SectionTitle("每行卡片数")
        Card(Modifier.fillMaxWidth()) {
            // v0.3.53：两个下拉一左一右并排，各占一半宽度。选项文字很短，
            // 不必再上下占两行；框内显示短值，完整的「自动（N 列）」留在下拉选项里。
            Row(
                Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 竖屏 / 横屏分开设定：各自只有放得下的候选值，不再出现
                // 「竖屏选 6 列却只显示 3 列」这种无效选项
                ColumnChoiceDropdown(
                    label = "竖屏",
                    autoLabel = "自动（3 列）",
                    options = WindowSizeRules.PORTRAIT_COLUMN_CHOICES,
                    selected = state.portraitColumnsChoice,
                    onSelect = viewModel::setPortraitColumns,
                    modifier = Modifier.weight(1f),
                )
                ColumnChoiceDropdown(
                    label = "横屏",
                    autoLabel = "自动（6 列）",
                    options = WindowSizeRules.LANDSCAPE_COLUMN_CHOICES,
                    selected = state.landscapeColumnsChoice,
                    onSelect = viewModel::setLandscapeColumns,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

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
                Spacer(Modifier.height(2.dp))
                // 用紧凑的文本按钮：文字跟随新主题橙，不再用旧的浅红语义色；
                // 也不用整行高的 TextButton，卡片留白明显收窄
                Text(
                    text = "退出登录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { showLogoutConfirm = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        // ---------- 按所在网络自动切换登录 ----------
        Spacer(Modifier.height(16.dp))
        SectionTitle("自动切换登录")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("根据网络自动切换登录", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "在家能连上「家里」地址就用局域网登录；连不上就自动切到「外面」",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.autoSwitchEnabled,
                        onCheckedChange = viewModel::setAutoSwitch,
                    )
                }

                if (state.autoSwitchEnabled) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))

                    val presets = state.switchPresets
                    if (presets.size < 2) {
                        Text(
                            "需要至少两条「已保存密码」的预设（一条家里、一条外面）。" +
                                "请先在登录页把两条地址都登录并勾上「记住密码」。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        RoleOptions(
                            title = "家里（局域网）",
                            presets = presets,
                            selected = state.lanPresetUrl,
                            onSelect = viewModel::setLanPreset,
                        )
                        Spacer(Modifier.height(10.dp))
                        RoleOptions(
                            title = "外面（外网）",
                            presets = presets,
                            selected = state.wanPresetUrl,
                            onSelect = viewModel::setWanPreset,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(10.dp))
                    Text("探测地址", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = probeDraft,
                        onValueChange = { probeDraft = it },
                        placeholder = {
                            Text(state.lanPresetUrl.ifBlank { "留空 = 家里预设的地址" })
                        },
                        supportingText = {
                            Text(
                                "留空直接探测「家里」那条预设的地址；能连上就认为在家。" +
                                    "也可以另填一个地址（例如只探测 NAS 是否在线）。",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        singleLine = true,
                        isError = state.probeError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.probeError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.saveProbe(probeDraft) }) {
                            Text("保存探测地址")
                        }
                        TextButton(onClick = viewModel::probeNow) { Text("立即检测") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "打开 App、从后台切回、网络变化时自动检查；正在播放视频时不打断，" +
                            "退出播放后再切。切换会用目标预设里保存的密码重新登录，" +
                            "失败会保持原来的连接。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---------- 显示 ----------
        Spacer(Modifier.height(16.dp))
        SectionTitle("显示")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                // v0.3.51：开启后横屏 / 宽窗口的原左侧栏只留图标，主 tab 与
                // 页面工具项都不再显示文字，整体更紧凑
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("左侧栏紧凑模式", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "只显示图标，不显示文字",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.railCompactMode,
                        onCheckedChange = viewModel::setRailCompactMode,
                    )
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text("启动显示界面", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                // 冷启动只给「视频 / JAV」两个入口；收藏夹与设置不作为启动页
                Row(Modifier.fillMaxWidth()) {
                    LaunchTab.startupOptions.forEach { option ->
                        Row(
                            Modifier
                                .weight(1f)
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
                }
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
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("锁屏自动打码", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "手机锁屏时自动给封面糊上毛玻璃，解锁后保持",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.autoMaskOnLock,
                        onCheckedChange = viewModel::setAutoMaskOnLock,
                    )
                }
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
                            onToggleEnabled = { viewModel.setDirectoryEnabled(dir.id, dir.path, it) },
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
        // ---------- 关于（放最下面）----------
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
        Spacer(Modifier.height(24.dp))
        }
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
            modifier = Modifier.adaptiveDialogWidth(),
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
            modifier = Modifier.adaptiveDialogWidth(),
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("退出登录") },
            text = { Text("将清除本机的登录状态，需要重新输入服务器地址和密码。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout()
                }) { Text("退出", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) { Text("取消") }
            },
        )
    }
}

/** 「家里 / 外面」用哪条预设：列出全部已存密码的预设，单选 */
@Composable
private fun RoleOptions(
    title: String,
    presets: List<ServerPreset>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelMedium)
    presets.forEach { preset ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onSelect(preset.url) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = preset.url == selected,
                onClick = { onSelect(preset.url) },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    preset.name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    preset.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 某个方向（竖屏 / 横屏）的列数下拉。候选值由 [options] 给定，0 = 自动，
 * 其余是「N 列」；两个方向各自独立，选项里只放该方向真正显示得了的列数。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnChoiceDropdown(
    label: String,
    autoLabel: String,
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // v0.3.53：并排后每个下拉只有半屏宽，框里显示短值（自动 / N 列）才放得下；
    // 「自动（3 列）」这类完整说明仍留在下拉选项里，信息不丢。
    fun displayTextOf(n: Int) = if (n == 0) "自动" else "$n 列"
    fun optionTextOf(n: Int) = if (n == 0) autoLabel else "$n 列"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = displayTextOf(selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { n ->
                DropdownMenuItem(
                    text = { Text(optionTextOf(n)) },
                    onClick = {
                        expanded = false
                        onSelect(n)
                    },
                )
            }
        }
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

/**
 * 读取路径一行，照服务端「目录管理」的布局：左侧路径 + 已扫描/已刮削统计，
 * 右上「启用」开关（是否显示此目录里的内容），右下扫描 / 删除按钮；停用额外给出提示。
 */
@Composable
private fun DirectoryRow(
    dir: DirectoryDto,
    enabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onScan: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(dir.path, style = MaterialTheme.typography.bodyMedium)
                Text(
                    buildString {
                        append("${dir.scannedVideoCount} 个视频")
                        append(" · 已刮削 ${dir.scrapedVideoCount}")
                        if (dir.isScanning) append(" · 扫描中")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = dir.enabled,
                    onCheckedChange = onToggleEnabled,
                    enabled = enabled,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!dir.enabled) {
                Text(
                    "已停用 · 不参与扫描与展示",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
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
}

@Composable
private fun AddDirectoryDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.adaptiveDialogWidth(),
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
