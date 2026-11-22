package com.deivide.webtoontranslator.floating

import androidx.compose.foundation.background
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.data.settings.AppSettings
import com.deivide.webtoontranslator.data.settings.TranslationMode
import com.deivide.webtoontranslator.ui.components.OptionGroupLabel
import com.deivide.webtoontranslator.ui.components.OptionItem
import com.deivide.webtoontranslator.ui.theme.AppTheme
import kotlinx.coroutines.flow.StateFlow

/** Página do sheet de opções: a lista ou a subpágina da velocidade da voz. */
internal enum class OptionsPage { MAIN, SPEED }

/**
 * ⚙ do cartão flutuante: modo de tradução, "Voltar fecha as traduções" e velocidade da voz (numa
 * subpágina com o slider). O modelo fica no rodapé do cartão, então não aparece aqui.
 */
@Composable
internal fun FloatingOptionsSheet(
    open: StateFlow<Boolean>,
    bottomInset: Dp,
    page: StateFlow<OptionsPage>,
    mode: StateFlow<TranslationMode>,
    backCloses: StateFlow<Boolean>,
    speed: StateFlow<Float>,
    onSelectMode: (TranslationMode) -> Unit,
    onBackClosesChange: (Boolean) -> Unit,
    onPage: (OptionsPage) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSpeedPreview: () -> Unit,
    onClose: () -> Unit,
) {
    val currentPage by page.collectAsStateWithLifecycle()
    val currentMode by mode.collectAsStateWithLifecycle()
    val closes by backCloses.collectAsStateWithLifecycle()
    val currentSpeed by speed.collectAsStateWithLifecycle()

    when (currentPage) {
        // O título do sheet já é o do primeiro grupo (como no Translate On Screen).
        OptionsPage.MAIN -> FloatingSheet(open, bottomInset, stringResource(R.string.mode_title), onClose) {
            ModeRows(currentMode, onSelectMode)
            OptionGroupLabel(stringResource(R.string.options_reading))
            OptionItem(
                stringResource(R.string.appearance_back_closes), selected = false,
                onClick = { onBackClosesChange(!closes) },
                icon = Icons.AutoMirrored.Filled.ArrowBack, iconColor = AppTheme.colors.icons.green, compact = true,
            ) { SmallSwitch(closes, onBackClosesChange) }
            OptionItem(
                stringResource(R.string.options_speech_speed), selected = false,
                onClick = { onPage(OptionsPage.SPEED) },
                icon = Icons.Filled.Speed, iconColor = AppTheme.colors.icons.orange, compact = true,
            ) {
                Text("%.2fx".format(currentSpeed), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OptionsPage.SPEED -> FloatingSheet(
            open, bottomInset, stringResource(R.string.options_speech_speed), onClose,
            onBack = { onPage(OptionsPage.MAIN) },
        ) {
            Column(Modifier.padding(horizontal = 6.dp)) {
                Text(
                    "%.2fx".format(currentSpeed),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                @Suppress("DEPRECATION")
                Slider(
                    value = currentSpeed,
                    onValueChange = { onSpeedChange(Math.round(it * 20) / 20f) },
                    onValueChangeFinished = onSpeedPreview,
                    valueRange = AppSettings.SPEECH_SPEED_RANGE,
                )
                Text(
                    stringResource(R.string.settings_speech_default_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (currentSpeed != AppSettings.DEFAULT_SPEECH_SPEED) {
                    TextButton(onClick = {
                        onSpeedChange(AppSettings.DEFAULT_SPEECH_SPEED)
                        onSpeedPreview()
                    }) { Text(stringResource(R.string.settings_reset_speed), color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

/** Os modos de tradução, com o atual marcado (⚙ do cartão e menu do ícone). */
@Composable
internal fun ModeRows(current: TranslationMode, onSelect: (TranslationMode) -> Unit) {
    TranslationMode.entries.forEach { option ->
        val (icon, color) = option.icon()
        OptionItem(
            stringResource(option.label()), selected = option == current, onClick = { onSelect(option) },
            icon = icon, iconColor = color, compact = true,
        )
    }
}

/** Mesmos ícones e cores do seletor de modo da tela Inicial. */
@Composable
private fun TranslationMode.icon() = when (this) {
    TranslationMode.REGION -> Icons.Filled.CropFree to AppTheme.colors.icons.purple
    TranslationMode.FIXED_REGION -> Icons.Filled.PushPin to AppTheme.colors.icons.green
    TranslationMode.FULL_SCREEN -> Icons.Filled.Fullscreen to AppTheme.colors.icons.blue
}

private fun TranslationMode.label() = when (this) {
    TranslationMode.REGION -> R.string.mode_region
    TranslationMode.FIXED_REGION -> R.string.mode_fixed_region
    TranslationMode.FULL_SCREEN -> R.string.mode_full_screen
}

/**
 * Switch menor (o padrão tem 48dp de área), pra o item dele ficar da altura dos outros. O toque no
 * item inteiro já alterna.
 */
@Composable
private fun SmallSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Box(Modifier.size(width = 40.dp, height = 24.dp), contentAlignment = Alignment.Center) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.wrapContentSize(unbounded = true).scale(0.75f),
            )
        }
    }
}
