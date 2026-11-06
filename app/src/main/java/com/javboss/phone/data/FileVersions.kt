package com.javboss.phone.data

import com.javboss.phone.data.api.dto.DirectoryRefDto
import com.javboss.phone.data.api.dto.VideoDto
import com.javboss.phone.data.api.dto.VideoLocationDto

/**
 * 详情页「文件版本」列表，对齐服务端 Web 端把 /videos/{id} 的 locations 展开的逻辑。
 *
 * 一个 video_id 可以挂多个 video_location（同一部作品的不同文件版本散落在不同读取目录，
 * 例如 SNIS-557 同时有 `G:\UnScrapy\SNIS-557 [小野夕子]\SNIS-557.mp4` 和
 * `G:\LTX\SNIS-557-LTX\SNIS-557-LTX.mp4`）。/jav 内嵌的 videos 对同一个 video_id
 * 给出的多条记录 **id 完全相同，只有 location_id 不同**；旧实现按 id 判断「当前播放的那条」，
 * 于是把**所有版本**都替换成 /videos/{id} 的单条主路径，每个版本显示的路径一模一样。
 *
 * 这里改为以 locations 为权威数据源逐条展开：每条用自己的 relative_path + 目录绝对路径 +
 * location_id；/jav 内嵌的 videos 只用来按 location_id 补充该文件各自的体积 / 时长 /
 * 播放次数 / 观看时长。展开结果每条自带准确的 location_id，重命名 / 删除也不必再反查。
 */
fun expandFileVersions(
    video: VideoDto?,
    metaVideos: List<VideoDto> = emptyList(),
): List<VideoDto> {
    if (video == null) return metaVideos
    val locations = video.locations
    if (locations.isEmpty()) return fallbackFileVersions(video, metaVideos)
    return locations
        .mapNotNull { loc -> expandOne(video, loc, metaVideos) }
        .ifEmpty { fallbackFileVersions(video, metaVideos) }
}

private fun expandOne(
    video: VideoDto,
    loc: VideoLocationDto,
    metaVideos: List<VideoDto>,
): VideoDto? {
    val rel = loc.relativePath.trim()
    val name = loc.resolvedFilename
    // 按 location_id → 相对路径 → 文件名 的优先级找 /jav 内嵌的那条统计信息
    val meta = metaVideos.firstOrNull { loc.id > 0 && it.locationId == loc.id }
        ?: metaVideos.firstOrNull { it.path.trim() == rel }
        ?: metaVideos.firstOrNull { name.isNotBlank() && it.filename.equals(name, ignoreCase = true) }

    // 目录绝对路径：location 自带优先；只给 relative_path + directory_id 时，
    // 依次用 /jav 内嵌同路径条目、/videos/{id} 顶层目录兜底，避免整条被丢掉
    val root = loc.resolvedDirectoryPath.trim().ifBlank {
        (
            meta?.directory?.path?.takeIf { it.isNotBlank() }
                ?: meta?.directoryPath?.takeIf { it.isNotBlank() }
                ?: video.directory?.path?.takeIf { it.isNotBlank() }
                ?: video.directoryPath
            ).trim()
    }
    if (rel.isBlank() || root.isBlank()) return null

    val directoryId = loc.directory?.id ?: loc.directoryRef?.id
        ?: meta?.directory?.id ?: video.directory?.id ?: 0L

    return video.copy(
        id = video.id,
        locationId = loc.id,
        path = rel,
        directoryPath = root,
        // fullPath 优先读 directory.path；必须换成这一条自己的目录，
        // 否则沿用主文件的 directory 会把所有版本又拼成同一个路径
        directory = DirectoryRefDto(id = directoryId, path = root),
        filename = name.ifBlank { video.filename },
        size = meta?.size ?: video.size,
        durationSec = meta?.durationSec ?: video.durationSec,
        playCount = meta?.playCount ?: video.playCount,
        watchedMs = meta?.watchedMs ?: video.watchedMs,
    )
}

private fun fallbackFileVersions(video: VideoDto, metaVideos: List<VideoDto>): List<VideoDto> =
    // 没有 locations 时退回 /jav 内嵌的 videos：它本身就是「每个文件一条」且各有自己的路径，
    // 原样返回即可，绝不能再按 id 整体替换成 /videos/{id} 的单条数据。
    metaVideos.ifEmpty { listOf(video) }
