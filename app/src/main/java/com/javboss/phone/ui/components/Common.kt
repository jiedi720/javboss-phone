package com.javboss.phone.ui.components

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.PrivacyMask
import java.time.LocalDate
import kotlin.math.roundToInt
import java.time.Period
import kotlinx.coroutines.delay

/** 秒 → "mm:ss" 或 "h:mm:ss" */
fun formatDuration(totalSec: Long): String {
    if (totalSec <= 0) return ""
    val h = totalSec / 3600
    val m = totalSec % 3600 / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * 毫秒 → 服务端那种「已观看」文案：`1 小时 23 分钟 4 秒`，
 * 不足 1 秒显示「不足 1 秒」，0 返回空串（调用方据此不显示这一行）。
 */
fun formatWatchedTime(ms: Long): String {
    if (ms <= 0) return ""
    val totalSec = ms / 1000
    if (totalSec <= 0) return "不足 1 秒"
    val h = totalSec / 3600
    val m = totalSec % 3600 / 60
    val s = totalSec % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it 小时" },
        m.takeIf { it > 0 }?.let { "$it 分钟" },
        s.takeIf { it > 0 }?.let { "$it 秒" },
    ).joinToString(" ")
}

/** 字节 → "512 MB" / "1.35 GB"（详情页区分同一部作品的多个文件版本） */fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gb >= 1.0) "%.2f GB".format(gb) else "%.0f MB".format(bytes / 1024.0 / 1024.0)
}

/** 图片加载完成前的占位，避免瀑布流跳动 */
@Composable
private fun ImagePlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

/**
 * 把服务端的 `cover_crop_position`（0..1，等于 Web 端 CSS `object-position: p*100% center`）
 * 换算成 Coil 的裁剪锚点：0 → 贴左，0.5 → 居中，1 → 贴右。
 *
 * Web 端封面组件是 `object-cover` + `object-position`，所以 App 用 ContentScale.Crop + Alignment
 * 才能还原同一块可视区域；早期版本固定居中，会把宽幅包装图切在正中间（也就是中缝）。
 */
fun coverCropAlignment(position: Double?): Alignment {
    val p = position?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 1.0
    return BiasAlignment(horizontalBias = (p * 2f - 1f).toFloat(), verticalBias = 0f)
}

// ---------- 封面打码（毛玻璃） ----------

/**
 * API 31+ 才有真正的 RenderEffect 高斯模糊：可以直接糊「已经画出来的那张图」，
 * 不需要重新请求图片，所以解锁的瞬间就是糊的，不会先亮一下原图。
 */
private val coverBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 毛玻璃模糊半径，按卡片宽度取，糊到认不出内容但颜色还能透出来 */
private val COVER_BLUR = 22.dp

/**
 * 低版本（API 26..30）没有 RenderEffect，退化成「小尺寸解码 + 双线性放大」：
 * 像素被平滑开，观感也是毛玻璃而不是马赛克方块。
 */
private const val FROST_DECODE_PX = 28

/**
 * 封面打码状态，在 `MainActivity` 根部一次性算好后用 CompositionLocal 下发。
 *
 * @property masked 现在就要打码吗（设置常开 或 刚锁过屏）
 * @property alwaysOn 设置里的「封面毛玻璃」是否常开（常开时「显示封面」没有意义，不显示入口）
 */
data class CoverMaskState(
    val masked: Boolean = false,
    val alwaysOn: Boolean = false,
)

/**
 * 为什么用 `staticCompositionLocalOf` 而不是「每张卡片各自 collect 一个 Flow」：
 * 后者在锁屏/解锁这种「UI 停帧期间状态变化」的场景下并不可靠——已经组合好的卡片
 * 可能不跟着刷新，表现为「解锁后还是原图，滑动一下才打码」。静态 CompositionLocal
 * 一变，整棵子树强制重组，所见即所得；也保证卡片和顶部提示条永远读的是同一份状态。
 */
val LocalCoverMask = staticCompositionLocalOf { CoverMaskState() }

