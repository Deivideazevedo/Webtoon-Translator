package com.deivide.webtoontranslator.ui.translations

import android.content.ClipboardManager
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.library.SavedList
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.components.PillTabs
import com.deivide.webtoontranslator.ui.translation.TranslationRequest
import com.deivide.webtoontranslator.ui.translation.TranslationSheet
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
            TypeToTranslate(onTranslate = viewModel::translateTyped)
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

    // Frase digitada tem prioridade (abrir uma da lista fecha a digitada e vice-versa).
    val typed = state.typed
    val opened = state.opened
    when {
        typed != null -> TranslationSheet(TranslationRequest.Text(typed), onDismiss = viewModel::closeTyped)
        opened != null -> TranslationSheet(TranslationRequest.Saved(opened), onDismiss = viewModel::closeOpened)
    }
}

/**
 * "Digite ou cole uma frase" (L5): traduz com a mesma análise didática do cartão. Vazio, o botão
 * da direita cola a área de transferência; com texto, traduz.
 */
@Composable
private fun TypeToTranslate(onTranslate: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val send = {
        onTranslate(text)
        text = ""
    }
    TextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(R.string.type_to_translate)) },
        maxLines = 4,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) send() }),
        trailingIcon = {
            if (text.isBlank()) {
                IconButton(onClick = {
                    clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { text = it.toString() }
                }) { Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.cd_paste)) }
            } else {
                IconButton(onClick = send) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_translate), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
