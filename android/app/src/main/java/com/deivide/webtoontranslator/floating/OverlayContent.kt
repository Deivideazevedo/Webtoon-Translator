package com.deivide.webtoontranslator.floating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.TranslationMode

/** Menu do ícone (toque com a seleção armada ou toque longo): modo, abrir o app, parar. */
@Composable
internal fun FloatingMenu(
    mode: TranslationMode,
    onSelectMode: (TranslationMode) -> Unit,
    onOpenApp: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .width(260.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(vertical = 6.dp)
    ) {
        Text(
            stringResource(R.string.mode_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
        TranslationMode.entries.forEach { option ->
            MenuItem(stringResource(option.label()), selected = option == mode) { onSelectMode(option) }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        MenuItem(stringResource(R.string.floating_open_app), onClick = onOpenApp)
        MenuItem(stringResource(R.string.floating_stop), onClick = onStop)
        MenuItem(stringResource(R.string.action_close), onClick = onClose)
    }
}

private fun TranslationMode.label() = when (this) {
    TranslationMode.REGION -> R.string.mode_region
    TranslationMode.FIXED_REGION -> R.string.mode_fixed_region
}

@Composable
private fun MenuItem(label: String, selected: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
        }
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
