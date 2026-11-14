package com.javboss.phone.ui.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.javboss.phone.data.PlaybackBookmarkRules
import com.javboss.phone.data.PlaybackChapter
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * 统一播放引擎抽象：播放页只有一套 UI 和控制条，
 * 引擎只负责解码渲染，通过本接口驱动。
 * 属性均为轮询读取（播放页控制条每 500ms 刷一次），事件走 onError 回调（主线程）。
 */
interface PlayerEngine {
    var onError: () -> Unit
    val isPlaying: Boolean
    val isBuffering: Boolean
    val positionMs: Long
    val durationMs: Long
    val decoderStatus: String
    val subtitlesAvailable: Boolean
    val subtitlesEnabled: Boolean

    /**
     * 视频源文件内嵌章节（v0.3.77 需求①）。
     * libVLC 能读 MKV/MP4 chapters；Media3 1.5.1 不向应用层暴露容器章节，
     * 因此 ExoPlayer 引擎返回空列表（播放设置里切到 libVLC 即可看到章节标记）。
     */
    val chapters: List<PlaybackChapter>

    /** 渲染表面（ExoPlayer 用 PlayerView 关控制条，libVLC 用 VLCVideoLayout） */
    fun createView(context: Context): View

    fun play(url: String, startPositionMs: Long = 0)
    fun setPlaying(playing: Boolean)
    fun seekTo(positionMs: Long)

    /** 设置播放倍速（长按加速用，1f 为原速） */
    fun setPlaybackSpeed(speed: Float)

    fun setSubtitlesEnabled(enabled: Boolean)
    fun stop()
    fun release()
}

/** ExoPlayer 引擎：硬解体验最好，HTTP 请求直接带会话 Cookie 头 */
@UnstableApi
class ExoPlayerEngine(context: Context, cookieHeader: String) : PlayerEngine {

    private val player: ExoPlayer

    override var onError: () -> Unit = {}
    private var subtitleEnabled = true
    private var decoderName: String? = null

    init {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("Cookie" to cookieHeader))
            .setUserAgent("JavBossPhone/0.1")
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory))
            .build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onError()
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                this@ExoPlayerEngine.decoderName = decoderName
            }
        })
    }

    override val isPlaying: Boolean get() = player.isPlaying
    override val isBuffering: Boolean get() = player.playbackState == Player.STATE_BUFFERING
    override val positionMs: Long get() = player.currentPosition.coerceAtLeast(0)
    override val durationMs: Long get() = player.duration.coerceAtLeast(0)
    override val decoderStatus: String
        get() = decoderName?.let { name ->
            if (name.startsWith("OMX.google.") || name.startsWith("c2.android.")) "软件解码" else "硬件解码"
        } ?: "解码器初始化中"
    override val subtitlesAvailable: Boolean
        get() = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
    override val subtitlesEnabled: Boolean get() = subtitleEnabled

    // Media3 1.5.1 的 Matroska/MP4 extractor 不把容器 chapters 暴露给应用层，
    // 保持空列表；进度条章节标记只在 libVLC 引擎下出现。
    override val chapters: List<PlaybackChapter> get() = emptyList()

    override fun createView(context: Context): View =
        PlayerView(context).apply {
            useController = false // 控制条由播放页统一绘制
            player = this@ExoPlayerEngine.player
        }

    override fun play(url: String, startPositionMs: Long) {
        player.setMediaItem(MediaItem.fromUri(url), startPositionMs)
        player.prepare()
        player.playWhenReady = true
    }

    override fun setPlaying(playing: Boolean) {
        if (playing) player.play() else player.pause()
    }

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun setPlaybackSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    override fun setSubtitlesEnabled(enabled: Boolean) {
        subtitleEnabled = enabled
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }
    override fun stop() = player.stop()
    override fun release() = player.release()
}

/**
 * libVLC 引擎：自带完整 ffmpeg 解封装+解码，全格式兜底。
 * h264/h265/vp9/av1 默认走 MediaCodec 硬解；wmv 等无硬解器的格式软解。
 * 无法注入 Cookie，播放地址需先经本地代理（AuthProxy）转换。
 * 事件从 native 线程回调，统一 post 到主线程再通知 UI。
 */
class VlcPlayerEngine(context: Context) : PlayerEngine {

