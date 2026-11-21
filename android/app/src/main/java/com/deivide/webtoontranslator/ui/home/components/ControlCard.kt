package com.deivide.webtoontranslator.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R

/** Par de idiomas (por enquanto fixo: inglês → português). */
@Composable
fun LanguagePair(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        LanguageBox(stringResource(R.string.language_english), Modifier.weight(1f))
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LanguageBox(stringResource(R.string.language_portuguese), Modifier.weight(1f))
    }
}

@Composable
private fun LanguageBox(text: String, modifier: Modifier) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
}

/**
 * Botão grande Iniciar/Parar (LAYOUT.md §5.1): círculo com anel; cinza parado, azul ligado.
 */
@Composable
fun PowerButton(running: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(if (running) R.string.floating_stop else R.string.floating_start)
    Box(
        modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(
                if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(116.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(52.dp),
            )
        }
    }
}
