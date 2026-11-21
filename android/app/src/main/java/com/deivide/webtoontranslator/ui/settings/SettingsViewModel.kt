package com.deivide.webtoontranslator.ui.settings

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
import com.deivide.webtoontranslator.ui.common.UiText
import com.deivide.webtoontranslator.ui.common.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val translations: TranslationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(repository.load()))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Carrega o que estava salvo pro provedor escolhido (a velocidade da fala é de todos). */
    fun selectProvider(provider: Provider) = _uiState.update {
        it.copy(settings = repository.load(provider).copy(speechSpeed = it.settings.speechSpeed), models = emptyList())
    }

    fun updateBaseUrl(value: String) = edit { copy(baseUrl = value) }

    fun updateApiKey(value: String) = edit { copy(apiKey = value) }

    fun updateModel(value: String) = edit { copy(model = value) }

    fun selectModel(id: String) = _uiState.update { it.copy(settings = it.settings.copy(model = id), models = emptyList()) }

    /** Arredonda em passos de 0,05 (igual ao slider do desktop). */
    fun updateSpeechSpeed(value: Float) = edit { copy(speechSpeed = Math.round(value * 20) / 20f) }

    fun resetSpeechSpeed() = edit { copy(speechSpeed = AppSettings.DEFAULT_SPEECH_SPEED) }

    fun listModels() {
        val settings = _uiState.value.settings
        _uiState.update { it.copy(loadingModels = true, status = UiText.Resource(R.string.settings_loading_models)) }
        viewModelScope.launch {
            runCatching { translations.listModels(settings) }
                .onSuccess { models ->
                    _uiState.update {
                        it.copy(models = models, status = UiText.Plural(R.plurals.settings_models_found, models.size))
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(status = error.toUiText()) } }
            _uiState.update { it.copy(loadingModels = false) }
        }
    }

    fun save() {
        repository.save(_uiState.value.settings)
        _uiState.update { it.copy(status = UiText.Resource(R.string.settings_saved)) }
    }

    private fun edit(change: AppSettings.() -> AppSettings) =
        _uiState.update { it.copy(settings = it.settings.change()) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as WebtoonApp).container
                SettingsViewModel(container.settingsRepository, container.translationRepository)
            }
        }
    }
}
