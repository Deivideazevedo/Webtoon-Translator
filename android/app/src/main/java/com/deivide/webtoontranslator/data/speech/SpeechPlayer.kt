package com.deivide.webtoontranslator.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Voz do próprio Android, em inglês dos EUA. Vive enquanto o app estiver aberto. */
class SpeechPlayer(context: Context) {
    @Volatile private var ready = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    sealed interface Result {
        data object Spoken : Result
        data object NotReady : Result
        data object MissingEnglishVoice : Result
    }

    fun speak(text: String, rate: Float): Result {
        if (!ready) return Result.NotReady
        val language = tts.setLanguage(Locale.US)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            return Result.MissingEnglishVoice
        }
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        return Result.Spoken
    }

    private companion object {
        const val UTTERANCE_ID = "webtoon-speech"
    }
}
