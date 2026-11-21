package com.deivide.webtoontranslator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Abas em pílula (ex.: Recentes | Favoritos): a escolhida fica com fundo azul transparente. */
@Composable
fun <T> PillTabs(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val active = option == selected
            Text(
                label(option),
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Pílula de status com bolinha ("● Iniciar tradutor flutuante"). */
@Composable
fun StatusPill(text: String, active: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) Color(0xFF2E9E6B) else MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
