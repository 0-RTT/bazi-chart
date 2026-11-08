package com.bazi.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bazi.engine.WuXing

/**
 * 设计 Token 唯一来源。
 *
 * 界面上所有颜色都必须从这里取，禁止在 Composable 里写字面色值。
 * 配色取「纸、墨、朱砂、青瓷」四色，不用渐变、不用发光。
 */
object BaziTokens {

    // 浅色
    val paper = Color(0xFFFAF7F0)
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF1A1A18)
    val inkMuted = Color(0xFF6B6862)
    val line = Color(0xFFE3DCCE)
    val cinnabar = Color(0xFFB4472F)
    val celadon = Color(0xFF3F6B5E)
    val gold = Color(0xFF9A7B3F)

    // 深色
    val nightPaper = Color(0xFF14130F)
    val nightSurface = Color(0xFF1E1C18)
    val nightInk = Color(0xFFEDE9DF)
    val nightMuted = Color(0xFFA6A096)
    val nightLine = Color(0xFF3A362E)

    /** 五行配色。木青、火朱、土黄、金白灰、水玄。 */
    fun wuXing(wx: WuXing): Color = when (wx) {
        WuXing.MU -> Color(0xFF4E7A4A)
        WuXing.HUO -> Color(0xFFB4472F)
        WuXing.TU -> Color(0xFF9A7B3F)
        WuXing.JIN -> Color(0xFF7A7A72)
        WuXing.SHUI -> Color(0xFF3A6280)
    }
}

private val LightScheme = lightColorScheme(
    primary = BaziTokens.cinnabar,
    onPrimary = BaziTokens.surface,
    secondary = BaziTokens.celadon,
    onSecondary = BaziTokens.surface,
    background = BaziTokens.paper,
    onBackground = BaziTokens.ink,
    surface = BaziTokens.surface,
    onSurface = BaziTokens.ink,
    surfaceVariant = BaziTokens.paper,
    onSurfaceVariant = BaziTokens.inkMuted,
    outline = BaziTokens.line,
    outlineVariant = BaziTokens.line,
    error = BaziTokens.cinnabar
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD27257),
    onPrimary = BaziTokens.nightPaper,
    secondary = Color(0xFF7FB3A2),
    onSecondary = BaziTokens.nightPaper,
    background = BaziTokens.nightPaper,
    onBackground = BaziTokens.nightInk,
    surface = BaziTokens.nightSurface,
    onSurface = BaziTokens.nightInk,
    surfaceVariant = BaziTokens.nightSurface,
    onSurfaceVariant = BaziTokens.nightMuted,
    outline = BaziTokens.nightLine,
    outlineVariant = BaziTokens.nightLine,
    error = Color(0xFFD27257)
)

@Composable
fun BaziTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = Typography(),
        content = content
    )
}
