package com.deivide.webtoontranslator.data.translation

import android.content.res.Resources
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import com.deivide.webtoontranslator.data.engine.EngineProvider
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.domain.model.BenchmarkResult
import com.deivide.webtoontranslator.domain.model.ScreenBlock
import com.deivide.webtoontranslator.domain.model.TranslationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import uniffi.webtoon_android.BenchmarkListener
import uniffi.webtoon_android.TranslateListener
import uniffi.webtoon_android.TextBlock
import uniffi.webtoon_android.TranslateRequest

/**
 * OCR + tradução. O OCR é o do Google (ML Kit, no aparelho: rápido e sem o ruído das bordas de
 * balão); a tradução e a pronúncia vêm do núcleo Rust. O resto do app só enxerga os tipos de
 * `domain.model`.
 */
class TranslationRepository(private val engines: EngineProvider, private val debugDir: File? = null) {
    private val googleOcr by lazy { GoogleOcr() }

    /** Carrega o modelo do OCR do Google antes da primeira leitura (a 1ª chamada o inicializa). */
    suspend fun warmUpOcr() {
        runCatching { googleOcr.lines(Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)) }
    }

    /**
     * Texto lido de uma área. [unsureDraft] não é null quando uma linha grande saiu com confiança
     * baixa (fonte estilizada) e ficou de fora: é tudo o que o OCR viu, pra o usuário corrigir.
     */
    data class AreaText(val text: String, val unsureDraft: String?)

    /** Texto em inglês de uma imagem: linhas do balão num parágrafo, balões separados por "\n". */
    suspend fun recognize(image: Bitmap): String = recognizeArea(image).text

    suspend fun recognizeArea(image: Bitmap): AreaText {
        val read = googleBlocks(image)
        return AreaText(read.blocks.joinToString("\n") { it.text }, read.unsureDraft)
    }

    /** Balões da imagem inteira (modo tela cheia), com a caixa de cada um. */
    suspend fun recognizeBlocks(image: Bitmap): List<ScreenBlock> =
        googleBlocks(image).blocks.map { ScreenBlock(it.text, it.left, it.top, it.right, it.bottom) }

    private class OcrRead(val blocks: List<TextBlock>, val unsureDraft: String?)

    /**
     * OCR do Google: descarta as linhas que não são texto do quadrinho e junta as outras em balões
     * pela mesma regra do ocrs (ponte Rust).
     */
    private suspend fun googleBlocks(image: Bitmap): OcrRead {
        val engine = engines.get()
        // Linha mais baixa que isso não é texto legível do quadrinho (ex.: a miniatura de um print
        // no canto da tela, 7–10 px; o texto dos balões tem 40–60 px numa tela de 1080 px).
        val minHeight = image.width / MIN_HEIGHT_DIVISOR
        debugDir?.let { dir -> withContext(Dispatchers.IO) { saveDebugImage(dir, image) } }
        val lines = googleOcr.lines(image)
        val kept = lines.filter { line ->
            // Texto real ficou de 0,56 pra cima nos testes; abaixo disso veio só lixo.
            val drop = when {
                line.confidence < MIN_CONFIDENCE -> "confiança baixa"
                line.bottom - line.top < minHeight -> "baixa demais (< $minHeight px)"
                line.text.count { it.isLetter() } < 2 -> "menos de 2 letras"
                // Barra de endereço do navegador ("asurascans.com/comics/…").
                WEB_ADDRESS.containsMatchIn(line.text) -> "endereço web"
                else -> null
            }
            if (debugDir != null) {
                Log.d(
                    DEV_TAG,
                    "OCR linha \"${line.text}\" conf=%.2f altura=%d caixa=[%d,%d,%d,%d] → %s".format(
                        line.confidence, line.bottom - line.top, line.left, line.top, line.right, line.bottom,
                        drop?.let { "DESCARTADA: $it" } ?: "ok",
                    ),
                )
            }
            drop == null
        }.map { TextBlock(it.text, it.left, it.top, it.right, it.bottom) }
        if (debugDir != null) Log.d(DEV_TAG, "OCR imagem ${image.width}x${image.height}: ${lines.size} linhas, ${kept.size} mantidas")
        // Letra grande e ainda assim insegura: é a fonte (estilizada), não lixo; avisa e oferece digitar.
        val bigLine = Resources.getSystem().displayMetrics.widthPixels / BIG_LINE_DIVISOR
        val unsure = lines.any {
            it.confidence < MIN_CONFIDENCE && it.bottom - it.top >= bigLine && it.text.count(Char::isLetter) >= 2
        }
        val draft = if (unsure) lines.sortedBy { it.top }.joinToString(" ") { it.text } else null
        return OcrRead(withContext(Dispatchers.Default) { engine.groupLines(kept) }, draft)
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


/** Linhas do OCR do Google com confiança abaixo disso são descartadas (lixo, não texto). */
private const val MIN_CONFIDENCE = 0.45f

/** Altura mínima de uma linha: largura da imagem / isso (15 px numa tela de 1080 px). */
private const val MIN_HEIGHT_DIVISOR = 72

/**
 * Linha "grande": altura ≥ largura da tela / isso (72 px numa tela de 1080 px). Letra assim com
 * confiança baixa é fonte estilizada (ex.: "FIRST" de grito, 0,30–0,38); o lixo é bem menor.
 */
private const val BIG_LINE_DIVISOR = 15

/** Endereço de site: "http(s)://", "www." ou "dominio.xx/caminho". */
private val WEB_ADDRESS = Regex("""https?://|www\.|[a-z0-9-]+\.[a-z]{2,}/""", RegexOption.IGNORE_CASE)

/** Diagnóstico da versão (dev): `adb logcat -s WT-Dev`. */
private const val DEV_TAG = "WT-Dev"

/** Última imagem que foi pro OCR (versão dev): `adb pull …/files/debug/ocr.png`. */
private fun saveDebugImage(dir: File, image: Bitmap) {
    runCatching {
        File(dir, "ocr.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
