package com.javboss.phone.data

/**
 * 服务端 Web 同款进度条 / 滑块的数值映射（v0.3.76）。
 *
 * Web 端是原生 `input[type=range]` + accent 主色：细线轨道，轨道两端各内缩半个滑块宽度，
 * 滑块中心在 [radius, width - radius] 之间线性对应 valueRange。
 * 这里把「value → 轨道比例」与「触点 x → value」抽成纯函数，便于 JVM 单测锁住越界钳制
 * 与退化区间（duration 未知、组件宽度极窄）时的行为。
 */
object WebSliderRules {

    /** value 在 [start, end] 内的比例，钳到 0..1；区间退化时返回 0。 */
    fun fraction(value: Float, start: Float, end: Float): Float {
        val span = end - start
        return if (span <= 0f) 0f else ((value - start) / span).coerceIn(0f, 1f)
    }

    /** 触点 x（相对组件左边，px）→ value，轨道两端各内缩 thumbSize/2。 */
    fun valueFromPointer(
        x: Float,
        width: Float,
        thumbSize: Float,
        start: Float,
        end: Float,
    ): Float {
        val radius = thumbSize / 2f
        val usable = (width - 2f * radius).coerceAtLeast(1f)
        val f = ((x - radius) / usable).coerceIn(0f, 1f)
        return start + f * (end - start)
    }
}
