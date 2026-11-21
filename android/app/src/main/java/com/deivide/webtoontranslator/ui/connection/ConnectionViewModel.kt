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
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.ui.common.ModelListLoader
import com.deivide.webtoontranslator.ui.common.UiText
import com.deivide.webtoontranslator.ui.components.ModelList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class ConnectionUiState(
    /** Valores em edição (só vão pro disco no "Salvar"). */
    val settings: AppSettings,
    /** Favoritos (★) do provedor em edição. */
    val favorites: List<String> = emptyList(),
    val models: ModelList = ModelList(),
    val status: UiText? = null,
)

/** Tela Conexão: provedor, URL do gateway, chave de API e modelo. */
class ConnectionViewModel(
    private val repository: SettingsRepository,
    translations: TranslationRepository,
) : ViewModel() {

    private val modelLoader = ModelListLoader(translations, viewModelScope)
    private val local = MutableStateFlow(
        ConnectionUiState(repository.load()).let { it.copy(favorites = repository.favoritesOf(it.settings.provider)) }
    )

    val uiState: StateFlow<ConnectionUiState> = combine(local, modelLoader.list) { l, models -> l.copy(models = models) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

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
                ConnectionViewModel(container.settingsRepository, container.translationRepository)
            }
        }
    }
}
