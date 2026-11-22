package com.deivide.webtoontranslator.ui.more

import android.text.format.Formatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SettingRow
import com.deivide.webtoontranslator.ui.theme.AppTheme

/** Armazenamento (Mais → Armazenamento): quanto as traduções salvas ocupam. */
@Composable
fun StorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val library = remember { (context.applicationContext as WebtoonApp).container.libraryRepository }
    val lib by library.library.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { runCatching { library.refresh() } }
    val icons = AppTheme.colors.icons
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    DetailScaffold(stringResource(R.string.more_storage), onBack) {
        SectionCard(padding = false) {
            SettingRow(
                icon = Icons.Filled.History,
                color = icons.blue,
                title = stringResource(R.string.tab_recent),
                subtitle = stringResource(R.string.storage_recent, lib.recent.size, lib.recentMax),
                trailing = { Text(size(lib.recentBytes), fontSize = 15.sp) },
            )
            SettingRow(
                icon = Icons.Filled.Star,
                color = icons.orange,
                title = stringResource(R.string.tab_favorites),
                subtitle = stringResource(R.string.storage_favorites, lib.favorites.size),
                divider = true,
                trailing = { Text(size(lib.favoritesBytes), fontSize = 15.sp) },
            )
        }
        Text(
            stringResource(R.string.storage_hint),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp),
        )
    }
}
