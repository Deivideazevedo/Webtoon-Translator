package com.deivide.webtoontranslator.ui.home

import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.ui.common.asString
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.home.components.EmptyState
import com.deivide.webtoontranslator.ui.home.components.PageView
import com.deivide.webtoontranslator.ui.home.components.ResultCard

/** Tela inicial: liga a tela ao [HomeViewModel]; o desenho fica em [HomeContent]. */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(viewModel::openImage) }
    HomeContent(
        state = state,
        onPickImage = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        onOpenSettings = onOpenSettings,
        onSelectArea = viewModel::translateArea,
        onCloseResult = viewModel::closeResult,
        onSpeak = viewModel::speak,
        onNoticeShown = viewModel::dismissNotice,
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onPickImage: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectArea: (Rect) -> Unit,
    onCloseResult: () -> Unit,
    onSpeak: (text: String, slow: Boolean) -> Unit,
    onNoticeShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val notice = state.notice?.asString()
    LaunchedEffect(notice) {
        if (notice != null) {
            snackbar.showSnackbar(notice)
            onNoticeShown()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.app_name)) {
                if (state.page != null) {
                    IconButton(onClick = onPickImage) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_open_image))
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val page = state.page
            if (page == null) {
                EmptyState(engineReady = state.engineReady, onPickImage = onPickImage)
            } else {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        stringResource(R.string.home_selection_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    PageView(page = page, onSelectArea = onSelectArea)
                }
            }
            state.result?.let { result ->
                ResultCard(
                    result = result,
                    onClose = onCloseResult,
                    onSpeak = onSpeak,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
