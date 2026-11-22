package com.deivide.webtoontranslator.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Uma voz em inglês instalada no celular (motor Samsung, Google…). */
data class VoiceOption(
    /** Nome interno do Android (ex.: "en-us-x-tpf-local"); é o que fica salvo. */
    val name: String,
    val locale: Locale,
    /** Precisa de internet pra falar (vozes "de rede" do motor). */
    val needsNetwork: Boolean,
)

/**
 * Voz do próprio Android (TextToSpeech): troca de voz, tom e velocidade são nativos e
 * instantâneos (o motor já gera na velocidade pedida, sem esticar o áudio). Vive enquanto o app
 * estiver aberto.
 */
class SpeechPlayer(context: Context) {
    private val _ready = MutableStateFlow(false)
    /** O motor terminou de iniciar (antes disso não dá pra falar nem listar vozes). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val tts = TextToSpeech(context.applicationContext) { status ->
        _ready.value = status == TextToSpeech.SUCCESS
    }

    sealed interface Result {
        data object Spoken : Result
        data object NotReady : Result
        data object MissingEnglishVoice : Result
    }

    /** Vozes em inglês instaladas, EUA primeiro; vazia se o motor ainda não iniciou. */
    fun englishVoices(): List<VoiceOption> {
        if (!_ready.value) return emptyList()
        return runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == "en" && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(compareBy<Voice>({ it.locale.country != "US" }, { it.locale.country }, { it.isNetworkConnectionRequired }, { it.name }))
            .map { VoiceOption(it.name, it.locale, it.isNetworkConnectionRequired) }
    }

    /**
     * Fala [text] com a [voice] escolhida (null = a padrão do motor em inglês dos EUA), no [pitch]
     * (1.0 = normal) e na [rate] (1.0 = normal).
     */
    fun speak(text: String, rate: Float, pitch: Float = 1f, voice: String? = null): Result {
        if (!_ready.value) return Result.NotReady
        val chosen = voice?.let { name -> runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet()).firstOrNull { it.name == name } }
        if (chosen != null) {
            tts.voice = chosen
        } else {
            val language = tts.setLanguage(Locale.US)
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                return Result.MissingEnglishVoice
            }
        }
        tts.setPitch(pitch)
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        return Result.Spoken
    }

    private companion object {
        const val UTTERANCE_ID = "webtoon-speech"
    }
}
