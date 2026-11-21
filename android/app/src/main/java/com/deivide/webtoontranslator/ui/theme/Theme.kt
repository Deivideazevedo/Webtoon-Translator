package com.deivide.webtoontranslator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mesma paleta do app desktop (tema escuro).
private val Accent = Color(0xFF6EA8FE)
private val Background = Color(0xFF14161B)
private val Card = Color(0xFF1C1F26)
private val CardRaised = Color(0xFF252932)
private val TextMuted = Color(0xFF9AA3B2)
private val TextFaint = Color(0xFF6B7280)
private val ErrorText = Color(0xFFFF8A80)

private val ColorScheme = darkColorScheme(
    primary = Accent,
    background = Background,
    surface = Card,
    surfaceVariant = CardRaised,
    onSurfaceVariant = TextMuted,
    outline = TextFaint,
    error = ErrorText,
)

/** Tema do app; também usado nas janelas flutuantes (por isso não desenha fundo nenhum). */
@Composable
fun WebtoonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, content = content)
}
