package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.65：列表接口内嵌 tags 不带 category，靠标签管理端点全量表回填。
 * 这里回归「回填 → 封面按分类折叠」这条此前断掉的数据链路，避免再出现
 * 「写了折叠规则但封面不折叠」的回归。
 */
class TagCategoryRulesTest {

    private fun tag(id: Long, name: String, category: String = "", provider: Int = 3) =
        TagDto(id = id, name = name, category = category, provider = provider)

    @Test
    fun `indexOf skips default category and invalid ids`() {
        val index = TagCategoryRules.indexOf(
            listOf(
                tag(1, "A", "体位"),
                tag(2, "B", ""),
                tag(0, "C", "身材"),
                tag(3, "D", " 主题 "),
            ),
        )
        assertEquals(mapOf(1L to "体位", 3L to "主题"), index)
    }

    @Test
    fun `enrich fills empty category from the full tag table`() {
        val tags = listOf(tag(1, "A"), tag(2, "B"))
        val enriched = TagCategoryRules.enrich(tags, mapOf(1L to "体位"))
        assertEquals("体位", enriched[0].category)
        assertEquals("", enriched[1].category)
        // 只补分类，其它字段（id / name / provider / count）原样保留
        assertEquals(tags[0].copy(category = "体位"), enriched[0])
    }

    @Test
    fun `enrich never overwrites a category already present`() {
        val tags = listOf(tag(1, "A", "原有分类"))
        val enriched = TagCategoryRules.enrich(tags, mapOf(1L to "体位"))
        assertEquals("原有分类", enriched[0].category)
    }

    @Test
    fun `enrich with an empty index returns the original list untouched`() {
        val tags = listOf(tag(1, "A"))
        assertSame(tags, TagCategoryRules.enrich(tags, emptyMap()))
    }

    @Test
    fun `enrich makes cover folding actually collapse same-category tags`() {
        // 模拟服务端：/jav 列表给的是不带 category 的简化 TagDto
        val listTags = listOf(tag(1, "立ちバック"), tag(2, "背面座位"), tag(3, "騎乗位"))
        // /jav/tags 全量表带分类
        val index = TagCategoryRules.indexOf(
            listOf(
                tag(1, "立ちバック", "体位"),
                tag(2, "背面座位", "体位"),
                tag(3, "騎乗位", "体位"),
            ),
        )
        val items = CoverTagFoldRules.fold(TagCategoryRules.enrich(listTags, index))
        assertEquals(1, items.size)
        assertEquals("体位", items[0].label)
        assertTrue(items[0].isGroup)
        assertEquals(listOf(1L, 2L, 3L), items[0].children.map { it.id })
    }

    @Test
    fun `without enrichment the same list tags stay unfolded`() {
        val listTags = listOf(tag(1, "立ちバック"), tag(2, "背面座位"))
        val items = CoverTagFoldRules.fold(listTags)
        // 这就是 v0.3.64 被用户指出「没修正」的现象：规则在，但数据没分类
        assertEquals(2, items.size)
        assertTrue(items.none { it.isGroup })
    }

    @Test
    fun `moveCategory reorders ids for the category order endpoint`() {
        assertEquals(listOf(2L, 1L, 3L), TagCategoryRules.moveCategory(listOf(1, 2, 3), 0, 1))
        assertEquals(listOf(1L, 3L, 2L), TagCategoryRules.moveCategory(listOf(1, 2, 3), 2, 1))
    }

    @Test
    fun `moveCategory ignores out-of-range and no-op moves`() {
        val ids = listOf(1L, 2L)
        assertEquals(ids, TagCategoryRules.moveCategory(ids, 0, 9))
        assertEquals(ids, TagCategoryRules.moveCategory(ids, -1, 1))
        assertEquals(ids, TagCategoryRules.moveCategory(ids, 0, 0))
    }
}
