package com.deivide.webtoontranslator.floating

import android.graphics.Bitmap
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.diagnostics.ErrorLog
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.domain.model.ScreenBlock
import com.deivide.webtoontranslator.ui.common.UiText
import kotlin.coroutines.cancellation.CancellationException

/** Modo tela cheia: acha os balões da tela e traduz todos numa chamada só. */
class ScreenTranslator(
    private val translations: TranslationRepository,
    private val settings: SettingsRepository,
    private val errors: ErrorLog,
) {
    sealed interface Result {
        data class Blocks(val blocks: List<ScreenBlock>) : Result
        data class Failed(val message: UiText) : Result
    }

    /**
     * [skipTop]/[skipBottom]: faixas da tela ignoradas (barra de status e de navegação), senão o
     * relógio e a URL do navegador viram "balões". A chamada à IA é tentada de novo uma vez sem
     * avisar; se falhar de novo, só uma mensagem curta (detalhes no [ErrorLog]).
     */
    suspend fun translate(
        screen: Bitmap,
        skipTop: Int,
        skipBottom: Int,
        /**
         * Traduções já feitas (texto do OCR normalizado → tradução): só os balões novos vão pra IA;
         * o mesmo balão lido com uma letra diferente reaproveita a tradução ([AutoText.same]).
         */
        cache: MutableMap<String, String>? = null,
    ): Result {
        val current = settings.load()
        if (!current.isConfigured) return Result.Failed(UiText.Resource(R.string.error_not_configured))
        val blocks = try {
            translations.recognizeBlocks(screen)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add("OCR da tela cheia", e)
            return Result.Failed(UiText.Resource(R.string.error_screen_failed))
        }.filter { it.top >= skipTop && it.bottom <= screen.height - skipBottom }
        if (blocks.isEmpty()) return Result.Failed(UiText.Resource(R.string.error_no_text_screen))

        val cached = blocks.associate { it.text to cache?.let { c -> AutoText.find(c, AutoText.normalize(it.text)) } }
        val missing = blocks.map { it.text }.distinct().filter { cached[it] == null }
        repeat(ATTEMPTS) { attempt ->
            try {
                val fresh = if (missing.isEmpty()) emptyMap()
                else missing.zip(translations.translateBlocks(missing, current)).toMap()
                fresh.forEach { (text, translation) -> cache?.put(AutoText.normalize(text), translation) }
                return Result.Blocks(
                    blocks.map { block -> block.copy(translation = fresh[block.text] ?: cached[block.text].orEmpty()) }
                        // Itens que o modelo achou que não são inglês (ruído, nome de site) voltam vazios.
                        .filter { it.translation.isNotBlank() }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errors.add("tela cheia (tentativa ${attempt + 1})", e)
            }
        }
        return Result.Failed(UiText.Resource(R.string.error_screen_failed))
    }

    private companion object {
        /** Primeira tentativa + uma automática. */
        const val ATTEMPTS = 2
    }
}
