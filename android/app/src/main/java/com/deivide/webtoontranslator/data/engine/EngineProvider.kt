package com.deivide.webtoontranslator.data.engine

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.webtoon_android.Engine

/**
 * Dono do motor Rust (tradução, pronúncia, agrupamento das linhas do OCR). Criado uma vez, sob
 * demanda: o dicionário da pronúncia vem nos assets do APK e é copiado pra pasta do app (o Rust lê
 * por caminho), de novo a cada atualização.
 */
class EngineProvider(private val context: Context) {
    private val mutex = Mutex()
    @Volatile private var engine: Engine? = null

    suspend fun get(): Engine = engine ?: mutex.withLock {
        engine ?: withContext(Dispatchers.IO) { create() }.also { engine = it }
    }

    private fun create(): Engine {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        installModels(dir)
        return Engine(File(dir, LEXICON).path).apply {
            // Carrega o dicionário agora, pra primeira tradução não pagar esse tempo.
            runCatching { warmUp() }
        }
    }

    private fun installModels(dir: File) {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        val marker = File(dir, ".installed")
        val upToDate = marker.exists() && marker.readText() == version && MODEL_FILES.all { File(dir, it).exists() }
        if (upToDate) return
        // Modelos do OCR antigo (ocrs), que versões anteriores copiavam pra cá.
        OLD_FILES.forEach { File(dir, it).delete() }
        MODEL_FILES.forEach { name ->
            context.assets.open(name).use { input -> File(dir, name).outputStream().use(input::copyTo) }
        }
        marker.writeText(version)
    }

    private companion object {
        const val LEXICON = "lexicon-en-us.txt"
        val MODEL_FILES = listOf(LEXICON)
        val OLD_FILES = listOf("text-detection.onnx", "text-recognition.onnx")
    }
}
