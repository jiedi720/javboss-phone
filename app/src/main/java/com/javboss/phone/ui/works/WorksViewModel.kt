package com.javboss.phone.ui.works

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.api.AuthExpiredException
import com.javboss.phone.data.api.dto.IdolDto
import com.javboss.phone.data.api.dto.JavDto
import com.javboss.phone.data.api.dto.JavItemUpdateRequest
import com.javboss.phone.data.api.dto.PrefixDto
import com.javboss.phone.data.api.dto.SeriesDto
import com.javboss.phone.data.api.dto.StudioDto
import com.javboss.phone.data.api.dto.TagDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 与网页版/TV 版一致的 JAV 排序维度（见 web/src/constants/jav.js 的 `je` 数组）。
 * 六个维度与 Web 端一一对应，升/降序取值也照搬（含 code_desc、height_desc 这种反向命名）。
 */
enum class JavSortBase(
    val label: String,
    val ascValue: String,
    val descValue: String,
    val ascLabel: String,
    val descLabel: String,
    val defaultAsc: Boolean,
) {
    RECENT("加入时间", "recent_asc", "recent", "远→近", "近→远", false),
    CODE("番号", "code", "code_desc", "A→Z", "Z→A", true),
    DURATION("时长", "duration_asc", "duration", "短→长", "长→短", false),
    RELEASE("发行时间", "release_asc", "release", "远→近", "近→远", false),
    // 服务端的排序键是 watched（jav.watched_ms），不是旧的 play_count
    WATCHED("观看时长", "watched_asc", "watched", "短→长", "长→短", false),
    FAVORITE_RATING("喜爱度", "favorite_rating_asc", "favorite_rating", "低→高", "高→低", false),
}

/**
 * 女优排序维度，与 Web 端 `Je`（女优页排序数组）完全一致。
 * 注意「年龄」这一项是反的：升序（小→大）用 `birth`，降序用 `birth_asc`，
 * 因为服务端按生日排序，生日越晚年龄越小。
 */
enum class IdolSortBase(
    val label: String,
    val ascValue: String,
    val descValue: String,
    val ascLabel: String,
    val descLabel: String,
    val defaultAsc: Boolean,
) {
    WORK("作品数量", "work_asc", "work", "少→多", "多→少", false),
    RECENT("加入时间", "recent_asc", "recent", "远→近", "近→远", false),
    AGE("年龄", "birth", "birth_asc", "小→大", "大→小", false),
    HEIGHT("身高", "height", "height_desc", "低→高", "高→低", false),
    BUST("胸围", "bust_asc", "bust", "小→大", "大→小", false),
    HIPS("臀围", "hips_asc", "hips", "小→大", "大→小", false),
    WAIST("腰围", "waist", "waist_desc", "小→大", "大→小", false),
    CUP("罩杯", "cup_asc", "cup", "小→大", "大→小", false),
}

/** 作品库的四个卡片墙视角（对齐 TV 版作品库子页签） */
enum class LibraryTab(val label: String) {
    WORKS("JAV"),
    IDOLS("女优"),
    STUDIOS("片商"),
    SERIES("系列"),
}

/** 女优/片商/系列三个列表共用的分页状态 */
data class EntityPage<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** 是否加载过：切到该视角时才触发首次加载 */
    val loaded: Boolean = false,
)

data class WorksUiState(
    val tab: LibraryTab = LibraryTab.WORKS,
    val search: String = "",
    // 作品
    val items: List<JavDto> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val sort: JavSortBase = JavSortBase.RECENT,
    val sortAsc: Boolean = false,
    val prefix: String = "",
    val tagIds: Set<Long> = emptySet(),
    val solo: Boolean = false,
    val random: Boolean = false,
    /** 随机模式下跨页去重依赖同一个 seed */
    val seed: Long? = null,
    // 筛选面板的选项数据（打开面板时才加载）
    val allTags: List<TagDto> = emptyList(),
    val allPrefixes: List<PrefixDto> = emptyList(),
    // 女优/片商/系列
    val idols: EntityPage<IdolDto> = EntityPage(),
    /** 女优排序维度（默认作品数量，多→少，与 Web 端一致） */
    val idolsSort: IdolSortBase = IdolSortBase.WORK,
    val idolsSortAsc: Boolean = false,
    val studios: EntityPage<StudioDto> = EntityPage(),
    val series: EntityPage<SeriesDto> = EntityPage(),
    /** 卡片墙每行数量（2/3/4），持久化在 SettingsManager */
    val columns: Int = 2,
    val error: String? = null,
    val sessionExpired: Boolean = false,
)

class WorksViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JavBossApp).container

    var uiState by mutableStateOf(
        WorksUiState(
            columns = container.settings.gridColumns,
            // 排序沿用上次的选择（本地持久化），不再每次打开都重置成默认
            sort = container.settings.javSortName
                ?.let { runCatching { JavSortBase.valueOf(it) }.getOrNull() }
                ?: JavSortBase.RECENT,
            sortAsc = container.settings.javSortAsc,
            idolsSort = container.settings.idolSortName
                ?.let { runCatching { IdolSortBase.valueOf(it) }.getOrNull() }
                ?: IdolSortBase.WORK,
            idolsSortAsc = container.settings.idolSortAsc,
        ),
    )
        private set

    private var searchJob: Job? = null
    private var filterOptionsLoaded = false

    init {
        refreshWorks()
        // 每行卡片数在设置页里改，这里跟着流转，改完回作品页立即生效
        viewModelScope.launch {
            container.settings.gridColumnsFlow.collect { columns ->
                if (columns != uiState.columns) uiState = uiState.copy(columns = columns)
            }
        }
    }

    // ---------- 作品 ----------

    private suspend fun fetchWorks(limit: Int, offset: Int) = uiState.let { s ->
        container.videoRepository.searchJavs(
            limit = limit,
            offset = offset,
            search = s.search,
            tagIds = s.tagIds.toList(),
            prefix = s.prefix,
            solo = s.solo,
            sort = if (s.random) "" else if (s.sortAsc) s.sort.ascValue else s.sort.descValue,
            seed = if (s.random) s.seed ?: Random.nextLong() else null,
        )
    }

    fun refreshWorks() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                val res = fetchWorks(PAGE_SIZE, 0)
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

    private fun loadMoreWorks() {
        val s = uiState
        if (s.loading || s.loadingMore || s.endReached) return
        viewModelScope.launch {
            uiState = uiState.copy(loadingMore = true)
            try {
                val res = fetchWorks(PAGE_SIZE, s.items.size)
                // 服务端排序不稳定时跨页可能返回重复条目（番号前缀有码/无码变体），按 id 去重
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

    /** 点击排序 chip：未选中则选中（用该维度默认方向），已选中则切换升/降序 */
    fun onSortClick(base: JavSortBase) {
        uiState = if (uiState.sort == base && !uiState.random) {
            uiState.copy(sortAsc = !uiState.sortAsc)
        } else {
            uiState.copy(sort = base, sortAsc = base.defaultAsc, random = false, seed = null)
        }
        val s = uiState
        viewModelScope.launch { container.settings.saveJavSort(s.sort.name, s.sortAsc) }
        refreshWorks()
    }

    fun onPrefixChange(prefix: String) {
        uiState = uiState.copy(prefix = prefix)
        refreshWorks()
    }

    fun onTagToggle(tagId: Long) {
        val next = uiState.tagIds.toMutableSet().apply {
            if (!add(tagId)) remove(tagId)
        }
        uiState = uiState.copy(tagIds = next)
        refreshWorks()
    }

    fun onSoloChange(solo: Boolean) {
        uiState = uiState.copy(solo = solo)
        refreshWorks()
    }

    fun onRandomChange(random: Boolean) {
        uiState = uiState.copy(random = random, seed = if (random) Random.nextLong() else null)
        refreshWorks()
    }

    fun clearFilters() {
        uiState = uiState.copy(
            search = "", sort = JavSortBase.RECENT, sortAsc = false, prefix = "",
            tagIds = emptySet(), solo = false, random = false, seed = null,
        )
        viewModelScope.launch { container.settings.saveJavSort(JavSortBase.RECENT.name, false) }
        refreshWorks()
    }

    /** 筛选面板打开时加载标签和番号前缀（只加载一次） */
    fun loadFilterOptions() {
        if (filterOptionsLoaded) return
        filterOptionsLoaded = true
        viewModelScope.launch {
            val tags = runCatching { container.videoRepository.listJavTags() }.getOrDefault(emptyList())
            val prefixes = runCatching { container.videoRepository.listJavPrefixes() }.getOrDefault(emptyList())
            uiState = uiState.copy(allTags = tags, allPrefixes = prefixes)
        }
    }

    // ---------- 视角切换 / 搜索 ----------

    fun onTabChange(tab: LibraryTab) {
        if (tab == uiState.tab) return
        uiState = uiState.copy(tab = tab)
        // 首次切到某视角时加载；之后沿用已加载数据，搜索变化才刷新
        when (tab) {
            LibraryTab.WORKS -> Unit
            LibraryTab.IDOLS -> if (!uiState.idols.loaded) refreshIdols()
            LibraryTab.STUDIOS -> if (!uiState.studios.loaded) refreshStudios()
            LibraryTab.SERIES -> if (!uiState.series.loaded) refreshSeries()
        }
    }

    /** 搜索防抖：停止输入 400ms 后按当前视角刷新 */
    fun onSearchChange(value: String) {
        uiState = uiState.copy(search = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            refreshCurrent()
        }
    }

    fun refreshCurrent() {
        when (uiState.tab) {
            LibraryTab.WORKS -> refreshWorks()
            LibraryTab.IDOLS -> refreshIdols()
            LibraryTab.STUDIOS -> refreshStudios()
            LibraryTab.SERIES -> refreshSeries()
        }
    }

    fun loadMore() {
        when (uiState.tab) {
            LibraryTab.WORKS -> loadMoreWorks()
            LibraryTab.IDOLS -> loadMoreIdols()
            LibraryTab.STUDIOS -> loadMoreStudios()
            LibraryTab.SERIES -> loadMoreSeries()
        }
    }

    // ---------- 女优 / 片商 / 系列（同一套分页逻辑） ----------

    private suspend fun fetchIdols(limit: Int, offset: Int) = uiState.let { s ->
        container.videoRepository.listJavIdols(
            limit = limit,
            offset = offset,
            search = s.search,
            sort = if (s.idolsSortAsc) s.idolsSort.ascValue else s.idolsSort.descValue,
        )
    }

    /** 女优点排序：换维度用默认方向，点同一个维度切升降序（与作品一致） */
    fun onIdolSortClick(base: IdolSortBase) {
        uiState = if (uiState.idolsSort == base) {
            uiState.copy(idolsSortAsc = !uiState.idolsSortAsc)
        } else {
            uiState.copy(idolsSort = base, idolsSortAsc = base.defaultAsc)
        }
        val s = uiState
        viewModelScope.launch { container.settings.saveIdolSort(s.idolsSort.name, s.idolsSortAsc) }
        refreshIdols()
    }

    fun refreshIdols() {
        viewModelScope.launch {
            uiState = uiState.copy(idols = uiState.idols.copy(loading = true))
            try {
                val res = fetchIdols(PAGE_SIZE, 0)
                uiState = uiState.copy(
                    idols = EntityPage(
                        items = res.items, total = res.total, loaded = true,
                        endReached = res.items.size >= res.total,
                    ),
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(idols = uiState.idols.copy(loading = false), sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    idols = uiState.idols.copy(loading = false),
                    error = e.message ?: "加载失败",
                )
            }
        }
    }

    private fun loadMoreIdols() {
        val p = uiState.idols
        if (p.loading || p.loadingMore || p.endReached) return
        viewModelScope.launch {
            uiState = uiState.copy(idols = p.copy(loadingMore = true))
            try {
                val res = fetchIdols(PAGE_SIZE, p.items.size)
                val merged = (p.items + res.items).distinctBy { it.id }
                uiState = uiState.copy(
                    idols = p.copy(
                        items = merged, total = res.total, loadingMore = false, loaded = true,
                        endReached = merged.size >= res.total || res.items.isEmpty(),
                    ),
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(idols = uiState.idols.copy(loadingMore = false), sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(idols = uiState.idols.copy(loadingMore = false))
            }
        }
    }

    private suspend fun fetchStudios(limit: Int, offset: Int) =
        container.videoRepository.listJavStudios(limit = limit, offset = offset, search = uiState.search)

    fun refreshStudios() {
        viewModelScope.launch {
            uiState = uiState.copy(studios = uiState.studios.copy(loading = true))
            try {
                val res = fetchStudios(PAGE_SIZE, 0)
                uiState = uiState.copy(
                    studios = EntityPage(
                        items = res.items, total = res.total, loaded = true,
                        endReached = res.items.size >= res.total,
                    ),
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(studios = uiState.studios.copy(loading = false), sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    studios = uiState.studios.copy(loading = false),
                    error = e.message ?: "加载失败",
                )
            }
        }
    }

    private fun loadMoreStudios() {
        val p = uiState.studios
        if (p.loading || p.loadingMore || p.endReached) return
        viewModelScope.launch {
            uiState = uiState.copy(studios = p.copy(loadingMore = true))
            try {
                val res = fetchStudios(PAGE_SIZE, p.items.size)
                val merged = (p.items + res.items).distinctBy { it.id }
                uiState = uiState.copy(
                    studios = p.copy(
                        items = merged, total = res.total, loadingMore = false, loaded = true,
                        endReached = merged.size >= res.total || res.items.isEmpty(),
                    ),
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(studios = uiState.studios.copy(loadingMore = false), sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(studios = uiState.studios.copy(loadingMore = false))
            }
        }
    }

    private suspend fun fetchSeries(limit: Int, offset: Int) =
        container.videoRepository.listJavSeries(limit = limit, offset = offset, search = uiState.search)

    fun refreshSeries() {
        viewModelScope.launch {
            uiState = uiState.copy(series = uiState.series.copy(loading = true))
            try {
                val res = fetchSeries(PAGE_SIZE, 0)
                uiState = uiState.copy(
                    series = EntityPage(
                        items = res.items, total = res.total, loaded = true,
                        endReached = res.items.size >= res.total,
                    ),
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(series = uiState.series.copy(loading = false), sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(
                    series = uiState.series.copy(loading = false),
                    error = e.message ?: "加载失败",
                )
            }
        }
    }

    private fun loadMoreSeries() {
        val p = uiState.series
        if (p.loading || p.loadingMore || p.endReached) return
        viewModelScope.launch {
            uiState = uiState.copy(series = p.copy(loadingMore = true))
            try {
                val res = fetchSeries(PAGE_SIZE, p.items.size)
                val merged = (p.items + res.items).distinctBy { it.id }
                uiState = uiState.copy(
                    series = p.copy(
                        items = merged, total = res.total, loadingMore = false, loaded = true,
                        endReached = merged.size >= res.total || res.items.isEmpty(),
                    ),
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(series = uiState.series.copy(loadingMore = false), sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(series = uiState.series.copy(loadingMore = false))
            }
        }
    }

    // ---------- 喜爱度评分 ----------

    /**
     * 改喜爱度：0..5、步长 0.5，走 PUT /jav/items/{id}（与 Web 端封面左上角那排星同一个接口）。
     * 成功后只把返回的评分写回本地列表，避免整条对象被服务端裁剪后的版本覆盖掉。
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
                val applied = updated.favoriteRating
                uiState = uiState.copy(
                    items = uiState.items.map {
                        if (it.id == javId) it.copy(favoriteRating = applied) else it
                    },
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (_: Exception) {
                // 失败就保持原样，下一次刷新会从服务端取到真实值
            }
        }
    }

    // ---------- 图片 ----------

    fun coverUrl(code: String): String = container.apiClient.javCoverUrl(code)

    /** 女优封面：cover_code 非空时拼封面地址（同 TV 版逻辑） */
    fun idolCoverUrl(idol: IdolDto): String? =
        idol.coverCode.takeIf { it.isNotBlank() }?.let { coverUrl(it) }

    /** 片商/系列卡片封面：用 sample_code 的作品封面，没有则 null 走占位 */
    fun sampleCoverUrl(sampleCode: String): String? =
        sampleCode.takeIf { it.isNotBlank() }?.let { coverUrl(it) }

    companion object {
        const val PAGE_SIZE = 24
    }
}
