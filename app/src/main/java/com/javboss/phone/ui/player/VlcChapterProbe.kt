package com.javboss.phone.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import com.javboss.phone.data.Mp4ChapterParser
import com.javboss.phone.data.PlaybackBookmarkRules
import com.javboss.phone.data.PlaybackChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.ByteArrayOutputStream

/**
 * 与播放引擎无关的章节探测（v0.3.78 修复需求①）。
 *
 * 默认播放引擎是 ExoPlayer（AUTO 也走 EXO），而 Media3 1.5.1 不向应用层暴露容器
 * chapters，`ExoPlayerEngine.chapters` 恒为空 —— 上一版因此只有手动切到 libVLC 才可能
 * 看到章节标记，等于没实现。这里用一个独立、静默（不发声、不渲染）的 libVLC
 * MediaPlayer 只对当前播放地址做 demux 解析读 chapters，读到即 stop/release，
 * **不参与实际播放**，也不改用户的引擎偏好。
 *
 * 时间点单位由 [PlaybackBookmarkRules.normalizeTimeMs] 参照媒体总时长自动归一
 * （原生 libVLC 是微秒）。任何 native 异常都返回空列表，绝不影响正在进行的播放。
 */
class VlcChapterProbe(
    context: Context,
    /** 读 MP4 chpl box 时复用应用带鉴权的客户端；地址走本地代理，Range 会被转发 */
    private val okHttpClient: OkHttpClient,
) {

    private val appContext = context.applicationContext

    suspend fun probe(url: String): List<PlaybackChapter> = withContext(Dispatchers.IO) {
        var libVlc: LibVLC? = null
        var player: MediaPlayer? = null
        try {
            libVlc = LibVLC(
                appContext,
                arrayListOf(
                    "--no-audio",
                    "--no-video",
                    "--no-spu",
                    "--no-osd",
                    "--vout=dummy",
                    "--aout=dummy",
                    "--no-stats",
                    // v0.3.78 诊断：去掉 --quiet 才能在 logcat 看到 demux 对 chapters 的解析
                    "--verbose=2",
                ),
            )
            val mediaPlayer = MediaPlayer(libVlc)
            player = mediaPlayer
            val media = Media(libVlc, Uri.parse(url))
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()

            var raw: List<Pair<Long, String>> = emptyList()
            var lengthMs = 0L
            for (attempt in 0 until MAX_ATTEMPTS) {
                delay(POLL_MS)
                lengthMs = runCatching { mediaPlayer.length }.getOrDefault(0L)
                raw = readRawChapters(mediaPlayer, lengthMs, verbose = attempt == 0 || attempt == MAX_ATTEMPTS - 1)
                if (raw.isNotEmpty() && lengthMs > 0L) break
            }
            if (raw.isEmpty()) {
                // MP4 chpl box：VLC demux 日志能看到 `read box: "chpl" 4 chapters`，
                // 但 Java 层 titles=0 / getChapters() 异常，只能自己按 Range 读容器 box。
                Log.d(TAG, "probe: no VLC chapters (lengthMs=$lengthMs), try mp4 chpl")
                val chpl = readChplChapters(url)
                Log.d(TAG, "probe: chpl fallback ${chpl.size} chapters, times=${chpl.map { it.timeMs }}")
                return@withContext chpl
            }

            val times = PlaybackBookmarkRules.normalizeTimeMs(raw.map { it.first }, lengthMs)
            val result = times.mapIndexed { index, timeMs -> PlaybackChapter(timeMs, raw[index].second) }
                .distinctBy { it.timeMs }
                .sortedBy { it.timeMs }
            Log.d(TAG, "probe: ${result.size} chapters, lengthMs=$lengthMs, times=${result.map { it.timeMs }}")
            result
        } catch (e: Throwable) {
            emptyList()
        } finally {
            runCatching {
                player?.stop()
                player?.release()
                libVlc?.release()
            }
        }
    }

    /**
     * MP4 chpl 兜底：先按 Range 读文件头（faststart / moov 在前），找不到再读文件尾。
     * 只读窗口字节，服务端忽略 Range 返回整文件时也只读 [MAX_READ] 就关闭连接，不会 OOM。
     */
    private fun readChplChapters(url: String): List<PlaybackChapter> {
        val ranges = listOf("bytes=0-${HEAD_BYTES - 1}", "bytes=-$TAIL_BYTES")
        for (range in ranges) {
            val bytes = fetchRange(url, range) ?: continue
            val parsed = Mp4ChapterParser.parseChpl(bytes)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyList()
    }

    private fun fetchRange(url: String, range: String): ByteArray? = try {
        val request = Request.Builder().url(url).header("Range", range).get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val stream = response.body?.byteStream() ?: return null
            stream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                var total = 0
                while (total < MAX_READ) {
                    val n = input.read(buf, 0, minOf(buf.size, MAX_READ - total))
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                out.toByteArray()
            }
        }
    } catch (e: Throwable) {
        null
    }

    /** 原生调用全部 runCatching 兜底；titles 为空时退化为 -1 / 0 两种下标。 */
    private fun readRawChapters(
        mediaPlayer: MediaPlayer,
        lengthMs: Long,
        verbose: Boolean,
    ): List<Pair<Long, String>> {
        val found = mutableListOf<Pair<Long, String>>()
        val titles = runCatching { mediaPlayer.titles }.getOrNull()
        val indices = if (titles.isNullOrEmpty()) {
            intArrayOf(-1, 0)
        } else {
            IntArray(titles.size) { it }
        }
        if (verbose) {
            Log.d(
                TAG,
                "probe: titles=${titles?.size ?: 0}, durations=" +
                    titles?.joinToString { it.duration.toString() }.orEmpty() +
                    ", lengthMs=$lengthMs",
            )
        }
        for (index in indices) {
            val chapters = runCatching { mediaPlayer.getChapters(index) }.getOrNull() ?: continue
            if (verbose) Log.d(TAG, "probe: getChapters($index)=${chapters.size}")
            for (chapter in chapters) {
                if (chapter != null && chapter.timeOffset > 0L) {
                    found += chapter.timeOffset to (chapter.name ?: "")
                }
            }
        }
        if (found.isEmpty() && !titles.isNullOrEmpty() && lengthMs > 0L) {
            // v0.3.78：MP4 chapter track 在 libVLC 3.x 里被映射成 titles，
            // getChapters() 恒空。JUR-857 ffprobe 有 4 章，VLC 却读了 0 章，
            // 走 Title.duration（微秒）累加兜底还原章节起点。
            val offsets = PlaybackBookmarkRules.chapterOffsetsFromTitleDurations(
                titles.map { it.duration },
                lengthMs,
            )
            if (verbose) Log.d(TAG, "probe: title fallback offsets=$offsets")
            for ((i, offset) in offsets.withIndex()) {
                found += offset to (titles.getOrNull(i + 1)?.name ?: "")
            }
        }
        return found.distinctBy { it.first }.sortedBy { it.first }
    }

    private companion object {
        private const val TAG = "JavBossChapterProbe"
        /** 章节元数据可能晚于起播解析：最多等 2 秒 */
        const val MAX_ATTEMPTS = 20
        const val POLL_MS = 100L

        /** chpl box 通常在 moov/udta，JUR-857 实测在文件头 2.7MB 处 */
        const val HEAD_BYTES = 4 * 1024 * 1024
        const val TAIL_BYTES = 2 * 1024 * 1024
        const val MAX_READ = 6 * 1024 * 1024
    }
}
