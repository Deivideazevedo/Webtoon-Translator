package com.deivide.webtoontranslator.floating

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.deivide.webtoontranslator.MainActivity
import com.deivide.webtoontranslator.WebtoonApp

/**
 * Tela invisível aberta pelo widget: desliga o tradutor, ou liga pedindo a captura da tela (o
 * Android exige essa confirmação a cada vez). Sem a permissão de aparecer sobre outros apps, abre
 * o app, que explica e pede.
 */
class FloatingToggleActivity : ComponentActivity() {

    private val capture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) FloatingService.start(this, result.resultCode, data)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return // girou a tela com a pergunta da captura aberta
        when {
            (application as WebtoonApp).container.floatingStatus.running.value -> {
                FloatingService.stop(this)
                finish()
            }
            !Settings.canDrawOverlays(this) -> {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }
            else -> {
                val manager = getSystemService(MediaProjectionManager::class.java)
                capture.launch(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                    } else {
                        manager.createScreenCaptureIntent()
                    }
                )
            }
        }
    }
}
