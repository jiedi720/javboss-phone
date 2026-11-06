package com.javboss.phone.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.javboss.phone.JavBossApp
import com.javboss.phone.data.api.ServerAddressRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 按当前所在网络，自动在两条登录预设之间切换。
 *
 * 判定方式：用一个**独立**的短超时 OkHttpClient 去 GET「家里」地址的 /auth/status。
 * 只要拿到任意 HTTP 响应（含 401/404）就认为人在家、该服务可达；连不上（超时 / 拒绝 / 无网络）
 * 则认为人在外面。独立客户端不带会话 Cookie、也不挂 401 拦截器，探测不会污染当前登录态。
 *
 * 切换不是只改地址：Cookie 是 origin 绑定的，所以必须拿目标预设已保存的密码重新登录一次
 * （复用 [AuthRepository.login]，它会先清旧会话再登录）。登录失败会把会话**回滚**到切换前的
 * 快照，绝不会因为一次自动切换把用户踢回登录页。
 *
 * 播放期间不切换（把请求记成 pending，退出播放页再补检），避免中途换源把视频打断。
 */
class AutoLoginSwitcher(private val context: Context) {

    private val container get() = (context.applicationContext as JavBossApp).container

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val checkMutex = Mutex()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** 探测专用：不带 CookieJar、不带 401 拦截器，超时压得很短 */
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * UI 注册的通知回调。界面还没组合好时先缓存最后一条，
     * 注册后回放，避免冷启动探测太快把通知丢掉。回调一律在主线程执行。
     */
    @Volatile
    private var lastNotice: ServerSwitchNotice? = null

    var onNotice: ((ServerSwitchNotice) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                mainHandler.post {
                    val pending = lastNotice ?: return@post
                    val cb = onNotice ?: return@post
                    lastNotice = null
                    cb(pending)
                }
            }
        }

    @Volatile
    var playbackActive: Boolean = false
        private set

    @Volatile
    private var pendingCheck = false

    @Volatile
    private var lastCheckAt = 0L

    private var checkJob: Job? = null

    /** onStart / 网络可用回调常连发，短时间内反复探测重登没必要 */
    private fun throttled(force: Boolean): Boolean =
        !force && System.currentTimeMillis() - lastCheckAt < MIN_INTERVAL_MS

    /**
     * 请求一次检查。
     * [force] 用于设置页「立即检测」：忽略节流，并且把「无需切换 / 未配置」也反馈出来。
     */
    fun requestCheck(trigger: String, force: Boolean = false) {
        if (!container.settings.autoSwitchEnabled) return
        if (playbackActive) {
            pendingCheck = true
            return
        }
        if (throttled(force)) return
        lastCheckAt = System.currentTimeMillis()
        checkJob?.cancel()
        checkJob = scope.launch { doCheck(force) }
    }

    /** 播放页进入 / 退出时调用；退出时把播放期间攒下的检查补上 */
    fun setPlaybackActive(active: Boolean) {
        val ended = playbackActive && !active
        playbackActive = active
        if (ended && pendingCheck) {
            pendingCheck = false
            requestCheck("playback-end", force = true)
        }
    }

    private suspend fun doCheck(force: Boolean) {
        checkMutex.withLock {
            if (!container.settings.autoSwitchEnabled) return@withLock

            val lanUrl = container.settings.lanPresetUrl
            val wanUrl = container.settings.wanPresetUrl
            if (lanUrl.isBlank() || wanUrl.isBlank()) {
                if (force) {
                    emit(ServerSwitchNotice(false, "自动切换还没配好：请在设置里指定「家里」和「外面」两条预设"))
                }
                return@withLock
            }

            val session = container.session
            if (!session.hasSession) {
                // 用户主动登出 / 首次安装：尊重用户，不在后台偷偷把人登录回去
                if (force) {
                    emit(ServerSwitchNotice(false, "当前未登录，自动切换只在已登录后生效"))
                }
                return@withLock
            }

            val reachable = probe(AutoSwitchRules.probeUrl(container.settings.probeUrlOverride, lanUrl))
            val targetUrl = AutoSwitchRules.targetFor(reachable, lanUrl, wanUrl)
            val label = if (reachable) "家里（局域网）" else "外面（外网）"

            if (targetUrl.isBlank()) {
                if (force) emit(ServerSwitchNotice(false, "当前网络对应的预设还没指定，请在设置里补齐"))
                return@withLock
            }

            val target = container.presets.presets.firstOrNull { it.url == targetUrl }
            if (target == null || !target.canAutoLogin) {
                emit(ServerSwitchNotice(false, "$label 这条预设没保存密码，无法自动登录"))
                return@withLock
            }

            if (ServerAddressRules.normalize(target.url) == session.serverUrl) {
                if (force) emit(ServerSwitchNotice(false, "当前已经是$label，无需切换"))
                return@withLock
            }

            // 登录前先快照：AuthRepository.login 会先清掉旧会话，失败必须原样恢复
            val snapshotUrl = session.serverUrl
            val snapshotCookie = session.cookieHeader
            try {
                container.authRepository.login(target.url, target.password)
                emit(ServerSwitchNotice(true, "已切换到$label"))
            } catch (e: Exception) {
                restoreSession(snapshotUrl, snapshotCookie)
                emit(ServerSwitchNotice(false, "切换失败，已保持原来的连接：${e.message ?: "无法登录"}"))
            }
        }
    }

    private suspend fun restoreSession(url: String, cookie: String) {
        runCatching {
            container.session.saveServerUrl(url)
            container.cookieJar.clear()
            container.session.saveCookie(cookie)
            container.cookieJar.restoreFromSession()
        }
    }

    /** 拿到任意 HTTP 响应就算「服务可达」；网络层异常 / 超时才算连不上 */
    private suspend fun probe(baseUrl: String): Boolean {
        val normalized = ServerAddressRules.normalize(baseUrl) ?: return false
        val request = Request.Builder()
            .url("$normalized/auth/status")
            .header("Accept", "application/json")
            .get()
            .build()
        return withContext(Dispatchers.IO) {
            runCatching { probeClient.newCall(request).execute().use { true } }.getOrDefault(false)
        }
    }

    private fun emit(notice: ServerSwitchNotice) {
        mainHandler.post {
            val cb = onNotice
            if (cb == null) lastNotice = notice else cb(notice)
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 5_000L
    }
}
