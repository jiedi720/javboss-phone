package com.javboss.phone.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// 主题色由原来的粉色 #E57373 改为橙色 #FF9000。
// Material3 darkColorScheme 的 secondary / tertiary 默认是紫粉色系，
// 这里把它们及各自的容器 / 前景全部换成接近新主题色的暖橙系，
// 保证界面里不再残留粉紫色文字；背景 / 表面 / error 等语义色保持原样。
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9000),
    onPrimary = Color(0xFF2A1600),
    primaryContainer = Color(0xFF5A3200),
    onPrimaryContainer = Color(0xFFFFDDB3),
    inversePrimary = Color(0xFFB36500),
    secondary = Color(0xFFFFB059),
    onSecondary = Color(0xFF2A1600),
    secondaryContainer = Color(0xFF4A2A00),
    onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = Color(0xFFFFC266),
    onTertiary = Color(0xFF2A1600),
    tertiaryContainer = Color(0xFF4A2A00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
)

@Composable
fun JavBossTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
    ) {
        // 必须包 Surface：否则根布局 LocalContentColor 默认黑色，
        // 不显式传 color 的 Text 在深底上直接隐身
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
