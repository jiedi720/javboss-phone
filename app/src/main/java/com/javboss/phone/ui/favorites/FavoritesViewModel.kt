package com.javboss.phone.ui.favorites

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.FavoriteRepository
import com.javboss.phone.data.TagColorRules
import com.javboss.phone.data.api.AuthExpiredException
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import com.javboss.phone.data.api.dto.FavoriteItemDto
import com.javboss.phone.data.api.dto.IdolDto
import com.javboss.phone.data.api.dto.TagDto
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val groups: List<FavoriteGroupDto> = emptyList(),
    /** 当前展示的收藏夹实体类型：jav（作品）/ idol（女优） */
    val kind: String = FavoriteRepository.KIND_JAV,
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
                val groups = container.favoriteRepository.listGroups(uiState.kind)
                uiState = uiState.copy(groups = groups, loading = false)
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(loading = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    /** 切换「作品 / 女优」收藏夹：清空旧列表后按新类型重新拉取 */
    fun setKind(kind: String) {
        if (kind == uiState.kind) return
        uiState = uiState.copy(kind = kind, groups = emptyList(), error = null)
        refresh()
    }

    fun createGroup(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                container.favoriteRepository.createGroup(name.trim(), uiState.kind)
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
                container.favoriteRepository.renameGroup(id, name.trim(), uiState.kind)
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
                container.favoriteRepository.deleteGroup(id, uiState.kind)
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

/**
 * 收藏夹条目对应的 JAV：星级、观看时长，以及与 JAV 界面一致的排序维度所需字段
 * （收藏夹接口本身只给番号/标题，这些都要从 /jav?favorite_group_id= 的完整 JavDto 补回来）。
 * javId 同时作为「加入时间」排序的代理：自增主键顺序 ≈ 服务端 recent（加入库时间）顺序。
 */
data class FavRating(
    val javId: Long,
    val rating: Double,
    /** 服务端统计的该作品累计观看时长（jav.watched_ms） */
    val watchedMs: Long = 0,
    /** 时长（分钟），对应 JavDto.duration_min */
    val durationMin: Int = 0,
    /** 发行时间（unix 秒），对应 JavDto.release_unix */
    val releaseUnix: Long = 0,
    /** 该作品的自定义标签（provider=3），保留 TagDto 以便点击角标按 tag.id 筛选作品 */
    val customTags: List<TagDto> = emptyList(),
)

/**
 * 收藏夹**内部条目**（封面网格）的排序维度。
 * 与 JAV 界面（JavSortBase）对齐：加入时间 / 番号 / 时长 / 发行时间 / 观看时长 / 喜爱度
 * 六项的文案与默认方向全部照搬；另外保留收藏夹自己的「默认顺序」（服务端返回顺序）和「标题」。
 * 时长 / 发行 / 观看 / 喜爱度都从 /jav?favorite_group_id= 拉回的完整 JavDto 补回来（见 FavRating）。
 */
enum class FavItemSortBase(
    val label: String,
    val ascLabel: String,
    val descLabel: String,
    val defaultAsc: Boolean,
) {
    DEFAULT("默认顺序", "原序", "倒序", true),
    RECENT("加入时间", "远→近", "近→远", false),
    CODE("番号", "A→Z", "Z→A", true),
    TITLE("标题", "A→Z", "Z→A", true),
    DURATION("时长", "短→长", "长→短", false),
    RELEASE("发行时间", "远→近", "近→远", false),
    WATCHED("观看时长", "短→长", "长→短", false),
    RATING("喜爱度", "低→高", "高→低", false),
}

data class FavGroupUiState(
    val items: List<FavoriteItemDto> = emptyList(),
    val loading: Boolean = false,
    /** 番号（大写）→ 该作品的 jav id 与喜爱度，来自 /jav?favorite_group_id= */
    val ratings: Map<String, FavRating> = emptyMap(),
    /** 女优收藏夹：id → 完整 IdolDto，来自 /jav/idols?favorite_group_id=，补信息栏与排序元数据 */
    val idolMeta: Map<Long, IdolDto> = emptyMap(),
    /** 当前收藏夹实体类型：决定用作品还是女优的排序维度 */
    val kind: String = FavoriteRepository.KIND_JAV,
    /** 搜索关键词，按番号 / 标题过滤 */
    val search: String = "",
    val sort: FavItemSortBase = FavItemSortBase.DEFAULT,
    val sortAsc: Boolean = true,
    /** 女优收藏夹内页排序（v0.3.61，与作品收藏夹分开） */
    val idolSort: FavIdolSortBase = FavIdolSortBase.DEFAULT,
    val idolSortAsc: Boolean = true,
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
            // 女优收藏夹改用女优维度排序（作品数量 / 年龄 / 身高三围 / 罩杯…），
            // 不再错用「番号 / 时长 / 发行时间」这些作品维度
            if (kind == FavoriteRepository.KIND_IDOL) {
                return FavIdolSortRules.sort(filtered, idolMeta, idolSort, idolSortAsc)
            }
            val metaOf = { item: FavoriteItemDto -> ratings[item.code.uppercase()] }
            val sorted = when (sort) {
                FavItemSortBase.DEFAULT -> filtered
                // 加入时间：JavDto.id 是自增主键，顺序与服务端 recent（加入库时间）一致
                FavItemSortBase.RECENT -> filtered.sortedBy { metaOf(it)?.javId ?: 0L }
                FavItemSortBase.CODE -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.code })
                FavItemSortBase.TITLE -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                FavItemSortBase.DURATION -> filtered.sortedBy { metaOf(it)?.durationMin ?: 0 }
                FavItemSortBase.RELEASE -> filtered.sortedBy { metaOf(it)?.releaseUnix ?: 0L }
                FavItemSortBase.WATCHED -> filtered.sortedBy { metaOf(it)?.watchedMs ?: 0L }
                // 没评过分的按 0 算，排在一端
                FavItemSortBase.RATING -> filtered.sortedBy { metaOf(it)?.rating ?: 0.0 }
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
            sort = container.settings.favItemSortName
                ?.let { runCatching { FavItemSortBase.valueOf(it) }.getOrNull() }
                ?: FavItemSortBase.DEFAULT,
            sortAsc = container.settings.favItemSortAsc,
            idolSort = container.settings.favIdolSortName
                ?.let { runCatching { FavIdolSortBase.valueOf(it) }.getOrNull() }
                ?: FavIdolSortBase.DEFAULT,
            idolSortAsc = container.settings.favIdolSortAsc,
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

    /** 女优收藏夹内页排序：换维度用默认方向，点同一个维度切升降序（与主界面女优页一致） */
    fun onIdolSortClick(base: FavIdolSortBase) {
        uiState = if (uiState.idolSort == base) {
            uiState.copy(idolSortAsc = !uiState.idolSortAsc)
        } else {
            uiState.copy(idolSort = base, idolSortAsc = base.defaultAsc)
        }
        val s = uiState
        viewModelScope.launch { container.settings.saveFavIdolSort(s.idolSort.name, s.idolSortAsc) }
    }

    fun load(groupId: Long, kind: String = FavoriteRepository.KIND_JAV) {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null, kind = kind)
            try {
                val items = container.favoriteRepository.listItems(groupId, kind)
                // 作品收藏夹条目只有番号，星级 / 时长 / 发行时间 / 自定义标签得另外拿：
                // 用 favorite_group_id 过滤拉一份完整 JavDto 按番号建索引。
                // 女优收藏夹条目缺年龄 / 身高 / 三围 / 罩杯，同样拉一份完整 IdolDto 建索引，
                // 供信息栏两行数据与女优维度排序使用。
                val idolMeta: Map<Long, IdolDto> = if (kind == FavoriteRepository.KIND_IDOL) {
                    runCatching {
                        container.videoRepository
                            .listJavIdols(limit = 500, offset = 0, favoriteGroupId = groupId)
                            .items
                            .associateBy { it.id }
                    }.getOrDefault(emptyMap())
                } else {
                    emptyMap()
                }
                val ratings: Map<String, FavRating> = if (kind == FavoriteRepository.KIND_IDOL) {
                    emptyMap()
                } else {
                    runCatching {
                        container.videoRepository
                            .searchJavs(limit = 500, offset = 0, favoriteGroupId = groupId)
                            .items
                            .associate {
                                it.code.uppercase() to FavRating(
                                    javId = it.id,
                                    rating = it.favoriteRating,
                                    watchedMs = it.watchedMs,
                                    durationMin = it.durationMin,
                                    releaseUnix = it.releaseUnix,
                                    customTags = TagColorRules.customTags(it.tags),
                                )
                            }
                    }.getOrDefault(emptyMap())
                }
                uiState = uiState.copy(
                    items = items,
                    ratings = ratings,
                    idolMeta = idolMeta,
                    loading = false,
                )
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

    /** 封面左下角番号正上方要堆叠显示的自定义标签（作品收藏夹条目） */
    fun customTagsOf(code: String): List<TagDto> =
        uiState.ratings[code.uppercase()]?.customTags.orEmpty()

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

    fun removeItem(groupId: Long, item: FavoriteItemDto, kind: String = FavoriteRepository.KIND_JAV) {
        viewModelScope.launch {
            try {
                container.favoriteRepository.removeItems(groupId, listOf(item.id), kind)
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
