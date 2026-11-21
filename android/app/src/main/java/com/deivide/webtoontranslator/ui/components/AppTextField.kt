package com.deivide.webtoontranslator.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.deivide.webtoontranslator.R

/**
 * Campo de texto no padrão do app: bloco arredondado na cor de campo, sem contorno. Com
 * [secret], esconde o texto e mostra o botão de olho pra ver.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    @Suppress("DEPRECATION") // VisualTransformation segue funcionando; o substituto exige outro tipo de estado.
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
        visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (secret) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(if (visible) R.string.settings_hide_key else R.string.settings_show_key),
                    )
                }
            }
        } else null,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}
