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
 * Ordem da lista "Todos os modelos": os medidos no último benchmark do mais rápido pro mais lento,
 * depois os que falharam, depois os nunca medidos (A-Z). [speeds]: modelo → segundos (null = falhou).
 */
fun orderBySpeed(ids: List<String>, speeds: Map<String, Double?>): List<String> =
    ids.sortedWith(compareBy<String>({ id ->
        when {
            id !in speeds -> 2
            speeds[id] == null -> 1
            else -> 0
        }
    }, { speeds[it] ?: Double.MAX_VALUE }, { it }))

/**
 * Seletor de modelo (tela Inicial e Conexão): favoritos (★) no topo e todos os modelos da chave
 * embaixo, na ordem do último benchmark ([speeds]) com o tempo de cada um. A lista vem da API na
 * hora, então modelo que não existe mais some. A estrela marca/desmarca o favorito e o modelo em
 * uso tem borda azul. Sem chave de API, mostra um atalho pra configurar ([onOpenConnection]).
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
    speeds: Map<String, Double?> = emptyMap(),
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
            favorites.forEach { id -> ModelItem(id, id == current, favorite = true, speeds, onSelect, onToggleFavorite) }
        }
        OptionGroupLabel(stringResource(R.string.model_all))
        when {
            models.loading -> LoadingRow(stringResource(R.string.settings_loading_models), Modifier.padding(8.dp))
            models.error != null ->
                Text(models.error.asString(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
            else -> orderBySpeed(models.ids.filter { it !in favorites }, speeds).forEach { id ->
                ModelItem(id, id == current, favorite = false, speeds, onSelect, onToggleFavorite)
            }
        }
    }
}

@Composable
private fun ModelItem(
    id: String,
    selected: Boolean,
    favorite: Boolean,
    speeds: Map<String, Double?>,
    onSelect: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    OptionItem(
        title = id,
        description = when {
            id !in speeds -> null
            speeds[id] == null -> stringResource(R.string.benchmark_failed_short)
            else -> stringResource(R.string.speed_result, speeds[id]!!)
        },
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
