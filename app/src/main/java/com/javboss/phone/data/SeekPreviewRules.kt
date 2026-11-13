package com.javboss.phone.data

/**
 * 拖动进度条时预览缩略图的时间点计算（v0.3.77 需求②）。
 *
 * 服务端没有按时间点的预览图接口（Web 端「进度条预览」只在本机 MPV + thumbfast 下可用），
 * 客户端用 MediaMetadataRetriever 本地取帧；取帧要重新 setDataSource + seek 解码，代价高，
 * 因此按 [THROTTLE_MS] 把目标时间量化成桶：拖动过程中只要仍在同一桶就不重复取帧。
 */
object SeekPreviewRules {

    /** 预览帧的时间粒度：同一秒内拖动只取一次帧 */
    const val THROTTLE_MS = 1_000L

    /** 拖动比例 → 目标时间点；总时长未知时返回 0，调用方据此跳过取帧。 */
    fun timeMs(fraction: Float, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return (fraction.coerceIn(0f, 1f) * durationMs).toLong().coerceIn(0L, durationMs)
    }

    /** 时间点量化到桶，用来判断是否需要重新取帧。 */
    fun bucket(timeMs: Long, intervalMs: Long = THROTTLE_MS): Long =
        if (intervalMs <= 0L) timeMs else timeMs / intervalMs

    /** 与上一次取帧的桶不同才需要取帧（null 表示从未取过）。 */
    fun shouldFetch(lastBucket: Long?, currentBucket: Long): Boolean = lastBucket != currentBucket
}
