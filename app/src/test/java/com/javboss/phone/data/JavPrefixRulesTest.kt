package com.javboss.phone.data

import com.javboss.phone.data.api.dto.StudioCodePrefixDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.3.75：片商落地页「番号」分组筛选的纯规则回归。
 * 对齐服务端 Web bundle 的 `jk` / `Mk` 与 `internal/db/jav.go` 的 `normalizeJavCodePrefix`。
 * 样本取自 Web 端「S1 Number One Style」片商详情截图：AVOP 1 / SNIS 33 / SNOS 9 /
 * SONE 5 / SSIS 14 / SSNI 15（合计 77 部）。
 */
class JavPrefixRulesTest {

    private fun p(prefix: String, count: Int) = StudioCodePrefixDto(prefix, count)

    private val s1 = listOf(
        p("AVOP", 1),
        p("SNIS", 33),
        p("SNOS", 9),
        p("SONE", 5),
        p("SSIS", 14),
        p("SSNI", 15),
    )

    @Test
    fun `name ascending matches the web studio dialog order`() {
        val sorted = JavPrefixRules.sort(s1, byName = true)
        assertEquals(listOf("AVOP", "SNIS", "SNOS", "SONE", "SSIS", "SSNI"), sorted.map { it.prefix })
        assertEquals(6, sorted.size)
    }

    @Test
    fun `work count descending puts the biggest prefix first`() {
        val sorted = JavPrefixRules.sort(s1, byName = false)
        assertEquals(listOf("SNIS", "SSNI", "SSIS", "SNOS", "SONE", "AVOP"), sorted.map { it.prefix })
        assertEquals(listOf(33, 15, 14, 9, 5, 1), sorted.map { it.workCount })
    }

    @Test
    fun `equal work counts fall back to natural name order`() {
        val items = listOf(p("SONE", 5), p("AVOP", 5), p("SNIS", 5))
        assertEquals(
            listOf("AVOP", "SNIS", "SONE"),
            JavPrefixRules.sort(items, byName = false).map { it.prefix },
        )
    }

    @Test
    fun `sort returns a new list and does not mutate the input`() {
        val input = s1.toMutableList()
        val sorted = JavPrefixRules.sort(input, byName = false)
        assertEquals("AVOP", input[0].prefix)
        assertTrue(sorted !== input)
        assertEquals(6, sorted.size)
    }

    @Test
    fun `empty and single item lists are handled`() {
        assertTrue(JavPrefixRules.sort(emptyList(), byName = true).isEmpty())
        assertEquals(listOf("ABP"), JavPrefixRules.sort(listOf(p("ABP", 3)), byName = false).map { it.prefix })
    }

    @Test
    fun `natural compare orders numeric segments by value`() {
        assertTrue(JavPrefixRules.naturalCompare("MIA2", "MIA10") < 0)
        assertTrue(JavPrefixRules.naturalCompare("MIA10", "MIA2") > 0)
        assertEquals(0, JavPrefixRules.naturalCompare("MIA02", "MIA2"))
        assertTrue(JavPrefixRules.naturalCompare("ABP", "ABP1") < 0)
    }

    @Test
    fun `natural compare is case insensitive`() {
        assertEquals(0, JavPrefixRules.naturalCompare("snis", "SNIS"))
        assertTrue(JavPrefixRules.naturalCompare("avop", "SNIS") < 0)
    }

    @Test
    fun `natural compare covers pure numeric and mixed prefixes`() {
        assertTrue(JavPrefixRules.naturalCompare("2", "10") < 0)
        assertTrue(JavPrefixRules.naturalCompare("A10B", "A2B") > 0)
        assertEquals(0, JavPrefixRules.naturalCompare("", ""))
    }

    @Test
    fun `normalize mirrors server normalizeJavCodePrefix`() {
        assertEquals("SNIS", JavPrefixRules.normalize("  snis "))
        // 服务端只接受 A-Z0-9：带连字符等非法字符一律判空（客户端据此拒绝请求）
        assertEquals("", JavPrefixRules.normalize("ABP-123"))
        assertEquals("", JavPrefixRules.normalize(" "))
        assertEquals("", JavPrefixRules.normalize("SNIS*"))
        assertEquals("MIA10", JavPrefixRules.normalize("mia10"))
    }

    @Test
    fun `sort label and key toggle between the two web states`() {
        assertEquals("名称", JavPrefixRules.sortLabel(byName = true))
        assertEquals("作品数", JavPrefixRules.sortLabel(byName = false))
        assertEquals(JavPrefixRules.SORT_NAME_ASC, JavPrefixRules.sortKey(byName = true))
        assertEquals(JavPrefixRules.SORT_WORK_COUNT_DESC, JavPrefixRules.sortKey(byName = false))
        assertEquals(false, JavPrefixRules.toggle(true))
        assertEquals(true, JavPrefixRules.toggle(false))
    }
}
