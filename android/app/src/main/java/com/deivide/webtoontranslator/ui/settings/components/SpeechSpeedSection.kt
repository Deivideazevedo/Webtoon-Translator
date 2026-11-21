package com.deivide.webtoontranslator.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.AppSettings

/** Velocidade da fala, com a dica do padrão e "Voltar ao padrão" quando estiver diferente. */
@Composable
fun SpeechSpeedSection(
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            stringResource(R.string.settings_speech_speed, speed),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Slider(value = speed, onValueChange = onSpeedChange, valueRange = AppSettings.SPEECH_SPEED_RANGE)
        Text(
            stringResource(R.string.settings_speech_default_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        if (speed != AppSettings.DEFAULT_SPEECH_SPEED) {
            TextButton(onClick = onReset) { Text(stringResource(R.string.settings_reset_speed)) }
        }
    }
}
