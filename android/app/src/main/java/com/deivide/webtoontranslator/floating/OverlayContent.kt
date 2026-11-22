package com.deivide.webtoontranslator.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.TranslationMode
import com.deivide.webtoontranslator.ui.components.OptionGroupLabel
import com.deivide.webtoontranslator.ui.components.OptionItem
import com.deivide.webtoontranslator.ui.theme.AppTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * Menu do ícone (toque com a seleção armada ou toque longo), no mesmo bottom sheet do ⚙ do cartão:
 * modo de tradução, abrir o app e parar o tradutor. Fechar é o ✕ do sheet.
 */
@Composable
internal fun FloatingIconMenu(
    open: StateFlow<Boolean>,
    bottomInset: Dp,
    mode: TranslationMode,
    onSelectMode: (TranslationMode) -> Unit,
    onOpenApp: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    val icons = AppTheme.colors.icons
    FloatingSheet(open, bottomInset, stringResource(R.string.mode_title), onClose) {
        ModeRows(mode, onSelectMode)
        OptionGroupLabel(stringResource(R.string.floating_section))
        OptionItem(
            stringResource(R.string.floating_open_app), selected = false, onClick = onOpenApp,
            icon = Icons.AutoMirrored.Filled.OpenInNew, iconColor = icons.sky, compact = true,
        )
        OptionItem(
            stringResource(R.string.floating_stop), selected = false, onClick = onStop,
            icon = Icons.Filled.PowerSettingsNew, iconColor = icons.red, compact = true,
        )
    }
}

/** Dica rápida perto do ícone. */
@Composable
internal fun FloatingHint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
