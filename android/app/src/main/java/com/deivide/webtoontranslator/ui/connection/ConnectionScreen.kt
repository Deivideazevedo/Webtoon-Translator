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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.IconButton
import com.deivide.webtoontranslator.data.settings.SavedBenchmark
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.deivide.webtoontranslator.ui.components.SettingRow
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
            SectionCard(padding = false) {
                SelectorField(
                    icon = Icons.Filled.AutoAwesome,
                    color = icons.blue,
                    label = stringResource(R.string.model_label),
                    value = settings.model.ifBlank { stringResource(R.string.model_choose) },
                    onClick = { sheet = Sheet.MODEL },
                    modifier = Modifier.padding(16.dp),
                )
                val speed = state.speedTest
                SettingRow(
                    icon = Icons.Filled.Timer,
                    color = icons.orange,
                    title = stringResource(R.string.speed_test),
                    subtitle = when {
                        speed.running -> stringResource(R.string.speed_running)
                        speed.secs != null -> stringResource(R.string.speed_result, speed.secs)
                        speed.error != null -> speed.error.asString()
                        else -> stringResource(R.string.speed_test_desc)
                    },
                    divider = true,
                    onClick = if (speed.running) null else viewModel::testSpeed,
                    trailing = { if (speed.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                )
                val bench = state.benchmark
                SettingRow(
                    icon = Icons.Filled.Flag,
                    color = icons.green,
                    title = stringResource(R.string.benchmark),
                    subtitle = when {
                        bench.running && bench.total > 0 -> stringResource(R.string.benchmark_progress, bench.done, bench.total)
                        bench.running -> stringResource(R.string.settings_loading_models)
                        bench.error != null -> bench.error.asString()
                        state.saved != null -> stringResource(R.string.benchmark_last, formatTime(state.saved!!.timeMs))
                        else -> stringResource(R.string.benchmark_desc)
                    },
                    divider = true,
                    onClick = if (settings.apiKey.isBlank()) null else viewModel::openBenchmark,
                    trailing = { if (bench.running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
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
            speeds = state.saved?.speeds.orEmpty(),
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

    val saved = state.saved
    if (state.benchmark.showResults && saved != null) {
        BenchmarkSheet(
            saved = saved,
            running = state.benchmark.running,
            progress = state.benchmark.done to state.benchmark.total,
            current = settings.model,
            favorites = state.favorites,
            onSelect = {
                viewModel.selectModel(it)
                viewModel.dismissBenchmark()
            },
            onToggleFavorite = viewModel::toggleFavorite,
            onRunAgain = viewModel::runBenchmark,
            onDismiss = viewModel::dismissBenchmark,
        )
    }
}

/** Último benchmark: do mais rápido pro mais lento, com ★ pra marcar e "↻ Rodar de novo". */
@Composable
private fun BenchmarkSheet(
    saved: SavedBenchmark,
    running: Boolean,
    progress: Pair<Int, Int>,
    current: String,
    favorites: List<String>,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRunAgain: () -> Unit,
    onDismiss: () -> Unit,
) {
    OptionSheet(stringResource(R.string.benchmark_result), onDismiss = onDismiss) {
        Text(
            stringResource(R.string.benchmark_result_desc, formatTime(saved.timeMs)),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        OptionItem(
            title = stringResource(R.string.benchmark_run_again),
            description = if (running) stringResource(R.string.benchmark_progress, progress.first, progress.second) else null,
            icon = Icons.Filled.Refresh,
            iconColor = AppTheme.colors.icons.green,
            selected = false,
            enabled = !running,
            onClick = onRunAgain,
            trailing = { if (running) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
        )
        saved.results.forEach { r ->
            val favorite = r.model in favorites
            OptionItem(
                title = r.model,
                description = r.secs?.let { stringResource(R.string.speed_result, it) }
                    ?: stringResource(R.string.benchmark_failed, r.error.orEmpty()),
                selected = r.model == current,
                onClick = { onSelect(r.model) },
                trailing = {
                    IconButton(onClick = { onToggleFavorite(r.model) }) {
                        Icon(
                            if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(if (favorite) R.string.action_unfavorite else R.string.action_favorite),
                            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

private fun formatTime(ms: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(ms))

@Composable
private fun providerIcon(provider: Provider): Pair<ImageVector, Color> {
    val icons = AppTheme.colors.icons
    return when (provider) {
        Provider.GOOGLE -> Icons.Filled.Cloud to icons.blue
        Provider.ANTHROPIC -> Icons.Filled.Psychology to icons.orange
        Provider.OPENAI -> Icons.Filled.Hub to icons.green
    }
}
