package com.javboss.phone.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.AutoSwitchRules
import com.javboss.phone.data.LaunchTab
import com.javboss.phone.data.ServerPreset
import com.javboss.phone.data.api.AuthExpiredException
import com.javboss.phone.data.api.ServerAddressRules
import com.javboss.phone.data.api.dto.DirectoryCreateRequest
import com.javboss.phone.data.api.dto.DirectoryDto
import com.javboss.phone.data.api.dto.DirectoryUpdateRequest
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** 当前登录的服务器地址 */
    val serverUrl: String = "",
    /** 竖屏列数选择值：0 = 自动（3 列），可选 2 / 3 */
    val portraitColumnsChoice: Int = 0,
    /** 横屏列数选择值：0 = 自动（6 列），可选 5 / 6 */
    val landscapeColumnsChoice: Int = 0,
    /** 封面毛玻璃：作品/女优封面一直模糊显示 */
    val coverMask: Boolean = false,
    /** 锁屏后自动给封面打码；关掉后锁屏不再触发（常开的封面毛玻璃不受影响） */
    val autoMaskOnLock: Boolean = true,
    /** 冷启动默认进入的 tab（默认 JAV） */
    val launchTab: LaunchTab = LaunchTab.JAV,
    /** 按所在网络自动切换登录：总开关 */
    val autoSwitchEnabled: Boolean = false,
    /** 「家里（局域网）」用哪条预设（归一化 url） */
    val lanPresetUrl: String = "",
    /** 「外面（外网）」用哪条预设（归一化 url） */
    val wanPresetUrl: String = "",
    /** 探测地址覆盖；留空 = 跟随家里预设的地址 */
    val probeUrlOverride: String = "",
    /** 可用于自动切换的预设（存过密码的），供设置页单选 */
    val switchPresets: List<ServerPreset> = emptyList(),
    /** 探测地址输入校验错误 */
    val probeError: String? = null,
    /** 服务端的「读取路径」列表 */
    val directories: List<DirectoryDto> = emptyList(),
    val loading: Boolean = false,
    /** 有写操作在进行（新增/删除/扫描），按钮转圈用 */
    val busy: Boolean = false,
    val error: String? = null,
    /** 一次性提示，UI 用 Toast 展示 */
    val notice: String? = null,
    val sessionExpired: Boolean = false,
)

