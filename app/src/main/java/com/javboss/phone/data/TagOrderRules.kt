package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto

/**
 * 作品标签的展示顺序与「哪些标签可以提交」的纯规则（v0.3.70 引入，v0.3.71 定稿顺序）。
 *
 * 背景：
 * - 服务端 `GET /jav/tags` 与作品里的 `jav.tags` 都是**刮削标签在前、用户自定义标签在后**
 *   （`ListJavTags` 先拼 scrapedTags 再拼 userTags）。用户要的是反过来：
 *   **自定义（provider=3，绿色胶囊）排最前**，刮削（橙色）排在后面。组内保持服务端原顺序。
 * - `PUT /jav/items/{id}` 的 `tag_ids` 只接受 `is_user = true` 的标签，
 *   混入刮削标签 id 会被服务端判为 `invalid tag_id` 整单失败（保存报错）。
 *   编辑标签弹窗因此只把这些标签作为可提交项。
 */
object TagOrderRules {

    /** 展示顺序：自定义标签在前，刮削标签在后；组内保持原顺序 */
    fun customFirst(tags: List<TagDto>): List<TagDto> {
        val custom = tags.filter { TagColorRules.isCustom(it) }
        val scraped = tags.filterNot { TagColorRules.isCustom(it) }
        return custom + scraped
    }

    /**
     * 可提交给 `PUT /jav/items/{id}` 的用户标签（provider=3）。
     * id <= 0 的占位标签一并剔除，避免提交非法 id。
     */
    fun userTags(tags: List<TagDto>): List<TagDto> =
        tags.filter { TagColorRules.isCustom(it) && it.id > 0 }

    /** 只读展示的刮削标签（provider != 3），保持原顺序 */
    fun scrapedTags(tags: List<TagDto>): List<TagDto> =
        tags.filterNot { TagColorRules.isCustom(it) }
}
