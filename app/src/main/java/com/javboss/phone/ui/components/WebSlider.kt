package com.javboss.phone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.javboss.phone.data.WebSliderRules

/**
 * 服务端 Web 同款滑块 / 进度条（v0.3.76），v0.3.77 增加章节标记与拖动回调。
 *
 * Web 端为原生 `input[type=range]` + accent 主色：**4dp 细线轨道**，
 * 已过部分与**16dp 圆形滑块**为橙色主色，未过部分为灰色。
 * M3 默认 Slider 的 thumb 是一根竖条 handle（用户反馈「不要现在这种竖线」），
 * 因此这里不用 Slider，直接自绘轨道与滑块，手势语义保持一致：
 * 按下即定位、拖动跟随、越界钳到 0..1、enabled=false 不响应且整体置灰。
 *
 * 数值映射抽到 [WebSliderRules]，播放进度条（PlayerScreen）与调整封面
 * （CoverAdjustDialog）等所有滑块共用本组件。
 *
 * - [markerFractions]：视频内嵌章节在轨道上的位置（0..1），画白色倒水滴标记；
 *   只有播放进度条传值，调整封面等其它滑块保持默认空，完全不受影响。
 * - [onScrubFraction]：拖动过程中回传当前比例、抬手回传 null，供播放页显示预览缩略图。
 */
object WebSliderStyle {
    /** Web 未完成轨道灰 */
    val Inactive: Color = Color(0xFF6B7280)
    val TrackHeight = 4.dp
    val ThumbSize = 16.dp

    /** 触摸/绘制区域：比 20dp 高 4dp，给轨道上方的章节标记留出空间 */
    val TouchHeight = 24.dp

    /** 章节标记：白色倒水滴（上宽下尖），对齐 MPV 进度条上的章节标记 */
    val MarkerWidth = 7.dp
    val MarkerHeight = 9.dp
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
) {
    val start = valueRange.start
    val end = valueRange.endInclusive
    val fraction = WebSliderRules.fraction(value, start, end)
    val callback = rememberUpdatedState(onValueChange)
    val scrubCallback = rememberUpdatedState(onScrubFraction)
    val active = if (enabled) MaterialTheme.colorScheme.primary else WebSliderStyle.Inactive
    val inactive = WebSliderStyle.Inactive
    val thumbRadius = WebSliderStyle.ThumbSize / 2

    BoxWithConstraints(
        modifier = modifier
            .height(WebSliderStyle.TouchHeight)
            .pointerInput(enabled, start, end) {
                if (!enabled) return@pointerInput
                val scope = this
                val thumbPx = WebSliderStyle.ThumbSize.toPx()
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
                .height(WebSliderStyle.TrackHeight)
                .clip(RoundedCornerShape(50))
                .background(inactive),
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(WebSliderStyle.TrackHeight)
                        .clip(RoundedCornerShape(50))
                        .background(active),
                )
            }
        }
        Box(
            Modifier
                .offset(x = (maxWidth - WebSliderStyle.ThumbSize) * fraction)
                .size(WebSliderStyle.ThumbSize)
                .clip(CircleShape)
                .background(active),
        )
        // 章节标记最后绘制：画在滑块之上，避免倒水滴下半截被 16dp 圆形滑块盖住。
        // 标记本身位于轨道上方，不响应手势，不影响按下即定位 / 拖动跟随。
        if (markerFractions.isNotEmpty()) {
            ChapterMarkers(
                fractions = markerFractions,
                modifier = Modifier.fillMaxWidth().height(WebSliderStyle.TouchHeight),
            )
        }
    }
}

/**
 * 轨道上方的章节标记：白色、上宽下尖的倒水滴，尖端贴近轨道上沿。
 * 轨道两端各内缩半个 thumb，故标记中心 = thumbRadius + usable * fraction。
 */
@Composable
private fun ChapterMarkers(fractions: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val thumbRadiusPx = WebSliderStyle.ThumbSize.toPx() / 2f
        val usable = (size.width - 2f * thumbRadiusPx).coerceAtLeast(1f)
        val markerW = WebSliderStyle.MarkerWidth.toPx()
        val markerH = WebSliderStyle.MarkerHeight.toPx()
        val trackTop = (size.height - WebSliderStyle.TrackHeight.toPx()) / 2f
        val top = trackTop - markerH
        val half = markerW / 2f
        val corner = markerW * 0.16f
        for (f in fractions) {
            // 首尾端点不画：起点已有滑块，终点贴边会被裁切
            if (f <= 0f || f >= 1f) continue
            val cx = thumbRadiusPx + usable * f
            val path = Path().apply {
                moveTo(cx - half + corner, top)
                lineTo(cx + half - corner, top)
                quadraticTo(cx + half, top, cx + half, top + corner)
                lineTo(cx + half, top + markerH - corner * 2f)
                lineTo(cx, top + markerH) // 尖端朝下，贴住轨道上沿
                lineTo(cx - half, top + markerH - corner * 2f)
                lineTo(cx - half, top + corner)
                quadraticTo(cx - half, top, cx - half + corner, top)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}
