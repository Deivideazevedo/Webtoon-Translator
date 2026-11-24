package com.deivide.webtoontranslator.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.floating.FloatingService

/**
 * Liga o tradutor flutuante pedindo, na ordem, cada permissão necessária — sempre explicando
 * antes do aviso do Android (ideia do Translate On Screen):
 * 1. aparecer sobre outros apps (tela de configurações do sistema);
 * 2. captura da tela (a tela inteira ou só o app de leitura).
 * Depois o app vai pro fundo, deixando só o ícone flutuante. (A notificação que o Android exige
 * pra captura existe sem pedir permissão; só não aparece na gaveta.)
 */
class FloatingStarter internal constructor(val start: () -> Unit)

private enum class Step { NONE, EXPLAIN_OVERLAY, WAITING_OVERLAY, EXPLAIN_CAPTURE }

@Composable
fun rememberFloatingStarter(): FloatingStarter {
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as WebtoonApp).container.settingsRepository }
    var step by rememberSaveable { mutableStateOf(Step.NONE) }

    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            FloatingService.start(context, result.resultCode, data)
            context.findActivity()?.moveTaskToBack(true)
        }
    }
    val requestCapture = { captureLauncher.launch(FloatingService.captureIntent(context)) }
    val afterOverlay = {
        if (settings.skipCaptureExplanation) requestCapture() else step = Step.EXPLAIN_CAPTURE
    }

    // Voltando da tela de configurações do sistema: segue sozinho se a permissão foi dada.
    LifecycleResumeEffect(step) {
        if (step == Step.WAITING_OVERLAY) {
            step = Step.NONE
            if (Settings.canDrawOverlays(context)) afterOverlay()
        }
        onPauseOrDispose { }
    }

    when (step) {
        Step.EXPLAIN_OVERLAY -> ExplainDialog(
            title = stringResource(R.string.permission_overlay_title),
            body = stringResource(R.string.permission_overlay_body),
            onContinue = {
                step = Step.WAITING_OVERLAY
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
            },
            onDismiss = { step = Step.NONE },
        )
        Step.EXPLAIN_CAPTURE -> ExplainDialog(
            title = stringResource(R.string.permission_capture_title),
            body = stringResource(R.string.permission_capture_body),
            onContinue = { dontShowAgain ->
                settings.skipCaptureExplanation = dontShowAgain
                step = Step.NONE
                requestCapture()
            },
            onDismiss = { step = Step.NONE },
            offerDontShowAgain = true,
        )
        else -> Unit
    }

    return remember {
        FloatingStarter {
            if (Settings.canDrawOverlays(context)) afterOverlay() else step = Step.EXPLAIN_OVERLAY
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ExplainDialog(
    title: String,
    body: String,
    onContinue: (dontShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
    offerDontShowAgain: Boolean = false,
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body)
                if (offerDontShowAgain) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = dontShowAgain, onCheckedChange = { dontShowAgain = it })
                        Text(stringResource(R.string.permission_dont_show_again))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onContinue(dontShowAgain) }) { Text(stringResource(R.string.permission_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
