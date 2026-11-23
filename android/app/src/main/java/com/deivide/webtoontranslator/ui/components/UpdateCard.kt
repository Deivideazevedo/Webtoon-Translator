package com.deivide.webtoontranslator.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.update.AppUpdater
import com.deivide.webtoontranslator.ui.theme.AppTheme

/** O atualizador do app (uma instância pro app inteiro). */
@Composable
fun rememberAppUpdater(): AppUpdater = (LocalContext.current.applicationContext as WebtoonApp).container.appUpdater

/**
 * Aviso de versão nova na tela Inicial: baixa o APK do GitHub (com progresso) e abre o instalador.
 * Só aparece quando há uma versão mais nova que a instalada.
 */
@Composable
fun UpdateCard(updater: AppUpdater, modifier: Modifier = Modifier) {
    val state by updater.state.collectAsStateWithLifecycle()
    val version = when (val s = state) {
        is AppUpdater.State.Available -> s.version
        is AppUpdater.State.Downloading -> s.version
        is AppUpdater.State.Ready -> s.version
        is AppUpdater.State.Failed -> s.version
        else -> null
    } ?: return
    if (updater.dismissed) return

    SectionCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.SystemUpdate, AppTheme.colors.icons.green)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.update_available, version),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            // Fechar: volta na próxima abertura do app (Mais → Sobre também oferece).
            if (state !is AppUpdater.State.Downloading && state !is AppUpdater.State.Ready) {
                IconButton(onClick = updater::dismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.update_later))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            is AppUpdater.State.Downloading -> {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.update_downloading, (s.progress * 100).toInt()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            is AppUpdater.State.Ready -> Button(onClick = { updater.install(s.apk) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_install))
            }
            is AppUpdater.State.Failed -> {
                Text(stringResource(R.string.update_failed), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = updater::download, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_retry)) }
            }
            else -> Button(onClick = updater::download, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_download))
            }
        }
    }
}
