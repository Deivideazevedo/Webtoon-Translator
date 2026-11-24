package com.deivide.webtoontranslator.ui.translation

import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.domain.model.TranslationEvent
import com.deivide.webtoontranslator.ui.common.UiText

/** Etapas do cartão de tradução. */
sealed interface TranslationCardState {
    data object Reading : TranslationCardState

    /** Tradução chegando: cada campo aparece assim que o modelo termina de escrevê-lo. */
    data class Streaming(
        val original: String,
        val translation: String = "",
        val pronunciationPt: String = "",
        val pronunciationIpa: String = "",
    ) : TranslationCardState

    /**
     * [unsureDraft]: o OCR não teve certeza de parte do texto (fonte estilizada); o cartão avisa e
     * oferece digitar, já com tudo o que foi visto.
     */
    data class Done(val translation: Translation, val unsureDraft: String? = null) : TranslationCardState

    /**
     * [retryable]: falha passageira (API, rede, resposta quebrada) → o cartão oferece "Tentar de novo".
     * [model]: o modelo em uso; o rodapé dele aparece pra trocar por outro. [unsureDraft]: como em [Done].
     */
    data class Failed(
        val message: UiText,
        val retryable: Boolean = false,
        val model: String? = null,
        val unsureDraft: String? = null,
    ) : TranslationCardState
}

/** Aplica um evento do streaming ao cartão (ignorado se o cartão foi fechado nesse meio-tempo). */
fun TranslationCardState?.apply(event: TranslationEvent): TranslationCardState? = when (event) {
    is TranslationEvent.Done -> if (this == null) null else TranslationCardState.Done(event.translation)
    is TranslationEvent.Original -> (this as? TranslationCardState.Streaming)?.copy(
        original = event.text,
        pronunciationPt = event.pronunciationPt,
        pronunciationIpa = event.pronunciationIpa,
    ) ?: this
    is TranslationEvent.Translated -> (this as? TranslationCardState.Streaming)?.copy(translation = event.text) ?: this
}
