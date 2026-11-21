package com.deivide.webtoontranslator.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.domain.model.Segment
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.components.LoadingRow
import com.deivide.webtoontranslator.ui.home.ResultState

private typealias SpeakAction = (text: String, slow: Boolean) -> Unit

/** Cartão de tradução no pé da tela (equivalente ao balão do desktop). */
@Composable
fun ResultCard(
    result: ResultState,
    onClose: () -> Unit,
    onSpeak: SpeakAction,
    modifier: Modifier = Modifier,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.55f
    Card(
        modifier.fillMaxWidth().padding(8.dp).heightIn(max = maxHeight),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.result_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            Column(Modifier.padding(end = 12.dp)) {
                when (result) {
                    ResultState.Reading -> LoadingRow(stringResource(R.string.result_reading))
                    is ResultState.Failed -> Text(result.message.asString(), color = MaterialTheme.colorScheme.error)
                    is ResultState.Streaming -> StreamingBody(result, onSpeak)
                    is ResultState.Done -> TranslationBody(result.translation, onSpeak)
                }
            }
        }
    }
}

@Composable
private fun StreamingBody(state: ResultState.Streaming, onSpeak: SpeakAction) {
    if (state.translation.isBlank()) {
        LoadingRow(stringResource(R.string.result_translating))
    } else {
        Text(state.translation, fontSize = 18.sp)
    }
    Spacer(Modifier.size(8.dp))
    OriginalText(state.original, state.pronunciation, onSpeak)
}

@Composable
private fun TranslationBody(translation: Translation, onSpeak: SpeakAction) {
    Text(translation.translation, fontSize = 18.sp)
    Spacer(Modifier.size(8.dp))
    OriginalText(translation.originalText, translation.pronunciationPt, onSpeak)
    if (translation.segments.isNotEmpty()) {
        SectionTitle(stringResource(R.string.result_segments))
        translation.segments.forEach { SegmentItem(it, onSpeak) }
    }
    if (translation.grammarNotes.isNotBlank()) {
        SectionTitle(stringResource(R.string.result_grammar))
        Text(translation.grammarNotes, fontSize = 13.sp)
    }
    Spacer(Modifier.size(10.dp))
    Text(
        stringResource(R.string.result_footer, translation.model, translation.durationSecs),
        color = MaterialTheme.colorScheme.outline,
        fontSize = 11.sp,
    )
}

/** Frase original com 🔊/🐢 e a pronúncia em PT-BR. */
@Composable
private fun OriginalText(text: String, pronunciation: String, onSpeak: SpeakAction) {
    if (text.isBlank()) return
    Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onSpeak(text, false) }) { Text(stringResource(R.string.speak_normal)) }
        TextButton(onClick = { onSpeak(text, true) }) { Text(stringResource(R.string.speak_slow)) }
        if (pronunciation.isNotBlank()) {
            Text(
                stringResource(R.string.pronunciation, pronunciation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun SegmentItem(segment: Segment, onSpeak: SpeakAction) {
    Column(Modifier.fillMaxWidth().clickable { onSpeak(segment.text, false) }.padding(vertical = 6.dp)) {
        Text(stringResource(R.string.segment_line, segment.text, segment.meaning), fontSize = 14.sp)
        if (segment.pronunciationPt.isNotBlank()) {
            Text(
                stringResource(R.string.pronunciation, segment.pronunciationPt),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        if (segment.note.isNotBlank()) {
            Text(segment.note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text(title, fontWeight = FontWeight.SemiBold)
}
