package com.deivide.webtoontranslator.floating

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.deivide.webtoontranslator.R
import com.deivide.webtoontranslator.WebtoonApp

/**
 * Widget da tela inicial com o botão de ligar/desligar o tradutor flutuante (o mesmo da tela
 * Inicial). Tocar abre o [FloatingToggleActivity], que liga (pedindo a captura da tela, que o
 * Android exige a cada vez) ou desliga.
 */
class FloatingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val running = (context.applicationContext as WebtoonApp).container.floatingStatus.running.value
        manager.updateAppWidget(ids, views(context, running))
    }

    companion object {
        private const val ICON_ON = 0xFF0D1B33.toInt()
        private const val ICON_OFF = 0xFF9AA3B2.toInt()

        /** Atualiza os widgets e o atalho do painel rápido (o serviço chama ao ligar e ao desligar). */
        fun refresh(context: Context, running: Boolean) {
            TileService.requestListeningState(context, ComponentName(context, FloatingTileService::class.java))
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FloatingWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, running))
        }

        private fun views(context: Context, running: Boolean) = RemoteViews(context.packageName, R.layout.widget_floating).apply {
            setInt(R.id.widget_button, "setBackgroundResource", if (running) R.drawable.widget_button_on else R.drawable.widget_button_off)
            // Ícone com as cores do botão da tela Inicial (tema escuro): onPrimary ligado, onSurfaceVariant desligado.
            setInt(R.id.widget_icon, "setColorFilter", if (running) ICON_ON else ICON_OFF)
            setTextViewText(R.id.widget_status, context.getString(if (running) R.string.widget_on else R.string.widget_off))
            val toggle = PendingIntent.getActivity(
                context, 0,
                Intent(context, FloatingToggleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            setOnClickPendingIntent(R.id.widget_root, toggle)
        }
    }
}
