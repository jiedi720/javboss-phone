@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.javboss.phone.ui.player

import android.app.Activity
import android.graphics.Bitmap
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javboss.phone.data.api.ApiClient
import com.javboss.phone.data.EngineMode
import com.javboss.phone.data.PlaybackBookmarkRules
import com.javboss.phone.data.PlaybackChapter
import com.javboss.phone.data.SeekPreviewRules
import com.javboss.phone.data.PlayerOrientation
import com.javboss.phone.data.SeekSensitivity
import com.javboss.phone.data.WindowSizeRules
import com.javboss.phone.ui.components.adaptivePlayerStageWidth
import com.javboss.phone.ui.components.coverCropAlignment
import com.javboss.phone.ui.components.coverImageModel
import com.javboss.phone.ui.components.formatDuration
import com.javboss.phone.ui.components.rememberWindowHeightDp
import com.javboss.phone.ui.components.rememberWindowWidthDp
import com.javboss.phone.ui.components.WebSlider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** v0.3.79：播放器进度条比通用滑块更细、圆形滑块更小（用户反馈「黑底的播放进度条调小一点」）。
 *  调整封面等其它滑块不传这两个值，保持 v0.3.76 以来的尺寸不变。 */
private val PlayerTrackHeight = 3.dp
private val PlayerThumbSize = 12.dp

/** v0.3.80：预览图下沿停在轨道上沿再上 2dp 安全缝；配合浮层 zIndex(-1f)，
 *  控制条（含进度条）永远画在预览图之上，不会再出现「预览图挡住进度条」。 */
private val PreviewTrackGap = 2.dp

/**
 * 手机 / 平板播放页：保留现有双引擎与鉴权代理，只使用自绘触控层。
 * Compact 窗口进入时先竖屏播放：窗口宽度恒等于屏宽（16:9 横向铺满、无左右留白），
 * 背景是一整张毛玻璃化的封面，按住顶部把手只能上下拖动；
 * 点控件条右侧的全屏按钮才切横屏沉浸。
 * 宽横向窗口（平板横屏、宽自由窗口）首次直接全屏沉浸，且不再对平板强制 PORTRAIT。
 * 退出播放页恢复进来前的方向。
 * 左上始终可返回，左中打开设置，右中锁定；锁定时只保留返回与解锁。
 */
