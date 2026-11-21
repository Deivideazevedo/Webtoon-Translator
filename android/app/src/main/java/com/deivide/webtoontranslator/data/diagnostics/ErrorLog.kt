package com.deivide.webtoontranslator.data.diagnostics

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Uma falha registrada: quando, em quê ("tradução", "tela cheia"…) e a mensagem técnica. */
data class ErrorEntry(val timeMs: Long, val context: String, val message: String)

/**
 * Registro das últimas falhas (API, OCR…), guardado num arquivo do app. A tela mostra só uma
 * mensagem curta; os detalhes ficam aqui pra uma futura tela "Erros recentes" (ROADMAP).
 */
class ErrorLog(context: Context) {
    private val file = File(context.filesDir, "errors.json")

    @Synchronized
    fun add(context: String, error: Throwable) {
        val entries = read().toMutableList()
        entries.add(0, ErrorEntry(System.currentTimeMillis(), context, error.message ?: error.toString()))
        val json = JSONArray()
        entries.take(MAX_ENTRIES).forEach {
            json.put(JSONObject().put("time_ms", it.timeMs).put("context", it.context).put("message", it.message))
        }
        runCatching { file.writeText(json.toString()) }
    }

    @Synchronized
    fun read(): List<ErrorEntry> = runCatching {
        val json = JSONArray(file.readText())
        (0 until json.length()).map { i ->
            val o = json.getJSONObject(i)
            ErrorEntry(o.getLong("time_ms"), o.getString("context"), o.getString("message"))
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_ENTRIES = 50
    }
}
