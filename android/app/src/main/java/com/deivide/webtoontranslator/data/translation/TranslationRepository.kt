package com.deivide.webtoontranslator.data.translation

import android.graphics.Bitmap
import com.deivide.webtoontranslator.data.engine.EngineProvider
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.domain.model.Gloss
import com.deivide.webtoontranslator.domain.model.Segment
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.domain.model.TranslationEvent
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import uniffi.webtoon_android.TranslateListener
import uniffi.webtoon_android.TranslateRequest
import uniffi.webtoon_android.Translation as BridgeTranslation

/** OCR + tradução pelo núcleo Rust; o resto do app só enxerga os tipos de `domain.model`. */
class TranslationRepository(private val engines: EngineProvider) {

    /** Texto em inglês de uma imagem (roda fora da thread principal). */
    suspend fun recognize(image: Bitmap): String {
        val engine = engines.get()
        return withContext(Dispatchers.Default) {
            engine.recognize(image.rgbaBytes(), image.width.toUInt(), image.height.toUInt())
        }
    }

    /** Tradução em streaming: emite o original e a tradução assim que ficam prontos, e por fim [TranslationEvent.Done]. */
    fun translate(text: String, settings: AppSettings): Flow<TranslationEvent> = channelFlow {
        val engine = engines.get()
        val listener = object : TranslateListener {
            override fun onField(field: String, value: String) {
                val event = when (field) {
                    "original_text" -> TranslationEvent.Original(value, engine.pronounce(value)?.ptbr.orEmpty())
                    "translation" -> TranslationEvent.Translated(value)
                    else -> return
                }
                trySend(event)
            }
        }
        val request = TranslateRequest(settings.provider.id, settings.baseUrl, settings.apiKey, settings.model, text)
        send(TranslationEvent.Done(engine.translate(request, listener).toDomain()))
    }

    suspend fun listModels(settings: AppSettings): List<String> =
        engines.get().listModels(settings.provider.id, settings.baseUrl, settings.apiKey).sorted()
}

/** Pixels RGBA (o formato que a ponte Rust espera) de um bitmap ARGB_8888. */
private fun Bitmap.rgbaBytes(): ByteArray =
    ByteBuffer.allocate(byteCount).also(::copyPixelsToBuffer).array()

private fun BridgeTranslation.toDomain() = Translation(
    originalText = originalText,
    translation = translation,
    pronunciationPt = pronunciationPt,
    pronunciationIpa = pronunciationIpa,
    segments = segments.map { Segment(it.text, it.pronunciationPt, it.pronunciationIpa, it.meaning, it.note) },
    grammarNotes = grammarNotes,
    gloss = gloss.map { Gloss(it.word, it.meaning) },
    model = model,
    durationSecs = durationSecs,
)
