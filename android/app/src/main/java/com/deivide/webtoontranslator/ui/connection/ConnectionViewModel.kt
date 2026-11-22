package com.deivide.webtoontranslator.ui.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.data.settings.Provider
import com.deivide.webtoontranslator.data.settings.SavedBenchmark
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.translation.BenchmarkRunner
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.ui.common.ModelListLoader
import com.deivide.webtoontranslator.ui.common.UiText
import com.deivide.webtoontranslator.ui.common.toUiText
import com.deivide.webtoontranslator.ui.components.ModelList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** "Testar velocidade": parado, medindo, resultado (s) ou erro. */
data class SpeedTest(val running: Boolean = false, val secs: Double? = null, val error: UiText? = null)

/** Benchmark: andamento (done/total), erro e se o resultado está aberto na tela. */
data class Benchmark(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val error: UiText? = null,
    val showResults: Boolean = false,
)

data class ConnectionUiState(
    /** Valores em edição (só vão pro disco no "Salvar"). */
    val settings: AppSettings,
    /** Favoritos (★) do provedor em edição. */
    val favorites: List<String> = emptyList(),
    val models: ModelList = ModelList(),
    val status: UiText? = null,
    val speedTest: SpeedTest = SpeedTest(),
    val benchmark: Benchmark = Benchmark(),
    /** Último benchmark salvo do provedor em edição. */
    val saved: SavedBenchmark? = null,
)

/** Tela Conexão: provedor, URL do gateway, chave de API e modelo. */
class ConnectionViewModel(
    private val repository: SettingsRepository,
    private val translations: TranslationRepository,
    private val runner: BenchmarkRunner,
) : ViewModel() {

    private val modelLoader = ModelListLoader(translations, viewModelScope)
    private val local = MutableStateFlow(
        ConnectionUiState(repository.load()).let { it.copy(favorites = repository.favoritesOf(it.settings.provider)) }
    )

    val uiState: StateFlow<ConnectionUiState> = combine(
        local, modelLoader.list, repository.benchmarks, runner.state,
    ) { l, models, benchmarks, run ->
        val provider = l.settings.provider
        // O benchmark roda no app (continua fora da tela); aqui só mostra o andamento dele.
        val mine = run.provider == provider
        l.copy(
            models = models,
            saved = benchmarks[provider],
            favorites = repository.favoritesOf(provider),
            benchmark = l.benchmark.copy(
                running = mine && run.running,
                done = if (mine) run.done else 0,
                total = if (mine) run.total else 0,
                error = if (mine) run.error?.let(UiText::Raw) else null,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        // Terminou com a tela aberta: mostra o resultado.
        viewModelScope.launch {
            var seen = runner.state.value.completed
            runner.state.collect { run ->
                if (run.completed > seen && run.provider == local.value.settings.provider) {
                    local.update { it.copy(benchmark = it.benchmark.copy(showResults = true)) }
                }
                seen = run.completed
            }
        }
    }

    /** Carrega o que estava salvo pro provedor escolhido (a velocidade da fala é de todos). */
    fun selectProvider(provider: Provider) = local.update {
        it.copy(
            settings = repository.load(provider).copy(speechSpeed = it.settings.speechSpeed),
            favorites = repository.favoritesOf(provider),
            status = null,
        )
    }

    fun updateBaseUrl(value: String) = edit { copy(baseUrl = value) }

    fun updateApiKey(value: String) = edit { copy(apiKey = value) }

    fun selectModel(id: String) = edit { copy(model = id) }

    fun toggleFavorite(id: String) {
        val provider = local.value.settings.provider
        repository.toggleFavoriteModel(id, provider)
        local.update { it.copy(favorites = repository.favoritesOf(provider)) }
    }

    fun loadModels() = modelLoader.load(local.value.settings)

    fun testSpeed() {
        val settings = local.value.settings
        if (settings.model.isBlank()) {
            local.update { it.copy(speedTest = SpeedTest(error = UiText.Resource(R.string.speed_choose_model))) }
            return
        }
        local.update { it.copy(speedTest = SpeedTest(running = true)) }
        viewModelScope.launch {
            val result = runCatching { translations.testSpeed(settings) }
            local.update { it.copy(speedTest = result.fold({ SpeedTest(secs = it) }, { SpeedTest(error = it.toUiText()) })) }
        }
    }

    /** Tocar em "Benchmark": mostra o último resultado salvo; se nunca rodou, roda. */
    fun openBenchmark() {
        if (uiState.value.saved != null) local.update { it.copy(benchmark = it.benchmark.copy(showResults = true)) }
        else runBenchmark()
    }

    /**
     * Mede todos os modelos da chave em segundo plano ([BenchmarkRunner]): continua mesmo saindo da
     * tela ou minimizando o app. Os 5 mais rápidos viram favoritos (ajustáveis no resultado).
     */
    fun runBenchmark() = runner.start(local.value.settings)

    fun dismissBenchmark() = local.update { it.copy(benchmark = it.benchmark.copy(showResults = false)) }

    fun save() {
        repository.save(local.value.settings)
        local.update { it.copy(status = UiText.Resource(R.string.settings_saved)) }
    }

    private fun edit(change: AppSettings.() -> AppSettings) =
        local.update { it.copy(settings = it.settings.change(), status = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WebtoonApp).container
                ConnectionViewModel(container.settingsRepository, container.translationRepository, container.benchmarkRunner)
            }
        }
    }
}
