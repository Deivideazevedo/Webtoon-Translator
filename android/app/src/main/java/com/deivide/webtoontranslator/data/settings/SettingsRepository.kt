package com.deivide.webtoontranslator.data.settings

import android.content.Context
import android.graphics.Rect
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

    var translationMode: TranslationMode
        get() = TranslationMode.from(prefs.getString(KEY_MODE, null))
        set(value) = prefs.edit { putString(KEY_MODE, value.id) }

    /** Área do modo região fixa, em pixels da tela (null = ainda não marcada). */
    var fixedRegion: Rect?
        get() = prefs.getString(KEY_FIXED_REGION, null)?.let(Rect::unflattenFromString)
        set(value) = prefs.edit { putString(KEY_FIXED_REGION, value?.flattenToString()) }

    /** "Não mostrar novamente" na explicação da captura de tela. */
    var skipCaptureExplanation: Boolean
        get() = prefs.getBoolean(KEY_SKIP_CAPTURE_EXPLANATION, false)
        set(value) = prefs.edit { putBoolean(KEY_SKIP_CAPTURE_EXPLANATION, value) }

    private fun Provider.key(name: String) = "$id.$name"

    private companion object {
        const val KEY_PROVIDER = "provider"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_KEY = "api_key"
        const val KEY_SPEECH_SPEED = "speech_speed"
        const val KEY_SKIP_CAPTURE_EXPLANATION = "skip_capture_explanation"
        const val KEY_MODE = "translation_mode"
        const val KEY_FIXED_REGION = "fixed_region"
    }
}
