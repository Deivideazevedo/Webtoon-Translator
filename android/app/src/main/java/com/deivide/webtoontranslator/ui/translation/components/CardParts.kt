package com.deivide.webtoontranslator.ui.translation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.ReadingOptions

/** Tamanho dos botões redondos do cartão (cabeçalho e coluna 🔊/🐢). */
internal val CircleActionSize = 28.dp

/**
 * Botão redondo do cartão: todos do mesmo tamanho; ligado ([active]) = cor de destaque; sem
 * [enabled], apagado. Ícone ou, sem ele, um rótulo curto ("IPA", "🐢").
 */
@Composable
internal fun CircleAction(
    icon: ImageVector? = null,
    description: String,
    label: String? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(CircleActionSize)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(if (active) colors.primary.copy(alpha = 0.25f) else colors.onSurface.copy(alpha = 0.12f))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (active) colors.primary else colors.onSurface
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        } else {
            Text(label.orEmpty(), color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 🔊 e 🐢 empilhados: a coluna estreita à esquerda do texto (frase e trechos). */
@Composable
internal fun SpeakButtons(text: String, onSpeak: (String, Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CircleAction(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.cd_speak)) { onSpeak(text, false) }
        CircleAction(label = "🐢", description = stringResource(R.string.cd_speak_slow)) { onSpeak(text, true) }
    }
}

/** Pronúncia escrita em PT-BR ou IPA (o botão IPA do cabeçalho alterna, e a escolha fica salva). */
@Composable
internal fun Pronunciation(pt: String, ipa: String, options: ReadingOptions, modifier: Modifier = Modifier) {
    val shown = if (options.ipaEnabled && options.showIpa && ipa.isNotBlank()) ipa else pt
    if (shown.isBlank()) return
    Text(
        stringResource(R.string.pronunciation, shown),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = modifier,
    )
}
