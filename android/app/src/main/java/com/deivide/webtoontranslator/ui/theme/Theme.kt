package com.deivide.webtoontranslator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.em
import com.deivide.webtoontranslator.data.settings.ThemeMode

/**
 * Tema do app (claro/escuro, LAYOUT.md §2). Também é usado nas janelas flutuantes, por isso não
 * desenha fundo nenhum.
 */
@Composable
fun WebtoonTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkAppColors else LightAppColors) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}

/**
 * Espaço entre linhas proporcional ao tamanho da letra (1,3×). O padrão do Material é fixo por
 * estilo (24sp no texto comum), então um texto de 13sp ficava com linhas muito separadas.
 */
private val AppTypography = Typography().run {
    fun TextStyle.tight() = copy(lineHeight = 1.3.em)
    copy(
        displayLarge = displayLarge.tight(), displayMedium = displayMedium.tight(), displaySmall = displaySmall.tight(),
        headlineLarge = headlineLarge.tight(), headlineMedium = headlineMedium.tight(), headlineSmall = headlineSmall.tight(),
        titleLarge = titleLarge.tight(), titleMedium = titleMedium.tight(), titleSmall = titleSmall.tight(),
        bodyLarge = bodyLarge.tight(), bodyMedium = bodyMedium.tight(), bodySmall = bodySmall.tight(),
        labelLarge = labelLarge.tight(), labelMedium = labelMedium.tight(), labelSmall = labelSmall.tight(),
    )
}

/** Atalho pras cores extras do app ([AppColors]). */
object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}
