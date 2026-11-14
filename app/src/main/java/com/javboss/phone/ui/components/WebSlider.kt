package com.javboss.phone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.javboss.phone.data.WebSliderRules

/**
 * 服务端 Web 同款滑块 / 进度条（v0.3.76），v0.3.77 增加章节标记与拖动回调。
 * v0.3.79：按用户参考图把章节标记从「轨道上方白色倒水滴」改为
 * **轨道内与轨道等高的纯黑窄竖缝**，把进度条按章节起点切成若干段；
 * 同时支持调用方覆盖轨道高度 / 滑块大小（播放器进度条更细，调整封面滑块保持原尺寸）。
 *
 * Web 端为原生 `input[type=range]` + accent 主色：**细线轨道**，
 * 已过部分与**圆形滑块**为橙色主色，未过部分为灰色。
 * M3 默认 Slider 的 thumb 是一根竖条 handle（用户反馈「不要现在这种竖线」），
 * 因此这里不用 Slider，直接自绘轨道与滑块，手势语义保持一致：
 * 按下即定位、拖动跟随、越界钳到 0..1、enabled=false 不响应且整体置灰。
 *
 * 数值映射抽到 [WebSliderRules]，播放进度条（PlayerScreen）与调整封面
 * （CoverAdjustDialog）等所有滑块共用本组件。
 *
 * - [markerFractions]：视频内嵌章节在轨道上的位置（0..1），画纯黑竖缝分段；
 *   只有播放进度条传值，调整封面等其它滑块保持默认空，完全不受影响。
 * - [onScrubFraction]：拖动过程中回传当前比例、抬手回传 null，供播放页显示预览缩略图。
 * - [trackHeight] / [thumbSize]：默认走 [WebSliderStyle]；播放器传更小的专用值，
 *   其它滑块不传，保持 v0.3.76 以来的尺寸不变。
 */
object WebSliderStyle {
    /** Web 未完成轨道灰 */
    val Inactive: Color = Color(0xFF6B7280)
    val TrackHeight = 4.dp
    val ThumbSize = 16.dp

    /** 触摸/绘制区域固定 24dp：轨道变细后仍保留足够的手指命中高度 */
    val TouchHeight = 24.dp

    /** v0.3.79 章节标记：与轨道等高的纯黑窄竖缝，把轨道按章节起点切开分段 */
    val ChapterGapWidth = 2.dp
}

@Composable
fun WebSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    markerFractions: List<Float> = emptyList(),
    onScrubFraction: ((Float?) -> Unit)? = null,
    trackHeight: Dp = WebSliderStyle.TrackHeight,
    thumbSize: Dp = WebSliderStyle.ThumbSize,
) {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val fraction = WebSliderRules.fraction(value, start, end)
    val callback = rememberUpdatedState(onValueChange)
    val scrubCallback = rememberUpdatedState(onScrubFraction)
    val active = if (enabled) MaterialTheme.colorScheme.primary else WebSliderStyle.Inactive
    val inactive = WebSliderStyle.Inactive
    val thumbRadius = thumbSize / 2

    BoxWithConstraints(
        modifier = modifier
            .height(WebSliderStyle.TouchHeight)
            .pointerInput(enabled, start, end, thumbSize) {
                if (!enabled) return@pointerInput
                val scope = this
                val thumbPx = thumbSize.toPx()
                fun emit(x: Float) {
                    val v = WebSliderRules.valueFromPointer(
                        x = x,
                        width = scope.size.width.toFloat(),
                        thumbSize = thumbPx,
                        start = start,
                        end = end,
                    )
                    callback.value(v)
                    scrubCallback.value?.invoke(WebSliderRules.fraction(v, start, end))
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    try {
                        emit(down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            emit(change.position.x)
                        }
                    } finally {
                        // 抬手或手势取消都通知播放页收起预览卡片
                        scrubCallback.value?.invoke(null)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 轨道两端各内缩一个滑块半径，滑块中心与轨道端点重合（与浏览器原生 range 一致）
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = thumbRadius)
                .height(trackHeight)
                .clip(RoundedCornerShape(50))
                .background(inactive),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(trackHeight)
                        .clip(RoundedCornerShape(50))
                        .background(active),
                )
            }
            // v0.3.79 需求①：章节标记 = 与轨道等高的纯黑竖缝，按章节起点把轨道切成若干段。
            // 参考样式为橙色轨道被几道黑缝分段；不再使用 v0.3.77 的白色倒水滴标签。
            // 画在轨道之上、圆形滑块之下，黑缝不会切掉滑块；竖缝受轨道圆角 clip 约束不外溢。
            if (markerFractions.isNotEmpty()) {
                Canvas(Modifier.fillMaxSize()) {
                    val gapW = WebSliderStyle.ChapterGapWidth.toPx()
                    val usable = size.width.coerceAtLeast(1f)
                    for (f in markerFractions) {
                        // 首尾端点不画：起点已有滑块，终点贴边会被裁切
                        if (f <= 0f || f >= 1f) continue
                        val cx = usable * f
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(cx - gapW / 2f, 0f),
                            size = Size(gapW, size.height),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .offset(x = (maxWidth - thumbSize) * fraction)
                .size(thumbSize)
                .clip(CircleShape)
                .background(active),
        )
    }
}
