package com.javboss.phone.ui.javlist

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.CoverCropRules
import com.javboss.phone.data.JavPrefixRules
import com.javboss.phone.data.api.AuthExpiredException
import com.javboss.phone.data.api.dto.JavDto
import com.javboss.phone.data.api.dto.JavItemUpdateRequest
import com.javboss.phone.data.api.dto.StudioCodePrefixDto
import kotlinx.coroutines.launch

data class JavListUiState(
    val items: List<JavDto> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val sessionExpired: Boolean = false,
    /** v0.3.75：片商详情的番号分组（GET /jav/studios/{id} 的 code_prefixes） */
    val prefixes: List<StudioCodePrefixDto> = emptyList(),
    /** 当前选中的番号前缀；空＝不筛选。点击胶囊后与 studioId 一起传给 /jav */
    val prefix: String = "",
    /** 「名称」升序 / 「作品数」降序，对应 Web `jk` 的排序切换 */
    val prefixByName: Boolean = true,
    val prefixesLoading: Boolean = false,
    val prefixesError: String? = null,
)

/** 演员/标签/片商/系列点击后的过滤作品列表 */
class JavListViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JavBossApp).container

    var uiState by mutableStateOf(JavListUiState())
        private set

    private var idolIds: List<Long> = emptyList()
    private var tagIds: List<Long> = emptyList()
    private var studioId: Long? = null
    private var seriesId: Long? = null

    private var started = false

    fun start(idolIds: String, tagIds: String, studioId: Long, seriesId: Long) {
        if (started) return
        started = true
        this.idolIds = idolIds.split(',').mapNotNull { it.trim().toLongOrNull() }
        this.tagIds = tagIds.split(',').mapNotNull { it.trim().toLongOrNull() }
        this.studioId = studioId.takeIf { it > 0 }
        this.seriesId = seriesId.takeIf { it > 0 }
        refresh()
        // v0.3.75：片商落地页额外拉「番号」分组；其它入口（女优/标签/系列）不请求
        if (studioId > 0L) loadPrefixes(studioId)
    }

    private suspend fun fetch(limit: Int, offset: Int) =
        container.videoRepository.searchJavs(
            limit = limit,
            offset = offset,
            idolIds = idolIds,
            tagIds = tagIds,
            studioId = studioId,
            seriesId = seriesId,
            // 与 Web 端一致：点番号胶囊后同时带 prefix 与 studio_id 联合筛选
            prefix = uiState.prefix,
            sort = "recent",
        )

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                val res = fetch(PAGE_SIZE, 0)
                uiState = uiState.copy(
                    items = res.items,
                    total = res.total,
                    loading = false,
                    endReached = res.items.size >= res.total,
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(loading = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    fun loadMore() {
        val s = uiState
        if (s.loading || s.loadingMore || s.endReached) return
        viewModelScope.launch {
            uiState = uiState.copy(loadingMore = true)
            try {
                val res = fetch(PAGE_SIZE, s.items.size)
                val merged = (s.items + res.items).distinctBy { it.id }
                uiState = uiState.copy(
                    items = merged,
                    total = res.total,
                    loadingMore = false,
                    endReached = merged.size >= res.total || res.items.isEmpty(),
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(loadingMore = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(loadingMore = false, error = e.message ?: "加载失败")
            }
        }
    }

    /**
     * 改喜爱度评分（0..5、步长 0.5）：PUT /jav/items/{id}，与作品页 / 收藏夹同一接口。
     * 女优 / 标签 / 片商 / 系列点进来的是同一个列表，封面左上角的星级也要能改。
     */
    fun updateRating(javId: Long, rating: Double) {
        if (javId <= 0) return
        val normalized = (Math.round(rating * 2) / 2.0).coerceIn(0.0, 5.0)
        viewModelScope.launch {
            try {
                val updated = container.videoRepository.updateJavItem(
                    javId,
                    JavItemUpdateRequest(favoriteRating = normalized),
                )
                uiState = uiState.copy(
                    items = uiState.items.map {
                        if (it.id == javId) it.copy(favoriteRating = updated.favoriteRating) else it
                    },
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (_: Exception) {
                // 失败保持原样，下次刷新以服务端为准
            }
        }
    }

    // ---------- v0.3.75：片商「番号」分组筛选 ----------

    /**
     * 拉片商详情的 code_prefixes（服务端已按番号前缀聚合好该片商的作品数）。
     * 失败只影响筛选区，不阻断作品列表；401 仍按全局约定抛登录过期。
     */
    fun loadPrefixes(studioId: Long) {
        if (studioId <= 0L) return
        viewModelScope.launch {
            uiState = uiState.copy(prefixesLoading = true, prefixesError = null)
            try {
                val detail = container.videoRepository.getJavStudio(studioId)
                uiState = uiState.copy(prefixes = detail.codePrefixes, prefixesLoading = false)
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(prefixesLoading = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    prefixesLoading = false,
                    prefixesError = e.message?.takeIf { it.isNotBlank() } ?: "番号加载失败",
                )
            }
        }
    }

    /** 点番号胶囊：再次点已选中的＝清除（等价于「全部」）；非法前缀不请求 */
    fun onPrefixChange(rawPrefix: String) {
        val next = JavPrefixRules.normalize(rawPrefix)
        if (next == uiState.prefix) return
        uiState = uiState.copy(prefix = next)
        refresh()
    }

    /** 「名称」↔「作品数」排序切换（Web `jk` 的同款两态按钮） */
    fun onPrefixSortToggle() {
        uiState = uiState.copy(prefixByName = JavPrefixRules.toggle(uiState.prefixByName))
    }

    // ---------- 封面裁切 ----------

    /**
     * v0.3.74：列表页长按封面 → 服务端「调整封面」，与主页 / 收藏夹同一接口。
     * 保存成功后只把 cover_crop_position 写回该条作品，卡片立刻按新锚点重新裁切。
     * 返回 null＝成功，非 null＝错误文案（弹窗内提示并允许重试）。
     */
    suspend fun updateCoverCropPosition(javId: Long, position: Double): String? {
        if (javId <= 0L) return "作品 id 无效"
        val normalized = CoverCropRules.normalize(position)
        return try {
            val updated = container.videoRepository.updateJavItem(
                javId,
                JavItemUpdateRequest(coverCropPosition = normalized),
            )
            val applied = CoverCropRules.normalize(updated.coverCropPosition ?: normalized)
            uiState = uiState.copy(
                items = uiState.items.map {
                    if (it.id == javId) it.copy(coverCropPosition = applied) else it
                },
            )
            null
        } catch (e: AuthExpiredException) {
            uiState = uiState.copy(sessionExpired = true)
            "登录已过期"
        } catch (e: Exception) {
            e.message?.takeIf { it.isNotBlank() } ?: "保存失败"
        }
    }

    fun coverUrl(code: String): String = container.apiClient.javCoverUrl(code)

    companion object {
        const val PAGE_SIZE = 24
    }
}
