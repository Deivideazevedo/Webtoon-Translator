package com.deivide.webtoontranslator.data.translation

import android.graphics.Bitmap
import com.deivide.webtoontranslator.data.engine.EngineProvider
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.domain.model.BenchmarkResult
import com.deivide.webtoontranslator.domain.model.ScreenBlock
import com.deivide.webtoontranslator.domain.model.TranslationEvent
import java.nio.ByteBuffer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import uniffi.webtoon_android.BenchmarkListener
import uniffi.webtoon_android.TranslateListener
import uniffi.webtoon_android.TextBlock
import uniffi.webtoon_android.TranslateRequest

/**
 * OCR + tradução. O OCR é o do Google (ML Kit, no aparelho: mais rápido e sem o ruído das bordas
 * de balão); se ele falhar, o ocrs do núcleo Rust lê no lugar. O resto do app só enxerga os tipos
 * de `domain.model`.
 */
class TranslationRepository(private val engines: EngineProvider) {
    private val googleOcr by lazy { GoogleOcr() }

    /** Carrega o modelo do OCR do Google antes da primeira leitura (a 1ª chamada o inicializa). */
    suspend fun warmUpOcr() {
        runCatching { googleOcr.lines(Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)) }
    }

    /** Texto em inglês de uma imagem: linhas do balão num parágrafo, balões separados por "\n". */
    suspend fun recognize(image: Bitmap): String {
        googleBlocksOrNull(image)?.let { blocks -> return blocks.joinToString("\n") { it.text } }
        val engine = engines.get()
        return withContext(Dispatchers.Default) {
            engine.recognize(image.rgbaBytes(), image.width.toUInt(), image.height.toUInt())
        }
    }

    /** Balões da imagem inteira (modo tela cheia), com a caixa de cada um. */
    suspend fun recognizeBlocks(image: Bitmap): List<ScreenBlock> {
        googleBlocksOrNull(image)?.let { blocks -> return blocks.map { ScreenBlock(it.text, it.left, it.top, it.right, it.bottom) } }
        val engine = engines.get()
        return withContext(Dispatchers.Default) {
            engine.recognizeBlocks(image.rgbaBytes(), image.width.toUInt(), image.height.toUInt())
                .map { ScreenBlock(it.text, it.left, it.top, it.right, it.bottom) }
        }
    }

    /** Balões lidos pelo OCR do Google; null se ele falhou (aí quem lê é o ocrs). */
    private suspend fun googleBlocksOrNull(image: Bitmap): List<TextBlock>? = try {
        googleBlocks(image)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * OCR do Google: descarta as linhas que não são texto do quadrinho e junta as outras em balões
     * pela mesma regra do ocrs (ponte Rust).
     */
    private suspend fun googleBlocks(image: Bitmap): List<TextBlock> {
        val engine = engines.get()
        // Linha mais baixa que isso não é texto legível do quadrinho (ex.: a miniatura de um print
        // no canto da tela, 7–10 px; o texto dos balões tem 40–60 px numa tela de 1080 px).
        val minHeight = image.width / MIN_HEIGHT_DIVISOR
        val kept = googleOcr.lines(image).filter { line ->
            // Texto real ficou de 0,56 pra cima nos testes; abaixo disso veio só lixo.
            line.confidence >= MIN_CONFIDENCE &&
                line.bottom - line.top >= minHeight &&
                line.text.count { it.isLetter() } >= 2 &&
                // Barra de endereço do navegador ("asurascans.com/comics/…").
                !WEB_ADDRESS.containsMatchIn(line.text)
        }.map { TextBlock(it.text, it.left, it.top, it.right, it.bottom) }
        return withContext(Dispatchers.Default) { engine.groupLines(kept) }
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

/** Linhas do OCR do Google com confiança abaixo disso são descartadas (lixo, não texto). */
private const val MIN_CONFIDENCE = 0.45f

/** Altura mínima de uma linha: largura da imagem / isso (15 px numa tela de 1080 px). */
private const val MIN_HEIGHT_DIVISOR = 72

/** Endereço de site: "http(s)://", "www." ou "dominio.xx/caminho". */
private val WEB_ADDRESS = Regex("""https?://|www\.|[a-z0-9-]+\.[a-z]{2,}/""", RegexOption.IGNORE_CASE)
