package com.javboss.phone.data.api

import com.javboss.phone.data.SessionManager
import com.javboss.phone.data.api.dto.ApiErrorResponse
import com.javboss.phone.data.api.dto.AuthStatusResponse
import com.javboss.phone.data.api.dto.DirectoryCreateRequest
import com.javboss.phone.data.api.dto.DirectoryDto
import com.javboss.phone.data.api.dto.DirectoryUpdateRequest
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import com.javboss.phone.data.api.dto.FavoriteGroupListResponse
import com.javboss.phone.data.api.dto.FavoriteGroupNameRequest
import com.javboss.phone.data.api.dto.FavoriteItemListResponse
import com.javboss.phone.data.api.dto.IdolListResponse
import com.javboss.phone.data.api.dto.JavDbUrlResponse
import com.javboss.phone.data.api.dto.JavDto
import com.javboss.phone.data.api.dto.JavItemUpdateRequest
import com.javboss.phone.data.api.dto.JavListResponse
import com.javboss.phone.data.api.dto.JavTagsReplaceRequest
import com.javboss.phone.data.api.dto.LoginRequest
import com.javboss.phone.data.api.dto.LoginResponse
import com.javboss.phone.data.api.dto.PlaybackSessionReportRequest
import com.javboss.phone.data.api.dto.PlaybackSessionRequest
import com.javboss.phone.data.api.dto.PlaybackSessionResponse
import com.javboss.phone.data.api.dto.PrefixDto
import com.javboss.phone.data.api.dto.RemoveFavoriteItemsRequest
import com.javboss.phone.data.api.dto.ReplaceFavoriteGroupsRequest
import com.javboss.phone.data.api.dto.ScreenshotListResponse
import com.javboss.phone.data.api.dto.SelectedFavoriteGroupsResponse
import com.javboss.phone.data.api.dto.SeriesDto
import com.javboss.phone.data.api.dto.SeriesListResponse
import com.javboss.phone.data.api.dto.StreamsResponse
import com.javboss.phone.data.api.dto.StudioListResponse
import com.javboss.phone.data.api.dto.TagCategoryAssignRequest
import com.javboss.phone.data.api.dto.TagCategoryDto
import com.javboss.phone.data.api.dto.TagCategoryNameRequest
import com.javboss.phone.data.api.dto.TagCategoryOrderRequest
import com.javboss.phone.data.api.dto.TagDto
import com.javboss.phone.data.api.dto.TagNameRequest
import com.javboss.phone.data.api.dto.VideoDto
import com.javboss.phone.data.api.dto.VideoLocationRenameRequest
import com.javboss.phone.data.api.dto.VideoListResponse
import com.javboss.phone.data.api.dto.VideoTagsReplaceRequest
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 与服务端 Gin 路由一一对应（见 JavBoss/internal/server/api.go） */
interface JavBossApi {

    @GET("auth/status")
    suspend fun authStatus(): AuthStatusResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("videos")
    suspend fun listVideos(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("search") search: String = "",
        @Query("sort") sort: String = "",
        /** 1 = 只返回没关联 JAV 的普通视频（服务端配置 video_hide_jav 为 true 时 Web 端就这么请求） */
        @Query("hide_jav") hideJav: Int = 0,
        /** 点卡片上的文件夹路径时按目录筛选（Web 端 /videos 同款参数） */
        @Query("directory_id") directoryId: Long? = null,
        @Query("subdirectory") subdirectory: String? = null,
        /** 按标签名筛选（Web 端 `/videos?tags=a,b`）；点封面标签时用单个标签名 */
        @Query("tags") tags: String? = null,
    ): VideoListResponse

    /**
     * 服务端配置（扁平 key-value）。服务端把布尔值存成字符串 "true"/"false"，
     * 所以类型不固定，这里用 JsonElement 统一接，避免某天变成 bool 就整包解析失败。
     */
    @GET("config")
    suspend fun getConfig(): Map<String, kotlinx.serialization.json.JsonElement>

    @GET("videos/{id}")
    suspend fun getVideo(@Path("id") id: Long): VideoDto

    @GET("videos/{id}/streams")
    suspend fun getStreams(@Path("id") id: Long): StreamsResponse

    /**
     * 文件位置操作：/jav 内嵌的 videos 是「每个文件一条」，id 是作品视频 id，
     * locationId 才是这条文件记录的 id。服务端会同时改/删磁盘上的真实文件。
     */
    @PATCH("videos/{id}/locations/{locationId}")
    suspend fun renameVideoLocation(
        @Path("id") id: Long,
        @Path("locationId") locationId: Long,
        @Body body: VideoLocationRenameRequest,
    )

    @DELETE("videos/{id}/locations/{locationId}")
    suspend fun deleteVideoLocation(
        @Path("id") id: Long,
        @Path("locationId") locationId: Long,
    )

