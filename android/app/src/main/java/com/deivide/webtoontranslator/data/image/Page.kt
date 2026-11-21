package com.deivide.webtoontranslator.data.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/**
 * Página do webtoon dividida em fatias de até [TILE_HEIGHT] px: tiras longas passam do limite de
 * textura da GPU se forem desenhadas inteiras.
 */
class Page(val tiles: List<Bitmap>, val width: Int, val height: Int) {

    /** Recorta uma área (em pixels da página), juntando fatias se ela cruzar a divisa. */
    fun crop(area: Rect): Bitmap {
        val out = Bitmap.createBitmap(area.width(), area.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        tiles.forEachIndexed { index, tile ->
            val tileTop = index * TILE_HEIGHT
            val src = Rect(area.left, max(area.top - tileTop, 0), area.right, min(area.bottom - tileTop, tile.height))
            if (src.top < src.bottom) {
                val dstTop = tileTop + src.top - area.top
                canvas.drawBitmap(tile, src, Rect(0, dstTop, area.width(), dstTop + src.height()), null)
            }
        }
        return out
    }

    companion object {
        const val TILE_HEIGHT = 4096
    }
}
