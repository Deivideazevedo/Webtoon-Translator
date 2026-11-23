package com.deivide.webtoontranslator.floating

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.deivide.webtoontranslator.WebtoonApp

/**
 * Atalho no painel rápido do Android (puxando a barra de cima): liga e desliga o tradutor
 * flutuante, como o botão da tela Inicial e o widget. Ligar passa pelo [FloatingToggleActivity],
 * que pede a captura da tela (o Android exige a cada vez).
 */
class FloatingTileService : TileService() {

    private val running get() = (application as WebtoonApp).container.floatingStatus.running.value

    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        if (running) {
            FloatingService.stop(this)
            return
        }
        val intent = Intent(this, FloatingToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
