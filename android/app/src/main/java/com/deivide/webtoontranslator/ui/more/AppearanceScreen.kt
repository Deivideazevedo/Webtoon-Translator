package com.deivide.webtoontranslator.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.data.settings.SettingsRepository
import com.deivide.webtoontranslator.data.settings.ThemeMode
import com.deivide.webtoontranslator.ui.components.AppTopBar
import com.deivide.webtoontranslator.ui.components.IconTile
import com.deivide.webtoontranslator.ui.components.OptionItem
import com.deivide.webtoontranslator.ui.components.OptionSheet
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SettingRow
import com.deivide.webtoontranslator.ui.theme.AppTheme
import kotlin.math.roundToInt

/**
 * Aparência (Mais → Aparência): tema, pronúncia em IPA, voltar fechando as traduções flutuantes e
 * transparência do cartão flutuante.
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as WebtoonApp).container }
    val settings = container.settingsRepository
    val reading = container.readingPreferences
    val theme by settings.theme.collectAsStateWithLifecycle()
    val opacity by settings.cardOpacity.collectAsStateWithLifecycle()
    val backCloses by settings.backClosesBubbles.collectAsStateWithLifecycle()
    val fixedCard by settings.fixedCard.collectAsStateWithLifecycle()
    val options by reading.options.collectAsStateWithLifecycle()
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    val icons = AppTheme.colors.icons

    Scaffold(
        topBar = { AppTopBar(title = stringResource(R.string.more_appearance), onBack = onBack) },
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
            SectionCard(padding = false) {
                SettingRow(
                    icon = Icons.Filled.DarkMode,
                    color = icons.purple,
                    title = stringResource(R.string.appearance_theme),
                    onClick = { choosingTheme = true },
                    trailing = {
                        Text(stringResource(theme.label()), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                )
                SettingRow(
                    icon = Icons.Filled.RecordVoiceOver,
                    color = icons.sky,
                    title = stringResource(R.string.appearance_ipa),
                    subtitle = stringResource(R.string.appearance_ipa_desc),
                    divider = true,
                    onClick = { reading.update { it.copy(ipaEnabled = !it.ipaEnabled, showIpa = false) } },
                    trailing = {
                        Switch(
                            checked = options.ipaEnabled,
                            onCheckedChange = { on -> reading.update { it.copy(ipaEnabled = on, showIpa = false) } },
                        )
                    },
                )
                SettingRow(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    color = icons.green,
                    title = stringResource(R.string.appearance_back_closes),
                    subtitle = stringResource(R.string.appearance_back_closes_desc),
                    divider = true,
                    onClick = { settings.setBackClosesBubbles(!backCloses) },
                    trailing = { Switch(checked = backCloses, onCheckedChange = settings::setBackClosesBubbles) },
                )
                SettingRow(
                    icon = Icons.Filled.PictureInPicture,
                    color = icons.purple,
                    title = stringResource(R.string.appearance_fixed_card),
                    subtitle = stringResource(R.string.appearance_fixed_card_desc),
                    divider = true,
                    onClick = { settings.setFixedCard(!fixedCard) },
                    trailing = { Switch(checked = fixedCard, onCheckedChange = settings::setFixedCard) },
                )
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    IconTile(Icons.Filled.Opacity, icons.blue)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.appearance_opacity), fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Text("${(opacity * 100).roundToInt()}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            stringResource(R.string.appearance_opacity_desc),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        @Suppress("DEPRECATION")
                        Slider(
                            value = opacity,
                            onValueChange = { settings.setCardOpacity(Math.round(it * 20) / 20f) },
                            valueRange = SettingsRepository.CARD_OPACITY_RANGE,
                        )
                        OpacityPreview(opacity, Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }

    if (choosingTheme) {
        OptionSheet(stringResource(R.string.appearance_theme), onDismiss = { choosingTheme = false }) {
            ThemeMode.entries.forEach { mode ->
                OptionItem(
                    title = stringResource(mode.label()),
                    selected = mode == theme,
                    onClick = {
                        settings.themeMode = mode
                        choosingTheme = false
                    },
                )
            }
        }
    }
}

/** Prévia: o fundo do cartão flutuante, na opacidade escolhida, sobre uma "página" colorida. */
@Composable
private fun OpacityPreview(opacity: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFF4C27A), Color(0xFF7FB3E8), Color(0xFFE88FA8))))
            .padding(12.dp)
    ) {
        Text(
            stringResource(R.string.appearance_opacity_sample),
            color = Color(0xFFF2F2F2),
            fontSize = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E1E1E).copy(alpha = opacity))
                .padding(10.dp),
        )
    }
}

internal fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
