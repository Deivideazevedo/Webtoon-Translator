package com.deivide.webtoontranslator.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.library.LibraryRepository
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.settings.TranslationMode
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.floating.FloatingStatus
import com.deivide.webtoontranslator.ui.common.ModelListLoader
import com.deivide.webtoontranslator.ui.common.UiText
import com.deivide.webtoontranslator.ui.components.ModelList
import com.deivide.webtoontranslator.ui.translation.TranslationActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val running: Boolean = false,
    val settings: AppSettings? = null,
    val mode: TranslationMode = TranslationMode.REGION,
    val favoriteModels: List<String> = emptyList(),
    val models: ModelList = ModelList(),
    val last: Translation? = null,
    val lastIsFavorite: Boolean = false,
    val options: ReadingOptions = ReadingOptions(),
    val notice: UiText? = null,
)

/** Tela Inicial: Iniciar/Parar, modelo de IA, modo de tradução e última tradução. */
class HomeViewModel(
    private val settings: SettingsRepository,
    translations: TranslationRepository,
    library: LibraryRepository,
    private val actions: TranslationActions,
    floating: FloatingStatus,
    warmUp: suspend () -> Unit,
) : ViewModel() {

    private val modelLoader = ModelListLoader(translations, viewModelScope)
    private val notice = MutableStateFlow<UiText?>(null)

    private val configuration = combine(settings.current, settings.mode, settings.favoriteModels) { s, m, f -> Triple(s, m, f) }

    val uiState: StateFlow<HomeUiState> = combine(
        configuration, floating.running, library.library, actions.options, combine(modelLoader.list, notice) { m, n -> m to n },
    ) { (current, mode, favorites), running, lib, options, (list, message) ->
        val last = lib.recent.firstOrNull()
        HomeUiState(
            running = running,
            settings = current,
            mode = mode,
            favoriteModels = favorites,
            models = list,
            last = last,
            lastIsFavorite = last != null && last.id in lib.favoriteIds,
            options = options,
            notice = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { runCatching { library.refresh() } }
        // OCR carregando desde já, pra primeira tradução não esperar.
        viewModelScope.launch { runCatching { warmUp() } }
    }

    fun loadModels() = modelLoader.load(settings.current.value)

    fun selectModel(id: String) = settings.selectModel(id)

    fun toggleFavoriteModel(id: String) = settings.toggleFavoriteModel(id)

    fun selectMode(mode: TranslationMode) {
        settings.translationMode = mode
    }

    fun speak(text: String, slow: Boolean) {
        actions.speak(text, slow)?.let { notice.value = it }
    }

    fun toggleFavorite(translation: Translation) {
        viewModelScope.launch { actions.toggleFavorite(translation) }
    }

    fun updateOptions(change: (ReadingOptions) -> ReadingOptions) = actions.updateOptions(change)

    fun dismissNotice() = notice.update { null }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WebtoonApp).container
                HomeViewModel(
                    settings = container.settingsRepository,
                    translations = container.translationRepository,
                    library = container.libraryRepository,
                    actions = container.translationActions,
                    floating = container.floatingStatus,
                    warmUp = container::warmUp,
                )
            }
        }
    }
}
