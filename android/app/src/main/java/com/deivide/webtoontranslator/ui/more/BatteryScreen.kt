package com.deivide.webtoontranslator.ui.more

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.ui.components.SectionCard
import com.deivide.webtoontranslator.ui.components.SettingRow
import com.deivide.webtoontranslator.ui.theme.AppTheme

/**
 * "App fechando sozinho" (Mais → Suporte): Samsung, Xiaomi e outros fecham apps em segundo plano
 * pra economizar bateria e o ícone flutuante some. Mostra o estado e leva pra tela do sistema
 * onde se libera o app.
 */
@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var unrestricted by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    // Voltando das configurações do sistema, atualiza o estado.
    LifecycleResumeEffect(Unit) {
        unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
        onPauseOrDispose { }
    }
    val icons = AppTheme.colors.icons

    DetailScaffold(stringResource(R.string.more_battery), onBack) {
        SectionCard(padding = false) {
            SettingRow(
                icon = if (unrestricted) Icons.Filled.CheckCircle else Icons.Filled.BatteryAlert,
                color = if (unrestricted) icons.green else icons.red,
                title = stringResource(if (unrestricted) R.string.battery_ok else R.string.battery_restricted),
                subtitle = stringResource(if (unrestricted) R.string.battery_ok_desc else R.string.battery_restricted_desc),
            )
        }
        Text(
            stringResource(R.string.battery_steps),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp),
        )
        Button(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            },
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
        ) { Text(stringResource(R.string.battery_open)) }
        Text(
            stringResource(R.string.battery_note),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp),
        )
    }
}
