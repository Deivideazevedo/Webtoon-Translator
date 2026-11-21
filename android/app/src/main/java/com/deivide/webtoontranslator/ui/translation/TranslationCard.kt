package com.deivide.webtoontranslator.ui.translation

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.components.LoadingRow
import com.deivide.webtoontranslator.ui.translation.components.GlossTooltipHost
import com.deivide.webtoontranslator.ui.translation.components.TranslationBody
import com.deivide.webtoontranslator.ui.translation.components.TranslationPreview

/** Ações do cartão; quem usa o cartão decide o que cada uma faz. */
class TranslationCardActions(
    val onSpeak: (text: String, slow: Boolean) -> Unit,
    val onToggleFavorite: (Translation) -> Unit,
    val onOptionsChange: ((ReadingOptions) -> ReadingOptions) -> Unit,
    val onClose: (() -> Unit)? = null,
    /** Refaz a última tradução (aparece quando ela falhou por um motivo passageiro). */
    val onRetry: (() -> Unit)? = null,
)

/**
 * Cartão de tradução (o "balão" do desktop): leitura do OCR, streaming, resultado com ★, 📋,
 * PT-BR ⇄ IPA e 📖 Aprender, ou erro.
 */
@Composable
fun TranslationCard(
    state: TranslationCardState,
    isFavorite: Boolean,
    options: ReadingOptions,
    actions: TranslationCardActions,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        val content = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        GlossTooltipHost(content.padding(start = 16.dp, end = 4.dp, bottom = 16.dp)) {
            Column {
                Header(state, isFavorite, actions)
                Column(Modifier.padding(end = 12.dp)) {
                    when (state) {
                        TranslationCardState.Reading -> LoadingRow(stringResource(R.string.result_reading))
                        is TranslationCardState.Failed ->
                            Text(state.message.asString(), color = MaterialTheme.colorScheme.error)
                        is TranslationCardState.Streaming -> TranslationPreview(state, options, actions)
                        is TranslationCardState.Done -> TranslationBody(state.translation, options, actions)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: TranslationCardState, isFavorite: Boolean, actions: TranslationCardActions) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.result_title),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (state is TranslationCardState.Done) {
            val translation = state.translation
            TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                    ClipData.newPlainText("tradução", "${translation.originalText}\n${translation.translation}")
                )
            }) { Text(stringResource(R.string.action_copy)) }
            IconButton(onClick = { actions.onToggleFavorite(translation) }) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.action_unfavorite else R.string.action_favorite
                    ),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions.onClose?.let { close ->
            IconButton(onClick = close) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
            }
        }
    }
}
