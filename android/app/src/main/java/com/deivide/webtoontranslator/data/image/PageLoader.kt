package com.deivide.webtoontranslator.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlin.math.ceil
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Abre uma imagem da galeria como [Page]. */
class PageLoader(private val context: Context) {

    suspend fun load(uri: Uri): Page = withContext(Dispatchers.IO) {
        val bitmap = decode(uri)
        val count = ceil(bitmap.height / Page.TILE_HEIGHT.toDouble()).toInt()
        val tiles = (0 until count).map { i ->
            val top = i * Page.TILE_HEIGHT
            Bitmap.createBitmap(bitmap, 0, top, bitmap.width, min(Page.TILE_HEIGHT, bitmap.height - top))
        }
        // Com uma fatia só, createBitmap devolve o próprio bitmap: não dá pra liberar.
        if (count > 1) bitmap.recycle()
        Page(tiles, bitmap.width, bitmap.height)
    }

    /** Decodifica em ARGB_8888 na memória comum (o OCR lê os pixels), limitando a largura. */
    private fun decode(uri: Uri): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            if (info.size.width > MAX_WIDTH) {
                decoder.setTargetSize(MAX_WIDTH, info.size.height * MAX_WIDTH / info.size.width)
            }
        }
        return if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    private companion object {
        /** Mais largo que isso só gasta memória; o OCR não melhora. */
        const val MAX_WIDTH = 1600
    }
}
