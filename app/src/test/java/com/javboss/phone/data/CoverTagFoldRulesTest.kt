package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.64：封面自定义标签按服务端分类折叠的纯规则回归。
 * 同一分类多个标签 → 只显示分类名胶囊（children 展开）；单个 → 直接显示具体标签。
 */
class CoverTagFoldRulesTest {

    private fun custom(id: Long, name: String, category: String = "", count: Int = 0) =
        TagDto(
            id = id,
            name = name,
            count = count,
            provider = TagColorRules.CUSTOM_PROVIDER,
            category = category,
        )

    private fun scraped(id: Long, name: String, category: String = "", count: Int = 0) =
        TagDto(id = id, name = name, count = count, provider = 1, category = category)

    @Test
    fun `multiple custom tags in one category collapse into a single group chip`() {
        val items = CoverTagFoldRules.fold(
            listOf(
                custom(1, "立ちバック", "体位", 52),
                custom(2, "背面座位", "体位", 43),
                custom(3, "騎乗位", "体位", 19),
            ),
        )
        assertEquals(1, items.size)
        assertEquals("体位", items[0].label)
        assertTrue(items[0].isGroup)
        assertEquals("体位", items[0].expandKey)
        assertEquals(listOf(1L, 2L, 3L), items[0].children.map { it.id })
        assertNull(items[0].tag)
    }

    @Test
    fun `single tag in a category shows the concrete tag name`() {
        val items = CoverTagFoldRules.fold(listOf(custom(7, "正常位", "体位", 10)))
        assertEquals(1, items.size)
        // 不是「体位」，是具体标签名
        assertEquals("正常位", items[0].label)
        assertFalse(items[0].isGroup)
        assertEquals(7L, items[0].tag?.id)
        assertTrue(items[0].children.isEmpty())
    }

    @Test
    fun `tags without a category are never collapsed`() {
        val items = CoverTagFoldRules.fold(
            listOf(custom(1, "A"), custom(2, "B"), custom(3, "C")),
        )
        assertEquals(listOf("A", "B", "C"), items.map { it.label })
        assertTrue(items.none { it.isGroup })
    }

    @Test
    fun `scraped tags are never collapsed even when they share a category`() {
        val items = CoverTagFoldRules.fold(
            listOf(scraped(1, "出勤", "主题", 70), scraped(2, "花癖", "主题", 53)),
        )
        assertEquals(listOf("出勤", "花癖"), items.map { it.label })
        assertTrue(items.none { it.isGroup })
        assertTrue(items.none { it.custom })
    }

    @Test
    fun `blank names and duplicate names are dropped before grouping`() {
        val items = CoverTagFoldRules.fold(
            listOf(
                custom(1, "A", "体位"),
                custom(2, "A", "体位"),
                TagDto(id = 3, name = "  ", provider = TagColorRules.CUSTOM_PROVIDER, category = "体位"),
            ),
        )
        // 去重 + 过滤空名后，「体位」只剩一个 → 退化为单项
        assertEquals(1, items.size)
        assertEquals("A", items[0].label)
        assertFalse(items[0].isGroup)
        assertEquals(1L, items[0].tag?.id)
    }

    @Test
    fun `fold keeps first-appearance order across mixed tags`() {
        val items = CoverTagFoldRules.fold(
            listOf(
                scraped(1, "出勤", "主题", 70),
                custom(2, "立ちバック", "体位", 52),
                custom(3, "笑顔", "身材", 6),
                custom(4, "背面座位", "体位", 43),
                custom(5, "奶子", "身材", 5),
            ),
        )
        assertEquals(listOf("出勤", "体位", "身材"), items.map { it.label })
        assertEquals(listOf(2L, 4L), items[1].children.map { it.id })
        assertEquals(listOf(3L, 5L), items[2].children.map { it.id })
        assertFalse(items[0].isGroup)
        assertTrue(items[1].isGroup)
        assertTrue(items[2].isGroup)
    }

    @Test
    fun `group chip is custom while scraped single item is not`() {
        val items = CoverTagFoldRules.fold(
            listOf(
                custom(1, "A", "体位"),
                custom(2, "B", "体位"),
                scraped(3, "出勤", "主题"),
            ),
        )
        assertEquals(2, items.size)
        assertTrue(items[0].isGroup)
        assertTrue(items[0].custom)
        assertFalse(items[1].custom)
        assertFalse(items[1].isGroup)
    }
}
