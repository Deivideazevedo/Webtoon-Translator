package com.deivide.webtoontranslator.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R

/** Tela inicial sem imagem aberta. */
@Composable
fun EmptyState(engineReady: Boolean, onPickImage: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_empty_title), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(8.dp))
        Text(
            stringResource(R.string.home_empty_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(20.dp))
        Button(onClick = onPickImage) { Text(stringResource(R.string.home_pick_image)) }
        if (!engineReady) {
            Spacer(Modifier.size(16.dp))
            Text(
                stringResource(R.string.home_preparing_ocr),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}
