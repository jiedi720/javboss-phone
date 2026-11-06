package com.javboss.phone.ui.login

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.ServerPreset
import com.javboss.phone.data.api.AuthExpiredException
import com.javboss.phone.data.api.ServerAddressResult
import com.javboss.phone.data.api.ServerAddressRules
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** 预设弹窗：新增（Save）与重命名（Rename）共用一套输入状态 */
sealed interface PresetDialog {
    val name: String
    val url: String
    val error: String?

    data class Save(
        override val name: String,
        override val url: String,
        override val error: String? = null,
    ) : PresetDialog

    data class Rename(
        override val name: String,
        override val url: String,
        override val error: String? = null,
    ) : PresetDialog
}

data class LoginUiState(
    val serverUrl: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** 地址实时校验/归一化提示（灰色小字），不阻断输入 */
    val addressHint: String? = null,
    /** 当前输入不合法（提示用错误色） */
    val addressInvalid: Boolean = false,
    /** 登录成功后是否把密码一并存进预设（默认开，点预设即可直接进） */
    val rememberPassword: Boolean = true,
    val presets: List<ServerPreset> = emptyList(),
    val manageMode: Boolean = false,
    /** 当前输入命中的预设（用于高亮 / 判断是否已保存） */
    val activePresetUrl: String? = null,
    /** 自动切换登录里「家里 / 外面」两条预设的 url，用于在预设行上打小标签 */
    val lanPresetUrl: String? = null,
    val wanPresetUrl: String? = null,
    val dialog: PresetDialog? = null,
)

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JavBossApp).container

    var uiState by mutableStateOf(
        LoginUiState(
            // 没存过地址时仅预填局域网地址，密码必须由用户输入
            serverUrl = container.session.serverUrl.ifBlank { DEFAULT_SERVER_URL },
            password = "",
        ).let { it.copy(presets = container.presets.presets) }
    )
        private set

    init {
        refreshPresetState()
    }

    fun onServerUrlChange(value: String) {
        val hint = hintFor(value)
        uiState = uiState.copy(
            serverUrl = value,
            error = null,
            addressHint = hint,
            addressInvalid = ServerAddressRules.resolve(value) is ServerAddressResult.Invalid,
        ).let { it.copy(activePresetUrl = activePreset(it.serverUrl, it.presets)) }
    }

    fun onPasswordChange(value: String) {
        uiState = uiState.copy(password = value, error = null)
    }

    fun onRememberPasswordChange(value: Boolean) {
        uiState = uiState.copy(rememberPassword = value)
    }

    /**
     * 点选预设：填地址 + 密码；该预设存过密码就直接登录，否则只填地址等用户输入。
     */
    fun selectPreset(preset: ServerPreset, onSuccess: () -> Unit) {
        uiState = uiState.copy(
            serverUrl = preset.url,
            password = preset.password,
            error = null,
            addressHint = hintFor(preset.url),
            addressInvalid = false,
            activePresetUrl = preset.url,
        )
        if (preset.canAutoLogin) {
            performLogin(preset.url, preset.password, fromPreset = true, onSuccess = onSuccess)
        }
    }

    fun toggleManageMode() {
        uiState = uiState.copy(manageMode = !uiState.manageMode)
    }

    // ---------- 预设新增 / 重命名 / 删除 ----------

    fun requestSavePreset() {
        val url = uiState.serverUrl
        when (val resolved = ServerAddressRules.resolve(url)) {
            is ServerAddressResult.Invalid ->
                uiState = uiState.copy(error = "无法保存预设：${resolved.reason}")
            is ServerAddressResult.Valid -> {
                val existing = uiState.presets.firstOrNull { it.url == resolved.url }
                uiState = uiState.copy(
                    dialog = PresetDialog.Save(
                        name = existing?.name ?: ServerAddressRules.suggestName(resolved.url),
                        url = resolved.url,
                    ),
                )
            }
        }
    }

    fun requestRename(url: String) {
        val preset = uiState.presets.firstOrNull { it.url == url } ?: return
        uiState = uiState.copy(dialog = PresetDialog.Rename(preset.name, preset.url))
    }

    fun onDialogNameChange(value: String) {
        uiState = uiState.copy(
            dialog = when (val dialog = uiState.dialog) {
                is PresetDialog.Save -> dialog.copy(name = value, error = null)
                is PresetDialog.Rename -> dialog.copy(name = value, error = null)
                null -> null
            },
        )
    }

    fun dismissDialog() {
        uiState = uiState.copy(dialog = null)
    }

    fun confirmDialog() {
        val dialog = uiState.dialog ?: return
        if (dialog.name.isBlank()) {
            uiState = uiState.copy(
                dialog = when (dialog) {
                    is PresetDialog.Save -> dialog.copy(error = "名称不能为空")
                    is PresetDialog.Rename -> dialog.copy(error = "名称不能为空")
                },
            )
            return
        }
        viewModelScope.launch {
            when (dialog) {
                is PresetDialog.Save -> container.presets.upsert(dialog.url, dialog.name)
                is PresetDialog.Rename -> container.presets.rename(dialog.url, dialog.name)
            }
            uiState = uiState.copy(dialog = null)
            refreshPresetState()
        }
    }

    fun deletePreset(url: String) {
        viewModelScope.launch {
            container.presets.remove(url)
            refreshPresetState()
        }
    }

    // ---------- 登录 ----------

    fun login(onSuccess: () -> Unit) {
        val resolved = ServerAddressRules.resolve(uiState.serverUrl)
        if (resolved is ServerAddressResult.Invalid) {
            uiState = uiState.copy(error = resolved.reason)
            return
        }
        if (uiState.password.isBlank()) {
            uiState = uiState.copy(error = "请填写密码")
            return
        }
        performLogin(
            url = (resolved as ServerAddressResult.Valid).url,
            password = uiState.password,
            fromPreset = false,
            onSuccess = onSuccess,
        )
    }

    private fun performLogin(
        url: String,
        password: String,
        fromPreset: Boolean,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                container.authRepository.login(url, password)
                // 登录成功即视为「用过的地址」：自动存入预设并置顶（同名/同址去重）
                // 勾了「记住密码」就把密码一并存下，下次点预设直接进
                val savePassword = when {
                    uiState.rememberPassword -> password
                    fromPreset -> null // 点预设登录成功、但用户没勾记住：不动已存的密码
                    else -> "" // 手输登录且没勾记住：清掉旧的，避免用过期密码
                }
                runCatching { container.presets.upsert(url, password = savePassword) }
                uiState = uiState.copy(loading = false)
                refreshPresetState()
                onSuccess()
            } catch (e: Exception) {
                val message = when (e) {
                    is HttpException -> {
                        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
                        if (e.code() == 429) "尝试次数过多，请稍后再试"
                        else container.apiClient.parseError(body)
                    }
                    is IOException -> "无法连接服务器，请检查地址和网络"
                    is AuthExpiredException -> "登录状态异常，请重试"
                    else -> e.message ?: "登录失败"
                }
                // 预设里的密码被服务端拒绝：清掉它，免得每次点都撞同一个错
                if (fromPreset && e is HttpException && e.code() == 401) {
                    runCatching { container.presets.clearPassword(url) }
                    uiState = uiState.copy(loading = false, error = "预设里保存的密码已失效，请重新输入")
                    refreshPresetState()
                    return@launch
                }
                uiState = uiState.copy(loading = false, error = message)
            }
        }
    }

    // ---------- 内部 ----------

    private fun refreshPresetState() {
        val presets = container.presets.presets
        uiState = uiState.copy(
            presets = presets,
            activePresetUrl = activePreset(uiState.serverUrl, presets),
            lanPresetUrl = container.settings.lanPresetUrl.takeIf { it.isNotBlank() },
            wanPresetUrl = container.settings.wanPresetUrl.takeIf { it.isNotBlank() },
        )
    }

    private fun activePreset(input: String, presets: List<ServerPreset>): String? {
        val normalized = ServerAddressRules.normalize(input) ?: return null
        return presets.firstOrNull { it.url == normalized }?.url
    }

    /** 输入过程中的提示：错误直接说原因，合法但被改写时告知将使用的地址 */
    private fun hintFor(input: String): String? {
        if (input.isBlank()) return null
        return when (val resolved = ServerAddressRules.resolve(input)) {
            is ServerAddressResult.Invalid -> "地址有误：${resolved.reason}"
            is ServerAddressResult.Valid -> {
                val trimmed = input.trim()
                if (trimmed == resolved.url || "$trimmed/" == resolved.url) {
                    "已识别为${resolved.kind.label}"
                } else {
                    "将使用 ${resolved.url}"
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_SERVER_URL = "http://192.168.1.1:8655"
    }
}
