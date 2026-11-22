package com.deivide.webtoontranslator.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SectionTitle
import com.deivide.webtoontranslator.ui.components.SettingRow
import com.deivide.webtoontranslator.ui.navigation.Routes
import com.deivide.webtoontranslator.ui.theme.AppTheme

/** Aba Mais (LAYOUT.md §5.3): linhas com ícone em cartões; cada uma abre uma tela de detalhe. */
@Composable
fun MoreScreen(onOpen: (route: String) -> Unit) {
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
                SettingRow(Icons.Filled.Link, icons.blue, stringResource(R.string.more_connection),
                    subtitle = stringResource(R.string.more_connection_desc), onClick = { onOpen(Routes.CONNECTION) })
                SettingRow(Icons.Filled.RecordVoiceOver, icons.sky, stringResource(R.string.more_voice),
                    subtitle = stringResource(R.string.more_voice_desc), divider = true, onClick = { onOpen(Routes.VOICE) })
                SettingRow(Icons.Filled.DarkMode, icons.purple, stringResource(R.string.more_appearance),
                    subtitle = stringResource(R.string.more_appearance_desc), divider = true, onClick = { onOpen(Routes.APPEARANCE) })
            }
            SectionTitle(stringResource(R.string.more_section_data))
            SectionCard(padding = false) {
                SettingRow(Icons.Filled.SwapVert, icons.green, stringResource(R.string.more_backup),
                    subtitle = stringResource(R.string.more_backup_desc), onClick = { onOpen(Routes.BACKUP) })
                SettingRow(Icons.Filled.Storage, icons.orange, stringResource(R.string.more_storage),
                    subtitle = stringResource(R.string.more_storage_desc), divider = true, onClick = { onOpen(Routes.STORAGE) })
            }
            SectionTitle(stringResource(R.string.more_section_support))
            SectionCard(padding = false) {
                SettingRow(Icons.Filled.BatteryAlert, icons.red, stringResource(R.string.more_battery),
                    subtitle = stringResource(R.string.more_battery_desc), onClick = { onOpen(Routes.BATTERY) })
                SettingRow(Icons.Filled.ErrorOutline, icons.orange, stringResource(R.string.more_errors),
                    subtitle = stringResource(R.string.more_errors_desc), divider = true, onClick = { onOpen(Routes.ERRORS) })
                SettingRow(Icons.Filled.Info, icons.blue, stringResource(R.string.more_about),
                    subtitle = stringResource(R.string.more_about_desc), divider = true, onClick = { onOpen(Routes.ABOUT) })
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
