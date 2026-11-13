package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto

/**
 * 作品标签的展示顺序与「哪些标签可以提交」的纯规则（v0.3.70）。
 *
 * 背景：
 * - 服务端 `jav.tags` 把**用户自定义标签**（provider=3）排在刮削标签前面返回，
 *   详情页直接按数组顺序渲染，于是自定义标签总是挤在最前。展示时改为
 *   **刮削标签在前、自定义标签在后**，组内保持服务端原顺序。
 * - `PUT /jav/items/{id}` 的 `tag_ids` 只接受 `is_user = true` 的标签，
 *   混入刮削标签 id 会被服务端判为 `invalid tag_id` 整单失败（保存报错）。
 *   编辑标签弹窗因此只呈递用户标签。
 */
object TagOrderRules {

    /** 展示顺序：刮削（非自定义）标签在前，自定义标签在后；组内保持原顺序 */
    fun scrapedFirst(tags: List<TagDto>): List<TagDto> {
        val scraped = tags.filterNot { TagColorRules.isCustom(it) }
        val custom = tags.filter { TagColorRules.isCustom(it) }
        return scraped + custom
    }

    /**
     * 可提交给 `PUT /jav/items/{id}` 的用户标签（provider=3）。
     * id <= 0 的占位标签一并剔除，避免提交非法 id。
     */
    fun userTags(tags: List<TagDto>): List<TagDto> =
        tags.filter { TagColorRules.isCustom(it) && it.id > 0 }
}
