package com.javboss.phone.ui.favorites

import com.javboss.phone.data.api.dto.FavoriteItemDto
import com.javboss.phone.data.api.dto.IdolDto
import org.junit.Assert.assertEquals
import org.junit.Test

class FavIdolSortTest {

    private fun item(id: Long, name: String) = FavoriteItemDto(id = id, name = name)

    private val a = item(1, "A")
    private val b = item(2, "B")
    private val c = item(3, "C")
    private val items = listOf(a, b, c)

    private val meta = mapOf(
        1L to IdolDto(
            id = 1, workCount = 10, heightCm = 150, birthDate = "2000-01-01",
            bust = 80, waist = 55, hips = 82, cup = 2,
        ),
        2L to IdolDto(
            id = 2, workCount = 30, heightCm = 170, birthDate = "1990-01-01",
            bust = 90, waist = 60, hips = 88, cup = 5,
        ),
        3L to IdolDto(
            id = 3, workCount = 20, heightCm = 160, birthDate = "1995-01-01",
            bust = 85, waist = 58, hips = 85, cup = 3,
        ),
    )

    @Test
    fun `DEFAULT keeps server order and reverses on desc`() {
        assertEquals(listOf(a, b, c), FavIdolSortRules.sort(items, meta, FavIdolSortBase.DEFAULT, true))
        assertEquals(listOf(c, b, a), FavIdolSortRules.sort(items, meta, FavIdolSortBase.DEFAULT, false))
    }

    @Test
    fun `WORK sorts by work count`() {
        assertEquals(listOf(a, c, b), FavIdolSortRules.sort(items, meta, FavIdolSortBase.WORK, true))
        assertEquals(listOf(b, c, a), FavIdolSortRules.sort(items, meta, FavIdolSortBase.WORK, false))
    }

    @Test
    fun `numeric profile dimensions sort ascending and reverse`() {
        assertEquals(listOf(a, c, b), FavIdolSortRules.sort(items, meta, FavIdolSortBase.HEIGHT, true))
        assertEquals(listOf(a, c, b), FavIdolSortRules.sort(items, meta, FavIdolSortBase.BUST, true))
        assertEquals(listOf(a, c, b), FavIdolSortRules.sort(items, meta, FavIdolSortBase.WAIST, true))
        assertEquals(listOf(a, c, b), FavIdolSortRules.sort(items, meta, FavIdolSortBase.HIPS, true))
        assertEquals(listOf(a, c, b), FavIdolSortRules.sort(items, meta, FavIdolSortBase.CUP, true))
        assertEquals(listOf(b, c, a), FavIdolSortRules.sort(items, meta, FavIdolSortBase.HEIGHT, false))
    }

    @Test
    fun `AGE small to large means later birthday first`() {
        // 年龄小→大 = 生日晚→早：A(2000) 最小，B(1990) 最大
        assertEquals(listOf(a, c, b), FavIdolSortRules.sort(items, meta, FavIdolSortBase.AGE, true))
        assertEquals(listOf(b, c, a), FavIdolSortRules.sort(items, meta, FavIdolSortBase.AGE, false))
    }

    @Test
    fun `RECENT uses idol id as join-time proxy`() {
        assertEquals(listOf(a, b, c), FavIdolSortRules.sort(items, meta, FavIdolSortBase.RECENT, true))
        assertEquals(listOf(c, b, a), FavIdolSortRules.sort(items, meta, FavIdolSortBase.RECENT, false))
    }

    @Test
    fun `missing metadata never crashes and keeps all items`() {
        val sorted = FavIdolSortRules.sort(items, emptyMap(), FavIdolSortBase.HEIGHT, true)
        assertEquals(3, sorted.size)
        assertEquals(items.toSet(), sorted.toSet())
    }

    @Test
    fun `idol sort options match Web Pe dimensions`() {
        assertEquals(
            listOf("默认顺序", "作品数量", "加入时间", "年龄", "身高", "胸围", "臀围", "腰围", "罩杯"),
            FavIdolSortBase.entries.map { it.label },
        )
    }
}
