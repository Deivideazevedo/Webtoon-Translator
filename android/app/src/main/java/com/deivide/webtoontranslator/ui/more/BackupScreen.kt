package com.deivide.webtoontranslator.ui.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.ui.common.friendlyMessage
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SettingRow
import com.deivide.webtoontranslator.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup (Mais → Backup): exportar/importar tudo num arquivo no MESMO formato do desktop
 * (configurações, chaves se quiser, favoritos de modelo, traduções recentes e favoritas).
 */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val backup = remember { (context.applicationContext as WebtoonApp).container.backupRepository }
    val scope = rememberCoroutineScope()
    var includeKeys by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    val icons = AppTheme.colors.icons

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                val json = backup.export(includeKeys)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
                }
                context.getString(R.string.backup_exported)
            }.getOrElse { context.getString(R.string.backup_failed, it.friendlyMessage()) }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
                val r = backup.import(json)
                context.getString(R.string.backup_imported, r.recent, r.favorites, r.apiKeys)
            }.getOrElse { context.getString(R.string.backup_failed, it.friendlyMessage()) }
        }
    }

    DetailScaffold(stringResource(R.string.more_backup), onBack) {
        Text(
            stringResource(R.string.backup_intro),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
        )
        SectionCard(padding = false) {
            SettingRow(
                icon = Icons.Filled.Key,
                color = icons.orange,
                title = stringResource(R.string.backup_include_keys),
                subtitle = stringResource(R.string.backup_include_keys_desc),
                onClick = { includeKeys = !includeKeys },
                trailing = { Switch(checked = includeKeys, onCheckedChange = { includeKeys = it }) },
            )
            SettingRow(
                icon = Icons.Filled.Upload,
                color = icons.blue,
                title = stringResource(R.string.backup_export),
                subtitle = stringResource(R.string.backup_export_desc),
                divider = true,
                onClick = {
                    val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                    exporter.launch("webtoon-translator-backup_$stamp.json")
                },
            )
            SettingRow(
                icon = Icons.Filled.Download,
                color = icons.green,
                title = stringResource(R.string.backup_import),
                subtitle = stringResource(R.string.backup_import_desc),
                divider = true,
                onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            )
        }
        status?.let {
            Text(it, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
        }
    }
}
