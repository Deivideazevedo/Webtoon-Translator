package com.deivide.webtoontranslator.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Cores do LAYOUT.md §2: neutros medidos no Translate On Screen + o azul do app.

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FE),
    onPrimary = Color(0xFF0D1B33),
    primaryContainer = Color(0xFF6EA8FE).copy(alpha = 0.25f),
    onPrimaryContainer = Color(0xFFDCE8FF),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF242424),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF3A3A3A),
    onSurfaceVariant = Color(0xFF9AA3B2),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerLow = Color(0xFF242424),
    surfaceContainerHigh = Color(0xFF2A2A2A),
    outline = Color(0xFF6B7280),
    outlineVariant = Color(0xFF333333),
    error = Color(0xFFFF8A80),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2D5BD4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2D5BD4).copy(alpha = 0.14f),
    onPrimaryContainer = Color(0xFF13306F),
    background = Color(0xFFE8E8E8),
    onBackground = Color(0xFF1C1C1C),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFF7F7F7),
    outline = Color(0xFF9AA3B2),
    outlineVariant = Color(0xFFDFDFDF),
    error = Color(0xFFD93025),
)

/** Cores de cada categoria de ícone (LAYOUT.md §2): fundo = cor em transparência, ícone sólido. */
@Immutable
data class IconColors(
    val blue: Color = Color(0xFF4C7BEA),
    val purple: Color = Color(0xFF8E5BD9),
    val green: Color = Color(0xFF2E9E6B),
    val orange: Color = Color(0xFFE8892B),
    val sky: Color = Color(0xFF2BA3D9),
    val red: Color = Color(0xFFD9534F),
)

/** O que o Material não tem nome pra: transparência do fundo dos ícones e cor da barra inferior. */
@Immutable
data class AppColors(
    val icons: IconColors = IconColors(),
    /** Fundo do quadrado/círculo do ícone: cor do ícone nessa opacidade. */
    val iconTileAlpha: Float,
    val navigationBar: Color,
)

internal val DarkAppColors = AppColors(iconTileAlpha = 0.20f, navigationBar = Color(0xFF121212))
internal val LightAppColors = AppColors(iconTileAlpha = 0.14f, navigationBar = Color.White)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }
