package com.deivide.webtoontranslator.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.settings.components.ConnectionSection
import com.deivide.webtoontranslator.ui.settings.components.ModelSection
import com.deivide.webtoontranslator.ui.settings.components.SpeechSpeedSection

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.settings_title), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            ConnectionSection(
                settings = state.settings,
                onSelectProvider = viewModel::selectProvider,
                onBaseUrlChange = viewModel::updateBaseUrl,
                onApiKeyChange = viewModel::updateApiKey,
            )
            ModelSection(
                model = state.settings.model,
                models = state.models,
                loading = state.loadingModels,
                onModelChange = viewModel::updateModel,
                onListModels = viewModel::listModels,
                onSelectModel = viewModel::selectModel,
            )
            Spacer(Modifier.size(20.dp))
            SpeechSpeedSection(
                speed = state.settings.speechSpeed,
                onSpeedChange = viewModel::updateSpeechSpeed,
                onReset = viewModel::resetSpeechSpeed,
            )
            Spacer(Modifier.size(20.dp))
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_save))
            }
            state.status?.let {
                Text(
                    it.asString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
