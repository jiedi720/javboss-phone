package com.javboss.phone.data

import com.javboss.phone.data.api.ApiClient
import com.javboss.phone.data.api.dto.IdolListResponse
import com.javboss.phone.data.api.dto.JavDto
import com.javboss.phone.data.api.dto.JavItemUpdateRequest
import com.javboss.phone.data.api.dto.JavListResponse
import com.javboss.phone.data.api.dto.JavTagsReplaceRequest
import com.javboss.phone.data.api.dto.PrefixDto
import com.javboss.phone.data.api.dto.PlaybackSessionReportRequest
import com.javboss.phone.data.api.dto.PlaybackSessionRequest
import com.javboss.phone.data.api.dto.SeriesDto
import com.javboss.phone.data.api.dto.SeriesListResponse
import com.javboss.phone.data.api.dto.StreamsResponse
import com.javboss.phone.data.api.dto.StudioListResponse
import com.javboss.phone.data.api.dto.TagDto
import com.javboss.phone.data.api.dto.TagNameRequest
import com.javboss.phone.data.api.dto.VideoDto
import com.javboss.phone.data.api.dto.VideoListResponse
import kotlinx.serialization.json.jsonArray
import retrofit2.HttpException

/** 可直接播放的源地址 + 本次命中的文件位置（观看时长要按位置归账） */
data class PlayableSource(
    val urls: List<String> = emptyList(),
    val locationId: Long = 0,
)

class VideoRepository(private val apiClient: ApiClient) {

    /**
     * 服务端是否开启了「视频页隐藏已关联 JAV 的视频」（config.video_hide_jav）。
     * 用户的服务端默认是 true —— 也就是 Web 的视频页只列普通视频，JAV 只从「作品」进。
     * 值只取一次并缓存，避免每次翻页都打一次 /config。
     */
    private var hideJavCache: Boolean? = null

