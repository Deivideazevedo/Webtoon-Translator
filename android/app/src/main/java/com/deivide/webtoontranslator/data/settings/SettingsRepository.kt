package com.deivide.webtoontranslator.data.settings

import android.content.Context
import android.graphics.Rect
import androidx.core.content.edit
import com.deivide.webtoontranslator.domain.model.BenchmarkResult
import org.json.JSONArray
import org.json.JSONObject
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

    private val _cardOpacity = MutableStateFlow(prefs.getFloat(KEY_CARD_OPACITY, DEFAULT_CARD_OPACITY))
    /** Opacidade do fundo do cartão flutuante (Mais → Aparência). */
    val cardOpacity: StateFlow<Float> = _cardOpacity.asStateFlow()

    private val _backClosesBubbles = MutableStateFlow(prefs.getBoolean(KEY_BACK_CLOSES, false))
    /** Voltar fecha o cartão e as traduções da tela cheia (Mais → Aparência). */
    val backClosesBubbles: StateFlow<Boolean> = _backClosesBubbles.asStateFlow()

    fun setBackClosesBubbles(value: Boolean) {
        prefs.edit { putBoolean(KEY_BACK_CLOSES, value) }
        _backClosesBubbles.value = value
    }

    fun setCardOpacity(value: Float) {
        prefs.edit { putFloat(KEY_CARD_OPACITY, value) }
        _cardOpacity.value = value
    }

    private val _benchmarks = MutableStateFlow(Provider.entries.associateWith { readBenchmark(it) })
    /** Último benchmark de cada provedor (null = nunca rodou). */
    val benchmarks: StateFlow<Map<Provider, SavedBenchmark?>> = _benchmarks.asStateFlow()

    fun saveBenchmark(provider: Provider, results: List<BenchmarkResult>) {
        val saved = SavedBenchmark(System.currentTimeMillis(), results)
        val json = JSONObject().put("time_ms", saved.timeMs).put("results", JSONArray().apply {
            results.forEach { r ->
                put(JSONObject().put("model", r.model).put("secs", r.secs ?: JSONObject.NULL).put("error", r.error ?: JSONObject.NULL))
            }
        })
        prefs.edit { putString(provider.key(KEY_BENCHMARK), json.toString()) }
        _benchmarks.value = _benchmarks.value + (provider to saved)
    }

    private fun readBenchmark(provider: Provider): SavedBenchmark? = runCatching {
        val json = JSONObject(prefs.getString(provider.key(KEY_BENCHMARK), null) ?: return null)
        val array = json.getJSONArray("results")
        SavedBenchmark(
            json.getLong("time_ms"),
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                BenchmarkResult(
                    model = o.getString("model"),
                    secs = if (o.isNull("secs")) null else o.getDouble("secs"),
                    error = if (o.isNull("error")) null else o.getString("error"),
                    favorite = false,
                )
            },
        )
    }.getOrNull()

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
        speechSpeed = speechSpeed(),
    )

    fun save(settings: AppSettings) {
        prefs.edit {
            val provider = settings.provider
            putString(KEY_PROVIDER, provider.id)
            putString(provider.key(KEY_BASE_URL), settings.baseUrl.trim())
            putString(provider.key(KEY_MODEL), settings.model.trim())
            putString(provider.key(KEY_API_KEY), SecretCipher.encrypt(settings.apiKey.trim()))
            putFloat(KEY_SPEECH_SCALE, settings.speechSpeed)
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

    /** Troca os favoritos de um provedor (resultado do benchmark), do mais rápido pro mais lento. */
    fun setFavoriteModels(provider: Provider, models: List<String>) {
        prefs.edit { putString(provider.key(KEY_FAVORITES), models.joinToString("\n")) }
        if (provider == _current.value.provider) _favorites.value = models
    }

    // --- Usado pelo backup (data/backup), que lê/grava todos os provedores de uma vez ---

    fun apiKeyOf(provider: Provider): String =
        prefs.getString(provider.key(KEY_API_KEY), null)?.let(SecretCipher::decrypt).orEmpty()

    fun setApiKey(provider: Provider, key: String) =
        prefs.edit { putString(provider.key(KEY_API_KEY), SecretCipher.encrypt(key.trim())) }

    /** URL e modelo de um provedor (null = não mexe). */
    fun setConnection(provider: Provider, baseUrl: String?, model: String?) = prefs.edit {
        baseUrl?.let { putString(provider.key(KEY_BASE_URL), it.trim()) }
        model?.let { putString(provider.key(KEY_MODEL), it.trim()) }
    }

    fun setProvider(provider: Provider) = prefs.edit { putString(KEY_PROVIDER, provider.id) }

    /**
     * Campos do backup que só o desktop usa (atalho, vozes Kokoro/Piper…): guardados como vieram
     * pra voltar no próximo export e o vai-e-volta PC ⇄ celular não apagar nada.
     */
    var desktopExtras: String?
        get() = prefs.getString(KEY_DESKTOP_EXTRAS, null)
        set(value) = prefs.edit { putString(KEY_DESKTOP_EXTRAS, value) }

    /** Relê tudo do disco (depois de importar um backup). */
    fun reload() {
        _current.value = load()
        _favorites.value = favoritesOf(_current.value.provider)
        _cardOpacity.value = prefs.getFloat(KEY_CARD_OPACITY, DEFAULT_CARD_OPACITY)
        _backClosesBubbles.value = prefs.getBoolean(KEY_BACK_CLOSES, false)
    }

    /** Tom da voz (1.0 = normal). */
    var speechPitch: Float
        get() = prefs.getFloat(KEY_PITCH, DEFAULT_PITCH)
        set(value) = prefs.edit { putFloat(KEY_PITCH, value) }

    /** Voz do Android escolhida (nome interno); null = padrão do motor. */
    var voiceName: String?
        get() = prefs.getString(KEY_VOICE, null)
        set(value) = prefs.edit { putString(KEY_VOICE, value) }

    /** Velocidade da fala (tela Voz), sem mexer no resto. */
    fun setSpeechSpeed(speed: Float) {
        prefs.edit { putFloat(KEY_SPEECH_SCALE, speed) }
        _current.value = load()
    }

    /**
     * Velocidade na escala do app (1,0x = normal). Versões antigas guardavam o valor do motor
     * (0,7 era o padrão): converte uma vez pra escala nova.
     */
    private fun speechSpeed(): Float = when {
        prefs.contains(KEY_SPEECH_SCALE) -> prefs.getFloat(KEY_SPEECH_SCALE, AppSettings.DEFAULT_SPEECH_SPEED)
        prefs.contains(KEY_SPEECH_SPEED) -> (prefs.getFloat(KEY_SPEECH_SPEED, AppSettings.NATIVE_BASE) / AppSettings.NATIVE_BASE)
            .coerceIn(AppSettings.SPEECH_SPEED_RANGE)
        else -> AppSettings.DEFAULT_SPEECH_SPEED
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

    /** O tutorial já abriu uma vez (na primeira abertura do app). */
    var tutorialSeen: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_TUTORIAL_SEEN, value) }

    /** "Não mostrar novamente" na explicação da captura de tela. */
    var skipCaptureExplanation: Boolean
        get() = prefs.getBoolean(KEY_SKIP_CAPTURE_EXPLANATION, false)
        set(value) = prefs.edit { putBoolean(KEY_SKIP_CAPTURE_EXPLANATION, value) }

    private fun Provider.key(name: String) = "$id.$name"

    companion object {
        const val DEFAULT_PITCH = 1f
        const val DEFAULT_CARD_OPACITY = 0.92f
        val CARD_OPACITY_RANGE = 0.5f..1f
        private const val KEY_CARD_OPACITY = "card_opacity"
        private const val KEY_BACK_CLOSES = "back_closes_bubbles"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        private const val KEY_DESKTOP_EXTRAS = "desktop_extras"
        private const val KEY_BENCHMARK = "benchmark"
        val PITCH_RANGE = 0.5f..1.5f

        private const val KEY_PITCH = "speech_pitch"
        private const val KEY_VOICE = "voice_name"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_API_KEY = "api_key"
        /** Escala antiga (valor do motor de voz); só lido pra migrar. */
        private const val KEY_SPEECH_SPEED = "speech_speed"
        private const val KEY_SPEECH_SCALE = "speech_speed_scale"
        private const val KEY_SKIP_CAPTURE_EXPLANATION = "skip_capture_explanation"
        private const val KEY_MODE = "translation_mode"
        private const val KEY_FIXED_REGION = "fixed_region"
        private const val KEY_THEME = "theme"
        private const val KEY_FAVORITES = "favorite_models"
    }
}

/** Resultado salvo de um benchmark (quando rodou + tempos), pra ordenar a lista de modelos. */
data class SavedBenchmark(val timeMs: Long, val results: List<BenchmarkResult>) {
    /** modelo → segundos (null = falhou). */
    val speeds: Map<String, Double?> get() = results.associate { it.model to it.secs }
}
