package com.javboss.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.javboss.phone.data.CoverCropRules
import kotlinx.coroutines.launch

/**
 * 服务端「调整封面」弹窗（v0.3.73）：主页长按 JAV 封面打开。
 *
 * 对齐 Web 端 `KE` 组件：显示宽幅封面原图，用白色裁切框标出竖版封面的可视区域，
 * 可以**在图上按位置拖动**或用**滑块**调整 `cover_crop_position`（0..1，默认 1＝靠右），
 * 底部「重置 / 取消 / 保存」；保存走 `PUT /jav/items/{id}` body `{cover_crop_position: r}`。
 *
 * 裁切框几何、拖动反解、百分比全部抽到 [CoverCropRules]（对应 Web `oT` / `aT`），
 * 保证这里和列表卡片 [coverCropAlignment] 用的是同一套服务端语义。
 *
 * 说明：这是编辑预览，与「查看完整封面」大图一样用清晰原图，不套浏览态的毛玻璃打码，
 * 否则开了打码就看不清要裁哪一块。
 */
@Composable
fun CoverAdjustDialog(
    code: String,
    coverUrl: String,
    initialPosition: Double?,
    onDismiss: () -> Unit,
    /** suspend 保存；返回 null＝成功（弹窗自行关闭），非 null＝错误文案（留在弹窗里重试） */
    onSave: suspend (Double) -> String?,
) {
    var position by remember(initialPosition) {
        mutableStateOf(CoverCropRules.normalize(initialPosition))
    }
    // 宽幅原图的真实像素尺寸；拿到之后才能按 iT 算出裁切框（Web `oT` 依赖图片宽高比）
    var imageSize by remember(coverUrl) { mutableStateOf<Pair<Int, Int>?>(null) }
    var imageFailed by remember(coverUrl) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val window = imageSize?.let { (w, h) -> CoverCropRules.cropWindow(w, h, position) }
    val canAdjust = window?.adjustable == true && !imageFailed
    val ratio = imageSize?.let { (w, h) -> w.toFloat() / h.toFloat() } ?: (800f / 538f)

    /** 把触点 x（容器内像素）换算成 0..1 位置，规则里含 clamp 与不可调时的保持逻辑 */
    fun applyDrag(xPx: Float, containerWidthPx: Int) {
        if (!canAdjust || containerWidthPx <= 0) return
        val dims = imageSize ?: return
        val current = CoverCropRules.cropWindow(dims.first, dims.second, position) ?: return
        position = CoverCropRules.positionFromDrag(
            xPx / containerWidthPx.toDouble(),
            current,
            position,
        )
    }

    AlertDialog(
        modifier = Modifier.adaptiveTagDialogWidth(),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("调整封面 · $code") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "拖动裁切区域或使用滑块调整竖版封面。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))

                // 宽幅原图：按真实宽高比显示，但最多 260dp 高，横屏/宽窗口也不会把弹窗顶穿。
                // 拖动坐标基于这个图片容器本身，而不是整块弹窗，避免宽屏下换算错位。
                BoxWithConstraints(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val imageWidth = minOf(maxWidth, 260.dp * ratio)
                    val imageHeight = imageWidth / ratio
                    Box(
                        Modifier
                            .width(imageWidth)
                            .height(imageHeight)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(canAdjust) {
                                if (!canAdjust) return@pointerInput
                                // 与 RatingDialog 同款：按下即定位、拖动连续跟随；
                                // 用 awaitEachGesture 手写，避开 detectDragGestures 的 touchSlop 延迟。
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    applyDrag(down.position.x, size.width)
                                    down.consume()
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break
                                        if (!change.pressed) break
                                        applyDrag(change.position.x, size.width)
                                        change.consume()
                                    }
                                }
                            },
                    ) {
                        AsyncImage(
                            model = coverImageModel(coverUrl, false),
                            contentDescription = "$code 完整封面",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                            onSuccess = { success: AsyncImagePainter.State.Success ->
                                val image = success.result.image
                                imageSize = image.width to image.height
                                imageFailed = false
                            },
                            onError = { imageFailed = true },
                        )
                        // 裁切框外压暗 + 白色描边框（Web shadow-[0_0_0_999px_rgba(15,23,42,.75)]）
                        window?.let { crop ->
                            Box(
                                Modifier.fillMaxSize().drawBehind {
                                    val w = size.width
                                    val h = size.height
                                    val left = (crop.left * w).toFloat()
                                    val top = (crop.top * h).toFloat()
                                    val right = ((crop.left + crop.width) * w).toFloat()
                                    val bottom = ((crop.top + crop.height) * h).toFloat()
                                    val dim = Color(0xBF0F172A)
                                    drawRect(dim, Offset(0f, 0f), Size(w, top))
                                    drawRect(dim, Offset(0f, bottom), Size(w, h - bottom))
                                    drawRect(dim, Offset(0f, top), Size(left, bottom - top))
                                    drawRect(dim, Offset(right, top), Size(w - right, bottom - top))
                                    drawRect(
                                        Color.White.copy(alpha = 0.10f),
                                        Offset(left, top),
                                        Size(right - left, bottom - top),
                                    )
                                    drawRect(
                                        Color.White,
                                        Offset(left, top),
                                        Size(right - left, bottom - top),
                                        style = Stroke(width = 2f * density),
                                    )
                                },
                            )
                        }
                        if (imageSize == null && !imageFailed) {
                            Text(
                                "封面加载中…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        if (imageFailed) {
                            Text(
                                "封面加载失败",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("位置", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = position.toFloat(),
                        onValueChange = {
                            if (canAdjust) position = CoverCropRules.normalize(it.toDouble())
                        },
                        valueRange = 0f..1f,
                        enabled = canAdjust && !saving,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${CoverCropRules.percent(position)}%",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(44.dp),
                    )
                }
                if (!canAdjust && imageSize != null && !imageFailed) {
                    Text(
                        "这张封面横向没有裁切余地，无需调整。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { position = CoverCropRules.DEFAULT_POSITION },
                        enabled = canAdjust && !saving,
                    ) { Text("重置") }
                }
                error?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && window != null,
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        val message = onSave(position)
                        saving = false
                        if (message == null) onDismiss() else error = message
                    }
                },
            ) { Text(if (saving) "保存中…" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
        },
    )
}
