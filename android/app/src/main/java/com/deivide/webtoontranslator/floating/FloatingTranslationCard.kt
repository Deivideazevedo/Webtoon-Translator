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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.alpha
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
import com.deivide.webtoontranslator.ui.translation.components.SpeakButtonSize
import com.deivide.webtoontranslator.ui.translation.components.alignGloss
import kotlin.math.abs
import kotlinx.coroutines.flow.StateFlow

// Cartão flutuante (LAYOUT.md §5.6): sempre escuro e translúcido, por cima de qualquer app.
private val CardBackground = Color(0xFF1E1E1E)
internal val FloatingButtonBackground = Color.White.copy(alpha = 0.12f)
private val Accent = Color(0xFF6EA8FE)
private val TextPrimary = Color(0xFFF2F2F2)
private val TextSecondary = Color(0xFFB8BEC8)

internal val FloatingColors = darkColorScheme(
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
    /** ⚙ opções (modo, voltar fecha, velocidade da voz). */
    val onOptions: () -> Unit,
    /** Toque no rodapé do modelo: escolher outro modelo (e já retraduzir). */
    val onPickModel: () -> Unit,
)

@Composable
internal fun FloatingTranslationCard(
    card: StateFlow<TranslationCardState?>,
    library: StateFlow<Library>,
    options: StateFlow<ReadingOptions>,
    actions: TranslationCardActions,
    controls: FloatingCardControls,
    opacity: StateFlow<Float>,
    backCloses: StateFlow<Boolean>,
) {
    val alpha by opacity.collectAsStateWithLifecycle()
    // "Voltar fecha as traduções": o ✕ sai (o ⌗, se houver, fica no lugar dele).
    val noClose by backCloses.collectAsStateWithLifecycle()
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
                    .background(CardBackground.copy(alpha = alpha))
                    .dragAnywhere(scroll, controls.onDrag)
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.5f)
                    .padding(10.dp)
            ) {
                GlossTooltipHost {
                    Column {
                        Header(current, translation, translation != null && translation.id in lib.favoriteIds, opts, actions, controls, noClose)
                        // A coluna 🔊/🐢 fica na mesma linha vertical do ⚙.
                        Column(Modifier.verticalScroll(scroll)) {
                            Body(current, opts, actions, controls)
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

/** O cabeçalho (fileira de botões) ocupa mais ou menos os primeiros 44 dp do cartão. */
private fun PointerInputScope.isInHeader(y: Float) = y < 44.dp.toPx()

@Composable
private fun Header(
    state: TranslationCardState,
    translation: Translation?,
    isFavorite: Boolean,
    options: ReadingOptions,
    actions: TranslationCardActions,
    controls: FloatingCardControls,
    noClose: Boolean,
) {
    val context = LocalContext.current
    val original = when (state) {
        is TranslationCardState.Done -> state.translation.originalText
        is TranslationCardState.Streaming -> state.original
        else -> ""
    }
    // 🔊/🐢 ficam à esquerda do texto, embaixo do ⚙ (ver [TextBlock]).
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Todos os botões aparecem desde o início (o topo não "pula" quando a tradução termina);
        // os que precisam dela pronta ficam apagados até lá.
        val ready = translation != null
        CircleAction(Icons.Filled.Settings, stringResource(R.string.cd_options), onClick = controls.onOptions)
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
        controls.onReselect?.let { reselect ->
            CircleAction(Icons.Filled.CropFree, stringResource(R.string.cd_reselect), onClick = reselect)
        }
        if (!noClose) {
            actions.onClose?.let { CircleAction(Icons.Filled.Close, stringResource(R.string.action_close), onClick = it) }
        }
    }
}

@Composable
private fun Body(
    state: TranslationCardState,
    options: ReadingOptions,
    actions: TranslationCardActions,
    controls: FloatingCardControls,
) {
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
        is TranslationCardState.Streaming -> TextBlock(
            state.original, emptyList(), state.pronunciationPt, state.pronunciationIpa, options, actions,
        ) {
            if (state.translation.isBlank()) Loading(stringResource(R.string.result_translating))
            else Text(state.translation, fontSize = 18.sp, color = TextPrimary)
        }
        is TranslationCardState.Done -> {
            val t = state.translation
            TextBlock(
                t.originalText, alignGloss(t.originalText, t.gloss), t.pronunciationPt, t.pronunciationIpa, options, actions,
            ) {
                Text(t.translation, fontSize = 18.sp, color = TextPrimary)
            }
            if (options.learnOpen) LearnSection(t, options.copy(learnOpen = true), actions, showToggle = false)
            ModelFooter(stringResource(R.string.result_footer, t.model, t.durationSecs), controls.onPickModel)
        }
    }
}

/**
 * 🔊/🐢 numa coluna estreita à esquerda, logo abaixo do ⚙ e na mesma linha vertical dele; tradução,
 * inglês e pronúncia à direita dela (o mesmo padrão dos trechos).
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
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CircleAction(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.cd_speak)) { actions.onSpeak(text, false) }
            CircleAction(label = "🐢", description = stringResource(R.string.cd_speak_slow)) { actions.onSpeak(text, true) }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            translation()
            GlossText(
                text, gloss,
                color = TextPrimary.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp),
                onSpeakWord = { actions.onSpeak(it, false) },
            )
            val pronunciation = if (options.ipaEnabled && options.showIpa && ipa.isNotBlank()) ipa else pt
            if (pronunciation.isNotBlank()) {
                Text(stringResource(R.string.pronunciation, pronunciation), fontSize = 13.sp, color = TextSecondary)
            }
        }
    }
}

/** Rodapé "modelo · tempo ▾": toque abre a escolha de modelo (que já retraduz). */
@Composable
private fun ModelFooter(text: String, onClick: () -> Unit) {
    val description = stringResource(R.string.cd_change_model)
    Row(
        Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(FloatingButtonBackground)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = TextSecondary, fontSize = 12.sp)
        Text(" ▾", color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun Loading(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
        Text(label, color = TextPrimary, modifier = Modifier.padding(start = 10.dp))
    }
}

/** Botão redondo do cabeçalho: todos do mesmo tamanho; ligado = azul; sem [enabled], apagado. */
@Composable
private fun CircleAction(
    icon: ImageVector? = null,
    description: String,
    label: String? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(SpeakButtonSize)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(if (active) Accent.copy(alpha = 0.3f) else FloatingButtonBackground)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (active) Accent else TextPrimary
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        } else {
            Text(label.orEmpty(), color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
