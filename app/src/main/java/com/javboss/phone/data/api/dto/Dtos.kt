package com.javboss.phone.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 与服务端 Gin API 对应的 DTO。
 * 只声明客户端用到的字段；Json 配置里开启了 ignoreUnknownKeys。
 * 所有字段都给默认值或可空，保证服务端版本演进后新增/缺失字段不崩。
 */

@Serializable
data class AuthStatusResponse(
    val authenticated: Boolean = false,
)

@Serializable
data class LoginRequest(
    val password: String,
)

@Serializable
data class LoginResponse(
    val authenticated: Boolean = false,
)

@Serializable
data class ApiErrorResponse(
    @SerialName("error_zh") val errorZh: String = "",
    @SerialName("error_en") val errorEn: String = "",
) {
    val displayMessage: String get() = errorZh.ifBlank { errorEn.ifBlank { "请求失败" } }
}

@Serializable
data class VideoListResponse(
    val items: List<VideoDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class VideoDto(
    val id: Long = 0,
    val filename: String = "",
    val size: Long = 0,
    /** 相对于所属读取目录的路径 */
    val path: String = "",
    /** 部分接口只在扁平字段里给目录绝对路径 */
    @SerialName("directory_path") val directoryPath: String = "",
    /** 所属读取目录 */
    val directory: DirectoryRefDto? = null,
    /** /jav 内嵌的 videos 会给 directory_id，扁平接口只给 directory.id */
    @SerialName("directory_id") val directoryId: Long = 0,
    @SerialName("duration_sec") val durationSec: Long = 0,
    @SerialName("play_count") val playCount: Long = 0,
    /** 服务端统计的该文件累计观看时长（毫秒） */
    @SerialName("watched_ms") val watchedMs: Long = 0,
    /** 文件位置 id：/jav 内嵌的 videos 是「每个文件一条」，改名/删除要用它 */
    @SerialName("location_id") val locationId: Long = 0,
    val jav: JavDto? = null,
    val tags: List<TagDto> = emptyList(),
    /** /videos/{id} 会给出该视频的全部文件位置；详情页按它逐条展开每个文件版本 */
    val locations: List<VideoLocationDto> = emptyList(),
) {
    /** 目录筛选用的 id：优先 directory_id，退回内嵌 directory.id（对齐 Web 端卡片） */
    val effectiveDirectoryId: Long
        get() = if (directoryId > 0) directoryId else directory?.id ?: 0L

    /**
     * 所在子目录（Web 端视频卡片第二行）：path 去掉文件名，
     * 同时兼容 / 与 \ 分隔符；根目录没有子目录时用 "."。
     */
    val subdirectory: String
        get() {
            val rel = path.trim()
            if (rel.isBlank()) return ""
            return rel.split('/', '\\').dropLast(1).joinToString("/").ifEmpty { "." }
        }

    /** 展示用标题：优先 JAV 番号 + 标题，否则文件名（去掉扩展名） */
    val displayTitle: String
        get() = jav?.let { "${it.code} ${it.title}" }?.trim()
            ?: run {
                val dot = filename.lastIndexOf('.')
                if (dot > 0) filename.substring(0, dot) else filename
            }

    /** 完整文件路径：目录绝对路径 + 相对路径，缺任意一半就退化成有的那个 */
    val fullPath: String
        get() {
            val root = directory?.path?.takeIf { it.isNotBlank() }
                ?: directoryPath.takeIf { it.isNotBlank() }
                ?: ""
            val rel = path.trim()
            return when {
                root.isBlank() -> rel
                rel.isBlank() -> root
                rel.startsWith("/") -> root.trimEnd('/') + rel
                else -> root.trimEnd('/') + "/" + rel
            }
        }
}

/** 视频里内嵌的目录引用（只需要路径） */
@Serializable
data class DirectoryRefDto(
    val id: Long = 0,
    val path: String = "",
)

/** 服务端「读取路径」：一个被扫描的媒体目录 */
@Serializable
data class DirectoryDto(
    val id: Long = 0,
    val path: String = "",
    @SerialName("is_delete") val isDelete: Boolean = false,
    @SerialName("scanned_video_count") val scannedVideoCount: Int = 0,
    @SerialName("scraped_video_count") val scrapedVideoCount: Int = 0,
    @SerialName("is_scanning") val isScanning: Boolean = false,
    val enabled: Boolean = true,
)

@Serializable
data class DirectoryCreateRequest(val path: String)

@Serializable
data class DirectoryUpdateRequest(
    val path: String? = null,
    val enabled: Boolean? = null,
    @SerialName("is_delete") val isDelete: Boolean? = null,
)

@Serializable
data class JavDto(
    val id: Long = 0,
    val code: String = "",
    val title: String = "",
    @SerialName("title_en") val titleEn: String = "",
    @SerialName("release_unix") val releaseUnix: Long = 0,
    @SerialName("duration_min") val durationMin: Int = 0,
    @SerialName("is_uncensored") val isUncensored: Boolean = false,
    @SerialName("favorite_count") val favoriteCount: Int = 0,
    @SerialName("series_id") val seriesId: Long? = null,
    /**
     * 封面横向裁剪位置 0..1（Web 端手动调过的那一项，对应 CSS object-position 的 X%）。
     * 1 = 最右（默认，多数包装图正面在右），null/缺失时按默认处理。
     */
    @SerialName("cover_crop_position") val coverCropPosition: Double? = null,
    /** 喜爱度评分 0..5，步长 0.5（Web 端封面左上角那排星）；0 表示未评分 */
    @SerialName("favorite_rating") val favoriteRating: Double = 0.0,
    /** 服务端统计的该作品累计观看时长（毫秒，所有文件版本累加） */
    @SerialName("watched_ms") val watchedMs: Long = 0,
    val studio: StudioDto? = null,
    val tags: List<TagDto> = emptyList(),
    val idols: List<IdolDto> = emptyList(),
    val videos: List<VideoDto> = emptyList(),
)

/** /jav/prefixes 返回项：番号前缀及其归属片商、作品数 */
@Serializable
data class PrefixDto(
    val prefix: String = "",
    @SerialName("studio_id") val studioId: Long = 0,
    @SerialName("studio_name") val studioName: String = "",
    @SerialName("work_count") val workCount: Int = 0,
    @SerialName("sample_code") val sampleCode: String = "",
)

@Serializable
data class JavListResponse(
    val items: List<JavDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class StudioDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("work_count") val workCount: Int = 0,
    @SerialName("sample_code") val sampleCode: String = "",
)

@Serializable
data class IdolDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("roman_name") val romanName: String = "",
    @SerialName("japanese_name") val japaneseName: String = "",
    @SerialName("chinese_name") val chineseName: String = "",
    @SerialName("cover_code") val coverCode: String = "",
    /**
     * 女优封面左移比例 0..1（Web 端 `cover_crop_left`）：封面按高度铺满后，
     * 从图片宽度的这个比例处开始显示；缺失时 Web 用 0.47。
     */
    @SerialName("cover_crop_left") val coverCropLeft: Double? = null,
    @SerialName("work_count") val workCount: Int = 0,
    /**
     * 被加入女优收藏夹的次数（Web 端女优卡封面右上角那颗实心爱心的判定依据，
     * 字段与 /jav 的 `favorite_count` 同名同义）。缺失时按 0 处理 = 不显示徽标。
     */
    @SerialName("favorite_count") val favoriteCount: Int = 0,
    @SerialName("height_cm") val heightCm: Int = 0,
    @SerialName("birth_date") val birthDate: String = "",
    // 以下四项与 Web 端女优卡片封面下方的数据行同源（jav_idol.bust/waist/hips/cup，缺失时为 0）
    val bust: Int = 0,
    val waist: Int = 0,
    val hips: Int = 0,
    /** 罩杯数字：1=A、2=B … 11=K（Web 端用 String.fromCharCode(64+n) 换算） */
    val cup: Int = 0,
    val aliases: List<String> = emptyList(),
) {
    val displayName: String get() = chineseName.ifBlank { name }

    /** 主名右侧的副名（Web 端 name 后面那个灰色小字） */
    val secondaryName: String
        get() = listOf(japaneseName, romanName)
            .firstOrNull { it.isNotBlank() && it != displayName }
            .orEmpty()
}

