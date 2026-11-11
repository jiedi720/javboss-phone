package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto

/**
 * 标签配色 / 自定义标签判定的纯规则（不依赖 Compose / Android，可在 JVM 单测回归）。
 *
 * 反查 Web bundle：`function tt(e){return Number(e?.provider)===3}`，标签 chip 渲染时
 *  - 自定义标签（provider=3）→ `bg-emerald-200 text-emerald-700`（蓝绿色）
 *  - 固有刮削标签（其余）  → `bg-orange-200 text-orange-700`（橘色）
 * 图例也一致：「自定义标签」`bg-emerald-100`、「刮削标签」`bg-orange-100`。
 * 服务端**没有** is_custom / custom_tag / tag_type 字段，唯一判据就是 provider。
 */
object TagColorRules {
    const val CUSTOM_PROVIDER = 3

    fun isCustom(provider: Int): Boolean = provider == CUSTOM_PROVIDER

    fun isCustom(tag: TagDto): Boolean = isCustom(tag.provider)

    /**
     * 作品卡封面左下角番号正上方要显示的自定义标签名。
     * 保持服务端返回顺序、过滤空名、按名字去重（视频 tags 与 jav.tags 合并时可能重复）。
     */
    fun customTagNames(tags: List<TagDto>): List<String> =
        tags.filter { isCustom(it) && it.name.isNotBlank() }
            .map { it.name }
            .distinct()
}
