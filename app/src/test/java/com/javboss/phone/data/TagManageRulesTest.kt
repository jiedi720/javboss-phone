package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.62：标签管理 / 标签筛选纯规则回归。
 * 服务端 JAV 标签与视频标签是两张独立表，端点不能混用；
 * /videos?tags= 是逗号分隔的标签名；详情页点标签的标题是「标签：XXX」。
 */
class TagManageRulesTest {

    @Test
    fun `JAV and VIDEO tag endpoints stay on separate tables`() {
        assertEquals("jav/tags", TagManageRules.listPath(TagScope.JAV))
        assertEquals("tags", TagManageRules.listPath(TagScope.VIDEO))
        assertEquals("jav/tags/12", TagManageRules.itemPath(TagScope.JAV, 12))
        assertEquals("tags/12", TagManageRules.itemPath(TagScope.VIDEO, 12))
    }

    @Test
    fun `scope labels are the two management dialog titles`() {
        assertEquals("JAV 标签", TagScope.JAV.label)
        assertEquals("视频标签", TagScope.VIDEO.label)
    }

    @Test
    fun `filterTitle matches detail page tag click`() {
        assertEquals("标签：高清", TagManageRules.filterTitle("高清"))
        assertEquals("标签：高清", TagManageRules.filterTitle("  高清  "))
    }

    @Test
    fun `videoTagQuery trims drops blanks and dedupes`() {
        assertEquals("A,B", TagManageRules.videoTagQuery(listOf(" A ", "B", "", "  ", "A")))
        assertEquals("", TagManageRules.videoTagQuery(emptyList()))
        assertEquals("", TagManageRules.videoTagQuery(listOf("", "   ")))
        assertEquals("A,B,C", TagManageRules.videoTagQuery(listOf("A", "B", "C")))
    }

    @Test
    fun `validName rejects blank names before hitting the server`() {
        assertTrue(TagManageRules.validName("  新品  "))
        assertFalse(TagManageRules.validName(""))
        assertFalse(TagManageRules.validName("   "))
    }

    @Test
    fun `customTags keeps TagDto ids so cover badges can filter works`() {
        val tags = listOf(
            TagDto(1, "刮削", provider = 1),
            TagDto(2, "自定A", provider = 3),
            TagDto(3, "自定B", provider = 3),
        )
        val custom = TagColorRules.customTags(tags)
        assertEquals(listOf(2L, 3L), custom.map { it.id })
        assertEquals(listOf("自定A", "自定B"), custom.map { it.name })
        // 兼容旧调用：只取名字
        assertEquals(listOf("自定A", "自定B"), TagColorRules.customTagNames(tags))
    }

    @Test
    fun `customTags dedupes same-name tags and keeps the first TagDto id`() {
        val byId = TagColorRules.customTags(
            listOf(TagDto(7, "同名", provider = 3), TagDto(8, "同名", provider = 3))
        )
        assertEquals(1, byId.size)
        assertEquals(7L, byId.first().id)

        val noId = TagColorRules.customTags(
            listOf(TagDto(0, "同名", provider = 3), TagDto(0, "同名", provider = 3))
        )
        assertEquals(1, noId.size)
    }

    // ---------- v0.3.65：分类管理端点 / 多选查找 ----------

    @Test
    fun `category management endpoints stay on the two separate tables`() {
        assertEquals("jav/tag-categories", TagManageRules.categoryListPath(TagScope.JAV))
        assertEquals("tags/categories", TagManageRules.categoryListPath(TagScope.VIDEO))
        assertEquals(
            "jav/tag-categories/order",
            TagManageRules.categoryOrderPath(TagScope.JAV),
        )
        assertEquals("tags/categories/order", TagManageRules.categoryOrderPath(TagScope.VIDEO))
        assertEquals(
            "jav/tag-categories/7",
            TagManageRules.categoryItemPath(TagScope.JAV, 7),
        )
        assertEquals("tags/categories/7", TagManageRules.categoryItemPath(TagScope.VIDEO, 7))
        assertEquals("jav/tags/category", TagManageRules.categorizePath(TagScope.JAV))
        assertEquals("tags/category", TagManageRules.categorizePath(TagScope.VIDEO))
    }

    @Test
    fun `multiFilterTitle joins clean tag names`() {
        assertEquals("标签：体位、身材", TagManageRules.multiFilterTitle(listOf(" 体位 ", "身材")))
        assertEquals("标签：A、B", TagManageRules.multiFilterTitle(listOf("A", "", "A", "B")))
        assertEquals("", TagManageRules.multiFilterTitle(emptyList()))
        assertEquals("标签：A", TagManageRules.multiFilterTitle(listOf("A")))
    }

    @Test
    fun `javTagIdsQuery builds a comma-separated tag_ids list`() {
        val tags = listOf(TagDto(3, "A"), TagDto(0, "无 id"), TagDto(5, "B"), TagDto(3, "重复"))
        assertEquals("3,5", TagManageRules.javTagIdsQuery(tags))
        assertEquals("", TagManageRules.javTagIdsQuery(emptyList()))
    }
}
