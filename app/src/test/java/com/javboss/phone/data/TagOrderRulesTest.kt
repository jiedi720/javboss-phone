package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 标签展示顺序 / 可提交标签的 JVM 单测（v0.3.70）。
 *
 * 两个真实故障点：
 * 1. 服务端 `jav.tags` 把用户自定义标签（provider=3）排在前面返回，详情页直接照数组顺序渲染，
 *    自定义标签总挤在最前 → 展示时改成刮削在前、自定义在后。
 * 2. 编辑标签弹窗把作品现有的刮削标签也算进 selected，保存时混进 `tag_ids`；
 *    服务端要求 tag_ids 全部 `is_user = true`，混入即整单失败 → 只提交用户标签。
 */
class TagOrderRulesTest {

    private fun scraped(id: Long, name: String) = TagDto(id = id, name = name, provider = 0)
    private fun custom(id: Long, name: String) = TagDto(id = id, name = name, provider = 3)

    @Test
    fun `custom tags move behind scraped tags`() {
        val input = listOf(
            custom(10, "体位"),
            custom(11, "笑顔"),
            scraped(1, "単体作品"),
            scraped(2, "巨乳"),
        )
        val result = TagOrderRules.scrapedFirst(input).map { it.name }
        assertEquals(listOf("単体作品", "巨乳", "体位", "笑顔"), result)
    }

    @Test
    fun `order inside each group is preserved`() {
        val input = listOf(
            custom(11, "乙"),
            scraped(3, "C"),
            custom(12, "甲"),
            scraped(1, "A"),
            scraped(2, "B"),
        )
        assertEquals(
            listOf("C", "A", "B", "乙", "甲"),
            TagOrderRules.scrapedFirst(input).map { it.name },
        )
    }

    @Test
    fun `already ordered list stays unchanged`() {
        val input = listOf(scraped(1, "A"), scraped(2, "B"), custom(9, "X"))
        assertEquals(input, TagOrderRules.scrapedFirst(input))
    }

    @Test
    fun `empty list is safe`() {
        assertEquals(emptyList<TagDto>(), TagOrderRules.scrapedFirst(emptyList()))
        assertEquals(emptyList<TagDto>(), TagOrderRules.userTags(emptyList()))
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
}
