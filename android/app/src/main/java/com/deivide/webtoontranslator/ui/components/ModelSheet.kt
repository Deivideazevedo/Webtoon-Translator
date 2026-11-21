package com.deivide.webtoontranslator.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.ui.common.UiText
import com.deivide.webtoontranslator.ui.common.asString

/** Lista de modelos do seletor (carregada na primeira vez que ele abre). */
data class ModelList(
    /** Provedor + URL + chave de onde a lista veio (trocou → busca de novo). */
    val source: String = "",
    val ids: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: UiText? = null,
)

/**
 * Seletor de modelo (tela Inicial e Conexão): favoritos (★) no topo e todos os modelos da chave
 * embaixo; a estrela marca/desmarca o favorito e o modelo em uso tem borda azul.
 * Sem chave de API, mostra um atalho pra configurar ([onOpenConnection], se houver).
 */
@Composable
fun ModelSheet(
    current: String,
    favorites: List<String>,
    models: ModelList,
    hasApiKey: Boolean,
    onLoad: () -> Unit,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpenConnection: (() -> Unit)? = null,
) {
    LaunchedEffect(Unit) { onLoad() }
    OptionSheet(stringResource(R.string.model_sheet_title), onDismiss) {
        if (!hasApiKey) {
            OptionItem(
                title = stringResource(R.string.model_not_configured),
                description = stringResource(R.string.model_not_configured_desc),
                selected = false,
                enabled = onOpenConnection != null,
                onClick = { onOpenConnection?.invoke() },
            )
            return@OptionSheet
        }
        if (favorites.isNotEmpty()) {
            OptionGroupLabel(stringResource(R.string.model_favorites))
            favorites.forEach { id -> ModelItem(id, id == current, favorite = true, onSelect, onToggleFavorite) }
        }
        OptionGroupLabel(stringResource(R.string.model_all))
        when {
            models.loading -> LoadingRow(stringResource(R.string.settings_loading_models), Modifier.padding(8.dp))
            models.error != null ->
                Text(models.error.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            else -> models.ids.filter { it !in favorites }.forEach { id ->
                ModelItem(id, id == current, favorite = false, onSelect, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun ModelItem(
    id: String,
    selected: Boolean,
    favorite: Boolean,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    OptionItem(
        title = id,
        selected = selected,
        onClick = { onSelect(id) },
        trailing = {
            IconButton(onClick = { onToggleFavorite(id) }) {
                Icon(
                    if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = stringResource(if (favorite) R.string.action_unfavorite else R.string.action_favorite),
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
