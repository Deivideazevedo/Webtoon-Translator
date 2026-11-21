package com.deivide.webtoontranslator.data.settings

import android.content.Context
import androidx.core.content.edit

/**
 * Configurações em SharedPreferences. Cada provedor guarda a própria URL, modelo e chave, então
 * trocar de provedor não perde nada; a chave de API vai cifrada pelo [SecretCipher].
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Configurações do provedor em uso. */
    fun load(): AppSettings = load(Provider.from(prefs.getString(KEY_PROVIDER, null)))

    /** Configurações salvas de um provedor específico (pra trocar de provedor na tela). */
    fun load(provider: Provider) = AppSettings(
        provider = provider,
        baseUrl = prefs.getString(provider.key(KEY_BASE_URL), null) ?: provider.defaultBaseUrl,
        model = prefs.getString(provider.key(KEY_MODEL), null).orEmpty(),
        apiKey = prefs.getString(provider.key(KEY_API_KEY), null)?.let(SecretCipher::decrypt).orEmpty(),
        speechSpeed = prefs.getFloat(KEY_SPEECH_SPEED, AppSettings.DEFAULT_SPEECH_SPEED),
    )

    fun save(settings: AppSettings) = prefs.edit {
        val provider = settings.provider
        putString(KEY_PROVIDER, provider.id)
        putString(provider.key(KEY_BASE_URL), settings.baseUrl.trim())
        putString(provider.key(KEY_MODEL), settings.model.trim())
        putString(provider.key(KEY_API_KEY), SecretCipher.encrypt(settings.apiKey.trim()))
        putFloat(KEY_SPEECH_SPEED, settings.speechSpeed)
    }

    private fun Provider.key(name: String) = "$id.$name"

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
        const val KEY_SPEECH_SPEED = "speech_speed"
    }
}
