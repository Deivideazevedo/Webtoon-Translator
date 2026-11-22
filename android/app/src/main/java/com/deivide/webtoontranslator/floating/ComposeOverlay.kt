package com.deivide.webtoontranslator.floating

import android.content.Context
import android.graphics.Rect
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deivide.webtoontranslator.WebtoonApp
import com.deivide.webtoontranslator.ui.theme.WebtoonTheme

/**
 * Uma janela flutuante desenhada em Compose (cartão de tradução, menu). [onOutsideTouch] é
 * chamado quando o usuário toca fora dela (pra fechar menus); [onTouchDown], quando tocam nela.
 * Com [onBack], a janela pode receber o foco e o voltar (botão ou gesto) chama [onBack] em vez de
 * ir pro app de baixo — enquanto ela tiver o foco: tocar no app de baixo devolve o foco pra ele.
 */
class ComposeOverlay(
    context: Context,
    private val windows: WindowManager,
    private val onOutsideTouch: (() -> Unit)? = null,
    private val onTouchDown: (() -> Unit)? = null,
    backHandler: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    /** Trocável com a janela aberta ([setOnBack]). */
    private var onBack = backHandler
    private var params: WindowManager.LayoutParams? = null
    private val lifecycle = OverlayLifecycleOwner()

    /** Raiz da janela: vê os toques e as teclas antes do Compose. */
    private val view = object : FrameLayout(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> onOutsideTouch?.invoke()
                MotionEvent.ACTION_DOWN -> onTouchDown?.invoke()
            }
            return super.dispatchTouchEvent(event)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            val back = this@ComposeOverlay.onBack
            if (back != null && event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) back()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }.apply {
        setViewTreeLifecycleOwner(lifecycle)
        setViewTreeSavedStateRegistryOwner(lifecycle)
        val theme = (context.applicationContext as WebtoonApp).container.settingsRepository.theme
        addView(ComposeView(context).apply {
            setContent {
                // Tema escolhido em Aparência (o cartão tem as cores dele por cima).
                val mode by theme.collectAsStateWithLifecycle()
                WebtoonTheme(mode, content = content)
            }
        })
        if (backHandler != null) isFocusableInTouchMode = true
    }
    private var shown = false

    fun show(params: WindowManager.LayoutParams) {
        if (onOutsideTouch != null) params.flags = params.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        params.flags = if (onBack != null) {
            // Focável (pra receber o voltar), mas sem virar alvo do teclado no lugar do app de baixo.
            (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        } else {
            (params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()) or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        this.params = params
        if (shown) {
            windows.updateViewLayout(view, params)
        } else {
            lifecycle.start()
            windows.addView(view, params)
            shown = true
        }
        if (onBack != null && !view.hasFocus()) view.requestFocus()
    }

    /** Liga/desliga o voltar com a janela aberta (ex.: a opção mudou pelo ⚙ do cartão). */
    fun setOnBack(handler: (() -> Unit)?) {
        onBack = handler
        view.isFocusableInTouchMode = handler != null
        params?.let { if (shown) show(it) }
    }

    /** Onde a janela está na tela (null se não está aberta). */
    fun screenRect(): Rect? {
        if (!shown || !view.isAttachedToWindow) return null
        val at = IntArray(2).also(view::getLocationOnScreen)
        return Rect(at[0], at[1], at[0] + view.width, at[1] + view.height)
    }

    /** Some sem destruir (pra captura), mantendo o estado. */
    fun setHidden(hidden: Boolean) {
        view.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    fun remove() {
        if (!shown) return
        runCatching { windows.removeView(view) }
        lifecycle.destroy()
        shown = false
    }
}
