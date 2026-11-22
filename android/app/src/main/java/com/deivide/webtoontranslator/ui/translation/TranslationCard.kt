package com.deivide.webtoontranslator.ui.translation

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.translation.components.CircleAction
import com.deivide.webtoontranslator.ui.translation.components.GlossSpan
import com.deivide.webtoontranslator.ui.translation.components.GlossText
import com.deivide.webtoontranslator.ui.translation.components.GlossTooltipHost
import com.deivide.webtoontranslator.ui.translation.components.LearnSection
import com.deivide.webtoontranslator.ui.translation.components.Pronunciation
import com.deivide.webtoontranslator.ui.translation.components.SpeakButtons
import com.deivide.webtoontranslator.ui.translation.components.alignGloss

/** Ações do cartão; quem usa o cartão decide o que cada uma faz. */
class TranslationCardActions(
    val onSpeak: (text: String, slow: Boolean) -> Unit,
    val onToggleFavorite: (Translation) -> Unit,
    val onOptionsChange: ((ReadingOptions) -> ReadingOptions) -> Unit,
    val onClose: (() -> Unit)? = null,
    /** Refaz a última tradução (aparece quando ela falhou por um motivo passageiro). */
    val onRetry: (() -> Unit)? = null,
    /** Toque no rodapé "modelo · tempo ▾": escolher outro modelo e traduzir de novo. */
    val onPickModel: (() -> Unit)? = null,
)

/**
 * Cartão de tradução, o mesmo no tradutor flutuante e no app: cabeçalho de botões redondos
 * (📋 ★ IPA 📖 … ✕), 🔊/🐢 numa coluna estreita à esquerda da tradução, Aprender (Trechos /
 * Gramática) e o rodapé do modelo. Segue o tema do app. [leading] entra antes do 📋 (⚙ do
 * flutuante) e [trailing] antes do ✕ (⌗ da região fixa); [scroll] null = sem rolagem própria.
 */
@Composable
fun TranslationCardContent(
    state: TranslationCardState,
    isFavorite: Boolean,
    options: ReadingOptions,
    actions: TranslationCardActions,
    modifier: Modifier = Modifier,
    scroll: ScrollState? = rememberScrollState(),
    showClose: Boolean = true,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    GlossTooltipHost(modifier) {
        Column {
            CardHeader(state, isFavorite, options, actions, showClose, leading, trailing)
            Column(if (scroll != null) Modifier.verticalScroll(scroll) else Modifier) {
                CardBody(state, options, actions)
            }
        }
    }
}

/** O cartão dentro dos bottom sheets do app (última tradução, Traduções, frase digitada). */
@Composable
fun TranslationCard(
    state: TranslationCardState,
    isFavorite: Boolean,
    options: ReadingOptions,
    actions: TranslationCardActions,
    modifier: Modifier = Modifier,
) {
    TranslationCardContent(
        state, isFavorite, options, actions,
        modifier = modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
    )
}

@Composable
private fun CardHeader(
    state: TranslationCardState,
    isFavorite: Boolean,
    options: ReadingOptions,
    actions: TranslationCardActions,
    showClose: Boolean,
    leading: @Composable RowScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    val context = LocalContext.current
    val translation = (state as? TranslationCardState.Done)?.translation
    val original = when (state) {
        is TranslationCardState.Done -> state.translation.originalText
        is TranslationCardState.Streaming -> state.original
        else -> ""
    }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Todos os botões aparecem desde o início (o topo não "pula" quando a tradução termina);
        // os que precisam dela pronta ficam apagados até lá.
        val ready = translation != null
        leading()
        CircleAction(Icons.Filled.ContentCopy, stringResource(R.string.action_copy), enabled = ready) {
            translation?.let {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                    ClipData.newPlainText("tradução", "${it.originalText}\n${it.translation}")
                )
            }
        }
        CircleAction(
            if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
            stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
            active = isFavorite,
            enabled = ready,
        ) { translation?.let(actions.onToggleFavorite) }
        if (original.isNotBlank() && options.ipaEnabled) {
            CircleAction(label = "IPA", description = stringResource(R.string.cd_toggle_ipa), active = options.showIpa) {
                actions.onOptionsChange { it.copy(showIpa = !it.showIpa) }
            }
        }
        CircleAction(
            Icons.AutoMirrored.Filled.MenuBook,
            stringResource(R.string.cd_learn),
            active = options.learnOpen,
            enabled = ready,
        ) { actions.onOptionsChange { it.copy(learnOpen = !it.learnOpen) } }
        Spacer(Modifier.weight(1f))
        trailing()
        if (showClose) {
            actions.onClose?.let { CircleAction(Icons.Filled.Close, stringResource(R.string.action_close), onClick = it) }
        }
    }
}

@Composable
private fun CardBody(state: TranslationCardState, options: ReadingOptions, actions: TranslationCardActions) {
    when (state) {
        TranslationCardState.Reading -> Loading(stringResource(R.string.result_reading))
        is TranslationCardState.Failed -> {
            Text(state.message.asString(), color = MaterialTheme.colorScheme.error)
            val retry = actions.onRetry
            if (state.retryable && retry != null) {
                TextButton(onClick = retry, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.action_retry), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        is TranslationCardState.Streaming -> TextBlock(
            state.original, emptyList(), state.pronunciationPt, state.pronunciationIpa, options, actions,
        ) {
            if (state.translation.isBlank()) Loading(stringResource(R.string.result_translating))
            else Text(state.translation, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        is TranslationCardState.Done -> {
            val t = state.translation
            TextBlock(
                t.originalText, alignGloss(t.originalText, t.gloss), t.pronunciationPt, t.pronunciationIpa, options, actions,
            ) {
                Text(t.translation, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            if (options.learnOpen) LearnSection(t, options, actions)
            ModelFooter(stringResource(R.string.result_footer, t.model, t.durationSecs), actions.onPickModel)
        }
    }
}

/**
 * 🔊/🐢 numa coluna estreita à esquerda, logo abaixo do primeiro botão do cabeçalho e na mesma
 * linha vertical dele; tradução, inglês e pronúncia à direita dela (o mesmo padrão dos trechos).
 */
@Composable
private fun TextBlock(
    text: String,
    gloss: List<GlossSpan>,
    pt: String,
    ipa: String,
    options: ReadingOptions,
    actions: TranslationCardActions,
    translation: @Composable () -> Unit,
) {
    if (text.isBlank()) return translation()
    Row {
        SpeakButtons(text, actions.onSpeak)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            translation()
            GlossText(
                text, gloss,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp),
                onSpeakWord = { actions.onSpeak(it, false) },
            )
            Pronunciation(pt, ipa, options)
        }
    }
}

/** Rodapé "modelo · tempo": com [onClick], vira um botão (▾) que troca o modelo e retraduz. */
@Composable
private fun ModelFooter(text: String, onClick: (() -> Unit)?) {
    val description = stringResource(R.string.cd_change_model)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(onClick = onClick).semantics {
                    contentDescription = description
                    role = Role.Button
                }
            )
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = muted, fontSize = 12.sp)
        if (onClick != null) Text(" ▾", color = muted, fontSize = 12.sp)
    }
}

@Composable
private fun Loading(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 10.dp))
    }
}
