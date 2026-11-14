package com.javboss.phone.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.javboss.phone.data.api.ApiClient
import com.javboss.phone.data.api.dto.ScreenshotSecondRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 拖动进度条时的预览帧加载器（v0.3.78 最终实现）。
 *
 * 服务端没有「按时间点返回视频帧」的专用接口，但有按秒生成截图的
 * `POST /videos/{id}/screenshots`（ffmpeg 从服务端本地文件取帧），返回图片 URL。
 *
 * v0.3.77 / v0.3.78 初版用 [android.media.MediaMetadataRetriever] 从网络流取帧，
 * MuMu 实测始终停在「预览加载中…」：3 小时大文件需要反复 HTTP Range seek，
 * `MediaMetadataRetriever` 迟迟不返回，本地代理 NanoHTTPD 侧还伴随 sendBody 异常。
 * 改为服务端 ffmpeg 取帧后不依赖客户端框架解码器与网络 Range，稳定可用。
 *
 * 调用方负责按秒级分桶节流，并用**不会被手势变化取消**的协程调用 [load]；
 * 任何失败都返回 null，UI 保留上一帧 / 显示占位，绝不影响正在进行的播放。
 */
class SeekThumbnailLoader(
    private val apiClient: ApiClient,
    private val videoId: Long,
) {
    suspend fun load(timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        if (timeMs < 0L || videoId <= 0L) return@withContext null
        val bytes: ByteArray? = try {
            val info = apiClient.api()
                .createVideoScreenshot(videoId, ScreenshotSecondRequest(timeMs / 1000.0))
            if (info.url.isBlank()) {
                null
            } else {
                val url = apiClient.absoluteUrl(info.url)
                val request = Request.Builder().url(url).get().build()
                apiClient.okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.bytes() else null
                }
            }
        } catch (e: Throwable) {
            null
        }
        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
}
