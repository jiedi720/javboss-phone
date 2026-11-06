package com.javboss.phone.ui.videos

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.VideoTagEvents
import com.javboss.phone.data.api.AuthExpiredException
import com.javboss.phone.data.api.dto.VideoDto
import com.javboss.phone.data.api.dto.VideoListResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 与网页版一致的视频排序选项（见 web/src/constants/video.js） */
enum class VideoSort(val label: String, val value: String) {
    RECENT("最近加入", "recent"),
    RECENT_ASC("最早加入", "recent_asc"),
    DURATION("时长 长→短", "duration"),
    DURATION_ASC("时长 短→长", "duration_asc"),
    PLAY_COUNT("播放 多→少", "play_count"),
    PLAY_COUNT_ASC("播放 少→多", "play_count_asc"),
    FILENAME("文件名 A→Z", "filename"),
    FILENAME_DESC("文件名 Z→A", "filename_desc"),
}

data class VideosUiState(
    val videos: List<VideoDto> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val search: String = "",
    val sort: VideoSort = VideoSort.RECENT,
    /** 目录筛选：点卡片第二行文件夹路径时设置，null = 不过滤 */
    val directoryId: Long? = null,
    val subdirectory: String = "",
    val error: String? = null,
    val sessionExpired: Boolean = false,
)

class VideosViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JavBossApp).container

    var uiState by mutableStateOf(VideosUiState())
        private set

    private var searchJob: Job? = null

    /**
     * 「视频」页只列普通视频：服务端 config.video_hide_jav 为 true 时，
     * 已关联 JAV 的视频只在「作品」里出现（Web 端同一个开关、同一个请求参数）。
     *
     * null = 配置还没读到（启动中 / `GET /config` 瞬时失败）。初值必须是 null 而不是
     * false：init 里异步读配置完成前，标签变更信号或下拉刷新都可能并发触发 refresh，
     * 若初值是 false 就会发出不带 hide_jav 的请求，把 JAV 文件带进第一屏。
     */
    private var hideJav: Boolean? = null

    init {
        viewModelScope.launch { refresh() }
        // 详情页保存视频标签后，列表卡片左上角的标签要跟着更新
        viewModelScope.launch {
            VideoTagEvents.changes.collect { refresh() }
        }
    }

    /**
     * 每次拉取都确保配置已确定：未知时重新读一次 /config（成功即写入 repository 缓存，
     * 之后不再打）。在读到明确值之前一律按「隐藏 JAV」请求 —— 宁可暂时少列，也不能因为
     * 一次网络抖动把 JAV 关联视频放进视频 tab；配置恢复后下一次刷新自动纠正，无需重启 App。
     */
    private suspend fun fetch(limit: Int, offset: Int): VideoListResponse {
        // 配置未知时每次拉取都重试一次 /config；成功即写缓存，之后不再打。
        if (hideJav == null) hideJav = container.videoRepository.videosHideJav()
        return container.videoRepository.listVideos(
            limit = limit,
            offset = offset,
            search = uiState.search,
            sort = uiState.sort.value,
            hideJav = hideJav ?: true,
            directoryId = uiState.directoryId,
            subdirectory = uiState.subdirectory.ifBlank { null },
        )
    }

    fun refresh() {
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            try {
                val res = fetch(PAGE_SIZE, 0)
                // 服务端在「按文件名排序」等不稳定的排序键下会 JOIN 出同一 id 的多条记录，
                // 直接塞给 LazyVerticalGrid 的 key = { it.id } 会撞重复 key 抛异常闪退。
                // loadMore() 早就做了同样的去重，这里保持一致。
                val items = res.items.distinctBy { it.id }
                uiState = uiState.copy(
                    videos = items,
                    total = res.total,
                    loading = false,
                    endReached = items.size >= res.total,
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
                val res = fetch(PAGE_SIZE, s.videos.size)
                // 服务端排序键不稳定时跨页可能返回重复 id，合并时按 id 去重
                val merged = (s.videos + res.items).distinctBy { it.id }
                uiState = uiState.copy(
                    videos = merged,
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

    /** 搜索防抖：停止输入 400ms 后触发 */
    fun onSearchChange(value: String) {
        uiState = uiState.copy(search = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(400)
            refresh()
        }
    }

    fun onSortChange(sort: VideoSort) {
        if (sort == uiState.sort) return
        uiState = uiState.copy(sort = sort)
        refresh()
    }

    /**
     * 点卡片第二行的文件夹路径：按 directory_id + subdirectory 筛出该目录下的全部视频。
     * 对齐 Web 端 setVideoDirectoryFilter —— 进入目录时清空搜索词。
     */
    fun filterByDirectory(directoryId: Long, subdirectory: String) {
        if (directoryId <= 0) return
        uiState = uiState.copy(
            directoryId = directoryId,
            subdirectory = subdirectory,
            search = "",
        )
        refresh()
    }

    /** 清除目录筛选，回到全部视频 */
    fun clearDirectoryFilter() {
        if (uiState.directoryId == null) return
        uiState = uiState.copy(directoryId = null, subdirectory = "")
        refresh()
    }

    fun thumbnailUrl(videoId: Long): String =
        container.apiClient.videoThumbnailUrl(videoId)

    companion object {
        const val PAGE_SIZE = 24
    }
}
