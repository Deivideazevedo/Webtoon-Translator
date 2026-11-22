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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
 * Bottom sheet de opções (LAYOUT.md §5.5): título discreto centralizado, ✕ arredondado à direita,
 * itens em blocos arredondados. As janelas flutuantes desenham o mesmo padrão (FloatingSheet),
 * com [SheetHeader], [OptionGroupLabel] e [OptionItem].
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
        SheetHeader(title, onDismiss, Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        Column(
            Modifier
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(SheetItemSpacing),
            content = content,
        )
    }
}

/** Espaço entre os blocos de um sheet. */
val SheetItemSpacing = 6.dp

/** Título do sheet: discreto e centralizado, ✕ arredondado à direita ([onBack]: ← à esquerda). */
@Composable
fun SheetHeader(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Box(modifier.fillMaxWidth()) {
        if (onBack != null) {
            SheetIconButton(
                Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack,
                Modifier.align(Alignment.CenterStart),
            )
        }
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 44.dp),
        )
        SheetIconButton(
            Icons.Rounded.Close, stringResource(R.string.action_close), onClose,
            Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun SheetIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
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
    /** Menos altura, pra caber mais itens na tela (modelos, opções do tradutor flutuante). */
    compact: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 44.dp else 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp), // mesmo respiro nos quatro lados
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconTile(icon, iconColor, size = if (compact) 30.dp else 36.dp)
            Spacer(Modifier.width(if (compact) 12.dp else 14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = if (compact) 16.sp else 17.sp,
                // Compacto: linhas mais justas, a descrição fica coladinha no título.
                lineHeight = if (compact) 20.sp else TextUnit.Unspecified,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (description != null) {
                Text(
                    description,
                    fontSize = 13.sp,
                    lineHeight = if (compact) 16.sp else TextUnit.Unspecified,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
