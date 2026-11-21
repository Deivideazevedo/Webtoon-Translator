package com.deivide.webtoontranslator.ui.translation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.components.LoadingRow
import com.deivide.webtoontranslator.ui.translation.TranslationCardActions
import com.deivide.webtoontranslator.ui.translation.TranslationCardState

/** Tradução ainda chegando: o que já fechou no streaming. */
@Composable
fun TranslationPreview(
    state: TranslationCardState.Streaming,
    options: ReadingOptions,
    actions: TranslationCardActions,
) {
    if (state.translation.isBlank()) {
        LoadingRow(stringResource(R.string.result_translating))
    } else {
        Text(state.translation, fontSize = 18.sp)
    }
    Spacer(Modifier.size(8.dp))
    OriginalLine(state.original, state.pronunciationPt, state.pronunciationIpa, options, actions)
}

/** Tradução completa: frase, original com pronúncia, "📖 Aprender" e rodapé. */
@Composable
fun TranslationBody(translation: Translation, options: ReadingOptions, actions: TranslationCardActions) {
    Text(translation.translation, fontSize = 18.sp)
    Spacer(Modifier.size(8.dp))
    OriginalLine(
        translation.originalText,
        translation.pronunciationPt,
        translation.pronunciationIpa,
        options,
        actions,
    )
    LearnSection(translation, options, actions)
    Spacer(Modifier.size(10.dp))
    Text(
        stringResource(R.string.result_footer, translation.model, translation.durationSecs),
        color = MaterialTheme.colorScheme.outline,
        fontSize = 11.sp,
    )
}

/** Frase original com 🔊/🐢 e a pronúncia (PT-BR ou IPA, alternável). */
@Composable
private fun OriginalLine(
    text: String,
    pronunciationPt: String,
    pronunciationIpa: String,
    options: ReadingOptions,
    actions: TranslationCardActions,
) {
    if (text.isBlank()) return
    Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SpeakButtons(text, actions.onSpeak)
        Pronunciation(pronunciationPt, pronunciationIpa, options, actions, Modifier.weight(1f))
    }
}

@Composable
internal fun SpeakButtons(text: String, onSpeak: (String, Boolean) -> Unit) {
    TextButton(onClick = { onSpeak(text, false) }) { Text(stringResource(R.string.speak_normal)) }
    TextButton(onClick = { onSpeak(text, true) }) { Text(stringResource(R.string.speak_slow)) }
}

/** Pronúncia escrita, com o botão que alterna entre PT-BR e IPA (a escolha fica salva). */
@Composable
internal fun Pronunciation(
    pt: String,
    ipa: String,
    options: ReadingOptions,
    actions: TranslationCardActions,
    modifier: Modifier = Modifier,
) {
    val shown = if (options.showIpa && ipa.isNotBlank()) ipa else pt
    if (shown.isBlank()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.pronunciation, shown),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (ipa.isNotBlank()) {
            TextButton(onClick = { actions.onOptionsChange { it.copy(showIpa = !it.showIpa) } }) {
                Text(stringResource(if (options.showIpa) R.string.pronunciation_show_pt else R.string.pronunciation_show_ipa))
            }
        }
    }
}
