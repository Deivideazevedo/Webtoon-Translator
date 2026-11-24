package com.deivide.webtoontranslator.floating

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.deivide.webtoontranslator.R
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** O que o arrasto atual está fazendo com o retângulo (TL/TR/BL/BR = alças dos cantos). */
private enum class Drag { NONE, MOVE, NEW, TL, TR, BL, BR }

/**
 * Marca a área do modo região fixa: retângulo com alças nos 4 cantos (arrastar a alça
 * redimensiona, arrastar o meio move, arrastar fora desenha um novo) e ✓ / ✗ em cima.
 */
class FixedRegionEditor(
    context: Context,
    private val windows: WindowManager,
    initial: Rect?,
    /** Toque aqui (a bolinha, que fica embaixo do editor) fecha, como o ✗. */
    private val closeArea: Rect? = null,
    private val onConfirm: (Rect) -> Unit,
    private val onCancel: () -> Unit,
) {
    private val screen = screenBounds(context)
    private val view = EditorView(context, initial ?: defaultRegion())
    private var shown = false

    fun show() {
        windows.addView(view, overlayParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        shown = true
    }

    fun remove() {
        if (shown) runCatching { windows.removeView(view) }
        shown = false
    }

    /** Faixa no meio da tela, onde costuma estar o balão. */
    private fun defaultRegion(): Rect {
        val w = screen.width()
        val h = screen.height()
        return Rect(w / 10, h * 2 / 5, w * 9 / 10, h * 3 / 5)
    }

    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private inner class EditorView(context: Context, initial: Rect) : View(context) {
        private val density = resources.displayMetrics.density
        private val handleRadius = 9 * density
        private val handleTouch = 28 * density
        private val buttonRadius = 18 * density
        private val minSize = 48 * density
        private val region = RectF(initial)
        private val location = IntArray(2)

        private val dim = Paint().apply { color = Color.argb(90, 0, 0, 0) }
        private val fill = Paint().apply { color = Color.argb(70, 40, 40, 40) }
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14 * density
            textAlign = Paint.Align.CENTER
        }
        private val confirmBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2E9E6B") }
        private val cancelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D9534F") }
        private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val labelText = context.getString(R.string.fixed_region_select)

        private var drag = Drag.NONE
        private var last = PointF()
        private var anchor = PointF()

        private fun confirmCenter() = PointF(region.right - buttonRadius * 3.4f, region.top - buttonRadius * 1.6f)
        private fun cancelCenter() = PointF(region.right - buttonRadius, region.top - buttonRadius * 1.6f)

        override fun onDraw(canvas: Canvas) {
            // Coordenadas da tela → da janela (a janela pode começar abaixo da barra de status).
            getLocationOnScreen(location)
            canvas.translate(-location[0].toFloat(), -location[1].toFloat())
            canvas.drawRect(0f, 0f, screen.width().toFloat(), region.top, dim)
            canvas.drawRect(0f, region.bottom, screen.width().toFloat(), screen.height().toFloat(), dim)
            canvas.drawRect(0f, region.top, region.left, region.bottom, dim)
            canvas.drawRect(region.right, region.top, screen.width().toFloat(), region.bottom, dim)
            canvas.drawRect(region, fill)
            canvas.drawRect(region, stroke)
            canvas.drawText(labelText, region.centerX(), region.centerY() + label.textSize / 3, label)
            for ((x, y) in corners()) canvas.drawCircle(x, y, handleRadius, handle)

            val ok = confirmCenter()
            canvas.drawCircle(ok.x, ok.y, buttonRadius, confirmBg)
            val r = buttonRadius * 0.45f
            canvas.drawPath(Path().apply {
                moveTo(ok.x - r, ok.y)
                lineTo(ok.x - r * 0.2f, ok.y + r * 0.75f)
                lineTo(ok.x + r, ok.y - r * 0.6f)
            }, glyph)
            val no = cancelCenter()
            canvas.drawCircle(no.x, no.y, buttonRadius, cancelBg)
            canvas.drawLine(no.x - r * 0.8f, no.y - r * 0.8f, no.x + r * 0.8f, no.y + r * 0.8f, glyph)
            canvas.drawLine(no.x + r * 0.8f, no.y - r * 0.8f, no.x - r * 0.8f, no.y + r * 0.8f, glyph)
        }

        private fun corners() = listOf(
            region.left to region.top, region.right to region.top,
            region.left to region.bottom, region.right to region.bottom,
        )

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val x = event.rawX
            val y = event.rawY
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    last = PointF(x, y)
                    drag = when {
                        onCloseArea(x, y) -> Drag.NONE
                        near(x, y, region.left, region.top) -> Drag.TL
                        near(x, y, region.right, region.top) -> Drag.TR
                        near(x, y, region.left, region.bottom) -> Drag.BL
                        near(x, y, region.right, region.bottom) -> Drag.BR
                        region.contains(x, y) -> Drag.MOVE
                        hit(x, y, confirmCenter()) || hit(x, y, cancelCenter()) -> Drag.NONE
                        else -> Drag.NEW.also { anchor = PointF(x, y) }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = x - last.x
                    val dy = y - last.y
                    when (drag) {
                        Drag.MOVE -> region.offset(dx, dy)
                        Drag.TL -> { region.left = min(region.left + dx, region.right - minSize); region.top = min(region.top + dy, region.bottom - minSize) }
                        Drag.TR -> { region.right = max(region.right + dx, region.left + minSize); region.top = min(region.top + dy, region.bottom - minSize) }
                        Drag.BL -> { region.left = min(region.left + dx, region.right - minSize); region.bottom = max(region.bottom + dy, region.top + minSize) }
                        Drag.BR -> { region.right = max(region.right + dx, region.left + minSize); region.bottom = max(region.bottom + dy, region.top + minSize) }
                        Drag.NEW -> region.set(min(anchor.x, x), min(anchor.y, y), max(anchor.x, x), max(anchor.y, y))
                        Drag.NONE -> Unit
                    }
                    last = PointF(x, y)
                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    if (drag == Drag.NONE) {
                        when {
                            hit(x, y, confirmCenter()) -> onConfirm(result())
                            hit(x, y, cancelCenter()) || onCloseArea(x, y) -> onCancel()
                        }
                    } else if (drag == Drag.NEW && (region.width() < minSize || region.height() < minSize)) {
                        region.right = region.left + max(region.width(), minSize)
                        region.bottom = region.top + max(region.height(), minSize)
                        invalidate()
                    }
                    drag = Drag.NONE
                }
            }
            return true
        }

        private fun near(x: Float, y: Float, cx: Float, cy: Float) = hypot(x - cx, y - cy) < handleTouch
        private fun hit(x: Float, y: Float, c: PointF) = hypot(x - c.x, y - c.y) < buttonRadius * 1.3f
        private fun onCloseArea(x: Float, y: Float) = closeArea?.contains(x.toInt(), y.toInt()) == true

        private fun result(): Rect {
            val r = Rect()
            region.round(r)
            r.intersect(screen)
            return r
        }
    }

    private companion object {
        val ACCENT = Color.parseColor("#4C7BEA")
    }
}