/** 当前是否要给封面打码：设置页「封面毛玻璃」常开，或者刚锁过屏（[PrivacyMask]） */
@Composable
fun rememberCoverMask(): CoverMaskState {
    val context = LocalContext.current
    val settings = remember(context) {
        (context.applicationContext as JavBossApp).container.settings
    }
    val alwaysOn by settings.coverMaskFlow.collectAsState()
    val locked by PrivacyMask.locked.collectAsState()
    return CoverMaskState(masked = alwaysOn || locked, alwaysOn = alwaysOn)
}

/**
 * 封面加载模型。只有「打码 + 系统不支持 RenderEffect 模糊」时才换成小尺寸请求，
 * 其余情况一律用原地址（不重下、不闪）。
 */
@Composable
fun coverImageModel(url: String, smallDecode: Boolean): Any {
    val context = LocalContext.current
    return remember(url, smallDecode) {
        if (!smallDecode) url
        else ImageRequest.Builder(context)
            .data(url)
            .size(FROST_DECODE_PX, FROST_DECODE_PX)
            .build()
    }
}

/** 给封面加上毛玻璃：API 31+ 走 RenderEffect，低版本无操作（靠小图放大去糊） */
fun Modifier.coverFrost(masked: Boolean): Modifier =
    if (masked && coverBlurSupported) blur(COVER_BLUR) else this

/**
 * 打码时不做任何"关插值"处理：默认的双线性插值会把小图平滑放大，
 * 观感是毛玻璃；一旦改成 `FilterQuality.None` 就变成硬边马赛克了。
 */

