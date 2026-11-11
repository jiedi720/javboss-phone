package com.javboss.phone.data

/**
 * 大图查看器（完整封面 / 样品图像 / 视频截图）左右翻页的手势与动画参数。
 *
 * 纯逻辑、不依赖 Android / Compose 类型，方便在 JVM 单测里锁死规则。
 *
 * v0.3.68 之前阈值按「图片容器宽度 × 18%」算。容器宽度在竖屏约 360dp、
 * 横屏被限到 1100dp，于是竖屏只要拖 65dp、横屏要拖近 200dp 才翻一张，
 * 横版明显比竖版吃力。改成按「容器短边」算并加上下限后：
 * - 竖屏手机 360×650 → 65dp（与旧行为一致，手感不变）
 * - 横屏手机 780×360 → 65dp（短边仍是 360dp）
 * - 平板横屏 1100×704 → 上限 84dp（不再随容器变宽线性变难）
 */
object ImagePagerRules {

    /** 翻页阈值占容器短边的比例（沿用旧的 18%） */
    const val SWIPE_RATIO = 0.18f

    /** 阈值下限：窄窗口里不至于轻轻一点就翻页 */
    const val MIN_SWIPE_DP = 56f

    /** 阈值上限：宽窗口 / 横屏不允许因为容器变宽而要拖更远 */
    const val MAX_SWIPE_DP = 84f

    /**
     * 翻出动画时长（当前图滑出屏幕）。
     * v0.3.69 从 180ms 收到 110ms：拖动本身已经跟手，松手后再慢悠悠滑出会显得拖沓。
     */
    const val PAGE_OUT_MS = 110

    /** 翻入动画时长（新图从反方向滑到正中）。v0.3.69 从 220ms 收到 140ms */
    const val PAGE_IN_MS = 140

    /**
     * 翻页所需的最小横向拖动距离（dp）。
     * 容器尺寸非法（<= 0，首帧还没测量出来）时回落到下限，避免阈值为 0 导致误翻。
     */
    fun swipeThresholdDp(containerWidthDp: Float, containerHeightDp: Float): Float {
        val width = if (containerWidthDp > 0f) containerWidthDp else 0f
        val height = if (containerHeightDp > 0f) containerHeightDp else 0f
        val shortSide = if (width < height) width else height
        return (shortSide * SWIPE_RATIO).coerceIn(MIN_SWIPE_DP, MAX_SWIPE_DP)
    }

    /**
     * 松手时决定翻到第几张：向左拨（总位移为负）→ 下一张，向右拨 → 上一张，
     * 没过阈值或已经到头就停在 [index]。
     *
     * 抽成纯函数的原因：这段判定曾经写在手势回调里，慢速滑动被拆成多段手势时会连续
     * 翻两页；判定独立出来后可以单测边界（首尾不越界、刚好等于阈值、负阈值等）。
     */
    fun resolveTarget(
        totalDxPx: Float,
        thresholdPx: Float,
        index: Int,
        size: Int,
    ): Int {
        if (size <= 1 || thresholdPx <= 0f) return index
        val delta = when {
            totalDxPx <= -thresholdPx -> 1
            totalDxPx >= thresholdPx -> -1
            else -> 0
        }
        if (delta == 0) return index
        val target = index + delta
        return if (target in 0 until size) target else index
    }

    /** 点按翻页的左右热区宽度占比（v0.3.68，仅横版大图查看器启用） */
    const val TAP_EDGE_RATIO = 0.3f

    /**
     * 点按左右侧时的目标索引（v0.3.68）：左侧 30% → 上一张，右侧 30% → 下一张，
     * 中间 40% 不翻页；已经在首 / 尾时点同侧保持不动。
     */
    fun tapTarget(tapX: Float, containerWidth: Float, index: Int, size: Int): Int {
        if (size <= 1 || containerWidth <= 0f) return index
        return when {
            tapX <= containerWidth * TAP_EDGE_RATIO -> if (index > 0) index - 1 else index
            tapX >= containerWidth * (1f - TAP_EDGE_RATIO) ->
                if (index < size - 1) index + 1 else index
            else -> index
        }
    }
}