@Composable
fun PlayerScreen(
    videoId: Long,
    /** 列表页传来的番号：用它拼出的封面与 JAV 界面 WorkCard 完全同源 */
    javCode: String? = null,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state = viewModel.uiState
    // 布局结论一律只看当前窗口实际宽高，不看设备型号 / 是否平板 / smallestScreenWidthDp。
    // 窗口够宽且是横向窗口（平板横屏、宽自由窗口、手机横屏达到阈值）首次直接全屏沉浸；
    // 平板竖屏、窄分屏仍走竖屏小窗。只在首次组合算一次，之后由 fullscreen 状态自己管。
    val windowWidthDp = rememberWindowWidthDp()
    val windowHeightDp = rememberWindowHeightDp()
    val startFullscreen = remember {
        WindowSizeRules.startFullscreen(windowWidthDp, windowHeightDp)
    }
    // v0.3.57：方向判定只在进播放页时冻结一次，绝不随旋转后的窗口宽度重算。
    // 旧实现把 windowWidthDp 放进方向 effect 的 key：竖屏手机点全屏转横屏后宽度变大，
    // isCompact() 变 false → 方向被改成 UNSPECIFIED → 系统拧回竖屏 → 又设回横屏，抖环。
    val compactAtEntry = remember { WindowSizeRules.isCompact(windowWidthDp) }
    var fullscreen by remember { mutableStateOf(startFullscreen) }
    val previousOrientation = remember(activity) { activity?.requestedOrientation }

    LaunchedEffect(videoId, javCode) { viewModel.load(videoId, javCode) }

    DisposableEffect(Unit) {
        onDispose {
            // 离开播放页：结束会话并把最后一段观看时长补报给服务端
            viewModel.stopPlaybackReporter()
            activity?.window?.let { window ->
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            activity?.requestedOrientation =
                previousOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 首次组合与用户切换全屏时统一设置方向 / 系统栏：
    // 宽横向窗口首次 fullscreen 即为 true → 直接沉浸全屏；
    // 手机 / 平板竖屏首次为 false → 竖屏小窗 + 显示系统栏。
    // Compact 才锁方向；平板 / 宽窗口一律不强制 PORTRAIT，避免分屏、自由窗口被拧成竖屏。
    //
    // v0.3.57 把「方向」与「系统栏」拆成两个 effect：
    // - 方向只看进页时冻结的 compactAtEntry，key 里刻意不含 windowWidthDp / windowHeightDp；
    //   旋转 / 分屏 / 自由窗口改变窗口宽度时绝不重新判定方向，抖环就此消失。
    // - 系统栏跟随 fullscreen，并在窗口尺寸变化后重新应用（部分 ROM 在配置变更后
    //   会把已隐藏的系统栏重新显示出来），但它不影响方向。
    LaunchedEffect(fullscreen, compactAtEntry) {
        activity?.requestedOrientation = when (
            WindowSizeRules.playerOrientation(fullscreen, compactAtEntry)
        ) {
            PlayerOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            PlayerOrientation.SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            PlayerOrientation.UNSPECIFIED -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(fullscreen, windowWidthDp, windowHeightDp) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // 背景异常全黑的根因：Theme 的 background 是 #121212、themes.xml 的 windowBackground
        // 又是纯黑，而竖屏外层此前直接用了主题底色，stage 再叠一块 Color.Black。
        // 这里改为铺一层不透明的毛玻璃封面（全屏才用纯黑舞台），把这两种黑都盖住。
        if (fullscreen) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        } else {
            FrostedCoverBackdrop(state.coverUrl, state.thumbnailUrl)
        }

        val density = LocalDensity.current
        // 竖屏窗口宽度恒等于屏宽（fillMaxWidth），16:9 高度随屏宽算，任何屏宽都成立。
        // 只允许上下拖动：X 位移永远为 0，也没有任何改变宽度的手势。
        val handleHeight = if (fullscreen) 0.dp else DRAG_HANDLE_HEIGHT
        val windowHeight = handleHeight + maxWidth * 9f / 16f
        val verticalRoom = ((maxHeight - windowHeight) / 2f - 8.dp).let { if (it < 0.dp) 0.dp else it }
        val maxOffsetY = with(density) { verticalRoom.toPx() }
        var dragY by rememberSaveable { mutableFloatStateOf(0f) }

        when {
            state.loading -> Text("加载播放源…", color = Color.White)
            state.error != null -> Text("无法播放：${state.error}", color = MaterialTheme.colorScheme.error)
            else -> Column(
                Modifier
                    // Compact 不加任何限宽：宽度恒等于手机屏宽，16:9 横向铺满（旧行为）。
                    // 宽窗口（平板 / 手机横屏达阈值 / 自由窗口）按窗口宽度收窄居中，
                    // 不让 16:9 画面在 11 寸屏上拉到一米宽。
                    .then(
                        if (fullscreen) Modifier.fillMaxSize()
                        else Modifier.adaptivePlayerStageWidth().fillMaxWidth()
                    )
                    .offset {
                        // offset 只改绘制位置、不改测量宽度；X 恒为 0，拖动过程宽度不变
                        if (fullscreen) IntOffset.Zero
                        else IntOffset(0, dragY.coerceIn(-maxOffsetY, maxOffsetY).roundToInt())
                    },
            ) {
                if (!fullscreen) {
                    // 把手独立于视频区域，播放器原有的 seek / 亮度 / 音量 / 双击手势完全不受影响
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(DRAG_HANDLE_HEIGHT)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .pointerInput(maxOffsetY) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    // 只取 dy：左右拖动被完全忽略，宽度不随手势变化
                                    dragY = (dragY + dragAmount.y).coerceIn(-maxOffsetY, maxOffsetY)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)),
                        )
                    }
                }
                UnifiedPlayerStage(
                    urls = state.urls,
                    engineMode = state.engineMode,
                    activeEngine = state.activeEngine,
                    autoFallback = state.engineMode == EngineMode.AUTO,
                    startPositionMs = state.fallbackPositionMs,
                    seekSensitivity = state.seekSensitivity,
                    cookieHeader = viewModel.cookieHeader,
                    proxyUrl = viewModel::proxyUrl,
                    videoId = videoId,
                    apiClient = viewModel.apiClient,
                    fullscreen = fullscreen,
                    onToggleFullscreen = { fullscreen = !fullscreen },
                    onSwitchToVlc = viewModel::switchToVlc,
                    onEngineModeChange = viewModel::selectEngineMode,
                    onSeekSensitivityChange = viewModel::selectSeekSensitivity,
                    onPlaybackState = viewModel::onPlaybackState,
                    onAllSourcesFailed = { viewModel.failPlayback("所有播放源均播放失败") },
                    onBack = onBack,
                )
            }
        }
    }
}

