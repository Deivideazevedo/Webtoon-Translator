package com.deivide.webtoontranslator.ui.home.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.library.Library
import com.deivide.webtoontranslator.data.library.SavedList

/** Abas Recentes | Favoritos, com o contador da aba aberta à direita (como no desktop). */
@Composable
fun HistoryTabs(tab: SavedList, library: Library, onSelectTab: (SavedList) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val counter = when (tab) {
        SavedList.RECENT -> stringResource(R.string.history_recent_count, library.recent.size, library.recentMax)
        SavedList.FAVORITES -> stringResource(
            R.string.history_favorites_count,
            library.favorites.size,
            Formatter.formatShortFileSize(context, library.favoritesBytes),
        )
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PrimaryTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = Color.Transparent,
            modifier = Modifier.weight(1f),
        ) {
            SavedList.entries.forEach { list ->
                Tab(
                    selected = tab == list,
                    onClick = { onSelectTab(list) },
                    text = {
                        Text(stringResource(if (list == SavedList.RECENT) R.string.tab_recent else R.string.tab_favorites))
                    },
                )
            }
        }
        Text(
            counter,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