    @POST("videos/{id}/play")
    suspend fun incrementPlayCount(@Path("id") id: Long)

    /** 开一个播放会话，服务端返回 session_id（观看时长按会话累计） */
    @POST("videos/{id}/playback-sessions")
    suspend fun createPlaybackSession(
        @Path("id") id: Long,
        @Body body: PlaybackSessionRequest,
    ): PlaybackSessionResponse

    /** 上报本会话累计观看毫秒数；会话过期时服务端返回 410 */
    @PUT("videos/{id}/playback-sessions/{sessionId}")
    suspend fun reportPlaybackSession(
        @Path("id") id: Long,
        @Path("sessionId") sessionId: String,
        @Body body: PlaybackSessionReportRequest,
    )

    @GET("jav")
    suspend fun searchJav(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("search") search: String = "",
        @Query("idol_ids") idolIds: String = "",
        @Query("tag_ids") tagIds: String = "",
        @Query("studio_id") studioId: Long? = null,
        @Query("series_id") seriesId: Long? = null,
        @Query("prefix") prefix: String = "",
        @Query("solo") solo: Int = 0,
        @Query("sort") sort: String = "",
        @Query("seed") seed: Long? = null,
        /** 只看某个收藏夹里的作品（Web 端筛选面板同一个参数），返回的是完整 JavDto */
        @Query("favorite_group_id") favoriteGroupId: Long? = null,
    ): JavListResponse

    @GET("jav/prefixes")
    suspend fun listJavPrefixes(): List<PrefixDto>

    @GET("jav/idols")
    suspend fun listJavIdols(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("search") search: String = "",
        @Query("sort") sort: String = "",
        /** 只看某个女优收藏夹里的女优（Web 端同一个参数），返回的是完整 IdolDto */
        @Query("favorite_group_id") favoriteGroupId: Long? = null,
    ): IdolListResponse

    @GET("jav/studios")
    suspend fun listJavStudios(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("search") search: String = "",
    ): StudioListResponse

    @GET("jav/series")
    suspend fun listJavSeries(
        @Query("limit") limit: Int = 25,
        @Query("offset") offset: Int = 0,
        @Query("search") search: String = "",
    ): SeriesListResponse

    @GET("jav/series/{id}")
    suspend fun getJavSeries(@Path("id") id: Long): SeriesDto

    @GET("videos/{id}/screenshots")
    suspend fun listVideoScreenshots(@Path("id") id: Long): ScreenshotListResponse

    /**
     * 作品的样品图像列表（Web 端 KO 组件的数据源）。
     * 返回 { sample_images: [...] }，元素可能是字符串也可能是对象，
     * 这里只取数组长度，所以用 JsonObject 容错解析。
     */
    @POST("jav/items/{id}/sample-images")
    suspend fun listJavSampleImages(@Path("id") id: Long): JsonObject

    @GET("jav/tags")
    suspend fun listJavTags(): List<TagDto>

    /** 标签分类（服务端分组）：JAV 侧 `/jav/tag-categories`，返回分类数组 */
    @GET("jav/tag-categories")
    suspend fun listJavTagCategories(): List<TagCategoryDto>

    /**
     * 视频标签（与 /jav/tags 是两套独立数据）：GET /tags 列出可勾选标签，
     * POST /tags 新建，POST /videos/tags/replace 全量替换某个视频的标签。
     */
    @GET("tags")
    suspend fun listVideoTags(@Query("hide_jav") hideJav: Int? = null): List<TagDto>

    /** 视频标签分类：GET `/tags/categories`，返回分类数组 */
    @GET("tags/categories")
    suspend fun listVideoTagCategories(): List<TagCategoryDto>

    // ---------- 标签分类管理（v0.3.65）：JAV / 视频两套独立表 ----------
    // Web bundle：mn/hn/gn/_n/vn（JAV）、Vr/Hr/Ur/Wr/Gr（视频），
    // 排序 PUT .../categories/order {category_ids}，批量归类 POST .../category {tag_ids, category_id}。

    @POST("jav/tag-categories")
    suspend fun createJavTagCategory(@Body body: TagCategoryNameRequest): TagCategoryDto

    @PUT("jav/tag-categories/order")
    suspend fun reorderJavTagCategories(@Body body: TagCategoryOrderRequest)

    @PATCH("jav/tag-categories/{id}")
    suspend fun renameJavTagCategory(@Path("id") id: Long, @Body body: TagCategoryNameRequest)

    @DELETE("jav/tag-categories/{id}")
    suspend fun deleteJavTagCategory(@Path("id") id: Long)

    @POST("jav/tags/category")
    suspend fun assignJavTagsCategory(@Body body: TagCategoryAssignRequest)

