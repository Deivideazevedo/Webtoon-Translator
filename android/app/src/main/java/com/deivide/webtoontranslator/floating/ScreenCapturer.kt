package com.deivide.webtoontranslator.floating

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.delay

/**
 * Captura da tela pelo MediaProjection. O espelho da tela fica ligado enquanto o tradutor está
 * ativo (o Android 14+ não deixa recriar com a mesma permissão) e guardamos só o quadro mais recente.
 */
class ScreenCapturer(context: Context, projection: MediaProjection) {
    private val bounds: Rect = screenBounds(context)
    private val thread = HandlerThread("webtoon-capture").apply { start() }
    private val reader = ImageReader.newInstance(bounds.width(), bounds.height(), PixelFormat.RGBA_8888, 3)
    private val display: VirtualDisplay
    private val lock = Any()
    private var latest: Image? = null
    private var latestAt = 0L

    init {
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            synchronized(lock) {
                latest?.close()
                latest = image
                latestAt = System.nanoTime()
            }
        }, Handler(thread.looper))
        display = projection.createVirtualDisplay(
            "webtoon-capture",
            bounds.width(),
            bounds.height(),
            context.resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null,
        ) ?: error("Não consegui iniciar a captura da tela.")
    }

    /**
     * Quadro da tela de agora. Quem chama esconde as nossas janelas antes; esperamos um quadro
     * novo (posterior a isso) pra elas não entrarem na imagem. Sem nada escondido
     * ([waitForNewFrame] falso), o quadro mais recente já serve.
     */
    suspend fun capture(waitForNewFrame: Boolean = true, mask: List<Rect> = emptyList()): Bitmap {
        if (!waitForNewFrame) synchronized(lock) { latest?.let { return it.toBitmap(mask) } }
        val requestedAt = System.nanoTime()
        delay(FRAME_WAIT_MS)
        var waited = FRAME_WAIT_MS
        while (waited < MAX_WAIT_MS) {
            synchronized(lock) {
                val image = latest
                if (image != null && latestAt > requestedAt) return image.toBitmap(mask)
            }
            delay(POLL_MS)
            waited += POLL_MS
        }
        // Tela parada (nenhum quadro novo): o último quadro já é a tela sem as nossas janelas.
        synchronized(lock) {
            return latest?.toBitmap(mask) ?: error("A captura de tela ainda não começou. Tente de novo.")
        }
    }

    /**
     * "Impressão digital" barata do quadro mais recente, pra perceber que a tela mudou sem copiar
     * a imagem: o brilho de uma grade de pontos dentro de [area]. Pontos dentro de [exclude] (as
     * nossas janelas) ficam de fora (-1), pra o próprio cartão ou as traduções não contarem como mudança.
     */
    fun signature(area: Rect, exclude: List<Rect>): IntArray? = synchronized(lock) {
        val image = latest ?: return null
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bounds = Rect(area).apply { if (!intersect(0, 0, image.width, image.height)) return null }
        IntArray(SIGNATURE_COLS * SIGNATURE_ROWS) { i ->
            val x = bounds.left + (i % SIGNATURE_COLS * 2 + 1) * bounds.width() / (SIGNATURE_COLS * 2)
            val y = bounds.top + (i / SIGNATURE_COLS * 2 + 1) * bounds.height() / (SIGNATURE_ROWS * 2)
            if (exclude.any { it.contains(x, y) }) return@IntArray -1
            val at = y * plane.rowStride + x * plane.pixelStride
            val r = buffer.get(at).toInt() and 0xFF
            val g = buffer.get(at + 1).toInt() and 0xFF
            val b = buffer.get(at + 2).toInt() and 0xFF
            (r * 3 + g * 6 + b) / 10
        }
    }

    fun release() {
        display.release()
        synchronized(lock) {
            latest?.close()
            latest = null
        }
        reader.close()
        thread.quitSafely()
    }

    private companion object {
        const val FRAME_WAIT_MS = 120L
        const val POLL_MS = 30L
        const val MAX_WAIT_MS = 600L
        const val SIGNATURE_COLS = 32
        const val SIGNATURE_ROWS = 64
    }
}

/**
 * Copia o quadro (RGBA, com possível sobra no fim de cada linha) pra um Bitmap ARGB_8888. As
 * áreas de [mask] (nossas janelas que ficaram na tela) são pintadas com a cor ao lado delas, pra
 * não entrarem no OCR.
 */
private fun Image.toBitmap(mask: List<Rect> = emptyList()): Bitmap {
    val plane = planes[0]
    val rowPixels = plane.rowStride / plane.pixelStride
    val padded = Bitmap.createBitmap(rowPixels, height, Bitmap.Config.ARGB_8888)
    plane.buffer.rewind()
    padded.copyPixelsFromBuffer(plane.buffer)
    if (mask.isNotEmpty()) {
        val canvas = Canvas(padded)
        val paint = Paint()
        for (rect in mask) {
            val r = Rect(rect)
            if (!r.intersect(0, 0, width, height)) continue
            // Cor do fundo logo à esquerda (ou à direita, na borda) da janela.
            val x = if (r.left > 0) r.left - 1 else (r.right).coerceAtMost(width - 1)
            paint.color = padded.getPixel(x, r.centerY())
            canvas.drawRect(r, paint)
        }
    }
    return if (rowPixels == width) padded else Bitmap.createBitmap(padded, 0, 0, width, height)
}
