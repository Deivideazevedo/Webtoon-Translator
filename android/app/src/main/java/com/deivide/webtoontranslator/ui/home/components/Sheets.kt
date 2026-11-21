package com.deivide.webtoontranslator.ui.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.TranslationMode
import com.deivide.webtoontranslator.ui.components.OptionItem
import com.deivide.webtoontranslator.ui.components.OptionSheet
import com.deivide.webtoontranslator.ui.theme.AppTheme

/** Seletor de modo de tradução, com ícone colorido e descrição de cada modo. */
@Composable
fun ModeSheet(selected: TranslationMode, onSelect: (TranslationMode) -> Unit, onDismiss: () -> Unit) {
    val icons = AppTheme.colors.icons
    OptionSheet(stringResource(R.string.mode_title), onDismiss) {
        TranslationMode.entries.forEach { mode ->
            val (icon, color, title, description) = when (mode) {
                TranslationMode.REGION -> ModeInfo(Icons.Filled.CropFree, icons.purple, R.string.mode_region, R.string.mode_region_desc)
                TranslationMode.FIXED_REGION -> ModeInfo(Icons.Filled.PushPin, icons.green, R.string.mode_fixed_region, R.string.mode_fixed_region_desc)
                TranslationMode.FULL_SCREEN -> ModeInfo(Icons.Filled.Fullscreen, icons.blue, R.string.mode_full_screen, R.string.mode_full_screen_desc)
            }
            OptionItem(
                title = stringResource(title),
                description = stringResource(description),
                icon = icon,
                iconColor = color,
                selected = mode == selected,
                onClick = { onSelect(mode) },
            )
        }
    }
}

private data class ModeInfo(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val title: Int,
    val description: Int,
)

/** Ícone e cor de cada modo (mesmos do seletor), pra mostrar no seletor da tela Inicial. */
@Composable
fun modeIcon(mode: TranslationMode) = when (mode) {
    TranslationMode.REGION -> Icons.Filled.CropFree to AppTheme.colors.icons.purple
    TranslationMode.FIXED_REGION -> Icons.Filled.PushPin to AppTheme.colors.icons.green
    TranslationMode.FULL_SCREEN -> Icons.Filled.Fullscreen to AppTheme.colors.icons.blue
}

fun modeLabel(mode: TranslationMode) = when (mode) {
    TranslationMode.REGION -> R.string.mode_region
    TranslationMode.FIXED_REGION -> R.string.mode_fixed_region
    TranslationMode.FULL_SCREEN -> R.string.mode_full_screen
}
