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
import com.javboss.phone.data.api.dto.StudioDetailDto
import com.javboss.phone.data.api.dto.StudioListResponse
import com.javboss.phone.data.api.dto.TagCategoryAssignRequest
import com.javboss.phone.data.api.dto.TagCategoryDto
import com.javboss.phone.data.api.dto.TagCategoryNameRequest
import com.javboss.phone.data.api.dto.TagCategoryOrderRequest
import com.javboss.phone.data.api.dto.TagDto
import com.javboss.phone.data.api.dto.TagNameRequest
import com.javboss.phone.data.api.dto.VideoDto
import com.javboss.phone.data.api.dto.VideoListResponse
import com.javboss.phone.data.api.dto.VideoTagsReplaceRequest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import retrofit2.HttpException

/**
 * 解析服务端 `config.video_hide_jav`。
 *
 * 返回 null 表示「这次没读到明确配置」（字段缺失 / 类型不对 / 值不认识）。
 * 调用方**绝不能把 null 当成 false**：false 会让 /videos 不带 `hide_jav=1`，
 * 服务端就把已关联 JAV 的视频也返回，视频 tab 里就混进 JAV 文件了。
 *
 * 服务端可能把布尔存成字符串 "true"/"false"，也可能直接给 JSON bool 或数字，
 * 统一按 JsonPrimitive.content 的文本判断；无法识别的值一律算未知，不猜。
 */
fun parseVideoHideJav(raw: JsonElement?): Boolean? {
    val text = (raw as? JsonPrimitive)?.content ?: return null
    return when {
        text.equals("true", ignoreCase = true) || text == "1" -> true
        text.equals("false", ignoreCase = true) || text == "0" -> false
        else -> null
    }
}

/** 可直接播放的源地址 + 本次命中的文件位置（观看时长要按位置归账） */
data class PlayableSource(
    val urls: List<String> = emptyList(),
    val locationId: Long = 0,
)

class VideoRepository(private val apiClient: ApiClient) {

    /**
     * 服务端是否开启了「视频页隐藏已关联 JAV 的视频」（config.video_hide_jav）。
     * 用户的服务端默认是 true —— 也就是 Web 的视频页只列普通视频，JAV 只从「作品」进。
     *
     * 只在**成功读到明确值**时写缓存（避免每次翻页都打 /config）；
     * `GET /config` 瞬时失败、字段缺失或值不认识时返回 null 且**不写缓存**，
     * 下次调用会重新读配置自行纠正，不必杀掉 App 重启。
     *
     * 这里以前用 `runCatching{...}.getOrDefault(false)` 并缓存结果：一次网络抖动就会被
     * 永久记成 false，之后所有 /videos 都不带 hide_jav=1，把 JAV 关联视频全列出来，
     * 且缓存只在进程内，只有重启 App 才恢复 —— 就是「偶尔视频里冒出 JAV、重启就好」的根因。
     */
    private var hideJavCache: Boolean? = null

    /**
     * v0.3.65：列表接口内嵌 tags 不带 category，分类名只存在于标签管理端点。
     * 这里缓存两套全量标签表的 `id -> category` 索引（两套表 id 各自自增、可能冲突，
     * 必须分开存），在列表数据交给 UI 前用 [TagCategoryRules.enrich] 回填。
     * 只在**成功**读到数据时缓存；失败保持 null，下次列表请求会重试。
     */
    @Volatile
    private var javTagCategoryById: Map<Long, String>? = null

    @Volatile
    private var videoTagCategoryById: Map<Long, String>? = null

    private suspend fun ensureTagCategoryIndex() {
        if (javTagCategoryById == null) {
            runCatching { apiClient.api().listJavTags() }
                .onSuccess { javTagCategoryById = TagCategoryRules.indexOf(it) }
        }
        if (videoTagCategoryById == null) {
            // 这里要**全量**标签表，不能带 hide_jav，否则只挂在 JAV 上的标签查不到分类
            runCatching { apiClient.api().listVideoTags(hideJav = null) }
                .onSuccess { videoTagCategoryById = TagCategoryRules.indexOf(it) }
        }
    }

