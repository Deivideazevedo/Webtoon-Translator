package com.deivide.webtoontranslator.floating

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.domain.model.ScreenBlock

/**
 * Traduções do modo tela cheia, cada uma numa janelinha logo ACIMA do balão original (ou logo
 * abaixo, se não couber em cima), pra não cobrir o texto. Dá pra arrastar cada uma; tocar abre a
 * tradução completa; tocar fora (ou rolar a página) tira todas, porque os balões mudaram de lugar.
 */
class ScreenBlocksOverlay(
    private val context: Context,
    private val windows: WindowManager,
    private val onOpen: (ScreenBlock) -> Unit,
    private val onDismiss: () -> Unit,
    /** O que o voltar faz em cada tradução (null = vai pro app de baixo); lido a cada [show]. */
    private val onBack: () -> (() -> Unit)?,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val overlays = mutableListOf<ComposeOverlay>()
    private var lastBlockTouch = 0L
    private val minWidth = (MIN_WIDTH_DP * context.resources.displayMetrics.density).toInt()

    val isShown get() = overlays.isNotEmpty()

    /** [topLimit]: a caixinha não sobe pra cima disso (barra de status). */
    fun show(blocks: List<ScreenBlock>, topLimit: Int) {
        remove()
        val screen = screenBounds(context)
        val back = onBack()
        for (block in blocks) {
            val width = (block.right - block.left).coerceAtLeast(minWidth).coerceAtMost(screen.width())
            val params = overlayParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, gravity = Gravity.TOP or Gravity.START).apply {
                x = block.left.coerceIn(0, screen.width() - width)
                y = block.top
                alpha = 0f // invisível até saber a altura e ir pro lugar certo
            }
            lateinit var overlay: ComposeOverlay
            var placed = false
            overlay = ComposeOverlay(
                context, windows,
                onOutsideTouch = ::onOutside,
                onTouchDown = { lastBlockTouch = SystemClock.uptimeMillis() },
                backHandler = back,
            ) {
                BlockLabel(
                    text = block.translation,
                    onClick = { onOpen(block) },
                    onMeasured = { height ->
                        if (!placed) {
                            placed = true
                            val above = block.top - height - GAP_PX
                            params.y = if (above >= topLimit) above else block.bottom + GAP_PX
                            params.alpha = 1f
                            overlay.show(params)
                        }
                    },
                    onDrag = { dx, dy ->
                        lastBlockTouch = SystemClock.uptimeMillis()
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        overlay.show(params)
                    },
                )
            }
            overlays += overlay.also { it.show(params) }
        }
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        overlays.forEach { it.remove() }
        overlays.clear()
    }

    /**
     * Toque em qualquer lugar fora de UMA janela chega pras outras também; então só some se,
     * logo depois, nenhuma das nossas janelas foi tocada.
     */
    private fun onOutside() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (SystemClock.uptimeMillis() - lastBlockTouch > OWN_TOUCH_WINDOW_MS) onDismiss()
        }, OUTSIDE_CHECK_MS)
    }

    private companion object {
        const val MIN_WIDTH_DP = 120
        const val OUTSIDE_CHECK_MS = 120L
        const val OWN_TOUCH_WINDOW_MS = 300L
        const val GAP_PX = 6
    }
}

@Composable
private fun BlockLabel(
    text: String,
    onClick: () -> Unit,
    onMeasured: (heightPx: Int) -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    Text(
        text,
        color = Color(0xFFF2F2F2),
        fontSize = 14.sp,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasured(it.height) }
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E).copy(alpha = 0.9f))
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
