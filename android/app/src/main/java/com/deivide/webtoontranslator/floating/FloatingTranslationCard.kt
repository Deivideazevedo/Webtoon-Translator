package com.deivide.webtoontranslator.floating

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.library.Library
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.ui.translation.TranslationCardActions
import com.deivide.webtoontranslator.ui.translation.TranslationCardContent
import com.deivide.webtoontranslator.ui.translation.TranslationCardState
import com.deivide.webtoontranslator.ui.translation.components.CircleAction
import kotlin.math.abs
import kotlinx.coroutines.flow.StateFlow

/** Ações extras do cartão flutuante, além das do cartão comum. */
internal class FloatingCardControls(
    /** Arrasto pelo cartão, em pixels. */
    val onDrag: (dx: Float, dy: Float) -> Unit,
    /** ⌗ redefinir a área (só no modo região fixa). */
    val onReselect: (() -> Unit)?,
    /** ⚙ opções (modo, voltar fecha, velocidade da voz). */
    val onOptions: () -> Unit,
)

/**
 * Cartão flutuante (LAYOUT.md §5.6): o mesmo cartão do app ([TranslationCardContent]), no tema
 * escolhido em Aparência e translúcido, por cima de qualquer app; arrasta segurando em qualquer ponto.
 */
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
    // Sem isso, texto sem cor explícita (gramática, trechos) sai com a cor errada sobre o fundo.
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        TranslationCardContent(
            current,
            isFavorite = translation != null && translation.id in lib.favoriteIds,
            options = opts,
            actions = actions,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
                .dragAnywhere(scroll, controls.onDrag)
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.5f)
                .padding(10.dp),
            scroll = scroll,
            showClose = !noClose,
            leading = { CircleAction(Icons.Filled.Settings, stringResource(R.string.cd_options), onClick = controls.onOptions) },
            trailing = {
                controls.onReselect?.let { reselect ->
                    CircleAction(Icons.Filled.CropFree, stringResource(R.string.cd_reselect), onClick = reselect)
                }
            },
        )
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
