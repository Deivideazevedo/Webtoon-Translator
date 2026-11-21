package com.deivide.webtoontranslator.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.settings.ThemeMode
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.components.OptionItem
import com.deivide.webtoontranslator.ui.components.OptionSheet
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SettingRow
import com.deivide.webtoontranslator.ui.theme.AppTheme

/** Aba Mais (LAYOUT.md §5.3): linhas com ícone em cartões, cada uma abre uma tela ou seletor. */
@Composable
fun MoreScreen(onOpenConnection: () -> Unit, onOpenVoice: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as WebtoonApp).container.settingsRepository }
    val theme by settings.theme.collectAsStateWithLifecycle()
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    val icons = AppTheme.colors.icons

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.tab_more)) },
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
            SectionCard(padding = false) {
                SettingRow(
                    icon = Icons.Filled.Link,
                    color = icons.blue,
                    title = stringResource(R.string.more_connection),
                    subtitle = stringResource(R.string.more_connection_desc),
                    onClick = onOpenConnection,
                )
                SettingRow(
                    icon = Icons.Filled.RecordVoiceOver,
                    color = icons.sky,
                    title = stringResource(R.string.more_voice),
                    subtitle = stringResource(R.string.more_voice_desc),
                    divider = true,
                    onClick = onOpenVoice,
                )
                SettingRow(
                    icon = Icons.Filled.DarkMode,
                    color = icons.purple,
                    title = stringResource(R.string.more_appearance),
                    divider = true,
                    onClick = { choosingTheme = true },
                    trailing = {
                        Text(stringResource(theme.label()), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
            }
        }
    }

    if (choosingTheme) {
        OptionSheet(stringResource(R.string.more_appearance), onDismiss = { choosingTheme = false }) {
            ThemeMode.entries.forEach { mode ->
                OptionItem(
                    title = stringResource(mode.label()),
                    selected = mode == theme,
                    onClick = {
                        settings.themeMode = mode
                        choosingTheme = false
                    },
                )
            }
        }
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
