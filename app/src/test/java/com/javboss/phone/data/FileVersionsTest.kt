package com.javboss.phone.data

import com.javboss.phone.data.api.dto.DirectoryRefDto
import com.javboss.phone.data.api.dto.VideoDto
import com.javboss.phone.data.api.dto.VideoLocationDto
import org.junit.Assert.assertEquals
import org.junit.Test

class FileVersionsTest {

    private fun location(
        id: Long,
        relativePath: String,
        directoryPath: String = "",
        filename: String = "",
        directory: DirectoryRefDto? = null,
        directoryRef: DirectoryRefDto? = null,
    ) = VideoLocationDto(
        id = id,
        filename = filename,
        relativePath = relativePath,
        directoryPath = directoryPath,
        directory = directory,
        directoryRef = directoryRef,
    )

    private fun video(locations: List<VideoLocationDto>) = VideoDto(
        id = 5272,
        filename = "SNIS-557.mp4",
        path = "SNIS-557 [小野夕子]/SNIS-557.mp4",
        directoryPath = "G:\\UnScrapy",
        directory = DirectoryRefDto(id = 11, path = "G:\\UnScrapy"),
        locations = locations,
        size = 1000,
        durationSec = 120,
        watchedMs = 5000,
    )

    @Test
    fun `multiple locations expand into distinct full paths`() {
        val v = video(
            listOf(
                location(32452, "SNIS-557 [小野夕子]/SNIS-557.mp4", "G:\\UnScrapy", "SNIS-557.mp4"),
                location(36251, "SNIS-557-LTX/SNIS-557-LTX.mp4", "G:\\LTX", "SNIS-557-LTX.mp4"),
            ),
        )
        val files = expandFileVersions(v)
        assertEquals(2, files.size)
        assertEquals("G:\\UnScrapy/SNIS-557 [小野夕子]/SNIS-557.mp4", files[0].fullPath)
        assertEquals("G:\\LTX/SNIS-557-LTX/SNIS-557-LTX.mp4", files[1].fullPath)
        assertEquals(32452L, files[0].locationId)
        assertEquals(36251L, files[1].locationId)
        assertEquals("SNIS-557.mp4", files[0].filename)
        assertEquals("SNIS-557-LTX.mp4", files[1].filename)
    }

    @Test
    fun `embedded directory object is used when directory_path is missing`() {
        val v = video(
            listOf(location(1, "a/b.mp4", directory = DirectoryRefDto(id = 9, path = "G:\\LTX"))),
        )
        val files = expandFileVersions(v)
        assertEquals(1, files.size)
        assertEquals("G:\\LTX/a/b.mp4", files[0].fullPath)
        // 文件名缺省时从 relative_path 取最后一段
        assertEquals("b.mp4", files[0].filename)
        assertEquals(9L, files[0].directory?.id)
    }

    @Test
    fun `directory_ref is also accepted as a directory source`() {
        val v = video(
            listOf(location(2, "c/d.mp4", directoryRef = DirectoryRefDto(id = 7, path = "G:\\X"))),
        )
        assertEquals("G:\\X/c/d.mp4", expandFileVersions(v).single().fullPath)
    }

    @Test
    fun `locations without a relative path are dropped`() {
        val v = video(
            listOf(
                location(1, "", "G:\\X"),
                location(3, "c/d.mp4", "G:\\X"),
            ),
        )
        val files = expandFileVersions(v)
        assertEquals(1, files.size)
        assertEquals(3L, files[0].locationId)
    }

    @Test
    fun `missing directory path falls back to the video top-level directory`() {
        val v = video(listOf(location(2, "a/b.mp4", "")))
        val files = expandFileVersions(v)
        assertEquals(1, files.size)
        assertEquals("G:\\UnScrapy/a/b.mp4", files[0].fullPath)
    }

    @Test
    fun `missing directory path prefers the matching jav embedded entry`() {
        val v = video(listOf(location(2, "a/b.mp4", "")))
        val meta = listOf(
            VideoDto(id = 5272, locationId = 2, filename = "b.mp4", path = "a/b.mp4", directoryPath = "G:\\META"),
        )
        val files = expandFileVersions(v, meta)
        assertEquals("G:\\META/a/b.mp4", files[0].fullPath)
    }

    @Test
    fun `without locations the jav embedded videos keep their own paths`() {
        // 锁死旧 bug：以前按 id 匹配，把同一个 video_id 的所有版本都替换成主路径
        val v = video(emptyList())
        val meta = listOf(
            VideoDto(id = 5272, filename = "a.mp4", path = "A/a.mp4", directoryPath = "G:\\A", locationId = 1),
            VideoDto(id = 5272, filename = "b.mp4", path = "B/b.mp4", directoryPath = "G:\\B", locationId = 2),
        )
        val files = expandFileVersions(v, meta)
        assertEquals(2, files.size)
        assertEquals("G:\\A/A/a.mp4", files[0].fullPath)
        assertEquals("G:\\B/B/b.mp4", files[1].fullPath)
    }

    @Test
    fun `null video still shows the jav embedded file list`() {
        val meta = listOf(
            VideoDto(id = 5272, filename = "a.mp4", path = "A/a.mp4", directoryPath = "G:\\A"),
        )
        assertEquals(meta, expandFileVersions(null, meta))
    }

    @Test
    fun `per-file stats are matched by location id`() {
        val v = video(
            listOf(
                location(10, "A/a.mp4", "G:\\A", "a.mp4"),
                location(20, "B/b.mp4", "G:\\B", "b.mp4"),
            ),
        )
        val meta = listOf(
            VideoDto(id = 5272, locationId = 20, filename = "b.mp4", size = 999, durationSec = 77, watchedMs = 1234),
            VideoDto(id = 5272, locationId = 10, filename = "a.mp4", size = 111, durationSec = 33, watchedMs = 5),
        )
        val files = expandFileVersions(v, meta)
        assertEquals(111L, files[0].size)
        assertEquals(33L, files[0].durationSec)
        assertEquals(5L, files[0].watchedMs)
        assertEquals(999L, files[1].size)
        assertEquals(77L, files[1].durationSec)
        assertEquals(1234L, files[1].watchedMs)
    }

    @Test
    fun `falls back when every location is unusable`() {
        val v = video(listOf(location(1, "", "")))
        val meta = listOf(
            VideoDto(id = 5272, filename = "a.mp4", path = "A/a.mp4", directoryPath = "G:\\A"),
        )
        val files = expandFileVersions(v, meta)
        assertEquals(1, files.size)
        assertEquals("G:\\A/A/a.mp4", files[0].fullPath)
    }

    @Test
    fun `single video is shown when there is no location and no meta`() {
        val v = video(emptyList())
        val files = expandFileVersions(v)
        assertEquals(1, files.size)
        assertEquals("G:\\UnScrapy/SNIS-557 [小野夕子]/SNIS-557.mp4", files[0].fullPath)
    }
}
