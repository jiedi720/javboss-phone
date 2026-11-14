package com.javboss.phone.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackBookmarkRulesTest {

    @Test
    fun `chapter fractions exclude the zero timestamp at track start`() {
        val chapters = listOf(
            PlaybackChapter(0L, "Chapter 1"),
            PlaybackChapter(10_000L, "Chapter 2"),
            PlaybackChapter(50_000L, "Chapter 3"),
            PlaybackChapter(90_000L, "Chapter 4"),
        )
        val result = PlaybackBookmarkRules.fractions(chapters, 100_000L)
        assertEquals(3, result.size)
        assertEquals(0.1f, result[0], 1e-6f)
        assertEquals(0.5f, result[1], 1e-6f)
        assertEquals(0.9f, result[2], 1e-6f)
    }

    @Test
    fun `fractions are sorted even when chapters arrive out of order`() {
        val chapters = listOf(
            PlaybackChapter(80_000L),
            PlaybackChapter(20_000L),
            PlaybackChapter(50_000L),
        )
        val result = PlaybackBookmarkRules.fractions(chapters, 100_000L)
        assertEquals(listOf(0.2f, 0.5f, 0.8f), result.map { (it * 1000).toInt() / 1000f })
    }

    @Test
    fun `chapters closer than merge window collapse into one marker`() {
        val chapters = listOf(
            PlaybackChapter(10_000L),
            PlaybackChapter(10_400L),
            PlaybackChapter(10_999L),
            PlaybackChapter(20_000L),
        )
        val result = PlaybackBookmarkRules.fractions(chapters, 100_000L)
        assertEquals(2, result.size)
        assertEquals(0.1f, result[0], 1e-6f)
        assertEquals(0.2f, result[1], 1e-6f)
    }

    @Test
    fun `negative and out of duration timestamps are dropped`() {
        val chapters = listOf(
            PlaybackChapter(-5_000L),
            PlaybackChapter(100_000L),
            PlaybackChapter(150_000L),
            PlaybackChapter(30_000L),
        )
        val result = PlaybackBookmarkRules.fractions(chapters, 100_000L)
        assertEquals(1, result.size)
        assertEquals(0.3f, result[0], 1e-6f)
    }

    @Test
    fun `invalid duration returns no markers`() {
        val chapters = listOf(PlaybackChapter(10_000L))
        assertTrue(PlaybackBookmarkRules.fractions(chapters, 0L).isEmpty())
        assertTrue(PlaybackBookmarkRules.fractions(chapters, -1L).isEmpty())
    }

    @Test
    fun `empty chapters returns no markers`() {
        assertTrue(PlaybackBookmarkRules.fractions(emptyList(), 100_000L).isEmpty())
    }

    @Test
    fun `fractions always stay inside zero to one`() {
        val chapters = listOf(PlaybackChapter(1L), PlaybackChapter(99_999L))
        val result = PlaybackBookmarkRules.fractions(chapters, 100_000L)
        assertEquals(2, result.size)
        result.forEach { assertTrue(it in 0f..1f) }
    }

    @Test
    fun `real world mkv chapter offsets map to expected fractions`() {
        // [00:00:00.000] / [00:16:30.838] / [00:31:07.257] / [00:32:49.123]
        val chapters = listOf(
            PlaybackChapter(0L),
            PlaybackChapter(990_838L),
            PlaybackChapter(1_867_257L),
            PlaybackChapter(1_969_123L),
        )
        val result = PlaybackBookmarkRules.fractions(chapters, 2_100_000L)
        assertEquals(3, result.size)
        assertTrue(result[0] in 0.47f..0.48f)
        assertTrue(result[1] in 0.88f..0.90f)
        assertTrue(result[2] in 0.93f..0.95f)
    }

    // v0.3.78：libVLC timeOffset 单位自适应（原生层是微秒，上一版当毫秒用导致标记全丢）
    @Test
    fun `microsecond chapter offsets are normalised to milliseconds`() {
        // 35 分钟视频，章节在 16:30.838 → 990_838_000 微秒
        val offsets = listOf(0L, 990_838_000L, 1_867_257_000L)
        val result = PlaybackBookmarkRules.normalizeTimeMs(offsets, 2_100_000L)
        assertEquals(listOf(0L, 990_838L, 1_867_257L), result)
    }

    @Test
    fun `millisecond chapter offsets are left untouched`() {
        val offsets = listOf(0L, 990_838L, 1_867_257L)
        assertEquals(offsets, PlaybackBookmarkRules.normalizeTimeMs(offsets, 2_100_000L))
    }

    @Test
    fun `unknown duration keeps offsets unchanged instead of guessing`() {
        val offsets = listOf(10_000L, 990_838_000L)
        assertEquals(offsets, PlaybackBookmarkRules.normalizeTimeMs(offsets, 0L))
        assertEquals(offsets, PlaybackBookmarkRules.normalizeTimeMs(offsets, -1L))
    }

    @Test
    fun `normalised microseconds survive fractions filtering`() {
        val offsets = listOf(0L, 990_838_000L, 1_867_257_000L)
        val chapters = PlaybackBookmarkRules.normalizeTimeMs(offsets, 2_100_000L)
            .map { PlaybackChapter(it) }
        val result = PlaybackBookmarkRules.fractions(chapters, 2_100_000L)
        assertEquals(2, result.size)
        assertTrue(result[0] in 0.47f..0.48f)
        assertTrue(result[1] in 0.88f..0.90f)
    }

    @Test
    fun `empty offsets return empty list`() {
        assertTrue(PlaybackBookmarkRules.normalizeTimeMs(emptyList(), 2_100_000L).isEmpty())
    }

    // v0.3.78：MP4 chapter track 在 libVLC 3.x 里映射成 titles，getChapters() 恒空。
    // 下面用 NAS 上 JUR-857.mp4 的 ffprobe 实测数据（4 章 / 总时长 3144.582s）锁住兜底。
    @Test
    fun `JUR-857 mp4 title durations reconstruct chapter starts`() {
        val durationsUs = listOf(
            990_838_000L,   // Chapter 1
            876_419_000L,   // Chapter 2
            101_866_000L,   // Chapter 3
            1_175_459_000L, // Chapter 4
        )
        val lengthMs = 3_144_583L
        val offsetsUs = PlaybackBookmarkRules.chapterOffsetsFromTitleDurations(durationsUs, lengthMs)
        assertEquals(listOf(990_838_000L, 1_867_257_000L, 1_969_123_000L), offsetsUs)
        // 交由 normalizeTimeMs 归一后正好是 ffprobe 的毫秒起点
        assertEquals(
            listOf(990_838L, 1_867_257L, 1_969_123L),
            PlaybackBookmarkRules.normalizeTimeMs(offsetsUs, lengthMs),
        )
    }

    @Test
    fun `title fallback feeds fractions with three markers`() {
        val offsetsUs = PlaybackBookmarkRules.chapterOffsetsFromTitleDurations(
            listOf(990_838_000L, 876_419_000L, 101_866_000L, 1_175_459_000L),
            3_144_583L,
        )
        val chapters = PlaybackBookmarkRules.normalizeTimeMs(offsetsUs, 3_144_583L)
            .map { PlaybackChapter(it) }
        val result = PlaybackBookmarkRules.fractions(chapters, 3_144_583L)
        assertEquals(3, result.size)
        assertTrue(result[0] in 0.31f..0.32f)
        assertTrue(result[1] in 0.59f..0.60f)
        assertTrue(result[2] in 0.62f..0.63f)
    }

    @Test
    fun `title durations already in milliseconds are rejected`() {
        // 单位若已是毫秒，总和只有 3144ms，与 3144583ms 对不上 → 不画错标记
        assertTrue(
            PlaybackBookmarkRules.chapterOffsetsFromTitleDurations(
                listOf(990_838L, 876_419L, 101_866L, 1_175_459L),
                3_144_583L,
            ).isEmpty(),
        )
    }

    @Test
    fun `title fallback rejects unknown duration and invalid segment lengths`() {
        val durations = listOf(990_838_000L, 876_419_000L)
        assertTrue(PlaybackBookmarkRules.chapterOffsetsFromTitleDurations(durations, 0L).isEmpty())
        assertTrue(PlaybackBookmarkRules.chapterOffsetsFromTitleDurations(durations, -1L).isEmpty())
        assertTrue(PlaybackBookmarkRules.chapterOffsetsFromTitleDurations(emptyList(), 3_144_583L).isEmpty())
        assertTrue(PlaybackBookmarkRules.chapterOffsetsFromTitleDurations(listOf(1_000_000L), 3_144_583L).isEmpty())
        assertTrue(
            PlaybackBookmarkRules.chapterOffsetsFromTitleDurations(
                listOf(0L, 990_838_000L),
                1_000_000L,
            ).isEmpty(),
        )
    }
}
