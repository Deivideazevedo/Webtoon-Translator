package com.deivide.webtoontranslator.floating

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.ui.components.ModelList
import com.deivide.webtoontranslator.ui.components.ModelSheetContent
import com.deivide.webtoontranslator.ui.components.SheetHeader
import com.deivide.webtoontranslator.ui.components.SheetItemSpacing
import kotlinx.coroutines.flow.StateFlow

/**
 * Bottom sheet das janelas flutuantes, no mesmo padrão dos sheets do app ([SheetHeader],
 * OptionGroupLabel, OptionItem), que sobe de baixo com o fundo escurecido (o ModalBottomSheet do
 * app não abre fora de uma Activity, então é desenhado aqui numa janela da tela toda). Segue o
 * tema escolhido em Aparência. Tocar fora fecha; o voltar quem trata é a janela. [open] falso faz
 * o sheet descer (o serviço tira a janela logo depois). [onBack] mostra ← no título (subpágina).
 */
@Composable
internal fun FloatingSheet(
    open: StateFlow<Boolean>,
    bottomInset: Dp,
    title: String,
    onClose: () -> Unit,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isOpen by open.collectAsStateWithLifecycle()
    // Começa fechado e já abre: é o que faz a animação de subir rodar ao aparecer.
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = isOpen

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = null, indication = null, onClick = onClose)
            )
        }
        AnimatedVisibility(
            shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable(interactionSource = null, indication = null) {} // toque no sheet não fecha
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.7f)
                    .padding(top = 6.dp, bottom = 12.dp + bottomInset)
            ) {
                SheetHeader(title, onClose, Modifier.padding(horizontal = 10.dp), onBack = onBack)
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(SheetItemSpacing),
                    content = content,
                )
            }
        }
    }
}

/**
 * Escolher modelo pelo cartão flutuante (toque no rodapé): o mesmo conteúdo do seletor da tela
 * Inicial. Escolher já retraduz.
 */
@Composable
internal fun FloatingModelPicker(
    open: StateFlow<Boolean>,
    bottomInset: Dp,
    current: StateFlow<String>,
    favorites: StateFlow<List<String>>,
    speeds: StateFlow<Map<String, Double?>>,
    models: StateFlow<ModelList>,
    hasApiKey: Boolean,
    onLoad: () -> Unit,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onClose: () -> Unit,
) {
    val model by current.collectAsStateWithLifecycle()
    val favs by favorites.collectAsStateWithLifecycle()
    val times by speeds.collectAsStateWithLifecycle()
    val list by models.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { onLoad() }

    FloatingSheet(open, bottomInset, stringResource(R.string.model_sheet_title), onClose) {
        ModelSheetContent(model, favs, list, hasApiKey, onSelect, onToggleFavorite, onOpenConnection = null, speeds = times)
    }
}
