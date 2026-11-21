package com.deivide.webtoontranslator.floating

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.library.Library
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.translation.TranslationCardActions
import com.deivide.webtoontranslator.ui.translation.TranslationCardState
import com.deivide.webtoontranslator.ui.translation.components.GlossSpan
import com.deivide.webtoontranslator.ui.translation.components.GlossText
import com.deivide.webtoontranslator.ui.translation.components.GlossTooltipHost
import com.deivide.webtoontranslator.ui.translation.components.LearnSection
import com.deivide.webtoontranslator.ui.translation.components.alignGloss
import kotlin.math.abs
import kotlinx.coroutines.flow.StateFlow

// Cartão flutuante (LAYOUT.md §5.6): sempre escuro e translúcido, por cima de qualquer app.
private val CardBackground = Color(0xFF1E1E1E)
private val ButtonBackground = Color.White.copy(alpha = 0.12f)
private val Accent = Color(0xFF6EA8FE)
private val TextPrimary = Color(0xFFF2F2F2)
private val TextSecondary = Color(0xFFB8BEC8)
private const val CARD_ALPHA = 0.92f

private val FloatingColors = darkColorScheme(
    primary = Accent,
    surface = CardBackground,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF8A9099),
    error = Color(0xFFFF8A80),
)

/** Ações extras do cartão flutuante, além das do cartão comum. */
internal class FloatingCardControls(
    /** Arrasto pelo cartão, em pixels. */
    val onDrag: (dx: Float, dy: Float) -> Unit,
    /** ⌗ redefinir a área (só no modo região fixa). */
    val onReselect: (() -> Unit)?,
)

@Composable
internal fun FloatingTranslationCard(
    card: StateFlow<TranslationCardState?>,
    library: StateFlow<Library>,
    options: StateFlow<ReadingOptions>,
    actions: TranslationCardActions,
    controls: FloatingCardControls,
) {
    val state by card.collectAsStateWithLifecycle()
    val lib by library.collectAsStateWithLifecycle()
    val opts by options.collectAsStateWithLifecycle()
    val current = state ?: return
    val translation = (current as? TranslationCardState.Done)?.translation

    val scroll = rememberScrollState()
    // Sem isso, texto sem cor explícita (gramática, trechos) sai escuro sobre o fundo escuro.
    MaterialTheme(colorScheme = FloatingColors) {
        CompositionLocalProvider(LocalContentColor provides TextPrimary) {
            Column(
                Modifier
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardBackground.copy(alpha = CARD_ALPHA))
                    .dragAnywhere(scroll, controls.onDrag)
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.5f)
                    .padding(10.dp)
            ) {
                GlossTooltipHost {
                    Column {
                        Header(current, translation, translation != null && translation.id in lib.favoriteIds, opts, actions, controls)
                        Column(Modifier.verticalScroll(scroll).padding(horizontal = 4.dp)) {
                            Body(current, opts, actions)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Arrastar em qualquer ponto move o cartão. Só quando o conteúdo tem mais do que cabe (ex.:
 * Aprender aberto) o arrasto vertical fica pra rolagem; aí move na horizontal ou pelo cabeçalho.
 * Os toques nos botões continuam funcionando (o gesto só é tomado depois que vira arrasto).
 */
private fun Modifier.dragAnywhere(scroll: ScrollState, onDrag: (Float, Float) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var total = Offset.Zero
        var dragging = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            if (!dragging) {
                total += delta
                if (total.getDistance() < viewConfiguration.touchSlop) continue
                val vertical = abs(total.y) > abs(total.x)
                val scrollable = scroll.canScrollForward || scroll.canScrollBackward
                // Gesto de rolagem do conteúdo: deixa a rolagem cuidar dele.
                if (vertical && scrollable && !isInHeader(down.position.y)) break
                dragging = true
            }
            change.consume()
            onDrag(delta.x, delta.y)
        }
    }
}

/** O cabeçalho (fileira de botões) ocupa mais ou menos os primeiros 56 dp do cartão. */
private fun PointerInputScope.isInHeader(y: Float) = y < 56.dp.toPx()

@Composable
private fun Header(
    state: TranslationCardState,
    translation: Translation?,
    isFavorite: Boolean,
    options: ReadingOptions,
    actions: TranslationCardActions,
    controls: FloatingCardControls,
) {
    val context = LocalContext.current
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
        if (original.isNotBlank()) {
            CircleAction(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.cd_speak)) { actions.onSpeak(original, false) }
            CircleAction(label = "🐢", description = stringResource(R.string.cd_speak_slow)) { actions.onSpeak(original, true) }
        }
        if (translation != null) {
            CircleAction(Icons.Filled.ContentCopy, stringResource(R.string.action_copy)) {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                    ClipData.newPlainText("tradução", "${translation.originalText}\n${translation.translation}")
                )
            }
            CircleAction(
                if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                active = isFavorite,
            ) { actions.onToggleFavorite(translation) }
        }
        if (original.isNotBlank() && options.ipaEnabled) {
            CircleAction(label = "IPA", description = stringResource(R.string.cd_toggle_ipa), active = options.showIpa) {
                actions.onOptionsChange { it.copy(showIpa = !it.showIpa) }
            }
        }
        if (translation != null) {
            CircleAction(
                Icons.AutoMirrored.Filled.MenuBook,
                stringResource(R.string.cd_learn),
                active = options.learnOpen,
            ) { actions.onOptionsChange { it.copy(learnOpen = !it.learnOpen) } }
        }
        Spacer(Modifier.weight(1f))
        controls.onReselect?.let { reselect ->
            CircleAction(Icons.Filled.CropFree, stringResource(R.string.cd_reselect), onClick = reselect)
        }
        actions.onClose?.let { CircleAction(Icons.Filled.Close, stringResource(R.string.action_close), onClick = it) }
    }
}

