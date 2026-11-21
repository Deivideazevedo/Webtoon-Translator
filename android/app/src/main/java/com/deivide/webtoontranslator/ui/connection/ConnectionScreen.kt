package com.deivide.webtoontranslator.ui.connection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.Provider
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.components.AppTextField
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.components.ModelSheet
import com.deivide.webtoontranslator.ui.components.OptionItem
import com.deivide.webtoontranslator.ui.components.OptionSheet
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SectionTitle
import com.deivide.webtoontranslator.ui.components.SelectorField
import com.deivide.webtoontranslator.ui.theme.AppTheme

private enum class Sheet { NONE, PROVIDER, MODEL }

/** Conexão (Mais → Conexão): provedor, URL do gateway, chave de API e modelo. */
@Composable
fun ConnectionScreen(
    onBack: () -> Unit,
    viewModel: ConnectionViewModel = viewModel(factory = ConnectionViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    val settings = state.settings
    val icons = AppTheme.colors.icons

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.more_connection), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            SectionCard {
                val (icon, color) = providerIcon(settings.provider)
                SelectorField(
                    icon = icon,
                    color = color,
                    label = stringResource(R.string.settings_provider),
                    value = settings.provider.label,
                    onClick = { sheet = Sheet.PROVIDER },
                )
                if (settings.provider == Provider.OPENAI) {
                    Spacer(Modifier.height(12.dp))
                    AppTextField(
                        value = settings.baseUrl,
                        onValueChange = viewModel::updateBaseUrl,
                        label = stringResource(R.string.settings_gateway_url),
                        keyboardType = KeyboardType.Uri,
                    )
                }
                Spacer(Modifier.height(12.dp))
                AppTextField(
                    value = settings.apiKey,
                    onValueChange = viewModel::updateApiKey,
                    label = stringResource(R.string.settings_api_key),
                    secret = true,
                )
            }

            SectionTitle(stringResource(R.string.settings_model))
            SectionCard {
                SelectorField(
                    icon = Icons.Filled.AutoAwesome,
                    color = icons.blue,
                    label = stringResource(R.string.model_label),
                    value = settings.model.ifBlank { stringResource(R.string.model_choose) },
                    onClick = { sheet = Sheet.MODEL },
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::save,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.settings_save), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            state.status?.let {
                Text(
                    it.asString(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp),
                )
            }
        }
    }

    when (sheet) {
        Sheet.PROVIDER -> OptionSheet(stringResource(R.string.settings_provider), onDismiss = { sheet = Sheet.NONE }) {
            Provider.entries.forEach { provider ->
                val (icon, color) = providerIcon(provider)
                OptionItem(
                    title = provider.label,
                    icon = icon,
                    iconColor = color,
                    selected = provider == settings.provider,
                    onClick = {
                        viewModel.selectProvider(provider)
                        sheet = Sheet.NONE
                    },
                )
            }
        }
        Sheet.MODEL -> ModelSheet(
            current = settings.model,
            favorites = state.favorites,
            models = state.models,
            hasApiKey = settings.apiKey.isNotBlank(),
            onLoad = viewModel::loadModels,
            onSelect = {
                viewModel.selectModel(it)
                sheet = Sheet.NONE
            },
            onToggleFavorite = viewModel::toggleFavorite,
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.NONE -> Unit
    }
}

@Composable
private fun providerIcon(provider: Provider): Pair<ImageVector, Color> {
    val icons = AppTheme.colors.icons
    return when (provider) {
        Provider.GOOGLE -> Icons.Filled.Cloud to icons.blue
        Provider.ANTHROPIC -> Icons.Filled.Psychology to icons.orange
        Provider.OPENAI -> Icons.Filled.Hub to icons.green
    }
}