    private companion object {
        /** 章节解析可能晚于起播：未读到前最多重试次数（TimeChanged 约每 250ms 一次） */
        const val MAX_CHAPTER_ATTEMPTS = 20
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val libVlc = LibVLC(context, arrayListOf("--no-stats"))
    private val mediaPlayer = MediaPlayer(libVlc)

    override var onError: () -> Unit = {}

    private var pendingSeekMs = -1L
    private var lastSubtitleTrackId = -1

    @Volatile
    private var buffering = false

    // v0.3.77 需求①：视频源文件内嵌章节（MKV/MP4 chapters）。
    // 事件从 native 线程回调，章节元数据在起播后一小段时间才解析完，
    // 故 Playing 读一次、TimeChanged 里未读到前有限次重试，读到后停止。
    @Volatile
    private var cachedChapters: List<PlaybackChapter> = emptyList()
    private var chaptersLoaded = false
    private var chapterAttempts = 0

    init {
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    if (pendingSeekMs > 0) {
                        mediaPlayer.time = pendingSeekMs
                        pendingSeekMs = -1L
                    }
                    tryReadChapters()
                }
                MediaPlayer.Event.TimeChanged -> tryReadChapters()
                MediaPlayer.Event.Buffering -> buffering = event.buffering < 100f
                MediaPlayer.Event.EncounteredError -> mainHandler.post { onError() }
                else -> {}
            }
        }
    }

    /**
     * 读取容器章节：titles 存在时按 title 下标逐个读；多数 MKV 没有 title，
     * 退化为 -1（当前 title）与 0 两种下标，结果按 timeOffset 去重排序。
     * 全部 native 调用都 runCatching 兜底：读不到章节绝不影响播放。
     *
     * v0.3.78：原生 libVLC 的 `Chapter.timeOffset` 是**微秒**，上一版直接当毫秒用，
     * 微秒值远大于总时长，被 fractions() 的越界过滤全部丢掉，标记一个都不显示。
     * 这里用 [PlaybackBookmarkRules.normalizeTimeMs] 按媒体总时长自动归一；
     * 时长尚未解析出来时先不缓存，等下一次 TimeChanged 再试。
     */
    private fun tryReadChapters() {
        if (chaptersLoaded) return
        val raw = mutableListOf<Pair<Long, String>>()
        runCatching {
            val titles = mediaPlayer.titles
            val indices = if (titles.isNullOrEmpty()) {
                intArrayOf(-1, 0)
            } else {
                IntArray(titles.size) { it }
            }
            for (index in indices) {
                val chapters = runCatching { mediaPlayer.getChapters(index) }.getOrNull() ?: continue
                for (chapter in chapters) {
                    if (chapter != null && chapter.timeOffset > 0L) {
                        raw += chapter.timeOffset to (chapter.name ?: "")
                    }
                }
            }
        }
        val lengthMs = runCatching { mediaPlayer.length }.getOrDefault(0L)
        if (raw.isNotEmpty() && lengthMs <= 0L) return
        val times = PlaybackBookmarkRules.normalizeTimeMs(raw.map { it.first }, lengthMs)
        cachedChapters = times.mapIndexed { index, timeMs ->
            PlaybackChapter(timeMs, raw[index].second)
        }
            .distinctBy { it.timeMs }
            .sortedBy { it.timeMs }
        chapterAttempts += 1
        if (cachedChapters.isNotEmpty() || chapterAttempts >= MAX_CHAPTER_ATTEMPTS) {
            chaptersLoaded = true
        }
    }

    override val isPlaying: Boolean get() = mediaPlayer.isPlaying
    override val isBuffering: Boolean get() = buffering
    override val positionMs: Long get() = mediaPlayer.time.coerceAtLeast(0)
    override val durationMs: Long get() = mediaPlayer.length.coerceAtLeast(0)
    override val decoderStatus: String get() = "自动（MediaCodec 硬解优先）"
    override val subtitlesAvailable: Boolean get() = mediaPlayer.spuTracksCount > 0
    override val subtitlesEnabled: Boolean get() = mediaPlayer.spuTrack >= 0
    override val chapters: List<PlaybackChapter> get() = cachedChapters

    override fun createView(context: Context): View =
        VLCVideoLayout(context).also { mediaPlayer.attachViews(it, null, false, false) }

    override fun play(url: String, startPositionMs: Long) {
        pendingSeekMs = startPositionMs
        cachedChapters = emptyList()
        chaptersLoaded = false
        chapterAttempts = 0
        val media = Media(libVlc, Uri.parse(url))
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    override fun setPlaying(playing: Boolean) {
        if (playing) mediaPlayer.play() else mediaPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer.time = positionMs
    }

    override fun setPlaybackSpeed(speed: Float) {
        mediaPlayer.setRate(speed)
    }

    override fun setSubtitlesEnabled(enabled: Boolean) {
        if (enabled) {
            val track = mediaPlayer.spuTracks
                ?.firstOrNull { it.id >= 0 }
                ?.id
                ?: lastSubtitleTrackId.takeIf { it >= 0 }
                ?: return
            mediaPlayer.setSpuTrack(track)
        } else {
            lastSubtitleTrackId = mediaPlayer.spuTrack
            mediaPlayer.setSpuTrack(-1)
        }
    }

    override fun stop() = mediaPlayer.stop()

    override fun release() {
        runCatching {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
            libVlc.release()
        }
    }
}
