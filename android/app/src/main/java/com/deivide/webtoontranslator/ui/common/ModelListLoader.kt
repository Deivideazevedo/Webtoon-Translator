package com.deivide.webtoontranslator.ui.common

import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.data.translation.TranslationRepository
import com.deivide.webtoontranslator.ui.components.ModelList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Busca os modelos da chave pro seletor de modelo (tela Inicial e Conexão). Só busca de novo se a
 * conexão mudou (provedor, URL ou chave) ou se a última tentativa deu erro.
 */
class ModelListLoader(private val translations: TranslationRepository, private val scope: CoroutineScope) {
    private val _list = MutableStateFlow(ModelList())
    val list: StateFlow<ModelList> = _list.asStateFlow()

    fun load(settings: AppSettings) {
        val source = "${settings.provider.id}|${settings.baseUrl}|${settings.apiKey}"
        val current = _list.value
        if (settings.apiKey.isBlank() || current.loading || (current.source == source && current.ids.isNotEmpty())) return
        _list.value = ModelList(source = source, loading = true)
        scope.launch {
            _list.value = runCatching { translations.listModels(settings) }
                .fold({ ModelList(source = source, ids = it) }, { ModelList(source = source, error = it.toUiText()) })
        }
    }
}
