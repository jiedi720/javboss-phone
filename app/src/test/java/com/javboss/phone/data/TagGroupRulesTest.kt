package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagCategoryDto
import com.javboss.phone.data.api.dto.TagDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v0.3.63：标签管理弹层按服务端分类分组显示的纯规则回归。
 * 对齐 Web bundle 标签管理面板 `mq` 的 Xe（标签排序）/ Ge（分类排序）/ Ze（分组）三个 useMemo。
 */
class TagGroupRulesTest {

    private fun tag(id: Long, name: String, count: Int = 0, category: String = "") =
        TagDto(id = id, name = name, count = count, category = category)

    private fun category(id: Long, name: String, order: Int, isDefault: Boolean = false) =
        TagCategoryDto(id = id, name = name, sortOrder = order, isDefault = isDefault)

    @Test
    fun `JAV and VIDEO category endpoints stay on separate tables`() {
        assertEquals("jav/tag-categories", TagGroupRules.categoryListPath(TagScope.JAV))
        assertEquals("tags/categories", TagGroupRules.categoryListPath(TagScope.VIDEO))
        assertEquals("jav/tag-categories", TagManageRules.categoryListPath(TagScope.JAV))
        assertEquals("tags/categories", TagManageRules.categoryListPath(TagScope.VIDEO))
    }

    @Test
    fun `sortTags orders by count desc then name then id`() {
        val sorted = TagGroupRules.sortTags(
            listOf(
                tag(3, "B", 5),
                tag(1, "A", 5),
                tag(2, "A", 9),
                tag(4, "C", 0),
            ),
        )
        assertEquals(listOf(2L, 1L, 3L, 4L), sorted.map { it.id })
    }

    @Test
    fun `orderedCategories appends synthetic default with first unused sort order`() {
        val ordered = TagGroupRules.orderedCategories(
            listOf(category(1, "主题", 0), category(2, "角色", 2)),
        )
        assertEquals(listOf("主题", "默认分类", "角色"), ordered.map { it.name })
        val default = ordered.first { it.isDefault }
        assertEquals(0L, default.id)
        assertEquals(1, default.sortOrder)
    }

    @Test
    fun `orderedCategories breaks equal sort order by id`() {
        val ordered = TagGroupRules.orderedCategories(
            listOf(category(9, "后", 0), category(2, "先", 0)),
        )
        // 两个真实分类 sort_order=0 → 合成默认分类取未占用的 1，排在它们之后
        assertEquals(listOf(2L, 9L, 0L), ordered.map { it.id })
    }

    @Test
    fun `tags with blank category land in Default group`() {
        val groups = TagGroupRules.groupTags(
            listOf(tag(1, "甲", 3), tag(2, "乙", 1, "  ")),
            emptyList(),
        )
        assertEquals(1, groups.size)
        assertEquals("默认分类", groups.single().label)
        assertEquals(listOf(1L, 2L), groups.single().tags.map { it.id })
    }

    @Test
    fun `groups follow server category order and unknown categories go last`() {
        val groups = TagGroupRules.groupTags(
            listOf(
                tag(1, "甲", 1, "角色"),
                tag(2, "乙", 1, "主题"),
                tag(3, "丙", 1, "未知"),
                tag(4, "丁", 1, ""),
            ),
            listOf(category(1, "主题", 0), category(2, "角色", 1)),
        )
        assertEquals(listOf("主题", "角色", "默认分类", "未知"), groups.map { it.label })
    }

    @Test
    fun `unknown groups are ordered by category name`() {
        val groups = TagGroupRules.groupTags(
            listOf(tag(1, "甲", 1, "B"), tag(2, "乙", 1, "A"), tag(3, "丙", 1, "")),
            emptyList(),
        )
        assertEquals(listOf("默认分类", "A", "B"), groups.map { it.label })
    }

    @Test
    fun `default group takes its server slot instead of always being first`() {
        val groups = TagGroupRules.groupTags(
            listOf(tag(1, "甲", 1, ""), tag(2, "乙", 1, "主题")),
            listOf(category(1, "主题", 0), category(2, "角色", 1)),
        )
        assertEquals(listOf("主题", "默认分类"), groups.map { it.label })
    }

    @Test
    fun `groupTags keeps Web tag order inside each group`() {
        val groups = TagGroupRules.groupTags(
            listOf(
                tag(3, "低", 1, "主题"),
                tag(1, "高", 9, "主题"),
                tag(2, "中", 5, "主题"),
            ),
            listOf(category(1, "主题", 0)),
        )
        assertEquals(listOf(1L, 2L, 3L), groups.single().tags.map { it.id })
    }
}
