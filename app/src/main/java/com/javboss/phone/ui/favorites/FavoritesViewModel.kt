package com.javboss.phone.ui.favorites

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.api.AuthExpiredException
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import com.javboss.phone.data.api.dto.FavoriteItemDto
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val groups: List<FavoriteGroupDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** 操作失败（创建/重命名/删除）的提示信息，UI 用 Toast 展示 */
    val notice: String? = null,
    val sessionExpired: Boolean = false,
)

class FavoritesViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JavBossApp).container

    var uiState by mutableStateOf(FavoritesUiState())
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                val groups = container.favoriteRepository.listGroups()
                uiState = uiState.copy(groups = groups, loading = false)
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(loading = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    fun createGroup(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                container.favoriteRepository.createGroup(name.trim())
                refresh()
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "创建失败：名称可能为空或已存在")
            }
        }
    }

    fun renameGroup(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                container.favoriteRepository.renameGroup(id, name.trim())
                refresh()
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "重命名失败：名称可能为空或已存在")
            }
        }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch {
            try {
                container.favoriteRepository.deleteGroup(id)
                refresh()
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "删除失败")
            }
        }
    }

    fun consumeNotice() {
        uiState = uiState.copy(notice = null)
    }
}

// ---------- 收藏夹详情 ----------

/** 收藏夹条目对应的 JAV：用于显示星级（收藏夹接口本身不返回 favorite_rating） */
data class FavRating(
    val javId: Long,
    val rating: Double,
    /** 服务端统计的该作品累计观看时长（jav.watched_ms） */
    val watchedMs: Long = 0,
)

/**
 * 收藏夹**内部条目**（封面网格）的排序维度。
 * 每条只有番号 / 标题 / name 和喜爱度（喜爱度靠 /jav?favorite_group_id= 补回来），
 * 没有加入时间，所以维度就这四样。
 */
enum class FavItemSortBase(
    val label: String,
    val ascLabel: String,
    val descLabel: String,
    val defaultAsc: Boolean,
) {
    DEFAULT("默认顺序", "原序", "倒序", true),
    CODE("番号", "A→Z", "Z→A", true),
    TITLE("标题", "A→Z", "Z→A", true),
    RATING("喜爱度", "低→高", "高→低", false),
}

data class FavGroupUiState(
    val items: List<FavoriteItemDto> = emptyList(),
    val loading: Boolean = false,
    /** 卡片墙每行数量，跟随「作品」页的设置 */
    val columns: Int = 2,
    /** 番号（大写）→ 该作品的 jav id 与喜爱度，来自 /jav?favorite_group_id= */
    val ratings: Map<String, FavRating> = emptyMap(),
    /** 搜索关键词，按番号 / 标题过滤 */
    val search: String = "",
    val sort: FavItemSortBase = FavItemSortBase.DEFAULT,
    val sortAsc: Boolean = true,
    val error: String? = null,
    val notice: String? = null,
    val sessionExpired: Boolean = false,
) {
    /** 网格实际渲染的列表 = 搜索过滤 + 排序后的结果 */
    val visibleItems: List<FavoriteItemDto>
        get() {
            val keyword = search.trim()
            val filtered = if (keyword.isEmpty()) {
                items
            } else {
                items.filter {
                    it.code.contains(keyword, ignoreCase = true) ||
                        it.title.contains(keyword, ignoreCase = true) ||
                        it.name.contains(keyword, ignoreCase = true)
                }
            }
            val sorted = when (sort) {
                FavItemSortBase.DEFAULT -> filtered
                FavItemSortBase.CODE -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.code })
                FavItemSortBase.TITLE -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                // 没评过分的按 0 算，排在一端
                FavItemSortBase.RATING -> filtered.sortedBy { ratings[it.code.uppercase()]?.rating ?: 0.0 }
            }
            return if (sortAsc) sorted else sorted.reversed()
        }

    /** 有关键词但一条都没匹配上 */
    val noMatch: Boolean get() = items.isNotEmpty() && visibleItems.isEmpty()
}

class FavGroupViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JavBossApp).container

    var uiState by mutableStateOf(
        FavGroupUiState(
            columns = container.settings.gridColumns,
            sort = container.settings.favItemSortName
                ?.let { runCatching { FavItemSortBase.valueOf(it) }.getOrNull() }
                ?: FavItemSortBase.DEFAULT,
            sortAsc = container.settings.favItemSortAsc,
        ),
    )
        private set

    fun onSearchChange(value: String) {
        uiState = uiState.copy(search = value)
    }

    /** 点未选中的维度 → 切维度用默认方向；点已选中的 → 切升/降序（与作品页一致） */
    fun onSortClick(base: FavItemSortBase) {
        uiState = if (uiState.sort == base) {
            uiState.copy(sortAsc = !uiState.sortAsc)
        } else {
            uiState.copy(sort = base, sortAsc = base.defaultAsc)
        }
        val s = uiState
        viewModelScope.launch { container.settings.saveFavItemSort(s.sort.name, s.sortAsc) }
    }

    fun load(groupId: Long) {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                val items = container.favoriteRepository.listItems(groupId)
                // 收藏夹条目只有番号，星级得另外拿：直接用服务端的 favorite_group_id 过滤条件
                // 拉一份完整 JavDto，按番号建索引（一个请求，不用逐条查）
                val ratings = runCatching {
                    container.videoRepository
                        .searchJavs(limit = 500, offset = 0, favoriteGroupId = groupId)
                        .items
                        .associate {
                            it.code.uppercase() to FavRating(it.id, it.favoriteRating, it.watchedMs)
                        }
                }.getOrDefault(emptyMap())
                uiState = uiState.copy(items = items, ratings = ratings, loading = false)
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(loading = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    fun ratingOf(code: String): Double = uiState.ratings[code.uppercase()]?.rating ?: 0.0

    /** 服务端统计的累计观看时长，给封面右上角的「已观看」图标用 */
    fun watchedMsOf(code: String): Long = uiState.ratings[code.uppercase()]?.watchedMs ?: 0L

    /** 改喜爱度：写回服务端后同步本地映射（收藏夹卡片左上角那颗星） */
    fun updateRating(code: String, rating: Double) {
        val entry = uiState.ratings[code.uppercase()] ?: return
        val normalized = (Math.round(rating * 2) / 2.0).coerceIn(0.0, 5.0)
        viewModelScope.launch {
            try {
                val updated = container.videoRepository.updateJavItem(
                    entry.javId,
                    com.javboss.phone.data.api.dto.JavItemUpdateRequest(favoriteRating = normalized),
                )
                val applied = updated.favoriteRating
                uiState = uiState.copy(
                    ratings = uiState.ratings + (code.uppercase() to entry.copy(rating = applied)),
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "评分保存失败")
            }
        }
    }

    fun removeItem(groupId: Long, item: FavoriteItemDto) {
        viewModelScope.launch {
            try {
                container.favoriteRepository.removeItems(groupId, listOf(item.id))
                uiState = uiState.copy(items = uiState.items.filterNot { it.id == item.id })
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "移除失败")
            }
        }
    }

    /**
     * 收藏夹条目只有番号没有视频 id，点击时用番号搜 /jav 找关联视频；
     * 找不到视频时 videoId=0，详情页降级为纯元数据展示
     */
    fun openItem(item: FavoriteItemDto, onResolved: (videoId: Long, code: String) -> Unit) {
        viewModelScope.launch {
            try {
                val jav = container.videoRepository.searchJavs(limit = 5, offset = 0, search = item.code)
                    .items.firstOrNull { it.code.equals(item.code, ignoreCase = true) }
                onResolved(jav?.videos?.firstOrNull()?.id ?: 0, item.code)
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                onResolved(0, item.code)
            }
        }
    }

    /**
     * 封面播放钮：收藏夹条目只有番号，先用番号找关联视频再起播；
     * 找不到视频时给个提示，不起播
     */
    fun playItem(item: FavoriteItemDto, onResolved: (videoId: Long) -> Unit) {
        viewModelScope.launch {
            try {
                val jav = container.videoRepository.searchJavs(limit = 5, offset = 0, search = item.code)
                    .items.firstOrNull { it.code.equals(item.code, ignoreCase = true) }
                val videoId = jav?.videos?.firstOrNull()?.id ?: 0L
                if (videoId > 0) {
                    onResolved(videoId)
                } else {
                    uiState = uiState.copy(notice = "「${item.code}」没有找到可播放的视频文件")
                }
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "播放失败：${e.message ?: "网络异常"}")
            }
        }
    }

    fun coverUrl(code: String): String? =
        code.takeIf { it.isNotBlank() }?.let { container.apiClient.javCoverUrl(it) }

    fun consumeNotice() {
        uiState = uiState.copy(notice = null)
    }
}
