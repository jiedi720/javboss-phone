package com.javboss.phone.ui.detail

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.VideoTagEvents
import com.javboss.phone.data.expandFileVersions
import com.javboss.phone.data.api.AuthExpiredException
import com.javboss.phone.data.api.dto.FavoriteGroupDto
import com.javboss.phone.data.api.dto.IdolDto
import com.javboss.phone.data.api.dto.JavDto
import com.javboss.phone.data.api.dto.JavItemUpdateRequest
import com.javboss.phone.data.api.dto.ScreenshotDto
import com.javboss.phone.data.api.dto.SeriesDto
import com.javboss.phone.data.api.dto.StudioDto
import com.javboss.phone.data.api.dto.TagDto
import com.javboss.phone.data.api.dto.VideoDto
import com.javboss.phone.data.api.dto.VideoLocationRenameRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

data class DetailUiState(
    /** videoId <= 0 时为纯元数据模式（作品没有关联视频），video 保持 null */
    val video: VideoDto? = null,
    /** 通过 /jav?search=<code> 精确匹配拿到的完整元数据（含片商/标签/演员） */
    val javMeta: JavDto? = null,
    /** 按 /videos/{id} 的 locations 展开的文件版本列表（每个版本各带自己的路径与 location_id） */
    val files: List<VideoDto> = emptyList(),
    val seriesName: String = "",
    val screenshots: List<ScreenshotDto> = emptyList(),
    /** 服务端样品图像数量（POST /jav/items/{id}/sample-images）；0 = 没有样品图，不显示这一区 */
    val sampleImageCount: Int = 0,
    /** 样品图所属作品 id，拼 .../sample-images/{index}/thumbnail|detail 用 */
    val sampleJavId: Long = 0,
    val loading: Boolean = true,
    val error: String? = null,
    /** 一次性提示（收藏/编辑成功失败），UI 用 Toast 展示 */
    val notice: String? = null,
    val sessionExpired: Boolean = false,
)

class DetailViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JavBossApp).container

    var uiState by mutableStateOf(DetailUiState())
        private set

    /** 记住本次加载参数，文件改名/删除后原地刷新用 */
    private var loadedVideoId: Long = 0
    private var loadedJavCode: String? = null

    /**
     * @param javCode 从列表页带过来的番号；/videos/{id} 详情不内嵌 JAV 元数据，
     *   需要用它搜索 /jav 精确匹配出完整条目（对齐网页版 JavDetailModal 的数据源）
     */
    fun load(videoId: Long, javCode: String?) {
        loadedVideoId = videoId
        loadedJavCode = javCode
        viewModelScope.launch {
            // 换作品 / 刷新时先清掉上一部的样品图、截图与文件版本，避免串台
            uiState = uiState.copy(
                loading = true,
                error = null,
                screenshots = emptyList(),
                sampleImageCount = 0,
                sampleJavId = 0,
                files = emptyList(),
            )
            try {
                var code = javCode
                // /jav 内嵌的 videos：每个文件一条，用来补充各文件版本的统计信息
                var metaVideos: List<VideoDto> = emptyList()
                if (videoId > 0) {
                    // 删掉当前文件后 /videos/{id} 可能 404，这时仍要按番号展示作品元数据
                    val video = runCatching { container.videoRepository.getVideo(videoId) }.getOrNull()
                    uiState = uiState.copy(video = video)
                    if (code.isNullOrBlank()) code = video?.jav?.code
                }

                if (!code.isNullOrBlank()) {
                    val meta = runCatching {
                        container.videoRepository.searchJavs(limit = 5, offset = 0, search = code)
                            .items.firstOrNull { it.code.equals(code, ignoreCase = true) }
                    }.getOrNull()
                    metaVideos = meta?.videos.orEmpty()
                    uiState = uiState.copy(javMeta = meta)

                    // 样品图像：服务端按数组下标取图，这里只需要个数；接口异常返回 0，就不显示这一区
                    if (meta != null && meta.id > 0) {
                        val count = container.videoRepository.listJavSampleImageCount(meta.id)
                        uiState = uiState.copy(sampleImageCount = count, sampleJavId = meta.id)
                    }

                    // /videos/{id} 有时不含目录信息，用同一部作品里内嵌的视频对象补齐路径
                    val current = uiState.video
                    if (current != null && current.path.isBlank()) {
                        meta?.videos?.firstOrNull { it.id == current.id }?.let { fromMeta ->
                            uiState = uiState.copy(
                                video = current.copy(
                                    path = fromMeta.path,
                                    directoryPath = fromMeta.directoryPath,
                                    directory = fromMeta.directory,
                                ),
                            )
                        }
                    }

                    val sid = meta?.seriesId
                    if (sid != null && sid > 0) {
                        val series = container.videoRepository.getJavSeries(sid)
                        if (series != null) uiState = uiState.copy(seriesName = series.name)
                    }
                }

                if (videoId > 0) {
                    val shots = container.videoRepository.listVideoScreenshots(videoId)
                    uiState = uiState.copy(screenshots = shots)
                }
                // 文件版本按 /videos/{id} 的 locations 逐条展开：每个版本各带自己的路径与 location_id，
                // 不再按 video.id 把全部版本替换成单条主路径（同一 video_id 的多条记录 id 相同）
                uiState = uiState.copy(
                    loading = false,
                    files = expandFileVersions(uiState.video, metaVideos),
                )
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(loading = false, sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    // ---------- 文件位置：查看详情 / 重命名 / 删除 ----------

    /**
     * 改名/删除必须用「文件位置 id」，猜错会改/删到别的文件。
     * /jav 内嵌的 videos 正常自带 location_id；缺失时用 /videos/{id} 的 locations
     * 按文件名 + 相对路径精确反查，只有唯一命中（或该视频只有一个位置）才允许操作。
     */
    private suspend fun locationIdOf(file: VideoDto): Long {
        if (file.locationId > 0) return file.locationId
        val detail = runCatching { container.videoRepository.getVideo(file.id) }.getOrNull() ?: return 0
        if (detail.locations.isEmpty()) return 0
        if (detail.locations.size == 1) return detail.locations.first().id
        val name = file.filename.substringAfterLast('/')
        val matches = detail.locations.filter {
            it.filename == name || it.filename == file.filename
        }
        if (matches.size == 1) return matches.first().id
        val path = file.path.trim().trimStart('/')
        return matches.firstOrNull { it.relativePath.trim().trimStart('/') == path }?.id ?: 0
    }

    /** 服务端会同时改磁盘上的真实文件名，成功后按当前参数原地刷新详情页 */
    fun renameFile(file: VideoDto, newFilename: String) {
        val name = newFilename.trim()
        if (name.isBlank() || name.contains('/') || name.contains('\\')) {
            uiState = uiState.copy(notice = "文件名不能为空，也不能包含路径分隔符")
            return
        }
        viewModelScope.launch {
            val locationId = locationIdOf(file)
            if (locationId <= 0) {
                uiState = uiState.copy(notice = "无法确定此文件的位置 ID，已取消重命名以免误改其它文件")
                return@launch
            }
            try {
                container.apiClient.api().renameVideoLocation(
                    file.id,
                    locationId,
                    VideoLocationRenameRequest(name),
                )
                uiState = uiState.copy(notice = "已重命名，服务端已同步")
                load(loadedVideoId, loadedJavCode)
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "重命名失败：${e.message ?: "网络异常"}")
            }
        }
    }

    /** 服务端会同时删除磁盘上的真实文件，成功后原地刷新详情页 */
    fun deleteFile(file: VideoDto) {
        viewModelScope.launch {
            val locationId = locationIdOf(file)
            if (locationId <= 0) {
                uiState = uiState.copy(notice = "无法确定此文件的位置 ID，已取消删除以免误删其它文件")
                return@launch
            }
            try {
                container.apiClient.api().deleteVideoLocation(file.id, locationId)
                uiState = uiState.copy(notice = "已删除，服务端已同步")
                load(loadedVideoId, loadedJavCode)
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "删除失败：${e.message ?: "网络异常"}")
            }
        }
    }

    // ---------- 收藏 ----------

    /** 收藏弹窗数据：全部收藏夹 + 当前作品已选中的收藏夹 id */
    suspend fun loadFavoriteData(javId: Long): Pair<List<FavoriteGroupDto>, Set<Long>> {
        val groups = container.favoriteRepository.listGroups()
        val selected = container.favoriteRepository.selectedGroupIds(javId)
        return groups to selected.toSet()
    }

    fun saveFavorite(javId: Long, groupIds: Set<Long>) {
        viewModelScope.launch {
            try {
                container.favoriteRepository.replaceGroups(javId, groupIds.toList())
                uiState = uiState.copy(notice = "收藏已保存")
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "收藏保存失败")
            }
        }
    }

    suspend fun createFavoriteGroup(name: String): FavoriteGroupDto =
        container.favoriteRepository.createGroup(name)

    // ---------- 编辑 ----------

    data class EditOptions(
        val studios: List<StudioDto> = emptyList(),
        val series: List<SeriesDto> = emptyList(),
        val tags: List<TagDto> = emptyList(),
    )

    suspend fun loadEditOptions(): EditOptions {
        val studios = runCatching {
            container.videoRepository.listJavStudios(limit = 500, offset = 0).items
        }.getOrDefault(emptyList())
        val series = runCatching {
            container.videoRepository.listJavSeries(limit = 500, offset = 0).items
        }.getOrDefault(emptyList())
        val tags = runCatching {
            container.videoRepository.listJavTags()
        }.getOrDefault(emptyList())
        return EditOptions(studios, series, tags)
    }

    suspend fun searchIdols(query: String): List<IdolDto> =
        runCatching {
            container.videoRepository.listJavIdols(limit = 8, offset = 0, search = query).items
        }.getOrDefault(emptyList())

    suspend fun createTag(name: String): TagDto =
        container.videoRepository.createJavTag(name)

    fun saveEdit(javId: Long, request: JavItemUpdateRequest) {
        viewModelScope.launch {
            try {
                val updated = container.videoRepository.updateJavItem(javId, request)
                var seriesName = uiState.seriesName
                val sid = updated.seriesId
                if (sid != null && sid > 0 && sid != uiState.javMeta?.seriesId) {
                    seriesName = container.videoRepository.getJavSeries(sid)?.name ?: seriesName
                } else if (sid == null || sid <= 0) {
                    seriesName = ""
                }
                uiState = uiState.copy(javMeta = updated, seriesName = seriesName, notice = "已保存")
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "保存失败：${e.message ?: "未知错误"}")
            }
        }
    }

    // ---------- 视频标签编辑（与作品标签分开） ----------

    /** 视频标签可选项（GET /tags），与 /jav/tags 是两套独立数据 */
    suspend fun loadVideoTagOptions(): List<TagDto> =
        runCatching { container.videoRepository.listVideoTags() }.getOrDefault(emptyList())

    /** 新建视频标签（POST /tags，不是 /jav/tags） */
    suspend fun createVideoTag(name: String): TagDto =
        container.videoRepository.createVideoTag(name)

    /** JAV 自定义标签可选项（GET /jav/tags），与视频标签 /tags 是两套独立数据 */
    suspend fun loadJavTagOptions(): List<TagDto> =
        runCatching { container.videoRepository.listJavTags() }.getOrDefault(emptyList())

    /**
     * 改喜爱度评分（0..5、步长 0.5）：PUT /jav/items/{id}，与封面左上角 / 收藏夹同一份数据。
     * 成功后把返回对象写回 javMeta，详情页番号上方的星级立即刷新。
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
                uiState = uiState.copy(javMeta = updated)
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (_: Exception) {
                uiState = uiState.copy(notice = "评分保存失败")
            }
        }
    }

    /**
     * 保存 JAV 自定义标签：只传 tag_ids，走 PUT /jav/items/{id}
     * （Web 端 JAV 详情「编辑自定义标签」同款），不会碰到视频标签。
     */
    fun saveJavTags(javId: Long, tagIds: List<Long>) {
        viewModelScope.launch {
            try {
                val updated = container.videoRepository.updateJavItem(
                    javId,
                    JavItemUpdateRequest(tagIds = tagIds),
                )
                uiState = uiState.copy(javMeta = updated, notice = "标签已保存")
                VideoTagEvents.notifyChanged()
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "标签保存失败：${e.message ?: "网络异常"}")
            }
        }
    }

    /** 全量替换当前视频的标签；成功后原地刷新详情页并通知列表页刷新 */
    fun saveVideoTags(videoId: Long, tagIds: List<Long>) {
        viewModelScope.launch {
            try {
                container.videoRepository.replaceVideoTags(videoId, tagIds)
                uiState = uiState.copy(notice = "视频标签已保存")
                VideoTagEvents.notifyChanged()
                load(loadedVideoId, loadedJavCode)
            } catch (e: AuthExpiredException) {
                uiState = uiState.copy(sessionExpired = true)
            } catch (e: Exception) {
                uiState = uiState.copy(notice = "视频标签保存失败：${e.message ?: "网络异常"}")
            }
        }
    }

    // ---------- 外部链接 ----------

    suspend fun resolveJavDbUrl(code: String): String? =
        container.videoRepository.getJavDbUrl(code)

    // ---------- 工具 ----------

    fun coverUrl(): String? {
        val code = uiState.javMeta?.code ?: uiState.video?.jav?.code
        return code?.takeIf { it.isNotBlank() }?.let { container.apiClient.javCoverUrl(it) }
            ?: uiState.video?.let { container.apiClient.videoThumbnailUrl(it.id) }
    }

    /** 完整封面：与 JAV 列表用的是同一张 jav/{code}/cover，查看器用 Fit 不裁剪 */
    fun fullCoverUrl(): String? = coverUrl()

    fun screenshotUrl(videoId: Long, name: String): String =
        container.apiClient.videoScreenshotUrl(videoId, name)

    /** 样品图列表用 thumbnail，点开放大用 detail（与 Web 端 KO 组件一致） */
    fun sampleThumbnailUrl(index: Int): String =
        container.apiClient.javSampleThumbnailUrl(uiState.sampleJavId, index)

    fun sampleDetailUrl(index: Int): String =
        container.apiClient.javSampleDetailUrl(uiState.sampleJavId, index)

    // ---------- 大图长按：保存到本地 ----------

    /**
     * 把服务端的一张图下载到本机「下载/JavBoss」，返回给界面展示的提示文案。
     * 用 App 自己的 OkHttp（带会话 Cookie）取图：封面/样品图/截图都要登录态才能拿到。
     * API 29+ 走 MediaStore.Downloads（免权限）；API 26–28 写公共 Download 目录，
     * 没有写权限就退回应用私有目录，并把完整路径告诉用户。
     */
    suspend fun saveImageToLocal(url: String): String = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val request = Request.Builder().url(url).build()
        val response = container.apiClient.okHttpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) throw IllegalStateException("下载失败：HTTP ${it.code}")
            val bytes = it.body?.bytes() ?: throw IllegalStateException("下载失败：内容为空")
            val mime = it.header("Content-Type").orEmpty().lowercase()
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "jpg"
            }
            val name = "javboss_${System.currentTimeMillis()}.$ext"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(
                        android.provider.MediaStore.Downloads.MIME_TYPE,
                        if (ext == "jpg") "image/jpeg" else "image/$ext",
                    )
                    put(
                        android.provider.MediaStore.Downloads.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_DOWNLOADS + "/JavBoss",
                    )
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: throw IllegalStateException("保存失败：无法创建文件")
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                } ?: throw IllegalStateException("保存失败：无法写入")
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                "已保存到「下载/JavBoss/$name」"
            } else {
                // API 26–28：公共目录要 WRITE_EXTERNAL_STORAGE，没有就存应用私有目录
                val publicDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                )
                val target = File(publicDir, "JavBoss")
                val canWrite = runCatching { target.mkdirs() || target.isDirectory }.getOrDefault(false)
                val dir = if (canWrite) target else context.getExternalFilesDir(null)
                    ?: File(context.filesDir, "downloads")
                dir.mkdirs()
                val file = File(dir, name)
                file.writeBytes(bytes)
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(file.absolutePath), null, null,
                )
                "已保存到 ${file.absolutePath}"
            }
        }
    }

    fun consumeNotice() {
        uiState = uiState.copy(notice = null)
    }
}
