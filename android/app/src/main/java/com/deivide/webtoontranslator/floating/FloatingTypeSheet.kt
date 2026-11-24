package com.deivide.webtoontranslator.floating

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deivide.webtoontranslator.R
import kotlinx.coroutines.flow.StateFlow

/**
 * "Digitar o texto" do cartão flutuante, quando o OCR não teve certeza (fonte estilizada): o
 * campo já vem com tudo o que o OCR viu, pra só corrigir; enviar traduz no cartão. Também é o ✏️
 * do cartão (corrigir o inglês). Fica preso embaixo: a página continua rolando e só o ✕ fecha.
 */
@Composable
internal fun FloatingTypeSheet(
    open: StateFlow<Boolean>,
    bottomInset: Dp,
    draft: String,
    onTranslate: (String) -> Unit,
    onClose: () -> Unit,
) {
    var text by remember { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val send = { if (text.text.isNotBlank()) onTranslate(text.text.trim()) }

    FloatingSheet(open, bottomInset, stringResource(R.string.ocr_type_title), onClose, modal = false) {
        TextField(
            value = text,
            onValueChange = { text = it },
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            trailingIcon = {
                IconButton(onClick = send) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.cd_translate), tint = MaterialTheme.colorScheme.primary)
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}
