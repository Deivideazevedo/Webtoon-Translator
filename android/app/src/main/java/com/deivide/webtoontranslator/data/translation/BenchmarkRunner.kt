package com.deivide.webtoontranslator.data.translation

import com.deivide.webtoontranslator.data.diagnostics.ErrorLog
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.data.settings.Provider
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Roda o benchmark fora das telas: continua se o usuário sair da Conexão ou minimizar o app (o
 * benchmark demora). O resultado vai pro [SettingsRepository] (ordena a lista de modelos) e os 5
 * mais rápidos viram favoritos do provedor, como no desktop.
 */
class BenchmarkRunner(
    private val translations: TranslationRepository,
    private val settings: SettingsRepository,
    private val errors: ErrorLog,
) {
    data class State(
        val running: Boolean = false,
        val provider: Provider? = null,
        val done: Int = 0,
        val total: Int = 0,
        /** Mensagem da última falha (null = deu certo ou ainda não rodou). */
        val error: String? = null,
        /** Quantas vezes terminou com sucesso (a tela usa pra abrir o resultado ao terminar). */
        val completed: Int = 0,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(connection: AppSettings) {
        if (_state.value.running) return
        _state.update { State(running = true, provider = connection.provider, completed = it.completed) }
        scope.launch {
            runCatching {
                val models = translations.listModels(connection)
                translations.benchmark(connection, models) { done, total ->
                    _state.update { it.copy(done = done, total = total) }
                }
            }.onSuccess { results ->
                settings.saveBenchmark(connection.provider, results)
                val favorites = results.filter { it.favorite }.map { it.model }
                if (favorites.isNotEmpty()) settings.setFavoriteModels(connection.provider, favorites)
                _state.update { it.copy(running = false, completed = it.completed + 1) }
            }.onFailure { e ->
                errors.add("benchmark", e)
                _state.update { it.copy(running = false, error = e.message ?: e.toString()) }
            }
        }
    }
}
