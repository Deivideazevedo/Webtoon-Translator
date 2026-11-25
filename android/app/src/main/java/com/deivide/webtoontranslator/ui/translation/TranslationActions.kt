package com.deivide.webtoontranslator.ui.translation

import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.library.LibraryRepository
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.data.settings.ReadingOptions
import com.deivide.webtoontranslator.data.settings.ReadingPreferences
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.speech.SpeechPlayer
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.common.UiText
import kotlinx.coroutines.flow.StateFlow

/**
 * O que todo cartão de tradução faz, seja na leitura ou no histórico: falar, favoritar e lembrar
 * como o cartão é exibido. Os ViewModels delegam pra cá em vez de repetir a lógica.
 */
class TranslationActions(
    private val speech: SpeechPlayer,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val reading: ReadingPreferences,
) {
    val options: StateFlow<ReadingOptions> = reading.options

    /** null se falou; senão o aviso pra mostrar. */
    fun speak(text: String, slow: Boolean): UiText? {
        val speed = if (slow) AppSettings.SLOW_SPEECH_SPEED else settings.load().speechSpeed
        return when (speech.speak(text, AppSettings.nativeRate(speed), settings.speechPitch, settings.voiceName)) {
            SpeechPlayer.Result.Spoken -> null
            SpeechPlayer.Result.NotReady -> UiText.Resource(R.string.speech_not_ready)
            SpeechPlayer.Result.MissingEnglishVoice -> UiText.Resource(R.string.speech_missing_voice)
        }
    }

    /** Tradução que acabou de chegar: fala o inglês se a opção estiver ligada (retorno como [speak]). */
    fun autoSpeak(translation: Translation): UiText? =
        if (settings.autoSpeak) speak(translation.originalText, slow = false) else null

    suspend fun toggleFavorite(translation: Translation) {
        val isFavorite = translation.id in library.library.value.favoriteIds
        library.setFavorite(translation, favorite = !isFavorite)
    }

    fun updateOptions(change: (ReadingOptions) -> ReadingOptions) = reading.update(change)

}
