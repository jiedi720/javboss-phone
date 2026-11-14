package com.javboss.phone.data

/**
 * 视频源文件内嵌章节（MKV / MP4 chapters），时间点为距片头的毫秒数。
 * 对应 MPV 章节列表里的 `[00:16:30.838] Chapter 2`。
 */
data class PlaybackChapter(
    val timeMs: Long,
    val name: String = "",
)

/**
 * 播放进度条上的章节标记位置（v0.3.77 需求①）。
 *
 * 标记画在 [WebSlider] 的轨道上方（白色倒水滴/倒三角），位置由「章节时间点 / 总时长」
 * 得到。这里把时间点列表转成 0..1 的比例，纯函数便于 JVM 单测锁住：
 * - 片头 t=0 不画标记（轨道起点已有滑块，MPV 同样不显示）；
 * - 负数、超出总时长、总时长未知时丢弃；
 * - 多个 title 里时间相近的章节合并成一个标记；
 * - 结果排序并钳制到 0..1。
 */
object PlaybackBookmarkRules {

    /** 相距小于该值的章节视为同一点，避免重复标记挤在一起 */
    const val MERGE_WINDOW_MS = 1_000L

    /**
     * 把 libVLC `Chapter.timeOffset` 归一成毫秒（v0.3.78 修复需求①）。
     *
     * 原生 libVLC 的 chapter 时间偏移是**微秒**，但不同构建/Java 绑定层可能已折算成毫秒。
     * 上一版直接当毫秒用，微秒值远大于总时长，被 [fractions] 的 `it < durationMs` 越界过滤
     * 全部丢掉，章节标记因此一个都不显示。这里用媒体总时长（毫秒）作参照自动判断：
     * 最大偏移明显超过总时长时按微秒除以 1000，否则原样返回。
     */
    fun normalizeTimeMs(offsets: List<Long>, durationMs: Long): List<Long> {
        if (offsets.isEmpty()) return emptyList()
        val max = offsets.max()
        val scale = if (durationMs > 0L && max > durationMs * 2L) 1_000L else 1L
        return offsets.map { it / scale }
    }

    /**
     * MP4 chapter track 的兜底还原（v0.3.78，JUR-857 实测）。
     *
     * libVLC 3.6.5 对 MP4 的 QuickTime chapter track 不通过 `getChapters()` 暴露，
     * 而是映射成多个 title，`MediaPlayer.Title.duration` 是**每章时长（微秒）**。
     * 累加前缀即可还原各章起点（首章起点 0 不返回，与 [fractions] 忽略 t<=0 一致）。
     *
     * 只有「微秒总和 / 1000 与媒体总时长同量级（±20%）」才采信：单位若是毫秒、
     * 或 titles 其实是 DVD 菜单而非章节，总和会对不上，直接返回空避免画错标记。
     * 返回的偏移仍交由 [normalizeTimeMs] 统一归一成毫秒。
     */
    fun chapterOffsetsFromTitleDurations(durations: List<Long>, durationMs: Long): List<Long> {
        if (durations.size < 2 || durationMs <= 0L) return emptyList()
        if (durations.any { it <= 0L }) return emptyList()
        val totalUs = durations.fold(0L) { acc, d -> acc + d }
        val totalMs = totalUs / 1_000L
        if (totalMs < durationMs * 8 / 10 || totalMs > durationMs * 12 / 10) return emptyList()
        val offsets = mutableListOf<Long>()
        var acc = 0L
        for (i in 1 until durations.size) {
            acc += durations[i - 1]
            offsets += acc
        }
        return offsets
    }

    fun fractions(chapters: List<PlaybackChapter>, durationMs: Long): List<Float> {
        if (durationMs <= 0L) return emptyList()
        val merged = chapters
            .map { it.timeMs }
            .filter { it > 0L && it < durationMs }
            .sorted()
            .fold(mutableListOf<Long>()) { acc, time ->
                if (acc.isEmpty() || time - acc.last() >= MERGE_WINDOW_MS) acc.add(time)
                acc
            }
        return merged.map { (it.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f) }
    }
}
