package com.javboss.phone.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /videos/{id}/screenshots 的请求体：服务端用 ffmpeg 在指定秒取帧。 */
@Serializable
data class ScreenshotSecondRequest(
    val second: Double,
)

/** POST /videos/{id}/screenshots 的返回体；进度条预览只用到 url。 */
@Serializable
data class ScreenshotInfoDto(
    @SerialName("video_id") val videoId: Long = 0L,
    val name: String = "",
    val url: String = "",
    val size: Long = 0L,
)
