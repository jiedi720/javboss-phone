package com.javboss.phone.ui.components

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 女优卡片封面下方数据行的拼装规则。
 * 关键约束：**永远返回固定两行**（生日行 + 身体数据行），
 * 缺失时用占位文案补位，否则卡片高矮不一，网格里对不齐。
 * 移动端窄卡片只留数字/字母：身高无 cm、三围无中文前缀、罩杯无「罩杯」、年龄无「岁」。
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
        assertEquals(listOf("1996-03-01（30）", "158 · 88-58-86 · E"), rows)
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
        assertEquals(listOf("生日未录入", "165"), onlyHeight)

        val onlyBirth = idolInfoRows(
            birthDate = "2000-01-02", heightCm = 0, bust = 0, waist = 0, hips = 0, cup = 0, today = today,
        )
        assertEquals(listOf("2000-01-02（26）", "身体数据未录入"), onlyBirth)
    }

    @Test
    fun `bwh needs all three values`() {
        assertEquals("90-60-88", bwhLabel(90, 60, 88))
        assertEquals("", bwhLabel(90, 0, 88))
        assertEquals("", bwhLabel(0, 0, 0))
        // 只有胸腰没有臀时不显示三围，身高/罩杯照旧
        assertEquals(
            listOf("生日未录入", "160 · D"),
            idolInfoRows(
                birthDate = "", heightCm = 160, bust = 90, waist = 60, hips = 0, cup = 4, today = today,
            ),
        )
    }

    @Test
    fun `cup index maps to letter`() {
        assertEquals("A", cupLabel(1))
        assertEquals("E", cupLabel(5))
        assertEquals("K", cupLabel(11))
        assertEquals("", cupLabel(0))
        assertEquals("", cupLabel(-3))
    }

    @Test
    fun `age is one less before this year's birthday`() {
        assertEquals("1996-03-01（30）", birthWithAgeLabel("1996-03-01", today))
        assertEquals("1996-12-31（29）", birthWithAgeLabel("1996-12-31", today))
    }

    @Test
    fun `unknown or future birth date falls back to raw text`() {
        assertEquals("", birthWithAgeLabel("", today))
        assertEquals("不详", birthWithAgeLabel("不详", today))
        assertEquals("2030-01-01", birthWithAgeLabel("2030-01-01", today))
    }
}
