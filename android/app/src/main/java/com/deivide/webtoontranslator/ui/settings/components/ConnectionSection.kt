package com.deivide.webtoontranslator.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.data.settings.Provider

/** Provedor, URL do gateway (só no compatível OpenAI) e chave de API. */
@Composable
fun ConnectionSection(
    settings: AppSettings,
    onSelectProvider: (Provider) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showKey by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Text(
            stringResource(R.string.settings_provider),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Provider.entries.forEach { provider ->
                FilterChip(
                    selected = settings.provider == provider,
                    onClick = { onSelectProvider(provider) },
                    label = { Text(provider.label) },
                )
            }
        }
        if (settings.provider == Provider.OPENAI) {
            OutlinedTextField(
                value = settings.baseUrl,
                onValueChange = onBaseUrlChange,
                label = { Text(stringResource(R.string.settings_gateway_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = settings.apiKey,
            onValueChange = onApiKeyChange,
            label = { Text(stringResource(R.string.settings_api_key)) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showKey = !showKey }) {
                    Text(stringResource(if (showKey) R.string.settings_hide_key else R.string.settings_show_key))
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
