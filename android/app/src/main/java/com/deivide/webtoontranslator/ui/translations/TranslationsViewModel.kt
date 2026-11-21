package com.deivide.webtoontranslator.ui.translations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.library.LibraryRepository
import com.deivide.webtoontranslator.data.library.SavedList
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.common.UiText
import com.deivide.webtoontranslator.ui.common.toUiText
import com.deivide.webtoontranslator.ui.translation.TranslationActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Aba Traduções: recentes e favoritos, seleção pra excluir e tradução aberta. */
class TranslationsViewModel(
    private val library: LibraryRepository,
    private val actions: TranslationActions,
) : ViewModel() {

    private val local = MutableStateFlow(TranslationsUiState())

    val uiState: StateFlow<TranslationsUiState> =
        combine(local, library.library, actions.options) { l, lib, options ->
            l.copy(library = lib, options = options)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TranslationsUiState())

    init {
        viewModelScope.launch {
            runCatching { library.refresh() }.onFailure { notify(it.toUiText()) }
        }
    }

    fun selectTab(tab: SavedList) = local.update { it.copy(tab = tab, selecting = false, selection = emptySet()) }

    fun onCardClick(translation: Translation) = local.update {
        if (it.selecting) it.withToggled(translation.id) else it.copy(opened = translation)
    }

    /** Segurar o dedo num cartão entra no modo de seleção, já com ele marcado. */
    fun onCardLongClick(translation: Translation) = local.update {
        it.copy(selecting = true, selection = it.selection + translation.id)
    }

    fun clearSelection() = local.update { it.copy(selecting = false, selection = emptySet()) }

    fun requestDelete() = local.update { it.copy(confirmingDelete = it.selection.isNotEmpty()) }

    fun cancelDelete() = local.update { it.copy(confirmingDelete = false) }

    fun confirmDelete() {
        val state = local.value
        local.update { it.copy(confirmingDelete = false, selecting = false, selection = emptySet()) }
        viewModelScope.launch {
            runCatching { library.delete(state.tab, state.selection) }.onFailure { notify(it.toUiText()) }
        }
    }

    fun closeOpened() = local.update { it.copy(opened = null) }

    fun toggleFavorite(translation: Translation) {
        viewModelScope.launch {
            runCatching { actions.toggleFavorite(translation) }.onFailure { notify(it.toUiText()) }
        }
    }

    fun speak(text: String, slow: Boolean) {
        actions.speak(text, slow)?.let(::notify)
    }

    fun updateOptions(change: (ReadingOptions) -> ReadingOptions) = actions.updateOptions(change)

    fun dismissNotice() = local.update { it.copy(notice = null) }

    private fun notify(message: UiText) = local.update { it.copy(notice = message) }

    private fun TranslationsUiState.withToggled(id: String): TranslationsUiState {
        val selection = if (id in selection) selection - id else selection + id
        // Desmarcar o último sai do modo de seleção.
        return copy(selection = selection, selecting = selection.isNotEmpty())
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WebtoonApp).container
                TranslationsViewModel(container.libraryRepository, container.translationActions)
            }
        }
    }
}
