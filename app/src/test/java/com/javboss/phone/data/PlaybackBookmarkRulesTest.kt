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
}
