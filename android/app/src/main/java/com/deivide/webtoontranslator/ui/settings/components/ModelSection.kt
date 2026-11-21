package com.deivide.webtoontranslator.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R

/** Campo do modelo + botão que lista os modelos da chave pra escolher com um toque. */
@Composable
fun ModelSection(
    model: String,
    models: List<String>,
    loading: Boolean,
    onModelChange: (String) -> Unit,
    onListModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                label = { Text(stringResource(R.string.settings_model)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onListModels, enabled = !loading) {
                Text(stringResource(R.string.settings_list_models))
            }
        }
        if (models.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .padding(top = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState())
            ) {
                models.forEach { id ->
                    Text(
                        id,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onSelectModel(id) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
