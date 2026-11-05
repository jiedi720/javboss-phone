package com.javboss.phone.ui.components

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 女优卡片封面下方数据行的拼装规则。
 * 关键约束：**永远返回固定两行**（生日行 + 身体数据行），
 * 缺失时用占位文案补位，否则卡片高矮不一，网格里对不齐。
 */
class IdolInfoRowsTest {

    private val today = LocalDate.of(2026, 11, 5)

    @Test
    fun `full data fills both rows`() {
        val rows = idolInfoRows(
            birthDate = "1996-03-01",
            heightCm = 158,
            bust = 88,
            waist = 58,
            hips = 86,
            cup = 5,
            today = today,
        )
        assertEquals(listOf("1996-03-01（30岁）", "158cm · 胸88-腰58-臀86 · E罩杯"), rows)
    }

    @Test
    fun `always exactly two rows even when everything is missing`() {
        val rows = idolInfoRows(
            birthDate = "",
            heightCm = 0,
            bust = 0,
            waist = 0,
            hips = 0,
            cup = 0,
            today = today,
        )
        assertEquals(listOf("生日未录入", "身体数据未录入"), rows)
    }

    @Test
    fun `partial data keeps the line count stable`() {
        val onlyHeight = idolInfoRows(
            birthDate = "", heightCm = 165, bust = 0, waist = 0, hips = 0, cup = 0, today = today,
        )
        assertEquals(listOf("生日未录入", "165cm"), onlyHeight)

        val onlyBirth = idolInfoRows(
            birthDate = "2000-01-02", heightCm = 0, bust = 0, waist = 0, hips = 0, cup = 0, today = today,
        )
        assertEquals(listOf("2000-01-02（26岁）", "身体数据未录入"), onlyBirth)
    }

    @Test
    fun `bwh needs all three values`() {
        assertEquals("胸90-腰60-臀88", bwhLabel(90, 60, 88))
        assertEquals("", bwhLabel(90, 0, 88))
        assertEquals("", bwhLabel(0, 0, 0))
        // 只有胸腰没有臀时不显示三围，身高/罩杯照旧
        assertEquals(
            listOf("生日未录入", "160cm · D罩杯"),
            idolInfoRows(
                birthDate = "", heightCm = 160, bust = 90, waist = 60, hips = 0, cup = 4, today = today,
            ),
        )
    }

    @Test
    fun `cup index maps to letter`() {
        assertEquals("A罩杯", cupLabel(1))
        assertEquals("E罩杯", cupLabel(5))
        assertEquals("K罩杯", cupLabel(11))
        assertEquals("", cupLabel(0))
        assertEquals("", cupLabel(-3))
    }

    @Test
    fun `age is one less before this year's birthday`() {
        assertEquals("1996-03-01（30岁）", birthWithAgeLabel("1996-03-01", today))
        assertEquals("1996-12-31（29岁）", birthWithAgeLabel("1996-12-31", today))
    }

    @Test
    fun `unknown or future birth date falls back to raw text`() {
        assertEquals("", birthWithAgeLabel("", today))
        assertEquals("不详", birthWithAgeLabel("不详", today))
        assertEquals("2030-01-01", birthWithAgeLabel("2030-01-01", today))
    }
}
