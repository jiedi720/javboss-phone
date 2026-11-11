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
     * 作品卡封面左下角番号正上方要显示的自定义标签。
     * 保留 TagDto（带 id），这样点标签时能直接用详情页同款方式按 tag_id 筛选作品。
     * 保持服务端返回顺序、过滤空名、按名字去重（同名标签只显示一次，保留第一个的 id）。
     */
    fun customTags(tags: List<TagDto>): List<TagDto> =
        tags.filter { isCustom(it) && it.name.isNotBlank() }
            .distinctBy { it.name.trim() }

    /** 只取名字（收藏夹内页早期实现用，保留兼容） */
    fun customTagNames(tags: List<TagDto>): List<String> =
        customTags(tags).map { it.name }
}
