package com.javboss.phone.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.api.AuthExpiredException
import com.javboss.phone.data.api.dto.DirectoryCreateRequest
import com.javboss.phone.data.LaunchTab
import com.javboss.phone.data.api.dto.DirectoryDto
import com.javboss.phone.data.api.dto.DirectoryUpdateRequest
import kotlinx.coroutines.launch

data class SettingsUiState(
    /** 当前登录的服务器地址 */
    val serverUrl: String = "",
    /** 卡片墙每行数量（2/3/4），与作品/收藏夹共用同一份设置 */
    val gridColumns: Int = 2,
    /** 封面毛玻璃：作品/女优封面一直模糊显示 */
    val coverMask: Boolean = false,
    /** 冷启动默认进入的 tab（默认 JAV） */
    val launchTab: LaunchTab = LaunchTab.JAV,
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
 * 设置页：账号信息、版本信息、以及和服务端一致的「读取路径」管理。
 * 读取路径走 /directories 系列接口（与 Web 端「目录管理」同一套）：
 *   GET /directories、POST /directories、PATCH /directories/{id}（改路径 / enabled / is_delete）、
 *   POST /directories/{id}/scan
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JavBossApp).container

    var uiState by mutableStateOf(
        SettingsUiState(
            serverUrl = container.session.serverUrl,
            gridColumns = container.settings.gridColumns,
            coverMask = container.settings.coverMask,
            launchTab = container.settings.launchTab,
        ),
    )
        private set

    init {
        refreshDirectories()
    }

    /** 每行卡片数：写入设置后，作品页/收藏夹通过 StateFlow 立刻跟着变 */
    fun setGridColumns(count: Int) {
        val clamped = count.coerceIn(2, 4)
        if (clamped == uiState.gridColumns) return
        uiState = uiState.copy(gridColumns = clamped)
        viewModelScope.launch { container.settings.saveGridColumns(clamped) }
    }

    /** 封面毛玻璃：写入设置后，已经在显示的作品/女优墙立刻糊掉 */
    fun setCoverMask(enabled: Boolean) {
        if (enabled == uiState.coverMask) return
        uiState = uiState.copy(coverMask = enabled)
        viewModelScope.launch { container.settings.saveCoverMask(enabled) }
    }

    /** 启动显示界面：只改设置，不动当前 tab，下次从桌面冷启动才生效 */
    fun setLaunchTab(tab: LaunchTab) {
        if (tab == uiState.launchTab) return
        uiState = uiState.copy(launchTab = tab)
        viewModelScope.launch { container.settings.saveLaunchTab(tab) }
    }

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