    @POST("tags/categories")
    suspend fun createVideoTagCategory(@Body body: TagCategoryNameRequest): TagCategoryDto

    @PUT("tags/categories/order")
    suspend fun reorderVideoTagCategories(@Body body: TagCategoryOrderRequest)

    @PATCH("tags/categories/{id}")
    suspend fun renameVideoTagCategory(@Path("id") id: Long, @Body body: TagCategoryNameRequest)

    @DELETE("tags/categories/{id}")
    suspend fun deleteVideoTagCategory(@Path("id") id: Long)

    @POST("tags/category")
    suspend fun assignVideoTagsCategory(@Body body: TagCategoryAssignRequest)

    @POST("tags")
    suspend fun createVideoTag(@Body body: TagNameRequest): TagDto

    /** 视频标签重命名 / 删除：Web 端 PATCH、DELETE `/tags/{id}` */
    @PATCH("tags/{id}")
    suspend fun renameVideoTag(@Path("id") id: Long, @Body body: TagNameRequest)

    @DELETE("tags/{id}")
    suspend fun deleteVideoTag(@Path("id") id: Long)

    @POST("videos/tags/replace")
    suspend fun replaceVideoTags(@Body body: VideoTagsReplaceRequest)

    // ---------- 收藏夹（作品实体） ----------

    @GET("jav/jav-favorite-groups")
    suspend fun listFavoriteGroups(): FavoriteGroupListResponse

    @POST("jav/jav-favorite-groups")
    suspend fun createFavoriteGroup(@Body body: FavoriteGroupNameRequest): FavoriteGroupDto

    @PATCH("jav/jav-favorite-groups/{id}")
    suspend fun renameFavoriteGroup(@Path("id") id: Long, @Body body: FavoriteGroupNameRequest)

    @DELETE("jav/jav-favorite-groups/{id}")
    suspend fun deleteFavoriteGroup(@Path("id") id: Long)

    @GET("jav/jav-favorite-groups/{id}/items")
    suspend fun listFavoriteGroupItems(@Path("id") id: Long): FavoriteItemListResponse

    @POST("jav/jav-favorite-groups/{id}/items/remove")
    suspend fun removeFavoriteGroupItems(@Path("id") id: Long, @Body body: RemoveFavoriteItemsRequest)

    @GET("jav/items/{id}/favorite-groups")
    suspend fun listFavoriteGroupIds(@Path("id") id: Long): SelectedFavoriteGroupsResponse

    @PUT("jav/items/{id}/favorite-groups")
    suspend fun replaceFavoriteGroups(@Path("id") id: Long, @Body body: ReplaceFavoriteGroupsRequest)

    // ---------- 收藏夹（按实体类型通用） ----------
    // Web 端契约（/tmp/wb.js 反查）：
    //   /jav/{kind}-favorite-groups          kind = jav | idol | studio | series
    //   /jav/{entity}/{id}/favorite-groups  entity = items | idols | studios | series
    // 女优收藏夹走 kind=idol、entity=idols；作品走 kind=jav、entity=items。

    @GET("jav/{kind}-favorite-groups")
    suspend fun listFavoriteGroupsOf(@Path("kind") kind: String): FavoriteGroupListResponse

    @POST("jav/{kind}-favorite-groups")
    suspend fun createFavoriteGroupOf(
        @Path("kind") kind: String,
        @Body body: FavoriteGroupNameRequest,
    ): FavoriteGroupDto

    @PATCH("jav/{kind}-favorite-groups/{id}")
    suspend fun renameFavoriteGroupOf(
        @Path("kind") kind: String,
        @Path("id") id: Long,
        @Body body: FavoriteGroupNameRequest,
    )

    @DELETE("jav/{kind}-favorite-groups/{id}")
    suspend fun deleteFavoriteGroupOf(@Path("kind") kind: String, @Path("id") id: Long)

    @GET("jav/{kind}-favorite-groups/{id}/items")
    suspend fun listFavoriteGroupItemsOf(
        @Path("kind") kind: String,
        @Path("id") id: Long,
    ): FavoriteItemListResponse

    @POST("jav/{kind}-favorite-groups/{id}/items/remove")
    suspend fun removeFavoriteGroupItemsOf(
        @Path("kind") kind: String,
        @Path("id") id: Long,
        @Body body: RemoveFavoriteItemsRequest,
    )

    @GET("jav/{entity}/{id}/favorite-groups")
    suspend fun listSelectedGroupsOf(
        @Path("entity") entity: String,
        @Path("id") id: Long,
    ): SelectedFavoriteGroupsResponse

    @PUT("jav/{entity}/{id}/favorite-groups")
    suspend fun replaceGroupsOf(
        @Path("entity") entity: String,
        @Path("id") id: Long,
        @Body body: ReplaceFavoriteGroupsRequest,
    )

