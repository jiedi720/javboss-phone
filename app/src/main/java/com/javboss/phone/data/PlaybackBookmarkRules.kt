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
