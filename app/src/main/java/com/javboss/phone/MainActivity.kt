package com.javboss.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import com.javboss.phone.data.PrivacyMask
import com.javboss.phone.navigation.JavBossNavGraph
import com.javboss.phone.ui.components.LocalCoverMask
import com.javboss.phone.ui.components.rememberCoverMask
import com.javboss.phone.ui.theme.JavBossTheme

class MainActivity : ComponentActivity() {

    /** 锁屏（屏幕关闭）→ 给作品/女优封面自动打码，解锁后保持 */
    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) PrivacyMask.onScreenLocked()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // savedInstanceState == null 表示从桌面（启动器）冷启动：不继承上回的自动打码
        if (savedInstanceState == null) PrivacyMask.reveal()
        ContextCompat.registerReceiver(
            this,
            screenLockReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val startWithSession = (application as JavBossApp).container.session.hasSession
        setContent {
            JavBossTheme {
                // 打码状态只在根部读一次，用静态 CompositionLocal 下发：
                // 状态一变整棵子树强制重组，不会出现「解锁后要滑一下才打码」
                val mask = rememberCoverMask()
                CompositionLocalProvider(LocalCoverMask provides mask) {
                    JavBossNavGraph(startWithSession = startWithSession)
                }
            }
        }
    }

    /**
     * 兜底：部分机型/ROM 的 ACTION_SCREEN_OFF 会晚到或漏发（国产 ROM 的省电策略尤其常见），
     * 这里在页面不可见时再判一次「屏幕是否已熄灭」。
     * 屏幕灭 = 锁屏 → 打码；只是切到别的 App（屏幕还亮着）则不打码。
     */
    override fun onStop() {
        super.onStop()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isInteractive) PrivacyMask.onScreenLocked()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenLockReceiver) }
        super.onDestroy()
    }
}