/**
 * 设置页：账号信息、自动切换登录、版本信息、以及和服务端一致的「读取路径」管理。
 * 读取路径走 /directories 系列接口（与 Web 端「目录管理」同一套）：
 *   GET /directories、POST /directories、PATCH /directories/{id}（改路径 / enabled / is_delete）、
 *   POST /directories/{id}/scan
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JavBossApp).container

    var uiState by mutableStateOf(
        SettingsUiState(
            serverUrl = container.session.serverUrl,
            portraitColumnsChoice = container.settings.portraitColumnsChoice,
            landscapeColumnsChoice = container.settings.landscapeColumnsChoice,
            coverMask = container.settings.coverMask,
            autoMaskOnLock = container.settings.autoMaskOnLock,
            launchTab = container.settings.launchTab,
            autoSwitchEnabled = container.settings.autoSwitchEnabled,
            lanPresetUrl = container.settings.lanPresetUrl,
            wanPresetUrl = container.settings.wanPresetUrl,
            probeUrlOverride = container.settings.probeUrlOverride,
            switchPresets = container.presets.presets.filter { it.canAutoLogin },
        ),
    )
        private set

    init {
        refreshDirectories()
    }

    /** 竖屏列数：0 = 自动（3 列），2 / 3 手选；写入后网格立刻跟着变 */
    fun setPortraitColumns(choice: Int) {
        if (choice == uiState.portraitColumnsChoice) return
        uiState = uiState.copy(portraitColumnsChoice = choice)
        viewModelScope.launch { container.settings.savePortraitColumns(choice) }
    }

    /** 横屏列数：0 = 自动（6 列），5 / 6 手选；写入后网格立刻跟着变 */
    fun setLandscapeColumns(choice: Int) {
        if (choice == uiState.landscapeColumnsChoice) return
        uiState = uiState.copy(landscapeColumnsChoice = choice)
        viewModelScope.launch { container.settings.saveLandscapeColumns(choice) }
    }

    /** 封面毛玻璃：写入设置后，已经在显示的作品/女优墙立刻糊掉 */
    fun setCoverMask(enabled: Boolean) {
        if (enabled == uiState.coverMask) return
        uiState = uiState.copy(coverMask = enabled)
        viewModelScope.launch { container.settings.saveCoverMask(enabled) }
    }

    /** 锁屏自动打码：关掉后锁屏不再自动糊封面，已打的码也会立刻解除 */
    fun setAutoMaskOnLock(enabled: Boolean) {
        if (enabled == uiState.autoMaskOnLock) return
        uiState = uiState.copy(autoMaskOnLock = enabled)
        viewModelScope.launch { container.settings.saveAutoMaskOnLock(enabled) }
    }

    /** 启动显示界面：只改设置，不动当前 tab，下次从桌面冷启动才生效 */
    fun setLaunchTab(tab: LaunchTab) {
        if (tab == uiState.launchTab) return
        uiState = uiState.copy(launchTab = tab)
        viewModelScope.launch { container.settings.saveLaunchTab(tab) }
    }

    // ---------- 按所在网络自动切换登录 ----------

    /**
     * 总开关。第一次打开且两条角色都空时，按预设地址自动认：私网 IP = 家里，域名 / 公网 = 外面；
     * 认得不准可在下面的单选里改。开启后立刻检查一次，让用户马上看到结果。
     */
    fun setAutoSwitch(enabled: Boolean) {
        if (enabled == uiState.autoSwitchEnabled) return
        uiState = uiState.copy(autoSwitchEnabled = enabled)
        viewModelScope.launch {
            if (enabled && uiState.lanPresetUrl.isBlank() && uiState.wanPresetUrl.isBlank()) {
                val (lan, wan) = AutoSwitchRules.guessRoles(container.presets.presets)
                container.settings.saveAutoSwitchRoles(lan, wan)
                uiState = uiState.copy(lanPresetUrl = lan, wanPresetUrl = wan)
            }
            container.settings.saveAutoSwitchEnabled(enabled)
            uiState = uiState.copy(switchPresets = usablePresets())
            // 同一协程里、开关已落盘之后再检查，避免读到旧值被跳过
            if (enabled) container.autoLoginSwitcher.requestCheck("enabled", force = true)
        }
    }

    fun setLanPreset(url: String) {
        val wan = uiState.wanPresetUrl.takeIf { it != url } ?: ""
        viewModelScope.launch {
            container.settings.saveAutoSwitchRoles(url, wan)
            uiState = uiState.copy(lanPresetUrl = url, wanPresetUrl = wan)
        }
    }

    fun setWanPreset(url: String) {
        val lan = uiState.lanPresetUrl.takeIf { it != url } ?: ""
        viewModelScope.launch {
            container.settings.saveAutoSwitchRoles(lan, url)
            uiState = uiState.copy(lanPresetUrl = lan, wanPresetUrl = url)
        }
    }

    /** 保存探测地址覆盖值；留空 = 跟随「家里」预设地址 */
    fun saveProbe(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty() && !ServerAddressRules.isValid(trimmed)) {
            uiState = uiState.copy(probeError = "地址格式不正确，请填写 http:// 或 https:// 开头的完整地址")
            return
        }
        val value = if (trimmed.isEmpty()) "" else ServerAddressRules.normalize(trimmed).orEmpty()
        viewModelScope.launch {
            container.settings.saveProbeUrlOverride(value)
            uiState = uiState.copy(
                probeUrlOverride = value,
                probeError = null,
                notice = if (value.isEmpty()) "已恢复为探测「家里」预设的地址" else "探测地址已更新",
            )
        }
    }

    /** 设置页「立即检测」：强制跑一次并给出结果提示 */
    fun probeNow() {
        container.autoLoginSwitcher.requestCheck("manual", force = true)
    }

    private fun usablePresets(): List<ServerPreset> =
        container.presets.presets.filter { it.canAutoLogin }

    // ---------- 读取路径 ----------

    fun refreshDirectories() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                val list = container.apiClient.api().listDirectories()
                    .filterNot { it.isDelete }
                    .sortedBy { it.path.lowercase() }
                uiState = uiState.copy(directories = list, loading = false)
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(loading = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(loading = false, error = e.message ?: "加载读取路径失败")
            }
        }
    }

    fun addDirectory(path: String, onDone: () -> Unit) {
        val trimmed = path.trim()
        if (trimmed.isBlank()) {
            uiState = uiState.copy(error = "路径不能为空")
            return
        }
        viewModelScope.launch {
            uiState = uiState.copy(busy = true, error = null)
            try {
                val created = container.apiClient.api().createDirectory(DirectoryCreateRequest(trimmed))
                uiState = uiState.copy(busy = false, notice = "已添加 ${created.path.ifBlank { trimmed }}")
                onDone()
                refreshDirectories()
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(busy = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(busy = false, error = e.message ?: "添加失败")
            }
        }
    }

    fun deleteDirectory(id: Long, path: String) {
        viewModelScope.launch {
            uiState = uiState.copy(busy = true, error = null)
            try {
                container.apiClient.api()
                    .updateDirectory(id, DirectoryUpdateRequest(isDelete = true))
                uiState = uiState.copy(busy = false, notice = "已移除 $path")
                refreshDirectories()
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(busy = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(busy = false, error = e.message ?: "移除失败")
            }
        }
    }

    fun scanDirectory(id: Long, path: String) {
        viewModelScope.launch {
            uiState = uiState.copy(busy = true, error = null)
            try {
                container.apiClient.api().scanDirectory(id)
                uiState = uiState.copy(busy = false, notice = "已触发扫描：$path（进度在服务端）")
                refreshDirectories()
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(busy = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(busy = false, error = e.message ?: "触发扫描失败")
            }
        }
    }

    /**
     * 启用 / 停用读取路径：与 Web 端目录管理里的「启用」开关同一套接口，
     * PATCH /directories/{id} body { enabled }，停用后服务端不再扫描、也不展示该目录里的内容。
     */
    fun setDirectoryEnabled(id: Long, path: String, enabled: Boolean) {
        viewModelScope.launch {
            uiState = uiState.copy(busy = true, error = null)
            try {
                container.apiClient.api()
                    .updateDirectory(id, DirectoryUpdateRequest(enabled = enabled))
                uiState = uiState.copy(
                    busy = false,
                    notice = if (enabled) "已启用 $path" else "已停用 $path",
                )
                refreshDirectories()
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(busy = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(busy = false, error = e.message ?: "切换启用状态失败")
                // 失败也要拉一次，避免开关停在错误的状态
                refreshDirectories()
            }
        }
    }

    fun consumeNotice() {
        uiState = uiState.copy(notice = null, error = null)
    }
}
