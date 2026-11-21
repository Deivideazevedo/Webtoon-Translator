package com.deivide.webtoontranslator.ui.home

import com.deivide.webtoontranslator.data.library.Library
import com.deivide.webtoontranslator.data.library.SavedList
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.common.UiText

data class HomeUiState(
    /** Tradutor flutuante ligado. */
    val floatingRunning: Boolean = false,
    val tab: SavedList = SavedList.RECENT,
    val library: Library = Library(),
    /** Modo de seleção (segurar o dedo num cartão): ids marcados pra excluir. */
    val selecting: Boolean = false,
    val selection: Set<String> = emptySet(),
    val confirmingDelete: Boolean = false,
    /** Tradução aberta no painel de baixo (o cartão dela fica destacado na lista). */
    val opened: Translation? = null,
    val options: ReadingOptions = ReadingOptions(),
    val notice: UiText? = null,
) {
    val items: List<Translation> get() = library.list(tab)
}
