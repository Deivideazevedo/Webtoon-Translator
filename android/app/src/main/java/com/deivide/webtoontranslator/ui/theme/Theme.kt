package com.deivide.webtoontranslator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
    }
}

/** Atalho pras cores extras do app ([AppColors]). */
object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}
