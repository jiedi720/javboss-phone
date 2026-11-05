package com.javboss.phone.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.data.ServerPreset

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel(),
) {
    val state = viewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("JavBoss", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "连接你的媒体库服务器",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        // ---------- 连接地址（IP 或完整 URL） ----------
        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = viewModel::onServerUrlChange,
            label = { Text("服务器地址") },
            placeholder = { Text("http://192.168.1.10:8655") },
            singleLine = true,
            isError = state.addressInvalid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            supportingText = {
                val hint = state.addressHint
                    ?: "支持 IP:端口 或完整 URL（如 https://xxx.ugdocker.link）；省略协议头按 http:// 处理"
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.addressInvalid) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            trailingIcon = {
                IconButton(onClick = viewModel::requestSavePreset) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "保存当前地址为预设",
                        tint = if (state.activePresetUrl != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // ---------- 预设列表 ----------
        if (state.presets.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "预设地址（${state.presets.size}）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::toggleManageMode) {
                    Text(if (state.manageMode) "完成" else "管理")
                }
            }
            state.presets.forEach { preset ->
                PresetRow(
                    preset = preset,
                    selected = preset.url == state.activePresetUrl,
                    manageMode = state.manageMode,
                    onSelect = { viewModel.selectPreset(preset, onLoginSuccess) },
                    onRename = { viewModel.requestRename(preset.url) },
                    onDelete = { viewModel.deletePreset(preset.url) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ---------- 密码 ----------
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        // 记住密码开关：开启后密码随预设一起存本地，点预设即可直接进
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("记住密码", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "保存到本机，点预设直接登录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.rememberPassword,
                onCheckedChange = viewModel::onRememberPasswordChange,
            )
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.login(onLoginSuccess) },
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.loading) "登录中…" else "登录")
        }
    }

    state.dialog?.let { dialog ->
        PresetNameDialog(
            dialog = dialog,
            // 保存时若地址已在列表中，提示将覆盖同名项（而不是新增）
            duplicated = dialog is PresetDialog.Save &&
                state.presets.any { it.url == dialog.url },
            onNameChange = viewModel::onDialogNameChange,
            onConfirm = viewModel::confirmDialog,
            onDismiss = viewModel::dismissDialog,
        )
    }
}

/** 单条预设：点按填入地址；管理模式右侧出现重命名/删除 */
@Composable
private fun PresetRow(
    preset: ServerPreset,
    selected: Boolean,
    manageMode: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onSelect)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                preset.name,
                style = MaterialTheme.typography.bodyMedium,
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
        // 存过密码的预设：点一下就直接登录，这里给个锁标记
        if (preset.canAutoLogin) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = "已保存密码，点按直接登录",
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (manageMode) {
            IconButton(onClick = onRename, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "重命名",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除预设",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "当前使用",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PresetNameDialog(
    dialog: PresetDialog,
    duplicated: Boolean,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isSave = dialog is PresetDialog.Save
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isSave) "保存为预设" else "重命名预设") },
        text = {
            Column {
                OutlinedTextField(
                    value = dialog.name,
                    onValueChange = onNameChange,
                    label = { Text("名称") },
                    singleLine = true,
                    isError = dialog.error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    dialog.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (duplicated) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "该地址已在预设中，保存将更新其名称并置顶",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                dialog.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
