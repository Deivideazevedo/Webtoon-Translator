package com.deivide.webtoontranslator.floating

import android.graphics.Bitmap
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.library.LibraryRepository
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.domain.model.TranslationEvent
import com.deivide.webtoontranslator.ui.common.UiText
import com.deivide.webtoontranslator.ui.common.toUiText
import com.deivide.webtoontranslator.ui.translation.TranslationCardState
import com.deivide.webtoontranslator.ui.translation.apply
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/** Área capturada → OCR → tradução em streaming → recentes, como estados do cartão. */
class RegionTranslator(
    private val translations: TranslationRepository,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
) {
    fun translate(image: Bitmap): Flow<TranslationCardState> = flow {
        val current = settings.load()
        if (!current.isConfigured) {
            emit(TranslationCardState.Failed(UiText.Resource(R.string.error_not_configured)))
            return@flow
        }
        emit(TranslationCardState.Reading)
        val text = try {
            translations.recognize(image)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(TranslationCardState.Failed(e.toUiText()))
            return@flow
        }
        if (text.isBlank()) {
            emit(TranslationCardState.Failed(UiText.Resource(R.string.error_no_text)))
            return@flow
        }
        var card: TranslationCardState? = TranslationCardState.Streaming(original = text)
        emit(card!!)
        translations.translate(text, current).collect { event ->
            card = card.apply(event)
            card?.let { emit(it) }
            if (event is TranslationEvent.Done) library.addRecent(event.translation)
        }
    }.catch { emit(TranslationCardState.Failed(it.toUiText())) }
}
