package com.javboss.phone.data

/**
 * MP4 / QuickTime `chpl` box 解析（v0.3.78 需求①，JUR-857 实测）。
 *
 * libVLC 3.6.5 对 MP4 的 Nero/QuickTime chapter box 只在 demux 内部解析
 * （logcat: `read box: "chpl" 4 chapters`），Java 层 `getTitles()` 返回 0、
 * `getChapters()` 直接抛异常，应用层拿不到章节。这里改为客户端自己读容器 box。
 *
 * chpl box 结构（大端），从文件 HTTP Range 读回的字节里查找即可：
 * ```
 * size(4) 'chpl'(4) version(1)=1 flags(3) reserved(4) count(1)
 * repeat count:
 *   timestamp(8)            // 100ns 单位，相对视频起点；÷10000 = 毫秒
 *   titleLen(1) title(len)
 * ```
 * 找不到 / 版本不对 / 截断时返回空列表，绝不影响播放。
 */
object Mp4ChapterParser {

    /** 100 纳秒 → 毫秒 */
    private const val NS100_PER_MS = 10_000L

    /** box 固定头：size4 + type4 + version1 + flags3 + reserved4 + count1 */
    private const val HEADER_LEN = 17

    fun parseChpl(bytes: ByteArray): List<PlaybackChapter> {
        val typeIndex = indexOf(bytes, "chpl".toByteArray(Charsets.US_ASCII))
        if (typeIndex < 4) return emptyList()
        val size = readU32(bytes, typeIndex - 4)
        if (size < HEADER_LEN || size > Int.MAX_VALUE) return emptyList()
        val version = bytes[typeIndex + 4].toInt() and 0xFF
        if (version != 1) return emptyList()
        val count = bytes[typeIndex + 12].toInt() and 0xFF
        if (count <= 0) return emptyList()

        val chapters = mutableListOf<PlaybackChapter>()
        var p = typeIndex + 13
        val limit = minOf(bytes.size, typeIndex - 4 + size.toInt())
        for (i in 0 until count) {
            if (p + 9 > limit) break
            var timestamp = 0L
            for (k in 0 until 8) {
                timestamp = (timestamp shl 8) or (bytes[p + k].toLong() and 0xFFL)
            }
            p += 8
            val titleLen = bytes[p].toInt() and 0xFF
            p += 1
            if (p + titleLen > limit) break
            val name = String(bytes, p, titleLen, Charsets.UTF_8)
            p += titleLen
            chapters += PlaybackChapter(timestamp / NS100_PER_MS, name)
        }
        return chapters.sortedBy { it.timeMs }
    }

    private fun indexOf(bytes: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || bytes.size < needle.size) return -1
        outer@ for (i in 0..bytes.size - needle.size) {
            for (j in needle.indices) {
                if (bytes[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun readU32(bytes: ByteArray, at: Int): Long =
        ((bytes[at].toLong() and 0xFF) shl 24) or
            ((bytes[at + 1].toLong() and 0xFF) shl 16) or
            ((bytes[at + 2].toLong() and 0xFF) shl 8) or
            (bytes[at + 3].toLong() and 0xFF)
}
