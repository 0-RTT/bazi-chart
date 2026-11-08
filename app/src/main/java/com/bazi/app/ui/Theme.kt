package com.bazi.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.bazi.engine.WuXing

/**
 * 设计 Token 唯一来源。
 *
 * 界面上所有颜色都必须从这里取，禁止在 Composable 里写字面色值。
 * 配色取「纸、墨、朱砂、青瓷」四色：暖白纸底、墨色正文、朱砂点睛、青瓷辅助。
 * 不用渐变、不用发光、不用彩色投影。
 */
object BaziTokens {

    // 浅色 —— 纸与墨
    val paper = Color(0xFFF7F4EC)
    val surface = Color(0xFFFDFBF6)
    val surfaceSunken = Color(0xFFF0EBDF)
    val ink = Color(0xFF1C1B18)
    val inkMuted = Color(0xFF7A756A)
    val inkFaint = Color(0xFFA8A294)
    val line = Color(0xFFE0D8C8)
    val cinnabar = Color(0xFFA8392A)
    val cinnabarWash = Color(0xFFF2E3DE)
    val celadon = Color(0xFF3D675B)
    val gold = Color(0xFF93753C)

    // 深色 —— 夜与烛
    val nightPaper = Color(0xFF12110E)
    val nightSurface = Color(0xFF1D1B16)
    val nightSurfaceSunken = Color(0xFF272420)
    val nightInk = Color(0xFFEDE8DC)
    val nightMuted = Color(0xFFA29B8C)
    val nightFaint = Color(0xFF6E685C)
    val nightLine = Color(0xFF37332B)

    /**
     * 五行配色。木青、火朱、土黄、金灰白、水玄。
     * 深色下需要提亮，否则在夜底上会糊成一团。
     */
    fun wuXing(wx: WuXing, dark: Boolean = false): Color = if (dark) when (wx) {
        WuXing.MU -> Color(0xFF7FB077)
        WuXing.HUO -> Color(0xFFD9785C)
        WuXing.TU -> Color(0xFFC7A55F)
        WuXing.JIN -> Color(0xFFB9B9AD)
        WuXing.SHUI -> Color(0xFF74A0C6)
    } else when (wx) {
        WuXing.MU -> Color(0xFF4A7345)
        WuXing.HUO -> Color(0xFFAA3A24)
        WuXing.TU -> Color(0xFF8E7134)
        WuXing.JIN -> Color(0xFF6F6F66)
        WuXing.SHUI -> Color(0xFF35607E)
    }
}

/** 五行色取用入口：自动适配明暗主题。 */
@Composable
fun elementColor(wx: WuXing): Color = BaziTokens.wuXing(wx, isSystemInDarkTheme())

private val LightScheme = lightColorScheme(
    primary = BaziTokens.cinnabar,
    onPrimary = BaziTokens.surface,
    primaryContainer = BaziTokens.cinnabarWash,
    onPrimaryContainer = BaziTokens.cinnabar,
    secondary = BaziTokens.celadon,
    onSecondary = BaziTokens.surface,
    background = BaziTokens.paper,
    onBackground = BaziTokens.ink,
    surface = BaziTokens.surface,
    onSurface = BaziTokens.ink,
    surfaceVariant = BaziTokens.surfaceSunken,
    onSurfaceVariant = BaziTokens.inkMuted,
    outline = BaziTokens.line,
    outlineVariant = BaziTokens.line,
    error = BaziTokens.cinnabar
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD27257),
    onPrimary = BaziTokens.nightPaper,
    primaryContainer = Color(0xFF3A211A),
    onPrimaryContainer = Color(0xFFF0BCA9),
    secondary = Color(0xFF7FB3A2),
    onSecondary = BaziTokens.nightPaper,
    background = BaziTokens.nightPaper,
    onBackground = BaziTokens.nightInk,
    surface = BaziTokens.nightSurface,
    onSurface = BaziTokens.nightInk,
    surfaceVariant = BaziTokens.nightSurfaceSunken,
    onSurfaceVariant = BaziTokens.nightMuted,
    outline = BaziTokens.nightLine,
    outlineVariant = BaziTokens.nightLine,
    error = Color(0xFFD27257)
)

/**
 * 字体：标题与干支用衬线（宋体系），正文用系统无衬线。
 * 干支是这套界面的视觉主体，衬线能立住「碑帖」的味道。
 */
private val BaziTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        letterSpacing = 2.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 21.sp,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    ),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontSize = 11.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 10.sp, letterSpacing = 0.3.sp)
)

@Composable
fun BaziTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = BaziTypography,
        content = content
    )
}
