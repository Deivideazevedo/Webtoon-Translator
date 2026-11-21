package com.deivide.webtoontranslator.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R

/** Botão grande Iniciar/Parar do tradutor flutuante, com o estado escrito embaixo. */
@Composable
fun FloatingToggle(running: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onToggle,
            shape = CircleShape,
            modifier = Modifier.size(112.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(
                if (running) Icons.Filled.Check else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (running) R.string.floating_stop else R.string.floating_start),
                modifier = Modifier.size(56.dp),
            )
        }
        Text(
            stringResource(if (running) R.string.floating_stop else R.string.floating_start),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(if (running) R.string.floating_running else R.string.floating_stopped),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
        )
    }
}
