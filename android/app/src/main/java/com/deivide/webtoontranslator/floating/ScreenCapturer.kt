package com.deivide.webtoontranslator.floating

import android.content.Context
import android.graphics.Bitmap
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
     * novo (posterior a isso) pra elas não entrarem na imagem.
     */
    suspend fun capture(): Bitmap {
        val requestedAt = System.nanoTime()
        delay(FRAME_WAIT_MS)
        var waited = FRAME_WAIT_MS
        while (waited < MAX_WAIT_MS) {
            synchronized(lock) {
                val image = latest
                if (image != null && latestAt > requestedAt) return image.toBitmap()
            }
            delay(POLL_MS)
            waited += POLL_MS
        }
        // Tela parada (nenhum quadro novo): o último quadro já é a tela sem as nossas janelas.
        synchronized(lock) {
            return latest?.toBitmap() ?: error("A captura de tela ainda não começou. Tente de novo.")
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
    }
}

/** Copia o quadro (RGBA, com possível sobra no fim de cada linha) pra um Bitmap ARGB_8888. */
private fun Image.toBitmap(): Bitmap {
    val plane = planes[0]
    val rowPixels = plane.rowStride / plane.pixelStride
    val padded = Bitmap.createBitmap(rowPixels, height, Bitmap.Config.ARGB_8888)
    plane.buffer.rewind()
    padded.copyPixelsFromBuffer(plane.buffer)
    return if (rowPixels == width) padded else Bitmap.createBitmap(padded, 0, 0, width, height)
}
