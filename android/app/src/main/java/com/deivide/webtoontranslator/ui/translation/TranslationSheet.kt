package com.deivide.webtoontranslator.ui.translation

import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.library.LibraryRepository
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.floating.RegionTranslator
import com.deivide.webtoontranslator.ui.common.ModelListLoader
import com.deivide.webtoontranslator.ui.common.UiText
import com.deivide.webtoontranslator.ui.common.resolve
import com.deivide.webtoontranslator.ui.components.ModelList
import com.deivide.webtoontranslator.ui.components.ModelSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** O que o sheet de tradução mostra ao abrir: uma tradução salva ou uma frase pra traduzir agora. */
sealed interface TranslationRequest {
    data class Saved(val translation: Translation) : TranslationRequest
    data class Text(val text: String) : TranslationRequest
}

data class TranslationSheetUiState(
    val card: TranslationCardState? = null,
    val isFavorite: Boolean = false,
    val options: ReadingOptions = ReadingOptions(),
    /** Escolhendo outro modelo pelo rodapé. */
    val picking: Boolean = false,
    val model: String = "",
    val favoriteModels: List<String> = emptyList(),
    val speeds: Map<String, Double?> = emptyMap(),
    val models: ModelList = ModelList(),
    val hasApiKey: Boolean = false,
)

/**
 * Cartão de tradução do app num bottom sheet (última tradução, Traduções e frase digitada): o
 * mesmo cartão do tradutor flutuante. O rodapé do modelo troca o modelo e traduz de novo o mesmo texto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSheet(request: TranslationRequest, onDismiss: () -> Unit) {
    val viewModel: TranslationSheetViewModel = viewModel(factory = TranslationSheetViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(request) { viewModel.open(request) }
    LaunchedEffect(viewModel) { viewModel.notices.collect { Toast.makeText(context, it.resolve(context), Toast.LENGTH_SHORT).show() } }
    val actions = remember(viewModel) {
        TranslationCardActions(
            onSpeak = viewModel::speak,
            onToggleFavorite = viewModel::toggleFavorite,
            onOptionsChange = viewModel::updateOptions,
            onRetry = viewModel::retry,
            onPickModel = viewModel::pickModel,
        )
    }
    val card = state.card ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        TranslationCard(card, state.isFavorite, state.options, actions)
    }
    if (state.picking) {
        ModelSheet(
            current = state.model,
            favorites = state.favoriteModels,
            models = state.models,
            hasApiKey = state.hasApiKey,
            speeds = state.speeds,
            onLoad = viewModel::loadModels,
            onSelect = viewModel::selectModel,
            onToggleFavorite = viewModel::toggleFavoriteModel,
            onDismiss = viewModel::dismissPicker,
        )
    }
}

class TranslationSheetViewModel(
    private val settings: SettingsRepository,
    private val translator: RegionTranslator,
    translations: TranslationRepository,
    library: LibraryRepository,
    private val actions: TranslationActions,
) : ViewModel() {

    private val card = MutableStateFlow<TranslationCardState?>(null)
    private val picking = MutableStateFlow(false)
    private val modelLoader = ModelListLoader(translations, viewModelScope)
    private val _notices = MutableSharedFlow<UiText>(extraBufferCapacity = 1)
    /** Avisos curtos (ex.: voz do Android ainda não pronta). */
    val notices: SharedFlow<UiText> = _notices
    private var job: Job? = null
    /** Inglês do cartão: o que "Tentar de novo" e a troca de modelo traduzem. */
    private var text = ""

    private val models = combine(settings.current, settings.favoriteModels, settings.benchmarks, modelLoader.list) { s, f, b, list ->
        TranslationSheetUiState(
            model = s.model,
            favoriteModels = f,
            speeds = b[s.provider]?.speeds.orEmpty(),
            models = list,
            hasApiKey = s.apiKey.isNotBlank(),
        )
    }

    val uiState: StateFlow<TranslationSheetUiState> =
        combine(card, picking, library.library, actions.options, models) { c, p, lib, options, m ->
            val id = (c as? TranslationCardState.Done)?.translation?.id
            m.copy(card = c, picking = p, isFavorite = id != null && id in lib.favoriteIds, options = options)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslationSheetUiState())

    fun open(request: TranslationRequest) {
        picking.value = false
        when (request) {
            is TranslationRequest.Saved -> {
                job?.cancel()
                text = request.translation.originalText
                card.value = TranslationCardState.Done(request.translation)
            }
            is TranslationRequest.Text -> translate(request.text)
        }
    }

    private fun translate(english: String) {
        text = english
        job?.cancel()
        job = viewModelScope.launch {
            translator.translateText(english).collect { state ->
                card.value = state
                if (state is TranslationCardState.Done) {
                    actions.autoSpeak(state.translation)?.let { _notices.tryEmit(it) }
                }
            }
        }
    }

    fun retry() = translate(text)

    fun pickModel() {
        picking.value = true
    }

    fun loadModels() = modelLoader.load(settings.load())

    /** O modelo escolhido vira o padrão do app e já traduz de novo o mesmo texto. */
    fun selectModel(id: String) {
        picking.value = false
        settings.selectModel(id)
        translate(text)
    }

    fun dismissPicker() {
        picking.value = false
    }

    fun toggleFavoriteModel(id: String) = settings.toggleFavoriteModel(id)

    fun speak(text: String, slow: Boolean) {
        actions.speak(text, slow)?.let { _notices.tryEmit(it) }
    }

    fun toggleFavorite(translation: Translation) {
        viewModelScope.launch { actions.toggleFavorite(translation) }
    }

    fun updateOptions(change: (ReadingOptions) -> ReadingOptions) = actions.updateOptions(change)

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WebtoonApp).container
                TranslationSheetViewModel(
                    container.settingsRepository,
                    container.regionTranslator,
                    container.translationRepository,
                    container.libraryRepository,
                    container.translationActions,
                )
            }
        }
    }
}