    /** 分类被增删改 / 标签被重新归类后调用，让下次列表加载重新拉全量表 */
    fun invalidateTagCategories() {
        javTagCategoryById = null
        videoTagCategoryById = null
    }

    /** 返回 null = 配置未知（未读到 / 失败），调用方需保守处理并允许重试 */
    suspend fun videosHideJav(): Boolean? {
        hideJavCache?.let { return it }
        val value = try {
            parseVideoHideJav(apiClient.api().getConfig()["video_hide_jav"])
        } catch (e: Exception) {
            null
        }
        if (value != null) hideJavCache = value
        return value
    }

    suspend fun listVideos(
        limit: Int,
        offset: Int,
        search: String = "",
        sort: String = "",
        hideJav: Boolean = false,
        directoryId: Long? = null,
        subdirectory: String? = null,
        /** 按标签名筛选；null = 不筛。Web 端 /videos?tags= 同款参数 */
        tags: String? = null,
    ): VideoListResponse {
        val response = apiClient.api().listVideos(
            limit = limit,
            offset = offset,
            search = search,
            sort = sort,
            hideJav = if (hideJav) 1 else 0,
            directoryId = directoryId,
            subdirectory = subdirectory,
            tags = tags,
        )
        ensureTagCategoryIndex()
        return response.copy(items = response.items.map { video ->
            video.copy(
                tags = TagCategoryRules.enrich(video.tags, videoTagCategoryById.orEmpty()),
                jav = video.jav?.let { jav ->
                    jav.copy(tags = TagCategoryRules.enrich(jav.tags, javTagCategoryById.orEmpty()))
                },
            )
        })
    }

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
    ): JavListResponse {
        val response = apiClient.api().searchJav(
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
        ensureTagCategoryIndex()
        return response.copy(items = response.items.map { jav ->
            jav.copy(tags = TagCategoryRules.enrich(jav.tags, javTagCategoryById.orEmpty()))
        })
    }

    suspend fun listJavPrefixes(): List<PrefixDto> =
        apiClient.api().listJavPrefixes()

    /** 片商详情的番号分组（Web 端「番号 (N)」）；异常交给 ViewModel 区分 401 / 普通失败 */
    suspend fun getJavStudio(id: Long): StudioDetailDto =
        apiClient.api().getJavStudio(id)

    suspend fun listJavTags(): List<TagDto> =
        apiClient.api().listJavTags()

    /** 标签管理弹层按服务端分类分组显示时加载分类列表；两套表各自独立 */
    suspend fun listTagCategories(scope: TagScope): List<TagCategoryDto> =
        when (scope) {
            TagScope.JAV -> apiClient.api().listJavTagCategories()
            TagScope.VIDEO -> apiClient.api().listVideoTagCategories()
        }

    // ---------- v0.3.65：分类管理（新建 / 重命名 / 删除 / 排序 / 批量归类） ----------

    suspend fun createTagCategory(scope: TagScope, name: String): TagCategoryDto {
        val body = TagCategoryNameRequest(name.trim())
        val created = when (scope) {
            TagScope.JAV -> apiClient.api().createJavTagCategory(body)
            TagScope.VIDEO -> apiClient.api().createVideoTagCategory(body)
        }
        invalidateTagCategories()
        return created
    }

    suspend fun renameTagCategory(scope: TagScope, id: Long, name: String) {
        val body = TagCategoryNameRequest(name.trim())
        when (scope) {
            TagScope.JAV -> apiClient.api().renameJavTagCategory(id, body)
            TagScope.VIDEO -> apiClient.api().renameVideoTagCategory(id, body)
        }
        invalidateTagCategories()
    }

    suspend fun deleteTagCategory(scope: TagScope, id: Long) {
        when (scope) {
            TagScope.JAV -> apiClient.api().deleteJavTagCategory(id)
            TagScope.VIDEO -> apiClient.api().deleteVideoTagCategory(id)
        }
        invalidateTagCategories()
    }

    /** ids 是分类按新顺序排列后的 id 列表，服务端据此重写 sort_order */
    suspend fun reorderTagCategories(scope: TagScope, ids: List<Long>) {
        val body = TagCategoryOrderRequest(ids)
        when (scope) {
            TagScope.JAV -> apiClient.api().reorderJavTagCategories(body)
            TagScope.VIDEO -> apiClient.api().reorderVideoTagCategories(body)
        }
        invalidateTagCategories()
    }

    /** 把选中的一批标签整体归入某个分类（categoryId=0 表示移回默认分类） */
    suspend fun assignTagsCategory(scope: TagScope, tagIds: List<Long>, categoryId: Long) {
        val body = TagCategoryAssignRequest(tagIds, categoryId)
        when (scope) {
            TagScope.JAV -> apiClient.api().assignJavTagsCategory(body)
            TagScope.VIDEO -> apiClient.api().assignVideoTagsCategory(body)
        }
        invalidateTagCategories()
    }

    suspend fun listJavIdols(
        limit: Int,
        offset: Int,
        search: String = "",
        sort: String = "",
        favoriteGroupId: Long? = null,
    ): IdolListResponse =
        apiClient.api().listJavIdols(
            limit = limit,
            offset = offset,
            search = search,
            sort = sort,
            favoriteGroupId = favoriteGroupId,
        )

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

    /** 重命名 / 删除 JAV 标签（/jav/tags/{id}） */
    suspend fun renameJavTag(id: Long, name: String) =
        apiClient.api().renameJavTag(id, TagNameRequest(name))

    suspend fun deleteJavTag(id: Long) =
        apiClient.api().deleteJavTag(id)

    /** 全量替换一批作品的标签（勾选保存语义） */
    suspend fun replaceJavTags(javIds: List<Long>, tagIds: List<Long>) =
        apiClient.api().replaceJavTags(JavTagsReplaceRequest(javIds, tagIds))

    /**
     * 视频标签可选项（GET /tags?hide_jav=<config>），与 /jav/tags 是两套数据。
     * Web 端 Rr({hideJav: videoHideJav}) 会按服务端 video_hide_jav 配置传参，
     * 开启时把只挂在 JAV 上的标签从视频标签列表里排除，避免两套标签混在一起。
     * 配置未知（/config 失败）时保守按 1 请求，宁可少列也不把 JAV 标签混进来。
     */
    suspend fun listVideoTags(): List<TagDto> =
        apiClient.api().listVideoTags(hideJav = if (videosHideJav() ?: true) 1 else 0)

    /** 新建视频标签（POST /tags，不是 /jav/tags） */
    suspend fun createVideoTag(name: String): TagDto =
        apiClient.api().createVideoTag(TagNameRequest(name))

    /** 重命名 / 删除视频标签（/tags/{id}） */
    suspend fun renameVideoTag(id: Long, name: String) =
        apiClient.api().renameVideoTag(id, TagNameRequest(name))

    suspend fun deleteVideoTag(id: Long) =
        apiClient.api().deleteVideoTag(id)

    /** 全量替换单个视频的标签；tagIds 为空即清空该视频的全部标签 */
    suspend fun replaceVideoTags(videoId: Long, tagIds: List<Long>) =
        apiClient.api().replaceVideoTags(VideoTagsReplaceRequest(listOf(videoId), tagIds))

    /** 编辑作品信息，仅传入需要修改的字段；返回更新后的作品 */
    suspend fun updateJavItem(id: Long, request: JavItemUpdateRequest): JavDto =
        apiClient.api().updateJavItem(id, request)

    /** 查询作品的 JavDB 页面地址；404/失败返回 null */
    suspend fun getJavDbUrl(code: String): String? =
        runCatching { apiClient.api().getJavDbUrl(code).url.takeIf { it.isNotBlank() } }.getOrNull()
}
