package com.deivide.webtoontranslator.ui.home

import com.deivide.webtoontranslator.data.image.Page
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.common.UiText

data class HomeUiState(
    /** OCR carregado (até lá a tela mostra "Preparando o OCR…"). */
    val engineReady: Boolean = false,
    val page: Page? = null,
    /** Cartão de tradução; null = fechado. */
    val result: ResultState? = null,
    /** Aviso rápido (snackbar), consumido pela tela com [HomeViewModel.dismissNotice]. */
    val notice: UiText? = null,
)

/** Etapas do cartão de tradução. */
sealed interface ResultState {
    data object Reading : ResultState

    /** Tradução chegando: cada campo aparece assim que o modelo termina de escrevê-lo. */
    data class Streaming(
        val original: String,
        val translation: String = "",
        val pronunciation: String = "",
    ) : ResultState

    data class Done(val translation: Translation) : ResultState

    data class Failed(val message: UiText) : ResultState
}
