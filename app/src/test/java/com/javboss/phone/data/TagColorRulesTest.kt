package com.javboss.phone.data

import com.javboss.phone.data.api.dto.TagDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagColorRulesTest {

    @Test
    fun `provider 3 is the only custom tag marker`() {
        assertTrue(TagColorRules.isCustom(3))
        assertFalse(TagColorRules.isCustom(0))
        assertFalse(TagColorRules.isCustom(1))
        assertFalse(TagColorRules.isCustom(2))
        assertFalse(TagColorRules.isCustom(-1))
        assertEquals(3, TagColorRules.CUSTOM_PROVIDER)
    }

    @Test
    fun `TagDto isCustom delegates to provider rule`() {
        assertTrue(TagDto(id = 1, name = "自定义", provider = 3).isCustom)
        assertFalse(TagDto(id = 1, name = "刮削", provider = 1).isCustom)
        // 老服务端不返回 provider 时按固有刮削标签处理
        assertFalse(TagDto(id = 1, name = "未知").isCustom)
    }

    @Test
    fun `customTagNames keeps only custom tags in server order`() {
        val tags = listOf(
            TagDto(1, "刮削A", provider = 1),
            TagDto(2, "自定1", provider = 3),
            TagDto(3, "刮削B", provider = 2),
            TagDto(4, "自定2", provider = 3),
        )
        assertEquals(listOf("自定1", "自定2"), TagColorRules.customTagNames(tags))
    }

    @Test
    fun `customTagNames drops blank names and duplicates`() {
        val tags = listOf(
            TagDto(1, "", provider = 3),
            TagDto(2, "重复", provider = 3),
            TagDto(3, "重复", provider = 3),
            TagDto(4, "  ", provider = 3),
        )
        assertEquals(listOf("重复"), TagColorRules.customTagNames(tags))
    }

    @Test
    fun `customTagNames empty for all scraped tags`() {
        val tags = listOf(TagDto(1, "A"), TagDto(2, "B", provider = 1))
        assertEquals(emptyList<String>(), TagColorRules.customTagNames(tags))
        assertEquals(emptyList<String>(), TagColorRules.customTagNames(emptyList()))
    }
}