/** 竖屏窗口顶部拖动把手高度（宽度仍是整屏） */
private val DRAG_HANDLE_HEIGHT = 22.dp

/** 背景的适度模糊：比隐私打码的 22dp 轻，仍看得出封面轮廓与颜色 */
private val BACKDROP_BLUR = 16.dp

/** 半透明质感：封面只轻微透出底下的中性色，主体仍是 JAV 界面那张封面 */
private const val BACKDROP_ALPHA = 0.90f

/** 压暗遮罩：保证把手、加载提示、控制条清晰可读；别压太狠，否则又变成一片黑 */
private const val BACKDROP_SCRIM = 0.32f

/**
 * 竖屏背景：只铺「JAV 界面同一张封面」（同一个 javCoverUrl(code)），适度毛玻璃 + 半透明。
 * 之前同时在底下垫视频缩略图、又给整层加透明度，两层图叠出重影，看起来就不是 JAV 封面了；
 * 现在同一时刻只画一张图：有番号用 JAV 封面，实在没有番号（普通视频）才用视频缩略图。
 * 半透明质感改由封面之上的半透明渐变罩实现，不再让另一张图透出来。
 * API 31+ 用 RenderEffect 模糊，API 26–30 退化成 28px 小解码 + 双线性放大。
 * 只对竖屏播放页生效：横屏仍是纯黑舞台，其它界面完全不动。
 */
@Composable
private fun FrostedCoverBackdrop(coverUrl: String?, thumbnailUrl: String?) {
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val imageUrl = coverUrl ?: thumbnailUrl
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        if (imageUrl != null) {
            FrostedBackdropImage(imageUrl, blurSupported)
        }
        // 半透明质感 + 压暗：上半轻、下半重，前景播放器与控制条清晰可读
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.12f),
                        Color.Black.copy(alpha = BACKDROP_SCRIM),
                    ),
                ),
            ),
        )
    }
}

@Composable
private fun FrostedBackdropImage(url: String, blurSupported: Boolean) {
    AsyncImage(
        model = coverImageModel(url, !blurSupported),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        // 与 JAV 列表卡片同一套裁切基准（cover_crop_position 默认 1 = 靠右），
        // 保证背景取到的是同一张封面的同一侧，不会看起来像另一张图
        alignment = coverCropAlignment(null),
        modifier = Modifier
            .fillMaxSize()
            .alpha(BACKDROP_ALPHA)
            .then(if (blurSupported) Modifier.blur(BACKDROP_BLUR) else Modifier),
    )
}

