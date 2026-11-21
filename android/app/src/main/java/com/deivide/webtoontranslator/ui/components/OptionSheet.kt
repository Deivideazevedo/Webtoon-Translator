package com.deivide.webtoontranslator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R

/**
 * Bottom sheet de opções (LAYOUT.md §5.5): título centralizado em negrito, ✕ à direita, itens em
 * blocos arredondados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        }
        Column(
            Modifier
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Item de [OptionSheet]: ícone opcional, título, descrição opcional; o escolhido ganha borda azul. */
@Composable
fun OptionItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, iconColor, size = 36.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (description != null) {
                Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

/** Rótulo de grupo dentro de um [OptionSheet] ("Favoritos", "Todos os modelos"). */
@Composable
fun OptionGroupLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}