    suspend fun videosHideJav(): Boolean {
        hideJavCache?.let { return it }
        val value = runCatching {
            val raw = apiClient.api().getConfig()["video_hide_jav"]
            val text = (raw as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@runCatching false
            text.equals("true", ignoreCase = true) || text == "1"
        }.getOrDefault(false)
        hideJavCache = value
        return value
    }

    suspend fun listVideos(
        limit: Int,
        offset: Int,
        search: String = "",
        sort: String = "",
        hideJav: Boolean = false,
    ): VideoListResponse = apiClient.api().listVideos(
        limit = limit,
        offset = offset,
        search = search,
        sort = sort,
        hideJav = if (hideJav) 1 else 0,
    )

    suspend fun getVideo(id: Long): VideoDto =
        apiClient.api().getVideo(id)

    suspend fun getStreams(id: Long): StreamsResponse =
        apiClient.api().getStreams(id)

    /**
     * 播放源地址 + 本次命中的文件位置 id。direct 永远排最前（免转码、原生 Range 拖动），
     * 服务端按浏览器标准未列出 direct 源时（mkv/wmv/h265 等）按固定格式自拼；
     * HLS 排最后兜底（依赖服务端 ffmpeg）。
     */
    suspend fun resolvePlayable(id: Long): PlayableSource {
        val streams = getStreams(id)
        val direct = streams.sources.firstOrNull { it.kind == "direct" }?.src
            ?: "videos/$id/stream"
        val hls = streams.sources.filter { it.kind == "hls" }.map { it.src }
        return PlayableSource(
            urls = (listOf(direct) + hls).map { apiClient.absoluteUrl(it) },
            locationId = streams.locationId,
        )
    }

    suspend fun reportPlay(id: Long) {
        runCatching { apiClient.api().incrementPlayCount(id) }
    }

    // ---------- 播放会话：把手机端的观看时长记进服务端 ----------

    /** 开一个播放会话；失败返回 null（拿不到就只是这次不上报，不影响播放） */
    suspend fun openPlaybackSession(videoId: Long, locationId: Long): String? =
        runCatching {
            apiClient.api()
                .createPlaybackSession(videoId, PlaybackSessionRequest(locationId))
                .sessionId
        }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * 上报「本会话累计观看毫秒数」（单调递增，不是增量）。
     * 返回 false 表示服务端已回收该会话（410），调用方应丢掉 session 重建；
     * 其他错误直接抛出，让调用方保持 session 下个周期重试，避免频繁重建。
     */
    suspend fun reportPlaybackSession(videoId: Long, sessionId: String, watchedMs: Long): Boolean =
        try {
            apiClient.api().reportPlaybackSession(
                videoId,
                sessionId,
                PlaybackSessionReportRequest(watchedMs),
            )
            true
        } catch (e: HttpException) {
            if (e.code() == 410) false else throw e
        }

    suspend fun searchJavs(
        limit: Int,
        offset: Int,
        search: String = "",
        idolIds: List<Long> = emptyList(),
        tagIds: List<Long> = emptyList(),
        studioId: Long? = null,
        seriesId: Long? = null,
        prefix: String = "",
        solo: Boolean = false,
        sort: String = "",
        seed: Long? = null,
        favoriteGroupId: Long? = null,
    ): JavListResponse =
        apiClient.api().searchJav(
            limit = limit,
            offset = offset,
            search = search,
            idolIds = idolIds.joinToString(","),
            tagIds = tagIds.joinToString(","),
            studioId = studioId,
            seriesId = seriesId,
            prefix = prefix,
            solo = if (solo) 1 else 0,
            sort = sort,
            seed = seed,
            favoriteGroupId = favoriteGroupId,
        )

    suspend fun listJavPrefixes(): List<PrefixDto> =
        apiClient.api().listJavPrefixes()

    suspend fun listJavTags(): List<TagDto> =
        apiClient.api().listJavTags()

    suspend fun listJavIdols(limit: Int, offset: Int, search: String = "", sort: String = ""): IdolListResponse =
        apiClient.api().listJavIdols(limit = limit, offset = offset, search = search, sort = sort)

    suspend fun listJavStudios(limit: Int, offset: Int, search: String = ""): StudioListResponse =
        apiClient.api().listJavStudios(limit = limit, offset = offset, search = search)

    suspend fun listJavSeries(limit: Int, offset: Int, search: String = ""): SeriesListResponse =
        apiClient.api().listJavSeries(limit = limit, offset = offset, search = search)

    suspend fun getJavSeries(id: Long): SeriesDto? =
        runCatching { apiClient.api().getJavSeries(id) }.getOrNull()

    suspend fun listVideoScreenshots(videoId: Long) =
        runCatching { apiClient.api().listVideoScreenshots(videoId).items }.getOrDefault(emptyList())

    /**
     * 样品图像数量。服务端 POST /jav/items/{id}/sample-images 返回 {sample_images:[...]}，
     * 图片 URL 只用下标（.../{index}/thumbnail、.../{index}/detail），所以客户端只需要个数。
     * 元素类型不固定，用 JsonArray.size 容错；接口失败返回 0，详情页就不显示这一区。
     */
    suspend fun listJavSampleImageCount(javId: Long): Int =
        runCatching {
            apiClient.api().listJavSampleImages(javId)["sample_images"]?.jsonArray?.size ?: 0
        }.getOrDefault(0)

    // ---------- 标签管理 / 作品编辑 / 外部链接 ----------

    /** 新建 JAV 标签，返回创建后的标签（服务端对空名/重名报 400） */
    suspend fun createJavTag(name: String): TagDto =
        apiClient.api().createJavTag(TagNameRequest(name))

    /** 全量替换一批作品的标签（勾选保存语义） */
    suspend fun replaceJavTags(javIds: List<Long>, tagIds: List<Long>) =
        apiClient.api().replaceJavTags(JavTagsReplaceRequest(javIds, tagIds))

    /** 编辑作品信息，仅传入需要修改的字段；返回更新后的作品 */
    suspend fun updateJavItem(id: Long, request: JavItemUpdateRequest): JavDto =
        apiClient.api().updateJavItem(id, request)

    /** 查询作品的 JavDB 页面地址；404/失败返回 null */
    suspend fun getJavDbUrl(code: String): String? =
        runCatching { apiClient.api().getJavDbUrl(code).url.takeIf { it.isNotBlank() } }.getOrNull()
}
