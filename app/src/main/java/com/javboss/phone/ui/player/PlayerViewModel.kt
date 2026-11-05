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
    /** 竖屏背景用的毛玻璃封面（拿不到就退回中性渐变，不影响播放） */
    val coverUrl: String? = null,
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

    val cookieHeader: String get() = container.cookieJar.cookieHeader()

    fun load(videoId: Long) {
        viewModelScope.launch {
            try {
                val source = container.videoRepository.resolvePlayable(videoId)
                if (source.urls.isEmpty()) {
                    uiState = PlayerUiState(loading = false, error = "没有可用的播放源")
                } else {
                    val mode = container.settings.engineMode
                    uiState = PlayerUiState(
                        urls = source.urls,
                        loading = false,
                        engineMode = mode,
                        activeEngine = if (mode == EngineMode.VLC) EngineMode.VLC else EngineMode.EXO,
                        seekSensitivity = container.settings.seekSensitivity,
                    )
                    container.videoRepository.reportPlay(videoId)
                    startPlaybackReporter(videoId, source.locationId)
                    loadCover(videoId)
                }
            } catch (e: Exception) {
                uiState = PlayerUiState(loading = false, error = e.message ?: "获取播放源失败")
            }
        }
    }

    /**
     * 竖屏背景要一张毛玻璃封面：先由 /videos/{id} 拿到番号，再拼封面地址。
     * 单独起协程，失败或普通视频没有 JAV 信息都只是没有封面，绝不能挡住起播。
     */
    private fun loadCover(videoId: Long) {
        viewModelScope.launch {
            val code = runCatching { container.videoRepository.getVideo(videoId) }
                .getOrNull()?.jav?.code?.takeIf { it.isNotBlank() } ?: return@launch
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
