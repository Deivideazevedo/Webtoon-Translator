package com.deivide.webtoontranslator.floating

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.deivide.webtoontranslator.R
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Ícone flutuante, no estilo do Translate On Screen. A setinha (^) em cima dele é a ponta do
 * "mouse": a seleção é desenhada por ela, e o ícone fica pendurado embaixo pra não cobrir o texto.
 * - arrastar move o ícone (o app embaixo continua rolando) e ele fica onde foi solto; no
 *   arrasto ele pode passar das bordas (pra setinha alcançar a tela toda) e, ao soltar, volta
 *   pra dentro encostado na borda;
 * - tocar arma a seleção; armado, arrastar desenha a área da ponta inicial até a ponta atual,
 *   e soltar traduz;
 * - tocar de novo sem arrastar, ou segurar, abre o menu;
 * - tocar em outra coisa ou rolar a página manda o ícone de volta pra borda, onde ele fica
 *   transparente depois de um tempo.
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingIcon(
    context: Context,
    private val windows: WindowManager,
    private val listener: Listener,
) {
    interface Listener {
        /** Toque quando [tapArms] é false (ex.: região fixa traduz direto). */
        fun onTap()
        /** Toque com a seleção desarmada: o ícone acabou de ser armado. */
        fun onArmed()
        /** Toque com a seleção armada (sem arrastar) ou toque longo. */
        fun onMenu()
        fun onRegionChanged(region: Rect)
        fun onRegionDone(region: Rect)
    }

    private val density = context.resources.displayMetrics.density
    private val size = (ICON_DP * density).toInt()
    private val caretHeight = (CARET_DP * density).toInt()
    private val screen = screenBounds(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private val view = IconView(context, size, caretHeight)
    private val params = overlayParams(size, size + caretHeight).apply {
        // Toques fora do ícone chegam como ACTION_OUTSIDE (e continuam indo pro app embaixo).
        flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        x = screen.width() - size
        y = screen.height() / 4
    }
    private var docked = true

    /** true: tocar arma a seleção (modo região); false: tocar chama [Listener.onTap]. */
    var tapArms = true
        set(value) {
            field = value
            if (!value) armed = false
        }

    /** Seleção armada (o próximo arrasto desenha a área). */
    var armed = false
        private set(value) {
            field = value
            view.armed = value
            if (value) wake() else scheduleDim()
        }

    fun show() {
        windows.addView(view, params)
        view.setOnTouchListener(TouchHandler())
        // Pegar o ícone na borda não pode virar o gesto de "voltar" do Android.
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            v.systemGestureExclusionRects = listOf(Rect(0, 0, v.width, v.height))
        }
        scheduleDim()
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        runCatching { windows.removeView(view) }
    }

    /**
     * Mostra o ícone armado por cima de uma janela que acabou de abrir (o editor da região
     * automática): o toque seguinte cai nele e desarma. [value] false só tira o destaque.
     */
    fun showArmedOnTop(value: Boolean) {
        armed = value
        if (value) runCatching {
            windows.removeView(view)
            windows.addView(view, params)
        }
    }

    /** Some da tela (sem sair do lugar) enquanto a captura é feita. */
    fun setHidden(hidden: Boolean) {
        view.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    /** Onde o ícone está na tela, pra posicionar o menu e a dica perto dele. */
    val position: Rect get() = Rect(params.x, params.y, params.x + size, params.y + size + caretHeight)

    /** Ponta da setinha, em coordenadas da tela. */
    private fun tip() = Point(params.x + size / 2, params.y)

    /**
     * Durante o arrasto o ícone pode sair metade pros lados e inteiro por baixo: a setinha (centro
     * de cima) precisa alcançar qualquer ponto da tela.
     */
    private fun move(x: Int, y: Int) {
        params.x = x.coerceIn(-size / 2, screen.width() - size / 2)
        params.y = y.coerceIn(0, screen.height())
        runCatching { windows.updateViewLayout(view, params) }
    }

    /** Depois do arrasto: se passou da borda, desliza de volta até encostar nela. */
    private fun keepInside() {
        val x = params.x.coerceIn(0, screen.width() - size)
        val y = params.y.coerceIn(0, screen.height() - size - caretHeight)
        if (x == params.x && y == params.y) return
        val fromX = params.x
        val fromY = params.y
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                move(fromX + ((x - fromX) * t).toInt(), fromY + ((y - fromY) * t).toInt())
            }
        }.start()
    }

    /**
     * Desliza até a borda mais perto, encostando nela (igual ao soltar depois de passar da borda).
     * O gesto de voltar do Android não atrapalha: a área do ícone é excluída dele (ver [show]).
     */
    private fun dock() {
        if (docked) return
        docked = true
        val target = if (params.x + size / 2 < screen.width() / 2) 0 else screen.width() - size
        ValueAnimator.ofInt(params.x, target).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { move(it.animatedValue as Int, params.y) }
        }.start()
        scheduleDim()
    }

    private fun scheduleDim() {
        handler.removeCallbacks(dim)
        if (docked && !armed) handler.postDelayed(dim, DIM_AFTER_MS)
    }

    private val dim = Runnable { view.alpha = DIM_ALPHA }

    private fun wake() {
        handler.removeCallbacks(dim)
        view.alpha = 1f
    }

    private inner class TouchHandler : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var startTip = Point()
        private var dragging = false
        private var selecting = false
        private var longPressed = false
        private val longPress = Runnable {
            longPressed = true
            listener.onMenu()
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                // Tocou em outra coisa (ou rolou a página): volta pra borda e desarma. Armado pelo
                // editor da região automática (tapArms false), fica armado até o editor fechar.
                MotionEvent.ACTION_OUTSIDE -> {
                    if (armed && tapArms) armed = false
                    dock()
                }
                MotionEvent.ACTION_DOWN -> {
                    wake()
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    startTip = tip()
                    dragging = false
                    selecting = false
                    longPressed = false
                    handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > touchSlop && !longPressed) {
                        dragging = true
                        selecting = armed && tapArms
                        docked = false
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        move(startX + dx.toInt(), startY + dy.toInt())
                        if (selecting) listener.onRegionChanged(region())
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    when {
                        longPressed -> armed = false
                        selecting -> {
                            armed = false
                            listener.onRegionDone(region())
                            keepInside()
                        }
                        // Solto onde o usuário quis: só volta pra borda num toque fora.
                        dragging -> keepInside()
                        event.actionMasked == MotionEvent.ACTION_UP && !tapArms -> listener.onTap()
                        event.actionMasked == MotionEvent.ACTION_UP && armed -> {
                            armed = false
                            listener.onMenu()
                        }
                        event.actionMasked == MotionEvent.ACTION_UP -> {
                            armed = true
                            listener.onArmed()
                        }
                    }
                }
            }
            return true
        }

        /** Área entre a ponta da setinha no começo do arrasto e a ponta agora. */
        private fun region(): Rect {
            val now = tip()
            return Rect(min(startTip.x, now.x), min(startTip.y, now.y), max(startTip.x, now.x), max(startTip.y, now.y))
        }
    }

    private class IconView(context: Context, private val size: Int, private val caretHeight: Int) : View(context) {
        private val logo: Bitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        // Sombra contida (curta, rente à borda, um pouco pra baixo/direita): dá recorte em página
        // branca sem contorno nem halo espalhado.
        private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            setShadowLayer(SHADOW_RADIUS * size, SHADOW_DX * size, SHADOW_DY * size, SHADOW)
        }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size * 0.07f
        }
        private val caret = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.085f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            // A mesma sombra contida: até a setinha branca (armada) aparece em página branca.
            setShadowLayer(SHADOW_RADIUS * size * 0.8f, SHADOW_DX * size, SHADOW_DY * size, SHADOW)
        }
        private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val caretPath = Path()
        private val contentClip = Path()

        var armed = false
            set(value) {
                field = value
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            val r = size / 2f
            // Setinha: chevron baixo e largo; a ponta (topo da janela) é o ponto da seleção.
            val half = size * 0.15f
            // Folga em cima pra sombra/traço não serem cortados pela borda da janela.
            val stroke = caret.strokeWidth / 2 + size * 0.01f
            val bottom = stroke + size * 0.1f
            caretPath.reset()
            caretPath.moveTo(r - half, bottom)
            caretPath.lineTo(r, stroke)
            caretPath.lineTo(r + half, bottom)
            caret.color = if (armed) Color.WHITE else ACCENT
            canvas.drawPath(caretPath, caret)

            // A bola fica um pouco acima do fim da área da setinha (vão curto entre as duas, sem a
            // setinha encostar no anel branco de "armado"); a ponta da setinha não se move.
            canvas.translate(0f, caretHeight - BALL_LIFT_DP * resources.displayMetrics.density)
            // O círculo azul vai só até onde começa o anel de "armado": parado não sobra faixa azul
            // em volta; armado, o anel branco aparece em volta dele (no mesmo lugar de sempre).
            val outer = r * 0.9f
            val content = outer - ring.strokeWidth
            canvas.drawCircle(r, r, content, background)
            canvas.save()
            contentClip.reset()
            contentClip.addCircle(r, r, content, Path.Direction.CW)
            canvas.clipPath(contentClip) // os cantos do ícone do app não passam do círculo
            val inset = size * 0.2f
            canvas.drawBitmap(logo, null, RectF(inset, inset, size - inset, size - inset), logoPaint)
            canvas.restore()
            if (armed) canvas.drawCircle(r, r, outer - ring.strokeWidth / 2, ring)
        }
    }

    private companion object {
        const val ICON_DP = 50
        const val CARET_DP = 14
        const val DIM_AFTER_MS = 2_000L
        const val DIM_ALPHA = 0.55f
        val ACCENT = Color.parseColor("#6EA8FE")
        /** Sombra contida: raio curto e deslocamento pequeno (frações do tamanho do ícone). */
        val SHADOW = Color.argb(85, 0, 0, 0)
        const val SHADOW_RADIUS = 0.035f
        const val SHADOW_DX = 0.012f
        const val SHADOW_DY = 0.025f
        /** Quanto a bola sobe em direção à setinha (vão final ≈ 2,75 dp com o ícone de 50 dp). */
        const val BALL_LIFT_DP = 4f
    }
}
