package com.deivide.webtoontranslator.floating

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.deivide.webtoontranslator.ui.theme.WebtoonTheme

/**
 * Uma janela flutuante desenhada em Compose (cartão de tradução, menu). [onOutsideTouch] é
 * chamado quando o usuário toca fora dela (pra fechar menus).
 */
class ComposeOverlay(
    context: Context,
    private val windows: WindowManager,
    private val onOutsideTouch: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    private val lifecycle = OverlayLifecycleOwner()

    @SuppressLint("ClickableViewAccessibility")
    private val view = ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycle)
        setViewTreeSavedStateRegistryOwner(lifecycle)
        setContent { WebtoonTheme(content) }
        if (onOutsideTouch != null) {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) onOutsideTouch.invoke()
                false
            }
        }
    }
    private var shown = false

    fun show(params: WindowManager.LayoutParams) {
        if (onOutsideTouch != null) params.flags = params.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        if (shown) {
            windows.updateViewLayout(view, params)
            return
        }
        lifecycle.start()
        windows.addView(view, params)
        shown = true
    }

    /** Some sem destruir (pra captura), mantendo o estado. */
    fun setHidden(hidden: Boolean) {
        view.visibility = if (hidden) android.view.View.INVISIBLE else android.view.View.VISIBLE
    }

    fun remove() {
        if (!shown) return
        runCatching { windows.removeView(view) }
        lifecycle.destroy()
        shown = false
    }
}
