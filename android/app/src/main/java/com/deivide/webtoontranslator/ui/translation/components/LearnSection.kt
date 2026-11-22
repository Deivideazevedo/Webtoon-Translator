package com.deivide.webtoontranslator.ui.translation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.deivide.webtoontranslator.data.settings.LearnTab
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.domain.model.Segment
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.translation.TranslationCardActions

/**
 * "📖 Aprender ▾": recolhível, com abas Trechos / Gramática. Aberto/fechado e a aba ficam salvos,
 * como no balão do desktop. Some se a tradução não tiver nada pra ensinar.
 */
@Composable
internal fun LearnSection(
    translation: Translation,
    options: ReadingOptions,
    actions: TranslationCardActions,
    /** false quando quem abre/fecha é outro botão (o 📖 do cartão flutuante). */
    showToggle: Boolean = true,
) {
    // Trecho igual à frase inteira não ensina nada a mais (o desktop também esconde).
    val segments = translation.segments.filter { !it.text.equals(translation.originalText, ignoreCase = true) }
    val hasGrammar = translation.grammarNotes.isNotBlank()
    if (segments.isEmpty() && !hasGrammar) return

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    if (showToggle) {
        TextButton(onClick = { actions.onOptionsChange { it.copy(learnOpen = !it.learnOpen) } }) {
            Text(stringResource(if (options.learnOpen) R.string.learn_hide else R.string.learn_show))
        }
    }
    if (!options.learnOpen) return

    // Sem trechos, a aba de gramática é a única que faz sentido (e vice-versa).
    val tab = when {
        segments.isEmpty() -> LearnTab.GRAMMAR
        !hasGrammar -> LearnTab.SEGMENTS
        else -> options.learnTab
    }
    if (segments.isNotEmpty() && hasGrammar) {
        Row(Modifier.padding(bottom = 4.dp)) {
            LearnTabChip(LearnTab.SEGMENTS, tab, R.string.result_segments, actions)
            LearnTabChip(LearnTab.GRAMMAR, tab, R.string.result_grammar, actions)
        }
    }
    val aligned = alignGloss(translation.originalText, translation.gloss)
    when (tab) {
        LearnTab.SEGMENTS -> segments.forEach {
            SegmentItem(it, segmentGloss(translation.originalText, aligned, it.text), options, actions)
        }
        LearnTab.GRAMMAR -> Text(translation.grammarNotes, fontSize = 13.sp)
    }
}

@Composable
private fun LearnTabChip(tab: LearnTab, selected: LearnTab, label: Int, actions: TranslationCardActions) {
    FilterChip(
        selected = tab == selected,
        onClick = { actions.onOptionsChange { it.copy(learnTab = tab) } },
        label = { Text(stringResource(label)) },
        modifier = Modifier.padding(end = 8.dp),
    )
}

@Composable
private fun SegmentItem(
    segment: Segment,
    gloss: List<GlossSpan>,
    options: ReadingOptions,
    actions: TranslationCardActions,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        GlossText(segment.text, gloss, fontSize = 15.sp, onSpeakWord = { actions.onSpeak(it, false) })
        Text(segment.meaning, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            SpeakButtons(segment.text, actions.onSpeak)
            Pronunciation(segment.pronunciationPt, segment.pronunciationIpa, options, actions, Modifier.weight(1f))
        }
        if (segment.note.isNotBlank()) {
            Text(segment.note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}
