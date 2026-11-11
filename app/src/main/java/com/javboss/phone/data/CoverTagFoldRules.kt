package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto

/**
 * 封面自定义标签按服务端分类折叠（v0.3.64）。
 *
 * 用户反馈：封面上同一分类（如「体位」）下有多个自定义标签时不要全部铺开，
 * 只显示一个「体位」分类胶囊，点击后展开显示具体标签；若该分类只有一个标签，
 * 则直接显示该具体标签（而不是分类名）。
 *
 * 规则：
 *  - 只按 [TagDto.category]（trim 后）折叠「自定义标签」（provider=3，见 [TagColorRules]）；
 *    空分类（默认分类）与刮削标签不折叠，各自作为单项显示。
 *  - 同一非空分类下 >= 2 个自定义标签 → 合成一个 [CoverTagItem.isGroup] 分类胶囊，
 *    [CoverTagItem.children] 保留该分类全部标签（顺序＝服务端返回顺序）；
 *    只有 1 个 → 退化为普通单项，显示具体标签名。
 *  - 折叠胶囊放在该分类第一次出现的位置，其余成员跳过，尽量保持原有堆叠顺序。
 *  - 过滤空名、按名字去重（与 [TagColorRules.customTags] 同语义）。
 *
 * 纯 Kotlin，不依赖 Android / Compose，可在 JVM 单测回归。
 */
data class CoverTagItem(
    /** 胶囊上显示的文字：具体标签名，或折叠后的分类名 */
    val label: String,
    /** 单项标签：非 null 表示点击直接按该标签筛选 */
    val tag: TagDto? = null,
    /** 折叠项：展开后显示的具体标签 */
    val children: List<TagDto> = emptyList(),
    /** 配色：自定义标签蓝绿、刮削标签橘色；分类胶囊属于自定义标签聚合 */
    val custom: Boolean = true,
) {
    /** 是否为「点击展开」的分类胶囊 */
    val isGroup: Boolean get() = tag == null && children.isNotEmpty()

    /** 展开状态按分类名（＝胶囊文字）记录 */
    val expandKey: String get() = label
}

object CoverTagFoldRules {
    fun fold(tags: List<TagDto>): List<CoverTagItem> {
        val seen = HashSet<String>()
        val clean = tags.filter { it.name.isNotBlank() && seen.add(it.name.trim()) }

        // 统计每个非空分类下的自定义标签（LinkedHashMap 保持分类首次出现顺序）
        val groups = LinkedHashMap<String, MutableList<TagDto>>()
        for (t in clean) {
            if (TagColorRules.isCustom(t)) {
                val key = t.category.trim()
                if (key.isNotEmpty()) groups.getOrPut(key) { mutableListOf() }.add(t)
            }
        }

        val result = mutableListOf<CoverTagItem>()
        val emitted = HashSet<String>()
        for (t in clean) {
            val key = if (TagColorRules.isCustom(t)) t.category.trim() else ""
            if (key.isEmpty()) {
                // 默认分类 / 刮削标签：不折叠，直接显示具体标签
                result.add(CoverTagItem(label = t.name, tag = t, custom = t.isCustom))
            } else if (emitted.add(key)) {
                val members = groups.getValue(key)
                if (members.size >= 2) {
                    // 同分类多个标签 → 只显示分类名，点击后展开 children
                    result.add(CoverTagItem(label = key, children = members, custom = true))
                } else {
                    // 只有一个 → 直接显示具体标签名
                    result.add(CoverTagItem(label = t.name, tag = t, custom = t.isCustom))
                }
            }
            // 同分类的其余成员已被折叠，跳过
        }
        return result
    }
}
