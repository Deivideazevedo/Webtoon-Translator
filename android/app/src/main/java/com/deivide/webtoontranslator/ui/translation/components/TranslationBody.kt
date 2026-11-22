package com.deivide.webtoontranslator.ui.translation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    OriginalLine(state.original, emptyList(), state.pronunciationPt, state.pronunciationIpa, options, actions)
}

/** Tradução completa: frase, original com pronúncia, "📖 Aprender" e rodapé. */
@Composable
fun TranslationBody(translation: Translation, options: ReadingOptions, actions: TranslationCardActions) {
    Text(translation.translation, fontSize = 18.sp)
    Spacer(Modifier.size(8.dp))
    OriginalLine(
        translation.originalText,
        alignGloss(translation.originalText, translation.gloss),
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

/** Frase original (com a glosa nas palavras), 🔊/🐢 e a pronúncia. */
@Composable
private fun OriginalLine(
    text: String,
    gloss: List<GlossSpan>,
    pronunciationPt: String,
    pronunciationIpa: String,
    options: ReadingOptions,
    actions: TranslationCardActions,
) {
    if (text.isBlank()) return
    GlossText(
        text, gloss,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        onSpeakWord = { actions.onSpeak(it, false) },
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        SpeakButtons(text, actions.onSpeak)
        Pronunciation(pronunciationPt, pronunciationIpa, options, actions, Modifier.weight(1f))
    }
}

/** 🔊 e 🐢 redondos e compactos: lado a lado ou, com [stacked], um embaixo do outro (trechos). */
@Composable
internal fun SpeakButtons(text: String, onSpeak: (String, Boolean) -> Unit, stacked: Boolean = false) {
    val buttons = @Composable {
        RoundButton(stringResource(R.string.speak_normal), stringResource(R.string.cd_speak)) { onSpeak(text, false) }
        RoundButton(stringResource(R.string.speak_slow), stringResource(R.string.cd_speak_slow)) { onSpeak(text, true) }
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { buttons() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 8.dp)) { buttons() }
    }
}

@Composable
private fun RoundButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(SpeakButtonSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 12.sp) }
}

/** Tamanho dos botões redondos de áudio (o mesmo dos botões do cartão flutuante). */
internal val SpeakButtonSize = 28.dp

/** Pronúncia escrita, com o botão que alterna entre PT-BR e IPA (a escolha fica salva). */
@Composable
internal fun Pronunciation(
    pt: String,
    ipa: String,
    options: ReadingOptions,
    actions: TranslationCardActions,
    modifier: Modifier = Modifier,
) {
    val shown = if (options.ipaEnabled && options.showIpa && ipa.isNotBlank()) ipa else pt
    if (shown.isBlank()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.pronunciation, shown),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        if (options.ipaEnabled && ipa.isNotBlank()) {
            TextButton(onClick = { actions.onOptionsChange { it.copy(showIpa = !it.showIpa) } }) {
                Text(stringResource(if (options.showIpa) R.string.pronunciation_show_pt else R.string.pronunciation_show_ipa))
            }
        }
    }
}
