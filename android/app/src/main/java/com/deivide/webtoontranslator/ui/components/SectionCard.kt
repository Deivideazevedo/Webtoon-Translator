package com.deivide.webtoontranslator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cartão de seção (LAYOUT.md §4): cantos 20 dp, cor de cartão, sobre o fundo neutro. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    padding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (padding) Modifier.padding(16.dp) else Modifier),
        content = content,
    )
}

/** Título de seção solto sobre o fundo, acima de um [SectionCard]. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp),
    )
}

/**
 * Linha de lista com ícone (LAYOUT.md §5.3): ícone + título + legenda opcional + o que vier à
 * direita (seta, switch, valor). [divider] desenha a linha de cima, recuada depois do ícone.
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    color: Color,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    },
) {
    Column(modifier) {
        if (divider) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(icon, color)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing()
        }
    }
}

/**
 * Seletor (LAYOUT.md §5.1): bloco arredondado na cor de campo com ícone, rótulo pequeno, valor e
 * ▾; tocar abre um bottom sheet de opções.
 */
@Composable
fun SelectorField(
    icon: ImageVector,
    color: Color,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, color, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
