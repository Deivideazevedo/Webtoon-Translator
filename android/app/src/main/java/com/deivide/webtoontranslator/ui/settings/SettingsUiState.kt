package com.deivide.webtoontranslator.ui.settings

import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.ui.common.UiText

data class SettingsUiState(
    /** Valores em edição (só vão pro disco no "Salvar"). */
    val settings: AppSettings,
    /** Modelos disponíveis pra chave atual; vazio = lista fechada. */
    val models: List<String> = emptyList(),
    val loadingModels: Boolean = false,
    val status: UiText? = null,
)
