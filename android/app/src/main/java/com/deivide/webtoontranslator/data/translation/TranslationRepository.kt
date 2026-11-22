package com.deivide.webtoontranslator.data.translation

import android.graphics.Bitmap
import com.deivide.webtoontranslator.data.engine.EngineProvider
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.domain.model.BenchmarkResult
import com.deivide.webtoontranslator.domain.model.ScreenBlock
import com.deivide.webtoontranslator.domain.model.TranslationEvent
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import uniffi.webtoon_android.BenchmarkListener
import uniffi.webtoon_android.TranslateListener
import uniffi.webtoon_android.TranslateRequest

/** OCR + tradução pelo núcleo Rust; o resto do app só enxerga os tipos de `domain.model`. */
class TranslationRepository(private val engines: EngineProvider) {

    /** Texto em inglês de uma imagem (roda fora da thread principal). */
    suspend fun recognize(image: Bitmap): String {
        val engine = engines.get()
        return withContext(Dispatchers.Default) {
            engine.recognize(image.rgbaBytes(), image.width.toUInt(), image.height.toUInt())
        }
    }

    /** Balões da imagem inteira (modo tela cheia), com a caixa de cada um. */
    suspend fun recognizeBlocks(image: Bitmap): List<ScreenBlock> {
        val engine = engines.get()
        return withContext(Dispatchers.Default) {
            engine.recognizeBlocks(image.rgbaBytes(), image.width.toUInt(), image.height.toUInt())
                .map { ScreenBlock(it.text, it.left, it.top, it.right, it.bottom) }
        }
    }

    /** Tradução curta de vários balões numa chamada só (uma por texto, na mesma ordem). */
    suspend fun translateBlocks(texts: List<String>, settings: AppSettings): List<String> =
        engines.get().translateBlocks(settings.provider.id, settings.baseUrl, settings.apiKey, settings.model, texts)

    /** Tradução em streaming: emite o original e a tradução assim que ficam prontos, e por fim [TranslationEvent.Done]. */
    fun translate(text: String, settings: AppSettings): Flow<TranslationEvent> = channelFlow {
        val engine = engines.get()
        val listener = object : TranslateListener {
            override fun onField(field: String, value: String) {
                val event = when (field) {
                    "original_text" -> {
                        val pronunciation = engine.pronounce(value)
                        TranslationEvent.Original(value, pronunciation?.ptbr.orEmpty(), pronunciation?.ipa.orEmpty())
                    }
                    "translation" -> TranslationEvent.Translated(value)
                    else -> return
                }
                trySend(event)
            }
        }
        val request = TranslateRequest(settings.provider.id, settings.baseUrl, settings.apiKey, settings.model, text)
        send(TranslationEvent.Done(engine.translate(request, listener).toDomain()))
    }

    /** Tempo (s) de uma tradução de teste com o prompt real, no modelo configurado. */
    suspend fun testSpeed(settings: AppSettings): Double =
        engines.get().testSpeed(settings.provider.id, settings.baseUrl, settings.apiKey, settings.model)

    /** Mede todos os [models] (mesmo benchmark do desktop); do mais rápido pro mais lento. */
    suspend fun benchmark(settings: AppSettings, models: List<String>, onProgress: (done: Int, total: Int) -> Unit): List<BenchmarkResult> {
        val listener = object : BenchmarkListener {
            override fun onProgress(done: UInt, total: UInt) = onProgress(done.toInt(), total.toInt())
        }
        return engines.get().benchmark(settings.provider.id, settings.baseUrl, settings.apiKey, models, listener)
            .map { BenchmarkResult(it.model, it.secs, it.error, it.favorite) }
    }

    suspend fun listModels(settings: AppSettings): List<String> =
        engines.get().listModels(settings.provider.id, settings.baseUrl, settings.apiKey).sorted()
}

/** Pixels RGBA (o formato que a ponte Rust espera) de um bitmap ARGB_8888. */
private fun Bitmap.rgbaBytes(): ByteArray =
    ByteBuffer.allocate(byteCount).also(::copyPixelsToBuffer).array()
