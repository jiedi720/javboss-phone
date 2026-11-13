package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 标签展示顺序 / 可提交标签的 JVM 单测（v0.3.70 引入，v0.3.71 定稿）。
 *
 * 两个真实故障点：
 * 1. 服务端 `GET /jav/tags`、`jav.tags` 都是「刮削在前、用户自定义在后」返回，
 *    客户端原样渲染导致自定义标签不在最前面 → 展示时改成**自定义在前、刮削在后**。
 * 2. 编辑标签弹窗曾把作品现有的刮削标签也算进 selected，保存时混进 `tag_ids`；
 *    服务端要求 tag_ids 全部 `is_user = true`，混入即整单失败 → 只提交用户标签。
 */
class TagOrderRulesTest {

    private fun scraped(id: Long, name: String) = TagDto(id = id, name = name, provider = 0)
    private fun custom(id: Long, name: String) = TagDto(id = id, name = name, provider = 3)

    @Test
    fun `custom tags are shown first`() {
        val input = listOf(
            scraped(1, "単体作品"),
            scraped(2, "巨乳"),
            custom(10, "体位"),
            custom(11, "笑顔"),
        )
        assertEquals(
            listOf("体位", "笑顔", "単体作品", "巨乳"),
            TagOrderRules.customFirst(input).map { it.name },
        )
    }

    @Test
    fun `order inside each group is preserved`() {
        val input = listOf(
            scraped(3, "C"),
            custom(11, "乙"),
            scraped(1, "A"),
            custom(12, "甲"),
            scraped(2, "B"),
        )
        assertEquals(
            listOf("乙", "甲", "C", "A", "B"),
            TagOrderRules.customFirst(input).map { it.name },
        )
    }

    @Test
    fun `already custom-first list stays unchanged`() {
        val input = listOf(custom(9, "X"), scraped(1, "A"), scraped(2, "B"))
        assertEquals(input, TagOrderRules.customFirst(input))
    }

    @Test
    fun `empty list is safe`() {
        assertEquals(emptyList<TagDto>(), TagOrderRules.customFirst(emptyList()))
        assertEquals(emptyList<TagDto>(), TagOrderRules.userTags(emptyList()))
        assertEquals(emptyList<TagDto>(), TagOrderRules.scrapedTags(emptyList()))
    }

    @Test
    fun `only custom tags with a real id can be submitted`() {
        val input = listOf(
            scraped(1, "単体作品"),
            custom(10, "体位"),
            custom(0, "占位标签"),
            custom(11, "笑顔"),
        )
        assertEquals(listOf(10L, 11L), TagOrderRules.userTags(input).map { it.id })
    }

    @Test
    fun `user tags keep their order and never include scraped ones`() {
        val input = listOf(scraped(1, "A"), custom(5, "X"), scraped(2, "B"), custom(6, "Y"))
        assertEquals(listOf("X", "Y"), TagOrderRules.userTags(input).map { it.name })
    }

    @Test
    fun `read only group keeps only scraped tags in server order`() {
        val input = listOf(custom(5, "X"), scraped(2, "B"), scraped(1, "A"))
        assertEquals(listOf("B", "A"), TagOrderRules.scrapedTags(input).map { it.name })
    }
}