@Serializable
data class IdolListResponse(
    val items: List<IdolDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class StudioListResponse(
    val items: List<StudioDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class SeriesDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("studio_name") val studioName: String = "",
    @SerialName("work_count") val workCount: Int = 0,
    @SerialName("sample_code") val sampleCode: String = "",
)

@Serializable
data class SeriesListResponse(
    val items: List<SeriesDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class TagDto(
    val id: Long = 0,
    val name: String = "",
    val count: Int = 0,
    /**
     * 标签来源。Web 端 `tt(e) = Number(e?.provider)===3` 用 provider=3 判定「自定义标签」，
     * 其余都是刮削得到的固有标签；/jav 与 /tags 返回的 tag 都带这个字段
     * （Web 标签 chip 的 key 里就有 `e.provider||0`）。缺省 0 兼容老服务端。
     */
    val provider: Int = 0,
    /** 服务端标签分类名：Web 端按 `String(t?.category||"").trim()` 分组，空串＝默认分类 */
    val category: String = "",
    /** 服务端标签分类 id；0 / 缺失表示默认分类 */
    @SerialName("category_id") val categoryId: Long = 0,
) {
    val isCustom: Boolean get() = provider == 3
}

/** 标签分类：GET `/jav/tag-categories` 与 GET `/tags/categories` 都返回分类数组。 */
@Serializable
data class TagCategoryDto(
    val id: Long = 0,
    val name: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_default") val isDefault: Boolean = false,
)

@Serializable
data class StreamsResponse(
    @SerialName("video_id") val videoId: Long = 0,
    @SerialName("preferred_kind") val preferredKind: String = "direct",
    /** 本次播放命中的文件位置（video_location.id）；Web 端建播放会话时带的就是它 */
    @SerialName("location_id") val locationId: Long = 0,
    val sources: List<StreamSourceDto> = emptyList(),
)

// ---------- 播放会话（/videos/{id}/playback-sessions） ----------
// 服务端按「会话内累计观看毫秒数」记账，Web 端就是这么报的，手机端口径保持一致。

@Serializable
data class PlaybackSessionRequest(
    @SerialName("location_id") val locationId: Long = 0,
)

@Serializable
data class PlaybackSessionResponse(
    @SerialName("session_id") val sessionId: String = "",
)

@Serializable
data class PlaybackSessionReportRequest(
    @SerialName("watched_ms") val watchedMs: Long = 0,
)

/** 重命名某个文件位置：只传新文件名，不含路径与扩展名之外的目录部分 */
@Serializable
data class VideoLocationRenameRequest(
    val filename: String = "",
)

@Serializable
data class StreamSourceDto(
    val kind: String = "",
    val src: String = "",
    @SerialName("mime_type") val mimeType: String = "",
    val label: String = "",
)

@Serializable
data class ScreenshotDto(
    val name: String = "",
    @SerialName("is_cover") val isCover: Boolean = false,
)

@Serializable
data class ScreenshotListResponse(
    val items: List<ScreenshotDto> = emptyList(),
)

// ---------- 收藏夹（/jav/jav-favorite-groups） ----------

/**
 * 文件位置：/videos/{id} 的 locations 数组元素。
 * 一个 video_id 可以挂多个 location（同一部作品的不同文件版本散落在不同读取目录），
 * 每条各带自己的 relative_path + 目录绝对路径，这才是详情页展开文件列表的权威数据源。
 */
@Serializable
data class VideoLocationDto(
    val id: Long = 0,
    val filename: String = "",
    @SerialName("relative_path") val relativePath: String = "",
    @SerialName("directory_path") val directoryPath: String = "",
    /** 部分接口把目录作为内嵌对象返回（Web 端读 directory.path / directory_ref.path） */
    val directory: DirectoryRefDto? = null,
    @SerialName("directory_ref") val directoryRef: DirectoryRefDto? = null,
) {
    /** 目录绝对路径：内嵌目录对象优先，最后退回扁平字段 */
    val resolvedDirectoryPath: String
        get() = directory?.path?.takeIf { it.isNotBlank() }
            ?: directoryRef?.path?.takeIf { it.isNotBlank() }
            ?: directoryPath

    /** 文件名：服务端给了就用，否则从相对路径取最后一段 */
    val resolvedFilename: String
        get() = filename.takeIf { it.isNotBlank() }
            ?: relativePath.trim().split('/', '\\').lastOrNull().orEmpty()
}

@Serializable
data class FavoriteGroupDto(
    val id: Long = 0,
    @SerialName("entity_type") val entityType: String = "",
    val name: String = "",
    val count: Long = 0,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class FavoriteGroupListResponse(
    val items: List<FavoriteGroupDto> = emptyList(),
)

@Serializable
data class FavoriteItemDto(
    val id: Long = 0,
    @SerialName("entity_type") val entityType: String = "",
    val name: String = "",
    @SerialName("roman_name") val romanName: String = "",
    @SerialName("japanese_name") val japaneseName: String = "",
    @SerialName("chinese_name") val chineseName: String = "",
    val code: String = "",
    val title: String = "",
    @SerialName("title_en") val titleEn: String = "",
    @SerialName("work_count") val workCount: Long = 0,
    /** 女优封面番号：Web 端女优卡用 cover_code 拼 `/jav/{cover_code}/cover` */
    @SerialName("cover_code") val coverCode: String = "",
    @SerialName("sample_code") val sampleCode: String = "",
)

@Serializable
data class FavoriteItemListResponse(
    val items: List<FavoriteItemDto> = emptyList(),
)

@Serializable
data class FavoriteGroupNameRequest(val name: String)

@Serializable
data class SelectedFavoriteGroupsResponse(
    @SerialName("selected_group_ids") val selectedGroupIds: List<Long> = emptyList(),
)

@Serializable
data class ReplaceFavoriteGroupsRequest(
    @SerialName("group_ids") val groupIds: List<Long>,
)

@Serializable
data class RemoveFavoriteItemsRequest(
    @SerialName("entity_ids") val entityIds: List<Long>,
)

// ---------- 标签（/jav/tags） ----------

@Serializable
data class TagNameRequest(val name: String)

// ---------- 标签分类管理（v0.3.65，Web bundle `mn/hn/gn/_n/vn` / `Vr/Hr/Ur/Wr/Gr`） ----------

/** 新建 / 重命名分类：POST .../categories、PATCH .../categories/{id}，body {name} */
@Serializable
data class TagCategoryNameRequest(val name: String)

/** 分类排序：PUT .../categories/order，body {category_ids}（顺序即 sort_order） */
@Serializable
data class TagCategoryOrderRequest(
    @SerialName("category_ids") val categoryIds: List<Long>,
)

/** 批量把标签归入分类：POST .../category，body {tag_ids, category_id} */
@Serializable
data class TagCategoryAssignRequest(
    @SerialName("tag_ids") val tagIds: List<Long>,
    @SerialName("category_id") val categoryId: Long,
)

@Serializable
data class JavTagsReplaceRequest(
    @SerialName("jav_ids") val javIds: List<Long>,
    @SerialName("tag_ids") val tagIds: List<Long>,
)

/**
 * 视频标签全量替换：Web 端视频列表「编辑标签」用的就是
 * POST /videos/tags/replace，tag_ids 传空数组即清空该视频的全部标签。
 * 注意与 /jav/tags 是两套标签，不能混用。
 */
@Serializable
data class VideoTagsReplaceRequest(
    @SerialName("video_ids") val videoIds: List<Long>,
    @SerialName("tag_ids") val tagIds: List<Long>,
)

// ---------- 作品编辑（PUT /jav/items/{id}），全部字段可选 ----------

@Serializable
data class JavItemUpdateRequest(
    val title: String? = null,
    @SerialName("tag_ids") val tagIds: List<Long>? = null,
    @SerialName("idol_ids") val idolIds: List<Long>? = null,
    @SerialName("studio_id") val studioId: Long? = null,
    @SerialName("series_id") val seriesId: Long? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
    /** 喜爱度 0..5（0.5 步长），null 表示不改；传 0 即清空 */
    @SerialName("favorite_rating") val favoriteRating: Double? = null,
)

// ---------- 外部链接（/jav/javdb-url） ----------

@Serializable
data class JavDbUrlResponse(
    val url: String = "",
)
