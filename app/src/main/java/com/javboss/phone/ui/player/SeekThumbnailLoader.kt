package com.javboss.phone.ui.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 拖动进度条时的预览帧加载器（v0.3.77 需求②）。
 *
 * 服务端没有「按时间点返回视频帧」的接口（Web 端的进度条预览只在本机 MPV +
 * thumbfast 下可用），因此客户端用 [MediaMetadataRetriever] 带会话 Cookie 在 IO 线程
 * 本地取帧。取帧代价较高，调用方已用 SeekPreviewRules 做 debounce + 秒级分桶节流。
 *
 * 任何失败（格式不支持、网络 seek 失败、无关键帧）都返回 null，UI 只隐藏/占位预览卡片，
 * 绝不影响正在进行的播放。
 */
class SeekThumbnailLoader(
    private val url: String,
    private val cookieHeader: String,
) {
    suspend fun load(timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        if (timeMs < 0L) return@withContext null
        val retriever = MediaMetadataRetriever()
        try {
            val headers = if (cookieHeader.isBlank()) {
                emptyMap()
            } else {
                mapOf("Cookie" to cookieHeader)
            }
            retriever.setDataSource(url, headers)
            // timeUs 参数是微秒；OPTION_CLOSEST_SYNC 取最近关键帧，比逐帧解码快得多
            retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
