package com.deivide.webtoontranslator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deivide.webtoontranslator.ui.theme.AppTheme

/**
 * Ícone no padrão do app (LAYOUT.md §3): quadrado de cantos arredondados (ou círculo) com o
 * fundo na cor do ícone em transparência e o ícone na mesma cor, sólida.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    size: Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(if (round) CircleShape else RoundedCornerShape(size / 4))
            .background(color.copy(alpha = AppTheme.colors.iconTileAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** Botão redondo solto (barra do topo, ações de cartão), no mesmo padrão do [IconTile]. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 44.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}