@Composable
private fun UnifiedPlayerStage(
    urls: List<String>,
    engineMode: EngineMode,
    activeEngine: EngineMode,
    autoFallback: Boolean,
    startPositionMs: Long,
    seekSensitivity: SeekSensitivity,
    cookieHeader: String,
    proxyUrl: (String) -> String,
    videoId: Long,
    apiClient: ApiClient,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onSwitchToVlc: (Long) -> Unit,
    onEngineModeChange: (EngineMode, Long) -> Unit,
    onSeekSensitivityChange: (SeekSensitivity) -> Unit,
    onPlaybackState: (Boolean) -> Unit,
    onAllSourcesFailed: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val window = activity?.window
    val density = LocalDensity.current
    val cutoutStartPadding = with(density) {
        val inset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.decorView?.rootWindowInsets?.displayCutout?.safeInsetLeft ?: 0
        } else 0
        inset.toDp()
    }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val engine = remember(activeEngine) {
        when (activeEngine) {
            EngineMode.VLC -> VlcPlayerEngine(context)
            else -> ExoPlayerEngine(context, cookieHeader)
        }
    }
    var surfaceReady by remember(activeEngine) { mutableStateOf(activeEngine != EngineMode.VLC) }
    var sourceIndex by remember(activeEngine) { mutableIntStateOf(0) }
    var resumePositionMs by remember(activeEngine) { mutableLongStateOf(startPositionMs) }
    var controlsVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var subtitlesAvailable by remember { mutableStateOf(false) }
    var subtitlesEnabled by remember { mutableStateOf(false) }
    // v0.3.78：章节来自与引擎无关的 VlcChapterProbe（默认 ExoPlayer 下也能读到），
    // 拖动进度条的预览帧由独立协程取帧（不会被手势变化取消）。
    var chapters by remember(activeEngine) { mutableStateOf(emptyList<PlaybackChapter>()) }
    var probedChapters by remember(urls, sourceIndex) {
        mutableStateOf(emptyList<PlaybackChapter>())
    }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var gestureSeekTarget by remember { mutableLongStateOf(0L) }
    var speedBoost by remember { mutableStateOf(false) }
    var brightness by remember { mutableStateOf(window?.screenBrightness().orDefaultBrightness()) }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    // 手势协程不能随轮询进度、亮度或音量更新而重启；通过这些状态引用读取最新值。
    val currentEngine by rememberUpdatedState(engine)
    val currentPositionMs by rememberUpdatedState(positionMs)
    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentBrightness by rememberUpdatedState(brightness)
    val currentSeekFactor by rememberUpdatedState(seekSensitivity.factor)

    DisposableEffect(window) {
        val previousBrightness = window?.screenBrightness()
        onDispose {
            if (window != null && previousBrightness != null) window.setBrightness(previousBrightness)
        }
    }

    DisposableEffect(activeEngine) {
        engine.onError = {
            resumePositionMs = engine.positionMs
            if (sourceIndex + 1 < urls.size) {
                sourceIndex += 1
            } else if (autoFallback && activeEngine != EngineMode.VLC) {
                onSwitchToVlc(engine.positionMs)
            } else {
                onAllSourcesFailed()
            }
        }
        onDispose { engine.release() }
    }

    // v0.3.57 禁止后台播放：Activity 真正不可见（按 Home / 切到别的 App / 锁屏）时
    // 立刻暂停引擎，并把「真实在播」置 false，让观看时长上报协程立即补报最后一次。
    // 只监听 ON_STOP、不监听 ON_PAUSE：下拉通知栏 / 系统弹窗等轻微失焦不该打断播放。
    // 切回前台不自动续播，用户点播放按钮再继续。
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        currentEngine.setPlaying(false)
        onPlaybackState(false)
        isPlaying = false
    }

    BackHandler {
        engine.stop()
        onBack()
    }

    LaunchedEffect(urls, sourceIndex, activeEngine, surfaceReady) {
        if (surfaceReady && sourceIndex < urls.size) {
            val raw = urls[sourceIndex]
            engine.play(if (activeEngine == EngineMode.VLC) proxyUrl(raw) else raw, resumePositionMs)
        }
    }

    LaunchedEffect(activeEngine) {
        while (true) {
            isPlaying = engine.isPlaying
            isBuffering = engine.isBuffering
            positionMs = engine.positionMs
            durationMs = engine.durationMs
            subtitlesAvailable = engine.subtitlesAvailable
            subtitlesEnabled = engine.subtitlesEnabled
            // VLC 引擎自己读到的章节优先（更及时）；默认 ExoPlayer 引擎用静默探测结果
            chapters = engine.chapters.takeIf { it.isNotEmpty() } ?: probedChapters
            onPlaybackState(isPlaying)   // 只有真正在播的时间才计入服务端观看时长
            if (isPlaying && positionMs > 0) resumePositionMs = positionMs
            delay(500)
        }
    }

    // v0.3.78 需求①：Media3 1.5.1 不暴露容器 chapters，默认 ExoPlayer 引擎下
    // engine.chapters 恒为空。用独立、静默的 libVLC 对当前播放地址探测一次章节，
    // 不改用户引擎偏好、不参与播放；失败返回空列表，不影响播放。
    val chapterProbe = remember(context) { VlcChapterProbe(context, apiClient.okHttpClient) }
    LaunchedEffect(urls, sourceIndex, activeEngine) {
        val raw = urls.getOrNull(sourceIndex) ?: return@LaunchedEffect
        // v0.3.78：静默 libVLC 探测也必须走本地鉴权代理。直接用不带 Cookie 的原始
        // 地址会被服务端 401（logcat: "libvlc stream: HTTP 401 error"），
        // 默认 ExoPlayer 下 chapters 因此恒为空 —— 这正是需求①上一版没落地的原因。
        probedChapters = chapterProbe.probe(proxyUrl(raw))
    }

    // v0.3.78 需求②：取帧必须用**不会被 scrubFraction 变化取消**的协程。
    // 上一版把 delay(150) + load() 放进以 scrubFraction 为 key 的 LaunchedEffect：
    // 拖动时比例高频变化 → effect 每次被取消重启 → delay 永远等不完 → 一帧都取不到，
    // 抬手又变 null 清空，所以只剩「预览加载中…」。现在改为回调里按秒级分桶节流，
    // 每个桶起一个独立协程，跑完才写回 bitmap。
    // 地址走本地鉴权代理（Cookie + Range 透传），比直接远程流更容易取到帧。
    // v0.3.78 最终：预览帧由服务端 ffmpeg 按秒生成（POST /videos/{id}/screenshots），
    // 不再用 MediaMetadataRetriever 从网络流取帧（MuMu 实测始终停在「预览加载中…」）。
    val thumbnailLoader = remember(videoId) {
        SeekThumbnailLoader(apiClient, videoId)
    }
    val previewScope = rememberCoroutineScope()
    var lastPreviewBucket by remember { mutableLongStateOf(-1L) }
    /** 已写回的最大分桶：快拖动时多个取帧请求并发乱序，用它挡住旧帧闪回。 */
    var appliedPreviewBucket by remember { mutableLongStateOf(-1L) }

    fun onScrub(f: Float?) {
        scrubFraction = f
        if (f == null) {
            previewBitmap = null
            lastPreviewBucket = -1L
            appliedPreviewBucket = -1L
            return
        }
        val timeMs = SeekPreviewRules.timeMs(f, durationMs)
        val loader = thumbnailLoader
        if (timeMs <= 0L) return
        val bucket = SeekPreviewRules.bucket(timeMs)
        val last = lastPreviewBucket.takeIf { it >= 0L }
        if (!SeekPreviewRules.shouldFetch(last, bucket)) return
        lastPreviewBucket = bucket
        previewScope.launch {
            val bmp = loader.load(bucket * SeekPreviewRules.THROTTLE_MS)
            // 只接受「不倒退」的桶：快拖动时多个取帧请求并发乱序完成，挡住旧帧闪回。
            // 不能要求 bucket == 当前最新桶 —— 快拖动时桶每秒都在变，那样一帧都写不回
            // （MuMu 实测：截图 API 全部 201/200，卡片却一直停在「预览加载中…」）。
            // 取帧失败保留上一帧，抬手后 scrubFraction 变 null，旧结果一律不回写。
            if (bmp != null && scrubFraction != null && bucket >= appliedPreviewBucket) {
                appliedPreviewBucket = bucket
                previewBitmap = bmp
            }
        }
    }

    LaunchedEffect(gestureHint) {
        if (gestureHint != null) {
            delay(900)
            gestureHint = null
        }
    }

    // 双击 ±10s：左/右三分之一屏快退/快进，中间暂停/继续
    fun seekBy(deltaMs: Long) {
        if (currentDurationMs <= 0) return
        val target = (currentPositionMs + deltaMs).coerceIn(0, currentDurationMs)
        currentEngine.seekTo(target)
        gestureHint = "${if (deltaMs >= 0) "快进" else "快退"} ${abs(deltaMs) / 1000}s  ${formatDuration(target / 1000)}"
    }

    val gestureModifier = if (locked) Modifier else Modifier
        .pointerInput(Unit) {
            var mode: GestureMode? = null
            var totalX = 0f
            var totalY = 0f
            var seekStartPositionMs = 0L
            var brightnessStart = currentBrightness
            var volumeStart = volume
            detectDragGestures(
                onDragStart = {
                    mode = null
                    totalX = 0f
                    totalY = 0f
                    seekStartPositionMs = currentPositionMs
                    brightnessStart = currentBrightness
                    volumeStart = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                },
                onDrag = { change, amount ->
                    totalX += amount.x
                    totalY += amount.y
                    if (mode == null && maxOf(abs(totalX), abs(totalY)) > 18f) {
                        mode = if (abs(totalX) > abs(totalY)) GestureMode.SEEK
                        else if (change.position.x < size.width / 2f) GestureMode.BRIGHTNESS
                        else GestureMode.VOLUME
                    }
                    when (mode) {
                        GestureMode.SEEK -> if (currentDurationMs > 0) {
                            gestureSeekTarget = (seekStartPositionMs + currentDurationMs * currentSeekFactor * totalX / size.width)
                                .toLong().coerceIn(0, currentDurationMs)
                            gestureHint = "进度  ${formatDuration(gestureSeekTarget / 1000)}"
                        }
                        GestureMode.BRIGHTNESS -> {
                            brightness = (brightnessStart - totalY / size.height).coerceIn(0.05f, 1f)
                            window?.setBrightness(brightness)
                            gestureHint = "亮度 ${(brightness * 100).roundToInt()}%"
                        }
                        GestureMode.VOLUME -> {
                            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            volume = (volumeStart - totalY / size.height * maxVolume)
                                .roundToInt().coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                            gestureHint = "音量 ${(volume * 100f / maxVolume).roundToInt()}%"
                        }
                        null -> Unit
                    }
                    if (mode != null) change.consume()
                },
                onDragEnd = {
                    if (mode == GestureMode.SEEK && currentDurationMs > 0) currentEngine.seekTo(gestureSeekTarget)
                },
            )
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { offset ->
                    when {
                        offset.x < size.width / 3f -> seekBy(-10_000L)
                        offset.x > size.width * 2 / 3f -> seekBy(10_000L)
                        else -> {
                            val playing = currentEngine.isPlaying
                            currentEngine.setPlaying(!playing)
                            gestureHint = if (playing) "已暂停" else "继续播放"
                        }
                    }
                },
                // 长按由加速手势处理；占位防止长按后抬手被当作单击
                onLongPress = {},
                onTap = { controlsVisible = !controlsVisible },
            )
        }
        .pointerInput(Unit) {
            // 长按加速：按住 2x，抬起或移动（转拖动 seek/亮度/音量）即恢复原速
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                speedBoost = true
                currentEngine.setPlaybackSpeed(SPEED_BOOST)
                awaitTouchSlopOrCancellation(longPress.id) { _, _ -> }
                speedBoost = false
                currentEngine.setPlaybackSpeed(1f)
            }
        }

    // 竖屏：16:9 画面横向铺满（宽度恒等于屏宽，不做圆角/留白，不横向缩放），
    // 父级 Column 只负责上下拖动位置；全屏才铺满整屏（纯黑舞台）
    val stageSizeModifier = if (fullscreen) {
        Modifier.fillMaxSize()
    } else {
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
    }

    Box(stageSizeModifier.then(gestureModifier)) {
        key(activeEngine) {
            AndroidView(
                factory = { ctx ->
                    engine.createView(ctx).also { view ->
                        if (activeEngine == EngineMode.VLC) {
                            findSurfaceView(view)?.holder?.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) { surfaceReady = true }
                                override fun surfaceChanged(holder: SurfaceHolder, f: Int, w: Int, h: Int) = Unit
                                override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                            })
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isBuffering && !controlsVisible) {
            Text("加载中…", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        gestureHint?.let { hint ->
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text(hint, color = Color.White, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
        }
        if (speedBoost) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            ) {
                Text("2x 加速中", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }

        IconButton(
            onClick = { engine.stop(); onBack() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = cutoutStartPadding + 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
        }

        if (!locked) {
            IconButton(
                onClick = { settingsVisible = !settingsVisible },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = cutoutStartPadding + 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Icon(Icons.Filled.Settings, "播放设置", tint = Color.White)
            }
        }
        IconButton(
            onClick = {
                locked = !locked
                if (locked) {
                    controlsVisible = false
                    settingsVisible = false
                    gestureHint = "已锁定"
                } else {
                    gestureHint = "已解锁"
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd).padding(12.dp),
        ) {
            Icon(
                if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                if (locked) "锁定" else "解锁",
                tint = Color.White,
            )
        }

        // 设置面板外的点击直接关闭面板；面板作为后绘制的子项优先消费自己的交互。
        if (settingsVisible && !locked) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { settingsVisible = false } },
            )
        }

        AnimatedVisibility(
            visible = settingsVisible && !locked,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 56.dp),
        ) {
            PlayerSettingsPanel(
                engineMode = engineMode,
                activeEngine = activeEngine,
                decoderStatus = engine.decoderStatus,
                subtitlesAvailable = subtitlesAvailable,
                subtitlesEnabled = subtitlesEnabled,
                seekSensitivity = seekSensitivity,
                onEngineModeChange = { mode ->
                    settingsVisible = false
                    onEngineModeChange(mode, positionMs)
                },
                onSeekSensitivityChange = onSeekSensitivityChange,
                onSubtitleChange = engine::setSubtitlesEnabled,
            )
        }

        if (controlsVisible && !locked) {
            PlaybackControls(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                fullscreen = fullscreen,
                chapterFractions = PlaybackBookmarkRules.fractions(chapters, durationMs),
                previewBitmap = previewBitmap,
                scrubFraction = scrubFraction,
                onScrubFraction = { onScrub(it) },
                onPlayingChange = engine::setPlaying,
                onSeek = engine::seekTo,
                onToggleFullscreen = onToggleFullscreen,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PlayerSettingsPanel(
    engineMode: EngineMode,
    activeEngine: EngineMode,
    decoderStatus: String,
    subtitlesAvailable: Boolean,
    subtitlesEnabled: Boolean,
    seekSensitivity: SeekSensitivity,
    onEngineModeChange: (EngineMode) -> Unit,
    onSeekSensitivityChange: (SeekSensitivity) -> Unit,
    onSubtitleChange: (Boolean) -> Unit,
) {
    Surface(color = Color.Black.copy(alpha = 0.78f), shape = MaterialTheme.shapes.large, modifier = Modifier.width(270.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("播放设置", color = Color.White, style = MaterialTheme.typography.titleMedium)
            EngineMode.entries.forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = engineMode == mode, onClick = { onEngineModeChange(mode) })
                    Text(mode.label, color = Color.White, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Text("拖动灵敏度", color = Color.White, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            SeekSensitivity.entries.forEach { value ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = seekSensitivity == value, onClick = { onSeekSensitivityChange(value) })
                    Text(value.label, color = Color.White, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.Subtitles, null, tint = Color.White)
                Text("显示字幕", color = Color.White, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Switch(
                    checked = subtitlesEnabled,
                    onCheckedChange = onSubtitleChange,
                    enabled = subtitlesAvailable,
                )
            }
            if (!subtitlesAvailable) {
                Text("当前视频没有可用字幕轨", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "当前：${if (activeEngine == EngineMode.VLC) "libVLC" else "ExoPlayer"}\n解码：$decoderStatus",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    fullscreen: Boolean,
    chapterFractions: List<Float>,
    previewBitmap: Bitmap?,
    scrubFraction: Float?,
    onScrubFraction: (Float?) -> Unit,
    onPlayingChange: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 外层用 BoxWithConstraints（不 clip），让预览卡片能浮出控制条上方盖在画面上
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        var sliderLeftPx by remember { mutableFloatStateOf(0f) }
        var sliderWidthPx by remember { mutableFloatStateOf(0f) }
        val density = LocalDensity.current
        // 控制条高度由最大子项（播放/全屏按钮）决定；WebSlider 在 Row 里垂直居中，
        // 故轨道中心距控制条顶边恒为 buttonSize/2，轨道上沿再减半个轨道高。
        // v0.3.80：预览卡片用 align(TopStart)（子项顶边恒为 0，不受「子项比父高」影响），
        // 下沿位置 = 轨道上沿 - 2dp 安全缝，全部由布局常量算出，不依赖任何运行时坐标。
        // 上一版用 align(BottomStart) + requiredHeight 的溢出子项，实际放置与推算不符，
        // 整张卡片往下盖住进度条；本版同时用 zIndex(-1f) 把预览图放到控制条下层绘制。
        val compact = WindowSizeRules.isCompact(rememberWindowWidthDp())
        val buttonSize = if (fullscreen || !compact) 48.dp else 34.dp
        val iconSize = if (fullscreen || !compact) 24.dp else 20.dp
        val previewBottomY = buttonSize / 2 - PlayerTrackHeight / 2 - PreviewTrackGap
        Surface(color = Color.Black.copy(alpha = 0.65f), modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = if (fullscreen) 8.dp else 2.dp),
            ) {
                Box(
                    Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .clickable { onPlayingChange(!isPlaying) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize),
                    )
                }
                // 竖屏时间用更小字号、更紧间距，并把「当前引擎」让到全屏再显示（设置浮层里也有）
                val timeStyle = if (fullscreen) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.labelSmall
                }
                Text(formatDuration(positionMs / 1000).ifEmpty { "0:00" }, color = Color.White, style = timeStyle, maxLines = 1)
                WebSlider(
                    value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                    onValueChange = { if (durationMs > 0) onSeek((it * durationMs).toLong()) },
                    markerFractions = chapterFractions,
                    onScrubFraction = onScrubFraction,
                    trackHeight = PlayerTrackHeight,
                    thumbSize = PlayerThumbSize,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = if (fullscreen) 8.dp else 4.dp)
                        .onGloballyPositioned { coords ->
                            sliderLeftPx = coords.positionInParent().x
                            sliderWidthPx = coords.size.width.toFloat()
                        },
                )
                Text(formatDuration(durationMs / 1000).ifEmpty { "--:--" }, color = Color.White, style = timeStyle, maxLines = 1)
                Box(
                    Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleFullscreen),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (fullscreen) "退出全屏" else "全屏",
                        tint = Color.White,
                        modifier = Modifier.size(iconSize),
                    )
                }
                // v0.3.79 需求③：控制条右侧不再显示 ExoPlayer / libVLC 引擎文字
            }
        }

        // v0.3.78 需求③：预览卡片必须是**不参与测量的浮层**。
        // 上一版把 84dp 卡片直接放进 BoxWithConstraints，拖动时把控制条从 ~48dp
        // 撑到 84dp；父级 align(BottomCenter) 底边固定 → 顶边上移 36dp，进度条跟着上移。
        // matchParentSize 子项不参与 Box 尺寸计算，控制条高度恒定，卡片只向上浮出。
        // v0.3.78 需求⑤：卡片尺寸按**原始视频帧宽高比**算，不再固定 128×84 + Crop。
        // 过高时按最大高度反算宽度，比例始终不变；水平居中于拖动点。
        // v0.3.80：卡片下沿 = 轨道上沿 - 2dp 安全缝；卡片顶边从浮层顶边(0)起，
        // 故 offset.y = previewBottomY - cardHeight，主体向上浮出控制条。
        // 浮层 zIndex(-1f) → 控制条（含进度条 / 滑块 / 时间）绘制在预览图之上，
        // 即使有 1~2dp 误差也绝不会再挡住进度条。
        val f = scrubFraction
        if (f != null) {
            val ratio = previewBitmap
                ?.takeIf { !it.isRecycled && it.width > 0 }
                ?.let { it.height.toFloat() / it.width.toFloat() }
                ?.coerceIn(0.25f, 2.5f)
                ?: (9f / 16f)
            val maxCardHeight = 150.dp
            var cardWidth = 160.dp
            var cardHeight = cardWidth * ratio
            if (cardHeight > maxCardHeight) {
                cardHeight = maxCardHeight
                cardWidth = cardHeight / ratio
            }
            val centerX = with(density) {
                (sliderLeftPx + sliderWidthPx * f).toDp()
            }
            val maxX = (maxWidth - cardWidth).coerceAtLeast(0.dp)
            Box(Modifier.matchParentSize().zIndex(-1f)) {
                SeekPreviewCard(
                    bitmap = previewBitmap,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(
                            x = (centerX - cardWidth / 2).coerceIn(0.dp, maxX),
                            y = previewBottomY - cardHeight,
                        )
                        // requiredWidth/Height：matchParentSize 的 Box 只有控制条那么高，
                        // 普通 width/height 会被父级 maxHeight 钳到 ~40dp，把 16:9 画面压扁成横条。
                        // required* 忽略 incoming 约束，卡片按真实比例完整浮出。
                        .requiredWidth(cardWidth)
                        .requiredHeight(cardHeight),
                )
            }
        }
    }
}

/**
 * 拖动预览卡片：v0.3.78 需求⑤ —— 只显示视频帧本身，按原始宽高比铺满，
 * 去掉原来的半透明黑底 Surface、4dp 内边距、圆角黑框和时间文字（用户截图里是纯画面）。
 * 取帧未完成时只留极淡占位，不再显示「预览加载中…」遮罩。
 */
@Composable
private fun SeekPreviewCard(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    if (bitmap != null && !bitmap.isRecycled) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            // 外层卡片尺寸已按 bitmap 比例算好，FillBounds 不会有形变/黑边
            contentScale = ContentScale.FillBounds,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(
            modifier
                .clip(shape)
                .background(Color.White.copy(alpha = 0.08f)),
        )
    }
}

private enum class GestureMode { SEEK, BRIGHTNESS, VOLUME }

/** 长按加速倍速 */
private const val SPEED_BOOST = 2f

private fun Window.screenBrightness(): Float = attributes.screenBrightness

private fun Window.setBrightness(value: Float) {
    attributes = attributes.apply { screenBrightness = value }
}

private fun Float?.orDefaultBrightness(): Float = this?.takeIf { it >= 0f } ?: 0.5f

private fun findSurfaceView(view: View): SurfaceView? = when (view) {
    is SurfaceView -> view
    is ViewGroup -> (0 until view.childCount).asSequence().mapNotNull { findSurfaceView(view.getChildAt(it)) }.firstOrNull()
    else -> null
}
