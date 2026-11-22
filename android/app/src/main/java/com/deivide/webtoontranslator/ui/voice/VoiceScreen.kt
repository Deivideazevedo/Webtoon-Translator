package com.deivide.webtoontranslator.ui.voice

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.speech.SpeechPlayer
import com.deivide.webtoontranslator.data.speech.VoiceOption
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.components.OptionItem
import com.deivide.webtoontranslator.ui.components.OptionSheet
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SectionTitle
import com.deivide.webtoontranslator.ui.components.SelectorField
import com.deivide.webtoontranslator.ui.theme.AppTheme
import java.util.Locale

/** Frase do "Ouvir exemplo". */
private const val SAMPLE = "Hey! Let's read some webtoons together."

private val PT_BR = Locale.forLanguageTag("pt-BR")

/**
 * Voz (Mais → Voz, LAYOUT.md §5.4): voz do próprio Android — escolher a voz instalada, tom e
 * velocidade, tudo nativo e na hora (sem gerar nem baixar áudio). Mexer em qualquer controle já
 * toca a frase de exemplo (ao escolher a voz, ou ao soltar o slider).
 */
@Composable
fun VoiceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as WebtoonApp).container }
    val settings = container.settingsRepository
    val speech = container.speechPlayer
    val ready by speech.ready.collectAsStateWithLifecycle()
    val voices = remember(ready) { speech.englishVoices() }
    var voice by remember { mutableStateOf(settings.voiceName) }
    var pitch by remember { mutableFloatStateOf(settings.speechPitch) }
    var speed by remember { mutableFloatStateOf(settings.load().speechSpeed) }
    var choosingVoice by rememberSaveable { mutableStateOf(false) }
    fun preview(voiceName: String? = voice) {
        val message = when (speech.speak(SAMPLE, AppSettings.nativeRate(speed), pitch, voiceName)) {
            SpeechPlayer.Result.Spoken -> null
            SpeechPlayer.Result.NotReady -> R.string.speech_not_ready
            SpeechPlayer.Result.MissingEnglishVoice -> R.string.speech_missing_voice
        }
        message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
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
                SelectorField(
                    icon = Icons.Filled.RecordVoiceOver,
                    color = AppTheme.colors.icons.sky,
                    label = stringResource(R.string.voice_label),
                    value = voices.firstOrNull { it.name == voice }?.let { voiceLabel(it, voices) }
                        ?: stringResource(R.string.voice_default),
                    onClick = { choosingVoice = true },
                )
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ValueSlider(
                    title = stringResource(R.string.voice_pitch),
                    value = pitch,
                    valueText = "%.2f".format(pitch),
                    range = SettingsRepository.PITCH_RANGE,
                    onChange = {
                        pitch = step(it)
                        settings.speechPitch = pitch
                    },
                    onChangeFinished = { preview() },
                    showReset = pitch != SettingsRepository.DEFAULT_PITCH,
                    onReset = {
                        pitch = SettingsRepository.DEFAULT_PITCH
                        settings.speechPitch = pitch
                        preview()
                    },
                )
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ValueSlider(
                    title = stringResource(R.string.voice_speed),
                    value = speed,
                    valueText = "%.2fx".format(speed),
                    range = AppSettings.SPEECH_SPEED_RANGE,
                    onChange = {
                        speed = step(it)
                        settings.setSpeechSpeed(speed)
                    },
                    onChangeFinished = { preview() },
                    hint = stringResource(R.string.settings_speech_default_hint),
                    showReset = speed != AppSettings.DEFAULT_SPEECH_SPEED,
                    onReset = {
                        speed = AppSettings.DEFAULT_SPEECH_SPEED
                        settings.setSpeechSpeed(speed)
                        preview()
                    },
                )
            }
        }
    }

    if (choosingVoice) {
        OptionSheet(stringResource(R.string.voice_label), onDismiss = { choosingVoice = false }) {
            OptionItem(
                title = stringResource(R.string.voice_default),
                description = stringResource(R.string.voice_default_desc),
                selected = voice == null,
                onClick = {
                    voice = null
                    settings.voiceName = null
                    preview(null)
                },
            )
            if (voices.isEmpty()) {
                Text(
                    stringResource(if (ready) R.string.voice_none else R.string.speech_not_ready),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
            voices.forEach { option ->
                OptionItem(
                    title = voiceLabel(option, voices),
                    description = if (option.needsNetwork) stringResource(R.string.voice_network) else null,
                    selected = option.name == voice,
                    onClick = {
                        voice = option.name
                        settings.voiceName = option.name
                        // Toca na hora, pra comparar as vozes sem sair da lista.
                        preview(option.name)
                    },
                )
            }
        }
    }
}

/** "Estados Unidos · Voz 2": o nome interno do Android não diz nada pra quem usa. */
@Composable
private fun voiceLabel(option: VoiceOption, all: List<VoiceOption>): String {
    val sameCountry = all.filter { it.locale.country == option.locale.country }
    val country = option.locale.getDisplayCountry(PT_BR).ifBlank { option.locale.getDisplayName(PT_BR) }
    return stringResource(R.string.voice_item, country, sameCountry.indexOf(option) + 1)
}

/** Passos de 0,05 (igual ao slider do desktop). */
private fun step(value: Float) = Math.round(value * 20) / 20f

@Composable
private fun ValueSlider(
    title: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
    showReset: Boolean,
    onReset: () -> Unit,
    hint: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(valueText, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    @Suppress("DEPRECATION")
    Slider(value = value, onValueChange = onChange, onValueChangeFinished = onChangeFinished, valueRange = range)
    if (hint != null) Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    if (showReset) TextButton(onClick = onReset) { Text(stringResource(R.string.settings_reset_speed)) }
}