/** 视频卡片：16:9 缩略图 + 标题 + 时长 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    thumbnailUrl: String,
    title: String,
    durationSec: Long,
    playCount: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 服务端统计的该视频累计观看时长；> 0 才在封面右上角显示已观看图标 */
    watchedMs: Long = 0,
) {
    Card(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onClick)) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                WatchedBadge(watchedMs)
                val duration = formatDuration(durationSec)
                if (duration.isNotEmpty()) {
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color(0x99000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Column(Modifier.padding(8.dp)) {
                // 与作品卡一样：标题恒定两行，短标题也留出第二行，卡片才能等高对齐
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // 播放次数这行也恒定占位（0 次时填一个空格），否则有无播放记录的卡片会一高一低
                Text(
                    text = if (playCount > 0) "播放 $playCount 次" else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 实体封面裁剪锚点，复刻 Web 端实体卡片「按高度铺满 + 横向左移」的算法：
 *   renderedWidth = 容器高 × 图片宽高比
 *   offset        = clamp(cropLeft × renderedWidth, 0, renderedWidth - 容器宽)
 * 换算成 Coil 的 Alignment：offset=0 → 贴左，=多余宽度 → 贴右。
 *
 * cropLeft 为 null 时表示该卡片在 Web 端就是 `object-cover` 居中（片商/系列），直接返回居中。
 */
fun entityCropAlignment(
    cropLeft: Double?,
    imageSize: Pair<Int, Int>?,
    frameWidth: Float,
    frameHeight: Float,
): Alignment {
    if (cropLeft == null) return Alignment.Center
    if (imageSize == null || frameWidth <= 0f || frameHeight <= 0f) return Alignment.Center
    val (iw, ih) = imageSize
    if (iw <= 0 || ih <= 0) return Alignment.Center
    val rendered = frameHeight * (iw.toFloat() / ih.toFloat())
    if (rendered <= frameWidth) return Alignment.Center
    val excess = rendered - frameWidth
    val offset = (cropLeft.toFloat() * rendered).coerceIn(0f, excess)
    return BiasAlignment(horizontalBias = 2f * offset / excess - 1f, verticalBias = 0f)
}

// ---------- 女优卡片正文的数据行（复刻 Web 端 wT() / xT() / yT() / CT()） ----------

/** 罩杯数字 → "E罩杯"（1=A … 11=K），<=0 视为无值 */
fun cupLabel(cup: Int): String {
    if (cup <= 0) return ""
    val letter = 'A' + (cup - 1).coerceAtMost(25)
    return "${letter}罩杯"
}

/** 三围 → "胸88-腰58-臀86"（三项都有才显示，与 Web 端 xT() 一致） */
fun bwhLabel(bust: Int, waist: Int, hips: Int): String =
    if (bust > 0 && waist > 0 && hips > 0) "胸$bust-腰$waist-臀$hips" else ""

/** 生日 → "1996-03-01（29岁）"；解析失败时原样返回，未来日期不带年龄 */
fun birthWithAgeLabel(birthDate: String, today: LocalDate = LocalDate.now()): String {
    val raw = birthDate.trim().take(10)
    if (raw.isEmpty()) return ""
    val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return birthDate.trim()
    if (date.isAfter(today)) return raw
    return "$raw（${Period.between(date, today).years}岁）"
}

/**
 * 女优卡片封面下方的数据行，**永远是固定两行**：
 *  1. `1996-03-01（30岁）`（没录入 → `生日未录入`）
 *  2. `158cm · 胸88-腰58-臀86 · E罩杯`（都没录入 → `身体数据未录入`）
 *
 * 固定行数是硬要求：行数随数据多少变化会让卡片高矮不一，
 * 瀑布流里就再也对不齐（Web 端是等高网格，看起来才整齐）。
 * 别名不在这两行里显示（Web 端有，但卡片信息栏太挤，用户要求去掉）。
 */
fun idolInfoRows(
    birthDate: String,
    heightCm: Int,
    bust: Int,
    waist: Int,
    hips: Int,
    cup: Int,
    today: LocalDate = LocalDate.now(),
): List<String> {
    val birth = birthWithAgeLabel(birthDate, today).ifBlank { "生日未录入" }
    val body = listOfNotNull(
        heightCm.takeIf { it > 0 }?.let { "${it}cm" },
        bwhLabel(bust, waist, hips).takeIf { it.isNotEmpty() },
        cupLabel(cup).takeIf { it.isNotEmpty() },
    ).joinToString(" · ").ifBlank { "身体数据未录入" }
    return listOf(birth, body)
}

/**
 * 女优卡片：竖版封面 + 左上角「作品 N」角标 + 名字 + 数据行。
 * 宽高比 100:143.1、封面按 `cover_crop_left` 左移，与 Web 端女优卡完全一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdolCard(
    coverUrl: String?,
    name: String,
    secondaryName: String,
    workCount: Int,
    infoRows: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cropLeft: Double? = 0.47,
) {
    var imageSize by remember(coverUrl) { mutableStateOf<Pair<Int, Int>?>(null) }
    val masked = LocalCoverMask.current.masked
    Card(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onClick)) {
        Column {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(100f / 143.1f),
            ) {
                val density = LocalDensity.current
                val frameW = with(density) { maxWidth.toPx() }
                val frameH = with(density) { maxHeight.toPx() }
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverImageModel(coverUrl, masked && !coverBlurSupported),
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        alignment = entityCropAlignment(cropLeft, imageSize, frameW, frameH),
                        onSuccess = { success: AsyncImagePainter.State.Success ->
                            val image = success.result.image
                            imageSize = image.width to image.height
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .coverFrost(masked),
                    )
                } else {
                    ImagePlaceholder(Modifier.fillMaxSize())
                }
                if (workCount > 0) {
                    Text(
                        text = "作品 $workCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xB3000000))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (secondaryName.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = secondaryName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // 固定渲染两行（缺失时是占位文案），保证每张卡片等高、网格能对齐
                infoRows.forEach { row ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = row,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 作品卡片左上角的喜爱度小标签（对齐 Web 端封面左上角那排爱心）。
 *
 * 图标与 Web 端一致用爱心（服务端 MUI Rating 的 icon/emptyIcon 就是心形，主色 #FBBF24）：
 * 已评分是实心心 + 数值，未评分是描边心——Web 在 rating=0 时把胶囊设成 `opacity-0`、
 * 只靠鼠标悬停显形（`group-hover:opacity-100`），手机没有悬停，照搬就等于「未评分的作品
 * 永远看不到评分入口」，所以 0 分这里改成描边心，既提示还没评分，也留出入口。
 */
@Composable
private fun RatingBadge(rating: Double, onClick: () -> Unit) {
    val rated = rating > 0.0
    Row(
        modifier = Modifier
            .padding(6.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (rated) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (rated) "喜爱度 $rating" else "未评分",
            tint = if (rated) Color(0xFFFBBF24) else Color(0xCCFFFFFF),
            modifier = Modifier.size(13.dp),
        )
        if (rated) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = if (rating % 1.0 == 0.0) rating.toInt().toString() else "%.1f".format(rating),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
            )
        }
    }
}

/** 作品封面右下角的播放小圆钮：直接起播，不用先进详情页 */
@Composable
private fun CoverPlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(6.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(0xB3000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "播放",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 作品卡片：2:3 封面 + 番号 + 标题（作品墙/过滤列表/收藏夹条目共用） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkCard(
    coverUrl: String?,
    code: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    cropPosition: Double? = null,
    rating: Double = 0.0,
    onRatingClick: (() -> Unit)? = null,
    /** 服务端统计的该作品累计观看时长（jav.watched_ms）；> 0 才在封面右上角显示已观看图标 */
    watchedMs: Long = 0,
    /** 非 null 时在封面右下角显示播放钮；点击不进入详情，直接起播 */
    onPlay: (() -> Unit)? = null,
) {
    val masked = LocalCoverMask.current.masked
    Card(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick ?: onClick,
        ),
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverImageModel(coverUrl, masked && !coverBlurSupported),
                        contentDescription = code,
                        contentScale = ContentScale.Crop,
                        alignment = coverCropAlignment(cropPosition),
                        modifier = Modifier
                            .fillMaxSize()
                            .coverFrost(masked),
                    )
                } else {
                    ImagePlaceholder(Modifier.fillMaxSize())
                }
                if (onRatingClick != null) {
                    Box(Modifier.align(Alignment.TopStart)) {
                        RatingBadge(rating, onRatingClick)
                    }
                }
                // 服务端已有观看记录才显示（对齐 Web 端的 watch-time-icons / 「已观看：…」）
                WatchedBadge(watchedMs)
                if (code.isNotBlank()) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC000000)),
                                ),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                // 放在番号条之后，保证盖在渐变之上且优先接收点击
                if (onPlay != null) {
                    Box(Modifier.align(Alignment.BottomEnd)) {
                        CoverPlayButton(onPlay)
                    }
                }
            }
            // 标题永远占两行高度（minLines=2）：只有一行标题的卡片也留出第二行，
            // 同一行的卡片底边才对得齐；空标题同样占位，不让卡片塌下去
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }

}