@Composable
private fun Body(state: TranslationCardState, options: ReadingOptions, actions: TranslationCardActions) {
    when (state) {
        TranslationCardState.Reading -> Loading(stringResource(R.string.result_reading))
        is TranslationCardState.Failed -> {
            Text(state.message.asString(), color = MaterialTheme.colorScheme.error)
            val retry = actions.onRetry
            if (state.retryable && retry != null) {
                TextButton(onClick = retry, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.action_retry), color = Accent)
                }
            }
        }
        is TranslationCardState.Streaming -> {
            if (state.translation.isBlank()) Loading(stringResource(R.string.result_translating))
            else Text(state.translation, fontSize = 18.sp, color = TextPrimary)
            Original(state.original, emptyList(), state.pronunciationPt, state.pronunciationIpa, options)
        }
        is TranslationCardState.Done -> {
            val t = state.translation
            Text(t.translation, fontSize = 18.sp, color = TextPrimary)
            Original(t.originalText, alignGloss(t.originalText, t.gloss), t.pronunciationPt, t.pronunciationIpa, options)
            if (options.learnOpen) LearnSection(t, options.copy(learnOpen = true), actions, showToggle = false)
            Text(
                stringResource(R.string.result_footer, t.model, t.durationSecs),
                color = MaterialTheme.colorScheme.outline,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Original(text: String, gloss: List<GlossSpan>, pt: String, ipa: String, options: ReadingOptions) {
    if (text.isBlank()) return
    GlossText(text, gloss, color = TextPrimary.copy(alpha = 0.85f), modifier = Modifier.padding(top = 6.dp))
    val pronunciation = if (options.ipaEnabled && options.showIpa && ipa.isNotBlank()) ipa else pt
    if (pronunciation.isNotBlank()) {
        Text(stringResource(R.string.pronunciation, pronunciation), fontSize = 13.sp, color = TextSecondary)
    }
}

@Composable
private fun Loading(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
        Text(label, color = TextPrimary, modifier = Modifier.padding(start = 10.dp))
    }
}

/** Botão redondo do cabeçalho: todos do mesmo tamanho; ligado = azul. */
@Composable
private fun CircleAction(
    icon: ImageVector? = null,
    description: String,
    label: String? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) Accent.copy(alpha = 0.3f) else ButtonBackground)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (active) Accent else TextPrimary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        } else {
            Text(label.orEmpty(), color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
