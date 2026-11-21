package com.deivide.webtoontranslator.data.settings

import android.content.Context
import android.graphics.Rect
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Configurações em SharedPreferences. Cada provedor guarda a própria URL, modelo, chave e
 * favoritos, então trocar de provedor não perde nada; a chave de API vai cifrada pelo
 * [SecretCipher]. As telas e o tradutor flutuante observam os `StateFlow`s, então uma mudança
 * feita num lugar (ex.: modo pelo menu do ícone) aparece no outro (tela Inicial).
 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _current = MutableStateFlow(load())
    /** Configurações do provedor em uso, atualizadas a cada [save]/[selectModel]. */
    val current: StateFlow<AppSettings> = _current.asStateFlow()

    private val _mode = MutableStateFlow(TranslationMode.from(prefs.getString(KEY_MODE, null)))
    val mode: StateFlow<TranslationMode> = _mode.asStateFlow()

    private val _theme = MutableStateFlow(ThemeMode.from(prefs.getString(KEY_THEME, null)))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _favorites = MutableStateFlow(favoritesOf(_current.value.provider))
    /** Modelos favoritos (★) do provedor em uso, na ordem em que foram marcados. */
    val favoriteModels: StateFlow<List<String>> = _favorites.asStateFlow()

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

    fun save(settings: AppSettings) {
        prefs.edit {
            val provider = settings.provider
            putString(KEY_PROVIDER, provider.id)
            putString(provider.key(KEY_BASE_URL), settings.baseUrl.trim())
            putString(provider.key(KEY_MODEL), settings.model.trim())
            putString(provider.key(KEY_API_KEY), SecretCipher.encrypt(settings.apiKey.trim()))
            putFloat(KEY_SPEECH_SPEED, settings.speechSpeed)
        }
        _current.value = load()
        _favorites.value = favoritesOf(settings.provider)
    }

    /** Troca só o modelo do provedor em uso (seletor da tela Inicial). */
    fun selectModel(model: String) {
        prefs.edit { putString(_current.value.provider.key(KEY_MODEL), model.trim()) }
        _current.value = load()
    }

    /** Marca/desmarca um modelo como favorito (do provedor em uso, ou de outro na tela Conexão). */
    fun toggleFavoriteModel(model: String, provider: Provider = _current.value.provider) {
        val list = favoritesOf(provider).toMutableList()
        if (!list.remove(model)) list.add(model)
        prefs.edit { putString(provider.key(KEY_FAVORITES), list.joinToString("\n")) }
        if (provider == _current.value.provider) _favorites.value = list
    }

    /** Velocidade da fala (tela Voz), sem mexer no resto. */
    fun setSpeechSpeed(speed: Float) {
        prefs.edit { putFloat(KEY_SPEECH_SPEED, speed) }
        _current.value = load()
    }

    fun favoritesOf(provider: Provider): List<String> =
        prefs.getString(provider.key(KEY_FAVORITES), null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    var translationMode: TranslationMode
        get() = _mode.value
        set(value) {
            prefs.edit { putString(KEY_MODE, value.id) }
            _mode.value = value
        }

    var themeMode: ThemeMode
        get() = _theme.value
        set(value) {
            prefs.edit { putString(KEY_THEME, value.id) }
            _theme.value = value
        }

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
        const val KEY_THEME = "theme"
        const val KEY_FAVORITES = "favorite_models"
    }
}
