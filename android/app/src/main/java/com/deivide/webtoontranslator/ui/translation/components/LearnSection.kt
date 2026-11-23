package com.deivide.webtoontranslator.ui.translation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.LearnTab
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.domain.model.Segment
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.translation.TranslationCardActions

/**
 * "Aprender" (aberto pelo 📖 do cabeçalho do cartão): abas Trechos / Gramática. Aberto/fechado e
 * a aba ficam salvos, como no balão do desktop. Some se a tradução não tiver nada pra ensinar.
 */
@Composable
internal fun LearnSection(
    translation: Translation,
    options: ReadingOptions,
    actions: TranslationCardActions,
) {
    // Trecho igual à frase inteira não ensina nada a mais (o desktop também esconde).
    val segments = translation.segments.filter { !it.text.equals(translation.originalText, ignoreCase = true) }
    val hasGrammar = translation.grammarNotes.isNotBlank()
    if (segments.isEmpty() && !hasGrammar) return

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
        LearnTab.SEGMENTS -> segments.forEachIndexed { i, it ->
            // Linha fina só entre um trecho e outro.
            if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
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

/**
 * Um trecho no padrão do cartão: 🔊/🐢 numa coluna estreita à esquerda e, à direita dela, o trecho,
 * a pronúncia e o sentido; a nota do modelo (às vezes "Literal: … → aqui: …", às vezes nenhuma)
 * vem embaixo, com uma barrinha que marca que é um comentário à parte.
 */
@Composable
private fun SegmentItem(
    segment: Segment,
    gloss: List<GlossSpan>,
    options: ReadingOptions,
    actions: TranslationCardActions,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SpeakButtons(segment.text, actions.onSpeak)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            GlossText(segment.text, gloss, fontSize = 15.sp, onSpeakWord = { actions.onSpeak(it, false) })
            Pronunciation(segment.pronunciationPt, segment.pronunciationIpa, options, Modifier.padding(top = 4.dp))
            Text(
                segment.meaning,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (segment.note.isNotBlank()) SegmentNote(segment.note)
        }
    }
}

/** Nota do trecho, com "Literal:" e "aqui:" em destaque quando o modelo usa esse formato. */
@Composable
private fun SegmentNote(note: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val text = buildAnnotatedString {
        var last = 0
        NOTE_LABELS.findAll(note).forEach { label ->
            append(note.substring(last, label.range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(label.value) }
            last = label.range.last + 1
        }
        append(note.substring(last))
    }
    Row(Modifier.padding(top = 4.dp).height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.dp))
                .background(muted.copy(alpha = 0.4f))
        )
        Text(text, color = muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

private val NOTE_LABELS = Regex("""\b(Literal|[Aa]qui):""")