/**
 * 服务端有观看记录才出现的右上角「已观看」徽标（没有记录时整个图标都不画）。
 * 点开在图标旁边浮出一行小字（不是大弹窗），写明该作品/视频在服务端累计的总播放时间，
 * 文案与 Web 端 `已观看：x 小时 y 分钟 z 秒` 一致。
 */
@Composable
private fun BoxScope.WatchedBadge(watchedMs: Long) {
    if (watchedMs <= 0) return
    var showWatched by remember { mutableStateOf(false) }
    val text = "已观看：${formatWatchedTime(watchedMs).ifEmpty { "不足 1 秒" }}"
    val gapPx = with(LocalDensity.current) { 6.dp.roundToPx() }
    // 显示后自动收起；配合点徽标 / 点浮层随时可关，避免小浮层一直挂在封面上
    LaunchedEffect(showWatched) {
        if (showWatched) {
            delay(3000)
            showWatched = false
        }
    }
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { showWatched = !showWatched },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Schedule,
            contentDescription = "已观看",
            tint = Color(0xFF14B8A6),
            modifier = Modifier.size(15.dp),
        )
        if (showWatched) {
            Popup(
                popupPositionProvider = WatchedTooltipPositionProvider(gapPx),
                // 必须给 onDismissRequest：v0.3.25 缺了它，点外部 / 返回键都关不掉
                onDismissRequest = { showWatched = false },
                properties = PopupProperties(
                    // focusable=false 才不拦截外部触摸，列表能照常点击 / 滚动
                    // （v0.3.25 用 true 是「无法点击其他操作」的根因）
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                Box(
                    Modifier
                        .background(Color(0xE61C1C1C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        // 点浮层本身也能收起
                        .clickable { showWatched = false },
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 「已观看」浮层贴在图标左侧（图标在封面右上角），垂直居中对齐 */
private class WatchedTooltipPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    // 注意：Compose 1.7.x 的 PopupPositionProvider.calculatePosition 不是 @Composable，
    // 加了 @Composable 会被当成另一个重载而报「Conflicting overloads」
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.left - popupContentSize.width - gapPx).coerceAtLeast(0)
        val y = anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2
        return IntOffset(x, y)
    }
}

/**
 * 实体卡片：封面（可无）+ 名称 + 副标题（女优/片商/系列视角共用）。
 *
 * 宽高比与裁剪按 Web 端来的：
 * - 女优卡：竖版 100:143.1，封面按高度铺满后按 `cover_crop_left` 左移（见 [entityCropAlignment]）；
 * - 片商/系列卡：横版 800:538，`object-cover` 居中（传 cropLeft = null）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntityCard(
    coverUrl: String?,
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 100f / 143.1f,
    cropLeft: Double? = null,
) {
    // 图片原始尺寸：Web 端要先读 naturalWidth/Height 才能算左移像素，这里同理
    var imageSize by remember(coverUrl) { mutableStateOf<Pair<Int, Int>?>(null) }
    Card(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onClick)) {
        Column {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
            ) {
                val density = LocalDensity.current
                val frameW = with(density) { maxWidth.toPx() }
                val frameH = with(density) { maxHeight.toPx() }
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        alignment = entityCropAlignment(cropLeft, imageSize, frameW, frameH),
                        onSuccess = { success: AsyncImagePainter.State.Success ->
                            val image = success.result.image
                            imageSize = image.width to image.height
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ImagePlaceholder(Modifier.fillMaxSize())
                }
            }
            Column(Modifier.padding(8.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 一部作品挂了多个视频文件时，让用户先选一个。
 * `options` 为 (videoId, 文件名)，`onPick` 收到被选项的 videoId。
 */
@Composable
fun VideoPickDialog(
    title: String,
    hint: String,
    options: List<Pair<Long, String>>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(hint, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                options.forEach { (id, name) ->
                    TextButton(
                        onClick = { onPick(id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 喜爱度编辑：0..5、步长 0.5，与 Web 端封面左上角的星级同一份数据（作品墙与收藏夹共用） */
@Composable
fun RatingDialog(
    code: String,
    current: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var value by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("喜爱度 · $code") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (value > 0.0) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (value <= 0.0) "未评分" else "%.1f 分".format(value),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Slider(
                    value = value.toFloat(),
                    onValueChange = { v -> value = (v * 2f).roundToInt() / 2.0 },
                    valueRange = 0f..5f,
                    steps = 9,
                )
                Text(
                    "0 分等于清除评分；改动会直接写回服务端",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 瀑布流底部状态：加载中转圈 / 到底提示 */@Composable
fun GridFooter(loadingMore: Boolean, endReached: Boolean, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        when {
            loadingMore -> Text("加载中…", style = MaterialTheme.typography.bodySmall)
            endReached && count > 0 -> Text(
                "共 $count 条，已全部加载",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 统一的错误/空态展示 */
@Composable
fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun LabeledValue(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** 排序列表里的一项：仿 Web 端，每行都显示「维度: 方向」（如「发行时间: 近→远」） */
data class SortEntry(val label: String, val selected: Boolean, val direction: String)

/**
 * 与服务端 Web 端一致的纵向排序列表：一行一个维度，左侧「维度: 方向」，右侧 ↑↓ 图标。
 * 点未选中的行切到该维度（用它的默认方向），点已选中的行切换升 / 降序 —— 与 Web 端行为一致。
 * 作品库 / 女优 / 收藏夹以及「排序 + 筛选」合并菜单共用同一套 UI。
 */
@Composable
fun SortList(
    entries: List<SortEntry>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        entries.forEachIndexed { index, entry ->
            val background = if (entry.selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            }
            val foreground = if (entry.selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .clickable { onPick(index) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${entry.label}: ${entry.direction}",
                    color = foreground,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.SwapVert,
                    contentDescription = "切换方向",
                    tint = foreground.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onPick(index) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "点一下切换排序维度，再点一次切换方向",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

