package com.deivide.webtoontranslator.floating

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.WindowManager

/** Retângulo da área sendo selecionada, numa janela da tela toda que não recebe toques. */
class SelectionOverlay(context: Context, private val windows: WindowManager) {
    private val view = RegionView(context)
    private var shown = false

    fun update(region: Rect) {
        view.region = region
        if (!shown) {
            val bounds = screenBounds(view.context)
            windows.addView(view, overlayParams(bounds.width(), bounds.height(), touchable = false))
            shown = true
        }
    }

    fun remove() {
        if (shown) runCatching { windows.removeView(view) }
        shown = false
    }

    private class RegionView(context: Context) : View(context) {
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            style = Paint.Style.STROKE
            strokeWidth = 2 * resources.displayMetrics.density
        }
        private val fill = Paint().apply { color = Color.argb(46, 110, 168, 254) }
        private val location = IntArray(2)

        var region = Rect()
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            // A janela pode não começar exatamente no (0,0) da tela: desenha em coordenadas da tela.
            getLocationOnScreen(location)
            val r = Rect(region).apply { offset(-location[0], -location[1]) }
            canvas.drawRect(r, fill)
            canvas.drawRect(r, stroke)
        }

        private companion object {
            val ACCENT = Color.parseColor("#6EA8FE")
        }
    }
}
