package com.deivide.webtoontranslator.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.floating.FloatingService
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.components.ModelSheet
import com.deivide.webtoontranslator.ui.components.UpdateCard
import com.deivide.webtoontranslator.ui.components.rememberAppUpdater
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SectionTitle
import com.deivide.webtoontranslator.ui.components.SelectorField
import com.deivide.webtoontranslator.ui.components.StatusPill
import com.deivide.webtoontranslator.ui.home.components.LanguagePair
import com.deivide.webtoontranslator.ui.home.components.ModeSheet
import com.deivide.webtoontranslator.ui.home.components.PowerButton
import com.deivide.webtoontranslator.ui.home.components.modeIcon
import com.deivide.webtoontranslator.ui.home.components.modeLabel
import com.deivide.webtoontranslator.ui.theme.AppTheme
import com.deivide.webtoontranslator.ui.translation.TranslationRequest
import com.deivide.webtoontranslator.ui.translation.TranslationSheet

private enum class Sheet { NONE, MODEL, MODE, LAST }

/** Aba Inicial (LAYOUT.md §5.1). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenConnection: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val floating = rememberFloatingStarter()
    val updater = rememberAppUpdater()
    // Versão nova no GitHub? Confere uma vez por abertura do app.
    LaunchedEffect(updater) { updater.check() }
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice?.asString()
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            viewModel.dismissNotice()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.app_name)) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        // A barra de navegação de baixo já cuida da área do sistema.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            UpdateCard(updater, Modifier.padding(bottom = 12.dp))
            SectionCard {
                LanguagePair()
                Spacer(Modifier.height(20.dp))
                PowerButton(
                    running = state.running,
                    onClick = { if (state.running) FloatingService.stop(context) else floating.start() },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(12.dp))
                StatusPill(
                    stringResource(if (state.running) R.string.floating_stop else R.string.floating_start),
                    active = state.running,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(20.dp))
                SelectorField(
                    icon = Icons.Filled.AutoAwesome,
                    color = AppTheme.colors.icons.blue,
                    label = stringResource(R.string.model_label),
                    value = state.settings?.model?.takeIf { it.isNotBlank() } ?: stringResource(R.string.model_choose),
                    onClick = { sheet = Sheet.MODEL },
                )
                Spacer(Modifier.height(12.dp))
                val (icon, color) = modeIcon(state.mode)
                SelectorField(
                    icon = icon,
                    color = color,
                    label = stringResource(R.string.mode_title),
                    value = stringResource(modeLabel(state.mode)),
                    onClick = { sheet = Sheet.MODE },
                )
            }

            state.last?.let { last ->
                SectionTitle(stringResource(R.string.last_translation))
                SectionCard(Modifier.clickable { sheet = Sheet.LAST }) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(last.translation, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                last.originalText,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            IconButton(onClick = { viewModel.speak(last.originalText, false) }) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.cd_speak))
                            }
                            IconButton(onClick = { viewModel.toggleFavorite(last) }) {
                                Icon(
                                    if (state.lastIsFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = stringResource(
                                        if (state.lastIsFavorite) R.string.action_unfavorite else R.string.action_favorite
                                    ),
                                    tint = if (state.lastIsFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    when (sheet) {
        Sheet.MODEL -> ModelSheet(
            current = state.settings?.model.orEmpty(),
            favorites = state.favoriteModels,
            models = state.models,
            hasApiKey = !state.settings?.apiKey.isNullOrBlank(),
            speeds = state.speeds,
            onLoad = viewModel::loadModels,
            onSelect = {
                viewModel.selectModel(it)
                sheet = Sheet.NONE
            },
            onToggleFavorite = viewModel::toggleFavoriteModel,
            onOpenConnection = {
                sheet = Sheet.NONE
                onOpenConnection()
            },
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.MODE -> ModeSheet(
            selected = state.mode,
            onSelect = {
                viewModel.selectMode(it)
                sheet = Sheet.NONE
            },
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.LAST -> state.last?.let { last ->
            TranslationSheet(TranslationRequest.Saved(last), onDismiss = { sheet = Sheet.NONE })
        }
        Sheet.NONE -> Unit
    }
}
