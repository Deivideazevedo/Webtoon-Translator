package com.deivide.webtoontranslator.ui.home.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sync
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
    OptionSheet(stringResource(R.string.mode_title), onDismiss) {
        TranslationMode.entries.forEach { mode ->
            val (icon, color) = modeIcon(mode)
            OptionItem(
                title = stringResource(modeLabel(mode)),
                description = stringResource(modeDescription(mode)),
                icon = icon,
                iconColor = color,
                selected = mode == selected,
                onClick = { onSelect(mode) },
            )
        }
    }
}

/** Ícone e cor de cada modo (os mesmos no app e no tradutor flutuante). */
@Composable
fun modeIcon(mode: TranslationMode) = when (mode) {
    TranslationMode.REGION -> Icons.Filled.CropFree to AppTheme.colors.icons.purple
    TranslationMode.FIXED_REGION -> Icons.Filled.PushPin to AppTheme.colors.icons.green
    TranslationMode.FULL_SCREEN -> Icons.Filled.Fullscreen to AppTheme.colors.icons.blue
    TranslationMode.AUTO_REGION -> Icons.Filled.AutoMode to AppTheme.colors.icons.orange
    TranslationMode.AUTO_FULL_SCREEN -> Icons.Filled.Sync to AppTheme.colors.icons.sky
}

fun modeLabel(mode: TranslationMode) = when (mode) {
    TranslationMode.REGION -> R.string.mode_region
    TranslationMode.FIXED_REGION -> R.string.mode_fixed_region
    TranslationMode.FULL_SCREEN -> R.string.mode_full_screen
    TranslationMode.AUTO_REGION -> R.string.mode_auto_region
    TranslationMode.AUTO_FULL_SCREEN -> R.string.mode_auto_full_screen
}

fun modeDescription(mode: TranslationMode) = when (mode) {
    TranslationMode.REGION -> R.string.mode_region_desc
    TranslationMode.FIXED_REGION -> R.string.mode_fixed_region_desc
    TranslationMode.FULL_SCREEN -> R.string.mode_full_screen_desc
    TranslationMode.AUTO_REGION -> R.string.mode_auto_region_desc
    TranslationMode.AUTO_FULL_SCREEN -> R.string.mode_auto_full_screen_desc
}
