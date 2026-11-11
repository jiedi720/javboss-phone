package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagCategoryDto
import com.javboss.phone.data.api.dto.TagDto

/**
 * 标签管理弹层的一个分组：[name] 是服务端分类名（空串＝默认分类），
 * [label] 是界面上显示的标题。
 */
data class TagGroup(
    val name: String,
    val tags: List<TagDto>,
) {
    val label: String get() = if (name.isEmpty()) TagGroupRules.DEFAULT_LABEL else name
}

/**
 * 标签按服务端分类分组显示的纯规则（对齐 Web bundle 标签管理面板 `mq` 的三个 useMemo）：
 *
 *  - `Xe`：标签排序 → count 降序、名字升序、id 升序。
 *  - `Ge`：分类列表按 `sort_order`（缺失按 0）升序、同序按 id；末尾补一个合成的
 *    「默认分类」（id=0，sort_order 取未被占用的最小非负整数）。
 *  - `Ze`：按标签的 `category` 字符串（trim，空串＝默认分类）建 Map 分组；
 *    分组顺序＝分类在服务端分类列表中的序号，未知分类排最后，同序按分类名。
 *
 * 纯 Kotlin，不依赖 Android / Compose，可在 JVM 单测回归。
 */
object TagGroupRules {
    const val DEFAULT_LABEL = "默认分类"
    const val DEFAULT_CATEGORY_ID = 0L

    /** 分类列表端点：JAV 与视频是两套独立表，不能混用 */
    fun categoryListPath(scope: TagScope): String = TagManageRules.categoryListPath(scope)

    /** Web `Xe`：count 降序 → 名字 → id */
    fun sortTags(tags: List<TagDto>): List<TagDto> =
        tags.sortedWith(
            compareByDescending<TagDto> { it.count }
                .thenBy { it.name }
                .thenBy { it.id },
        )

    /** Web `Ge`：服务端分类 + 合成默认分类，按 sort_order / id 排序 */
    fun orderedCategories(categories: List<TagCategoryDto>): List<TagCategoryDto> {
        val used = categories.map { it.sortOrder }.filter { it >= 0 }.toHashSet()
        var next = 0
        while (used.contains(next)) next++
        val withDefault = categories + TagCategoryDto(
            id = DEFAULT_CATEGORY_ID,
            name = DEFAULT_LABEL,
            sortOrder = next,
            isDefault = true,
        )
        return withDefault.sortedWith(
            compareBy<TagCategoryDto> { it.sortOrder }.thenBy { it.id },
        )
    }

    /** 分类名（默认分类用空串）→ 在 [orderedCategories] 结果中的序号；重复名取靠后项 */
    private fun categoryOrder(ordered: List<TagCategoryDto>): Map<String, Int> =
        ordered.mapIndexed { index, category ->
            (if (category.isDefault) "" else category.name.trim()) to index
        }.toMap()

    /** Web `Ze`：分组 + 按服务端分类顺序排序 */
    fun groupTags(
        tags: List<TagDto>,
        categories: List<TagCategoryDto>,
    ): List<TagGroup> {
        val sorted = sortTags(tags)
        val ordered = orderedCategories(categories)
        val order = categoryOrder(ordered)
        val unknownOrder = ordered.size

        val buckets = LinkedHashMap<String, MutableList<TagDto>>()
        for (tag in sorted) {
            val key = tag.category.trim()
            buckets.getOrPut(key) { mutableListOf() }.add(tag)
        }
        return buckets.map { (name, groupTags) -> TagGroup(name, groupTags) }
            .sortedWith(
                compareBy<TagGroup> { order[it.name] ?: unknownOrder }
                    .thenBy { it.name },
            )
    }
}
