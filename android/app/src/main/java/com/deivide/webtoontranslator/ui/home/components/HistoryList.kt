package com.deivide.webtoontranslator.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.library.SavedList
import com.deivide.webtoontranslator.domain.model.Translation
import com.deivide.webtoontranslator.ui.home.HomeUiState

/** Lista da aba aberta; vazia, mostra uma dica de como encher. */
@Composable
fun HistoryList(
    state: HomeUiState,
    onClick: (Translation) -> Unit,
    onLongClick: (Translation) -> Unit,
    onToggleFavorite: (Translation) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.items.isEmpty()) {
        Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(
                    if (state.tab == SavedList.RECENT) R.string.history_empty_recent else R.string.history_empty_favorites
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.items, key = { it.id }) { translation ->
            HistoryCard(
                translation = translation,
                isFavorite = translation.id in state.library.favoriteIds,
                isActive = translation.id == state.opened?.id,
                selecting = state.selecting,
                selected = translation.id in state.selection,
                onClick = { onClick(translation) },
                onLongClick = { onLongClick(translation) },
                onToggleFavorite = { onToggleFavorite(translation) },
            )
        }
    }
}

@Composable
private fun HistoryCard(
    translation: Translation,
    isFavorite: Boolean,
    isActive: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isActive || selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            Column(Modifier.weight(1f).padding(top = 8.dp, start = if (selecting) 0.dp else 4.dp)) {
                Text(translation.originalText, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                Text(
                    translation.translation,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.padding(top = 6.dp)) {
                    Text(
                        stringResource(R.string.history_via, translation.model),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.history_duration, translation.durationSecs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(
                        if (isFavorite) R.string.action_unfavorite else R.string.action_favorite
                    ),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
