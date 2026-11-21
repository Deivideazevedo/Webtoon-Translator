package com.deivide.webtoontranslator.ui.translations

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.library.SavedList
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.components.PillTabs
import com.deivide.webtoontranslator.ui.translation.TranslationCard
import com.deivide.webtoontranslator.ui.translation.TranslationCardActions
import com.deivide.webtoontranslator.ui.translation.TranslationCardState
import com.deivide.webtoontranslator.ui.translations.components.DeleteConfirmDialog
import com.deivide.webtoontranslator.ui.translations.components.HistoryList
import com.deivide.webtoontranslator.ui.translations.components.SelectionTopBar

/** Aba Traduções (LAYOUT.md §5.2): Recentes | Favoritos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationsScreen(viewModel: TranslationsViewModel = viewModel(factory = TranslationsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
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
                AppTopBar(title = stringResource(R.string.tab_translations))
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        // A barra de navegação de baixo já cuida da área do sistema.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillTabs(
                    options = SavedList.entries,
                    selected = state.tab,
                    label = { stringResource(if (it == SavedList.RECENT) R.string.tab_recent else R.string.tab_favorites) },
                    onSelect = viewModel::selectTab,
                )
                Spacer(Modifier.weight(1f))
                val lib = state.library
                Text(
                    when (state.tab) {
                        SavedList.RECENT -> stringResource(R.string.history_recent_count, lib.recent.size, lib.recentMax)
                        SavedList.FAVORITES -> stringResource(
                            R.string.history_favorites_count,
                            lib.favorites.size,
                            Formatter.formatShortFileSize(context, lib.favoritesBytes),
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
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
