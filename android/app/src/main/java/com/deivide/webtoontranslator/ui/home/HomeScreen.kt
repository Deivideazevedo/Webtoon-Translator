package com.deivide.webtoontranslator.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.floating.FloatingService
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.home.components.DeleteConfirmDialog
import com.deivide.webtoontranslator.ui.home.components.FloatingToggle
import com.deivide.webtoontranslator.ui.home.components.HistoryList
import com.deivide.webtoontranslator.ui.home.components.HistoryTabs
import com.deivide.webtoontranslator.ui.home.components.SelectionTopBar
import com.deivide.webtoontranslator.ui.translation.TranslationCard
import com.deivide.webtoontranslator.ui.translation.TranslationCardActions
import com.deivide.webtoontranslator.ui.translation.TranslationCardState

/** Tela inicial: Iniciar/Parar o tradutor flutuante + recentes/favoritos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val floating = rememberFloatingStarter()
    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice?.asString()
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            viewModel.dismissNotice()
        }
    }
    BackHandler(enabled = state.selecting, onBack = viewModel::clearSelection)

    Scaffold(
        topBar = {
            if (state.selecting) {
                SelectionTopBar(
                    count = state.selection.size,
                    onClear = viewModel::clearSelection,
                    onDelete = viewModel::requestDelete,
                )
            } else {
                AppTopBar(title = stringResource(R.string.app_name)) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FloatingToggle(
                running = state.floatingRunning,
                onToggle = { if (state.floatingRunning) FloatingService.stop(context) else floating.start() },
            )
            HistoryTabs(tab = state.tab, library = state.library, onSelectTab = viewModel::selectTab)
            HistoryList(
                state = state,
                onClick = viewModel::onCardClick,
                onLongClick = viewModel::onCardLongClick,
                onToggleFavorite = viewModel::toggleFavorite,
            )
        }
    }

    if (state.confirmingDelete) {
        DeleteConfirmDialog(
            count = state.selection.size,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }

    state.opened?.let { opened ->
        val cardActions = remember(viewModel) {
            TranslationCardActions(
                onSpeak = viewModel::speak,
                onToggleFavorite = viewModel::toggleFavorite,
                onOptionsChange = viewModel::updateOptions,
            )
        }
        ModalBottomSheet(onDismissRequest = viewModel::closeOpened, containerColor = MaterialTheme.colorScheme.surface) {
            TranslationCard(
                state = TranslationCardState.Done(opened),
                isFavorite = opened.id in state.library.favoriteIds,
                options = state.options,
                actions = cardActions,
                modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 24.dp),
            )
        }
    }
}