    // ---------- 标签管理 / 作品编辑 / 外部链接 ----------

    @POST("jav/tags")
    suspend fun createJavTag(@Body body: TagNameRequest): TagDto

    /** JAV 标签重命名 / 删除：Web 端 PATCH、DELETE `/jav/tags/{id}` */
    @PATCH("jav/tags/{id}")
    suspend fun renameJavTag(@Path("id") id: Long, @Body body: TagNameRequest)

    @DELETE("jav/tags/{id}")
    suspend fun deleteJavTag(@Path("id") id: Long)

    @POST("jav/tags/replace")
    suspend fun replaceJavTags(@Body body: JavTagsReplaceRequest)

    @PUT("jav/items/{id}")
    suspend fun updateJavItem(@Path("id") id: Long, @Body body: JavItemUpdateRequest): JavDto

    @GET("jav/javdb-url")
    suspend fun getJavDbUrl(@Query("code") code: String): JavDbUrlResponse

    // ---------- 读取路径（媒体目录） ----------

    @GET("directories")
    suspend fun listDirectories(): List<DirectoryDto>

    @POST("directories")
    suspend fun createDirectory(@Body body: DirectoryCreateRequest): DirectoryDto

    @PATCH("directories/{id}")
    suspend fun updateDirectory(
        @Path("id") id: Long,
        @Body body: DirectoryUpdateRequest,
    ): DirectoryDto

    @POST("directories/{id}/scan")
    suspend fun scanDirectory(@Path("id") id: Long)
}

/**
 * 登录态失效（服务端返回 401）时抛出，UI 层捕获后跳回登录页。
 *
 * 必须继承 [IOException]：401 是在 OkHttp application interceptor 里抛出的，
 * OkHttp 4.12.0 的 RealCall.AsyncCall 只把 IOException 交给 onFailure；抛其它 Throwable
 * 会先被包装成 IOException 回调、再把原异常原样重新抛到 Dispatcher 线程（无人捕获，
 * 触发全局 UncaughtExceptionHandler 闪退），而 Retrofit 协程里只能拿到包装后的
 * IOException，UI 层的 catch(AuthExpiredException) 也永远不会命中。
 */
class AuthExpiredException : IOException("登录状态已失效")

class ApiException(val statusCode: Int, message: String) : Exception(message)

/**
 * 网络客户端工厂。服务器地址由用户输入，运行时才能确定，
 * 所以 Retrofit 在地址变化时重建；OkHttpClient 全局复用（共享 CookieJar/连接池）。
 */
class ApiClient(
    private val session: SessionManager,
    val cookieJar: SessionCookieJar,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 401) throw AuthExpiredException()
            response
        }
        .build()

    @Volatile
    private var cachedApi: Pair<String, JavBossApi>? = null

    fun api(): JavBossApi {
        val baseUrl = session.serverUrl.trimEnd('/') + "/"
        cachedApi?.let { if (it.first == baseUrl) return it.second }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val api = retrofit.create(JavBossApi::class.java)
        cachedApi = baseUrl to api
        return api
    }

    /** 当前服务端的同源校验，流媒体和带鉴权的图片都只能访问此 origin。 */
    fun isServerUrl(url: String): Boolean = ServerOrigin.matches(session.serverUrl, url)

    /** 图片/流绝对地址：服务端返回的 src 多为相对路径；绝对地址必须保持同源。 */
    fun absoluteUrl(pathOrUrl: String): String {
        val url = if (pathOrUrl.contains("://")) pathOrUrl
        else session.serverUrl.trimEnd('/') + "/" + pathOrUrl.trimStart('/')
        require(isServerUrl(url)) { "资源地址必须与当前服务器同源" }
        return url
    }

    fun videoThumbnailUrl(videoId: Long) = absoluteUrl("videos/$videoId/thumbnail")
    fun javCoverUrl(code: String) = absoluteUrl("jav/$code/cover")
    fun videoScreenshotUrl(videoId: Long, name: String) =
        absoluteUrl("videos/$videoId/screenshots/${android.net.Uri.encode(name)}")

    /** 样品图按数组下标取图：缩略图走列表，点开看大图走 detail */
    fun javSampleThumbnailUrl(javId: Long, index: Int) =
        absoluteUrl("jav/items/$javId/sample-images/$index/thumbnail")

    fun javSampleDetailUrl(javId: Long, index: Int) =
        absoluteUrl("jav/items/$javId/sample-images/$index/detail")

    /** 把错误响应体解析成可读信息 */
    fun parseError(body: String?): String =
        body?.let {
            runCatching { json.decodeFromString<ApiErrorResponse>(it).displayMessage }.getOrNull()
        } ?: "请求失败"
}
