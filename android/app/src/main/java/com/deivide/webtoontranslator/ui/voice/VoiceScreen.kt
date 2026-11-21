package com.deivide.webtoontranslator.ui.voice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.speech.SpeechPlayer
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SectionTitle

/** Frase do "Ouvir exemplo". */
private const val SAMPLE = "Hey! Let's read some webtoons together."

/**
 * Voz (Mais → Voz, LAYOUT.md §5.4): voz do próprio Android. Por enquanto, velocidade e exemplo;
 * escolher a voz instalada e o tom entram depois (ROADMAP L7).
 */
@Composable
fun VoiceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as WebtoonApp).container }
    var speed by remember { mutableFloatStateOf(container.settingsRepository.load().speechSpeed) }
    fun setSpeed(value: Float) {
        // Passos de 0,05 (igual ao slider do desktop).
        speed = Math.round(value * 20) / 20f
        container.settingsRepository.setSpeechSpeed(speed)
    }

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.more_voice), onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            SectionTitle(stringResource(R.string.language_english))
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.voice_speed), fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("%.2fx".format(speed), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                @Suppress("DEPRECATION")
                Slider(value = speed, onValueChange = ::setSpeed, valueRange = AppSettings.SPEECH_SPEED_RANGE)
                Text(
                    stringResource(R.string.settings_speech_default_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (speed != AppSettings.DEFAULT_SPEECH_SPEED) {
                    TextButton(onClick = { setSpeed(AppSettings.DEFAULT_SPEECH_SPEED) }) {
                        Text(stringResource(R.string.settings_reset_speed))
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val message = when (container.speechPlayer.speak(SAMPLE, speed)) {
                            SpeechPlayer.Result.Spoken -> null
                            SpeechPlayer.Result.NotReady -> R.string.speech_not_ready
                            SpeechPlayer.Result.MissingEnglishVoice -> R.string.speech_missing_voice
                        }
                        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    },
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.voice_sample), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
