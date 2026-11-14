package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4ChapterParserTest {

    /** NAS 上 JUR-857.mp4 真实 chpl box（89 字节，4 章），ffprobe 起点 0/990838/1867257/1969123 ms。 */
    private val jur857Chpl = hex(
        "000000596368706c0100000000000000" +
            "04000000000000000009436861707465" +
            "722031000000024e95e1600943686170" +
            "74657220320000000458f8ce90094368" +
            "617074657220330000000495b0533009" +
            "436861707465722034",
    )

    @Test
    fun `JUR-857 chpl box parses to four chapters in milliseconds`() {
        val chapters = Mp4ChapterParser.parseChpl(jur857Chpl)
        assertEquals(4, chapters.size)
        assertEquals(
            listOf(0L, 990_838L, 1_867_257L, 1_969_123L),
            chapters.map { it.timeMs },
        )
        assertEquals(
            listOf("Chapter 1", "Chapter 2", "Chapter 3", "Chapter 4"),
            chapters.map { it.name },
        )
    }

    @Test
    fun `parsed chapters produce three seek bar markers`() {
        val chapters = Mp4ChapterParser.parseChpl(jur857Chpl)
        val fractions = PlaybackBookmarkRules.fractions(chapters, 3_144_583L)
        assertEquals(3, fractions.size)
        assertTrue(fractions[0] in 0.31f..0.32f)
        assertTrue(fractions[1] in 0.59f..0.60f)
        assertTrue(fractions[2] in 0.62f..0.63f)
    }

    @Test
    fun `chpl box is found inside a larger range window`() {
        // 真实文件里 chpl 在 ~2.7MB 处，前面是 moov 等其它 box
        val window = ByteArray(64) { 0x11 } + jur857Chpl + ByteArray(32) { 0x22 }
        assertEquals(4, Mp4ChapterParser.parseChpl(window).size)
    }

    @Test
    fun `no chpl box returns empty list`() {
        assertTrue(Mp4ChapterParser.parseChpl(ByteArray(256) { 0x00 }).isEmpty())
        assertTrue(Mp4ChapterParser.parseChpl(ByteArray(2)).isEmpty())
    }

    @Test
    fun `unsupported chpl version returns empty list`() {
        val wrong = jur857Chpl.clone().also { it[8] = 2 } // version byte
        assertTrue(Mp4ChapterParser.parseChpl(wrong).isEmpty())
    }

    @Test
    fun `truncated chpl keeps only complete entries`() {
        val truncated = jur857Chpl.copyOf(45) // 只够 Chapter 1 + 半个 Chapter 2
        val chapters = Mp4ChapterParser.parseChpl(truncated)
        assertEquals(1, chapters.size)
        assertEquals(0L, chapters[0].timeMs)
    }

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
