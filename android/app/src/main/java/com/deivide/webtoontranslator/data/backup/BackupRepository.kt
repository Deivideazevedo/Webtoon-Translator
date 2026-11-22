package com.deivide.webtoontranslator.data.backup

import com.deivide.webtoontranslator.data.library.LibraryRepository
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.data.settings.Provider
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject

/** O que entrou num import, pra mostrar o resumo. */
data class ImportResult(val settings: Boolean, val apiKeys: Int, val recent: Int, val favorites: Int)

/**
 * Backup no MESMO formato do desktop: o núcleo (core::library) monta e lê o arquivo; aqui as
 * configurações do Android são convertidas de/para os campos do `Settings` do desktop.
 */
class BackupRepository(
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
) {
    suspend fun export(includeKeys: Boolean): String {
        val keys = if (includeKeys) {
            Provider.entries.associate { it.id to settings.apiKeyOf(it) }.filterValues { it.isNotBlank() }
        } else {
            emptyMap()
        }
        return library.exportBackup(settingsJson().toString(), keys)
    }

    suspend fun import(json: String): ImportResult {
        val summary = library.importBackup(json)
        val applied = summary.settingsJson?.let { applySettings(JSONObject(it)) } ?: false
        var keys = 0
        for ((providerId, key) in summary.apiKeys) {
            val provider = Provider.entries.firstOrNull { it.id == providerId } ?: continue
            settings.setApiKey(provider, key)
            keys++
        }
        settings.reload()
        return ImportResult(applied, keys, summary.recent.toInt(), summary.favorites.toInt())
    }

    /** Configurações do Android nos campos do desktop, por cima dos campos só do desktop. */
    private fun settingsJson(): JSONObject {
        val json = settings.desktopExtras?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val current = settings.load()
        val openai = settings.load(Provider.OPENAI)
        val anthropic = settings.load(Provider.ANTHROPIC)
        val google = settings.load(Provider.GOOGLE)
        json.put("provider", current.provider.id)
        json.put("base_url", openai.baseUrl)
        json.put("model", openai.model)
        json.put("anthropic_base_url", anthropic.baseUrl)
        json.put("anthropic_model", anthropic.model)
        json.put("google_base_url", google.baseUrl)
        json.put("google_model", google.model)
        // O desktop guarda a velocidade na escala do motor (0,7 = padrão); o Android mostra 1,0x.
        json.put("speech_speed", AppSettings.nativeRate(current.speechSpeed).toDouble())
        json.put("bubble_opacity", settings.cardOpacity.value.toDouble())
        val favorites = JSONObject()
        Provider.entries.forEach { p -> favorites.put(p.id, JSONArray(settings.favoritesOf(p))) }
        json.put("favorites", favorites)
        return json
    }

    /** Aplica os campos que o Android usa e guarda o JSON inteiro (com os do desktop). */
    private fun applySettings(json: JSONObject): Boolean {
        settings.desktopExtras = json.toString()
        settings.setConnection(Provider.OPENAI, json.optStringOrNull("base_url"), json.optStringOrNull("model"))
        settings.setConnection(Provider.ANTHROPIC, json.optStringOrNull("anthropic_base_url"), json.optStringOrNull("anthropic_model"))
        settings.setConnection(Provider.GOOGLE, json.optStringOrNull("google_base_url"), json.optStringOrNull("google_model"))
        json.optStringOrNull("provider")?.let { id -> Provider.entries.firstOrNull { it.id == id }?.let(settings::setProvider) }
        if (json.has("speech_speed")) {
            val speed = json.getDouble("speech_speed").toFloat() / AppSettings.NATIVE_BASE
            settings.setSpeechSpeed(speed.coerceIn(AppSettings.SPEECH_SPEED_RANGE))
        }
        if (json.has("bubble_opacity")) settings.setCardOpacity(json.getDouble("bubble_opacity").toFloat())
        // Favoritos: soma os do backup aos daqui (como o desktop faz), sem repetir.
        json.optJSONObject("favorites")?.let { favorites ->
            Provider.entries.forEach { p ->
                val imported = favorites.optJSONArray(p.id) ?: return@forEach
                val merged = settings.favoritesOf(p).toMutableList()
                for (i in 0 until imported.length()) {
                    val model = imported.optString(i)
                    if (model.isNotBlank() && model !in merged) merged += model
                }
                settings.setFavoriteModels(p, merged)
            }
        }
        return true
    }
}

private fun JSONObject.optStringOrNull(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null
