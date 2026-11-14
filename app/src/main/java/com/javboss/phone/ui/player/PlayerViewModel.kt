package com.javboss.phone.ui.player

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.EngineMode
import com.javboss.phone.data.SeekSensitivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val urls: List<String> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /** 用户设置的引擎偏好（自动 / 仅 ExoPlayer / 仅 libVLC） */
    val engineMode: EngineMode = EngineMode.AUTO,
    /** 当前实际使用的引擎（AUTO 模式下失败可从 EXO 切到 VLC） */
    val activeEngine: EngineMode = EngineMode.EXO,
    /** 切引擎时的续播起点（上个引擎失败时的进度） */
    val fallbackPositionMs: Long = 0,
    /** 全屏拖动 seek 灵敏度偏好 */
    val seekSensitivity: SeekSensitivity = SeekSensitivity.MEDIUM,
    /** 竖屏背景用的 JAV 封面（有番号时优先） */
    val coverUrl: String? = null,
    /** 每个视频都有的缩略图：封面接口失败或普通视频没有 JAV 封面时的兜底背景 */
    val thumbnailUrl: String? = null,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as JavBossApp).container

    private companion object {
        /** 秒级节拍：只做毫秒累加，几乎零开销 */
        const val TICK_MS = 1_000L

        /** 对齐 Web 端：每 10 秒 PUT 一次累计观看时长 */
        const val REPORT_INTERVAL_MS = 10_000L
    }

    var uiState by mutableStateOf(PlayerUiState())
        private set

    /** 只认最后一次 load 的 videoId，防止切视频后旧封面异步写回来 */
    @Volatile
    private var currentVideoId: Long = 0L

    val cookieHeader: String get() = container.cookieJar.cookieHeader()

    /** 进度条预览取帧需要复用带 CookieJar 的 Retrofit / OkHttp 客户端。 */
    val apiClient get() = container.apiClient

    fun load(videoId: Long, javCode: String?) {
        currentVideoId = videoId
        // 切换视频：立刻换成新视频的封面，同时把一定存在的视频缩略图作为最后兜底。
        // javCode 由列表页传入，和 JAV 界面 WorkCard 用的是同一个 javCoverUrl(code)。
        val thumbnail = container.apiClient.videoThumbnailUrl(videoId)
        val cover = javCode?.takeIf { it.isNotBlank() }
            ?.let { container.apiClient.javCoverUrl(it) }
        uiState = uiState.copy(coverUrl = cover, thumbnailUrl = thumbnail)
        viewModelScope.launch {
            try {
                val source = container.videoRepository.resolvePlayable(videoId)
                if (source.urls.isEmpty()) {
                    uiState = PlayerUiState(
                        loading = false, error = "没有可用的播放源",
                        coverUrl = cover, thumbnailUrl = thumbnail,
                    )
                } else {
                    val mode = container.settings.engineMode
                    uiState = PlayerUiState(
                        urls = source.urls,
                        loading = false,
                        engineMode = mode,
                        activeEngine = if (mode == EngineMode.VLC) EngineMode.VLC else EngineMode.EXO,
                        seekSensitivity = container.settings.seekSensitivity,
                        coverUrl = cover,
                        thumbnailUrl = thumbnail,
                    )
                    container.videoRepository.reportPlay(videoId)
                    startPlaybackReporter(videoId, source.locationId)
                    // 只有没带番号进来（普通视频/旧入口）才反查一次
                    if (cover == null) loadCover(videoId)
                }
            } catch (e: Exception) {
                uiState = PlayerUiState(
                    loading = false, error = e.message ?: "获取播放源失败",
                    coverUrl = cover, thumbnailUrl = thumbnail,
                )
            }
        }
    }

    /**
     * 竖屏背景优先用当前作品的 JAV 封面：先由 /videos/{id} 拿到番号，再拼封面地址。
     * 拿不到番号时 PlayerScreen 会用 thumbnailUrl 兜底；单独起协程，绝不能挡住起播。
     */
    private fun loadCover(videoId: Long) {
        viewModelScope.launch {
            val code = runCatching { container.videoRepository.getVideo(videoId) }
                .getOrNull()?.jav?.code?.takeIf { it.isNotBlank() } ?: return@launch
            // 切视频期间旧的封面请求可能才返回，丢弃它
            if (currentVideoId != videoId) return@launch
            uiState = uiState.copy(coverUrl = container.apiClient.javCoverUrl(code))
        }
    }

    // ---------- 观看时长上报（服务端口径，与 Web 端一致） ----------

    /** 播放页每 500ms 轮询时回传的真实播放状态：只有 true 的时间才计入观看时长 */
    @Volatile
    private var playbackActive = false

    /** 暂停/切换时要求立刻补报一次，不等下一个上报周期 */
    @Volatile
    private var flushRequested = false

    private var playbackReporter: Job? = null

    /**
     * 把手机端的观看时长记进服务端：
     * 进播放页开一个 playback session，之后每 [REPORT_INTERVAL_MS] 把
     * 「本 session 内真实播放的毫秒数」PUT 回去（累计值、单调递增，不是增量）。
     * 暂停时立刻补报，退出时再补最后一次；服务端回 410 说明会话已被回收，
     * 丢掉重开并从新基线累计，避免同一段时长重复记账。
     */
    private fun startPlaybackReporter(videoId: Long, locationId: Long) {
        playbackReporter?.cancel()
        playbackActive = false
        flushRequested = false
        playbackReporter = viewModelScope.launch {
            var sessionId: String? = null
            var accumulatedMs = 0L          // 本次播放累计「真的在播」的毫秒数
            var baselineMs = 0L             // 建会话那一刻的 accumulatedMs
            var acknowledgedMs = 0L         // 服务端已确认到的累计值
            var lastMark = SystemClock.elapsedRealtime()
            var lastReportAt = lastMark
            try {
                while (isActive) {
                    delay(TICK_MS)
                    val now = SystemClock.elapsedRealtime()
                    if (playbackActive) accumulatedMs += (now - lastMark).coerceAtLeast(0)
                    lastMark = now

                    if (sessionId == null) {
                        sessionId = container.videoRepository
                            .openPlaybackSession(videoId, locationId)
                        baselineMs = accumulatedMs
                        acknowledgedMs = 0
                        lastReportAt = now
                        continue
                    }

                    val pending = accumulatedMs - baselineMs
                    val due = flushRequested || now - lastReportAt >= REPORT_INTERVAL_MS
                    flushRequested = false
                    if (pending > acknowledgedMs && due) {
                        val result = runCatching {
                            container.videoRepository
                                .reportPlaybackSession(videoId, sessionId!!, pending)
                        }
                        when {
                            // 只有真正成功才推进「已确认」，否则这段时长会丢
                            result.getOrNull() == true -> {
                                acknowledgedMs = pending
                                lastReportAt = now
                            }
                            // 服务端 410：会话已回收，丢掉重开（下次循环重建并从新基线累计）
                            result.getOrNull() == false -> sessionId = null
                            // 网络抖动：保持 session、10 秒后再试同一段累计值
                            else -> lastReportAt = now
                        }
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    val markNow = SystemClock.elapsedRealtime()
                    if (playbackActive) accumulatedMs += (markNow - lastMark).coerceAtLeast(0)
                    val id = sessionId ?: return@withContext
                    val pending = accumulatedMs - baselineMs
                    if (pending > acknowledgedMs) {
                        runCatching {
                            container.videoRepository.reportPlaybackSession(videoId, id, pending)
                        }
                    }
                }
            }
        }
    }

    /** 离开播放页时收尾：取消上报协程并补报最后一次（finally 里做） */
    fun stopPlaybackReporter() {
        playbackActive = false
        playbackReporter?.cancel()
        playbackReporter = null
    }

    /** 播放页轮询回传：播放/暂停切换时更新累加开关，暂停时立刻补报一次 */
    fun onPlaybackState(playing: Boolean) {
        if (playbackActive == playing) return
        playbackActive = playing
        if (!playing) flushRequested = true
    }

    /** AUTO 模式下 ExoPlayer 全部源失败 → 切 libVLC 引擎，从失败进度续播 */
    fun switchToVlc(startPositionMs: Long) {
        uiState = uiState.copy(activeEngine = EngineMode.VLC, fallbackPositionMs = startPositionMs)
    }

    /** 播放页内切换引擎偏好，立即从当前进度重建对应引擎。 */
    fun selectEngineMode(mode: EngineMode, positionMs: Long) {
        viewModelScope.launch { container.settings.saveEngineMode(mode) }
        uiState = uiState.copy(
            engineMode = mode,
            activeEngine = if (mode == EngineMode.VLC) EngineMode.VLC else EngineMode.EXO,
            fallbackPositionMs = positionMs,
        )
    }

    /** 切换拖动 seek 灵敏度偏好，立即生效并持久化 */
    fun selectSeekSensitivity(value: SeekSensitivity) {
        viewModelScope.launch { container.settings.saveSeekSensitivity(value) }
        uiState = uiState.copy(seekSensitivity = value)
    }

    /** libVLC 经本地代理播放（代理负责附加会话 Cookie） */
    fun proxyUrl(url: String): String = container.authProxy.proxyUrl(url)

    /** 播放终态失败 */
    fun failPlayback(message: String) {
        uiState = uiState.copy(error = message)
    }

    override fun onCleared() {
        stopPlaybackReporter()
        super.onCleared()
    }
}
