package com.deivide.webtoontranslator.floating

import android.graphics.Bitmap
import android.util.Log
import com.deivide.webtoontranslator.BuildConfig
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.diagnostics.ErrorLog
import com.deivide.webtoontranslator.data.library.LibraryRepository
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.domain.model.TranslationEvent
import com.deivide.webtoontranslator.ui.common.UiText
import com.deivide.webtoontranslator.ui.translation.TranslationCardState
import com.deivide.webtoontranslator.ui.translation.apply
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Área capturada → OCR → tradução em streaming → recentes, como estados do cartão. */
class RegionTranslator(
    private val translations: TranslationRepository,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val errors: ErrorLog,
) {
    fun translate(image: Bitmap): Flow<TranslationCardState> = flow {
        if (!settings.load().isConfigured) {
            emit(TranslationCardState.Failed(UiText.Resource(R.string.error_not_configured)))
            return@flow
        }
        emit(TranslationCardState.Reading)
        val text = try {
            translations.recognize(image)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add("OCR", e)
            emit(TranslationCardState.Failed(UiText.Resource(R.string.error_read_failed), retryable = true))
            return@flow
        }
        if (text.isBlank()) {
            emit(TranslationCardState.Failed(UiText.Resource(R.string.error_no_text)))
            return@flow
        }
        emitAll(translateText(text))
    }

    /**
     * Tradução completa (didática) de um texto já lido, ex.: um balão da tela cheia. Falhas
     * passageiras (rede, API, JSON quebrado pelo modelo) são tentadas de novo uma vez sem avisar;
     * se falhar de novo, o cartão mostra só uma mensagem curta e os detalhes vão pro [ErrorLog].
     */
    fun translateText(text: String): Flow<TranslationCardState> = flow {
        val current = settings.load()
        if (!current.isConfigured) {
            emit(TranslationCardState.Failed(UiText.Resource(R.string.error_not_configured)))
            return@flow
        }
        repeat(ATTEMPTS) { attempt ->
            var card: TranslationCardState? = TranslationCardState.Streaming(original = text)
            emit(card!!)
            try {
                if (BuildConfig.DEBUG) Log.d(DEV_TAG, "OCR leu: \"$text\"")
                translations.translate(text, current).collect { event ->
                    if (BuildConfig.DEBUG && event is TranslationEvent.Done) {
                        val t = event.translation
                        Log.d(DEV_TAG, "IA: original=\"${t.originalText}\" tradução=\"${t.translation}\" notas=\"${t.grammarNotes}\"")
                    }
                    // O modelo não achou inglês pra traduzir (área com outro idioma, ruído…) e explica o
                    // motivo em grammar_notes: mostra o motivo em vez de um cartão vazio, sem salvar nos recentes.
                    if (event is TranslationEvent.Done && event.translation.translation.isBlank()) {
                        val reason = event.translation.grammarNotes.trim()
                        emit(
                            TranslationCardState.Failed(
                                if (reason.isNotEmpty()) UiText.Raw(reason) else UiText.Resource(R.string.error_no_english),
                                retryable = true,
                            )
                        )
                        return@collect
                    }
                    card = card.apply(event)
                    card?.let { emit(it) }
                    if (event is TranslationEvent.Done) library.addRecent(event.translation)
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("tradução (tentativa ${attempt + 1})", e)
            }
        }
        emit(TranslationCardState.Failed(UiText.Resource(R.string.error_translate_failed), retryable = true))
    }

    private companion object {
        /** Primeira tentativa + uma automática. */
        const val ATTEMPTS = 2
        /** Diagnóstico da versão (dev): `adb logcat -s WT-Dev`. */
        const val DEV_TAG = "WT-Dev"
    }
}
