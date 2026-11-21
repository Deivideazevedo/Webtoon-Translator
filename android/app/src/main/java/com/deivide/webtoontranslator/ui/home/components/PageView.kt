package com.deivide.webtoontranslator.ui.home.components

import android.graphics.Rect as PixelRect
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.deivide.webtoontranslator.data.image.Page
import kotlin.math.max

/** Menor seleção aceita, em pixels da página (evita traduzir um toque acidental). */
private const val MIN_SELECTION_PX = 20

/**
 * Página rolável; toque longo + arrastar desenha a seleção. [onSelectArea] recebe a área em pixels
 * da página (não da tela).
 */
@Composable
fun PageView(page: Page, onSelectArea: (PixelRect) -> Unit, modifier: Modifier = Modifier) {
    var viewWidth by remember { mutableIntStateOf(1) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf(Offset.Zero) }
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            Modifier.fillMaxWidth()
                .onSizeChanged { viewWidth = max(it.width, 1) }
                .pointerInput(page) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragStart = it; dragEnd = it },
                        onDrag = { change, amount -> change.consume(); dragEnd += amount },
                        onDragEnd = {
                            val start = dragStart ?: return@detectDragGesturesAfterLongPress
                            dragStart = null
                            val area = Rect(start, dragEnd).toPagePixels(page, page.width.toFloat() / viewWidth)
                            if (area.width() > MIN_SELECTION_PX && area.height() > MIN_SELECTION_PX / 2) {
                                onSelectArea(area)
                            }
                        },
                        onDragCancel = { dragStart = null },
                    )
                }
                .drawWithContent {
                    drawContent()
                    val start = dragStart ?: return@drawWithContent
                    val selection = Rect(start, dragEnd).normalized()
                    drawRect(accent.copy(alpha = 0.18f), selection.topLeft, selection.size)
                    drawRect(accent, selection.topLeft, selection.size, style = Stroke(width = 2.dp.toPx()))
                }
        ) {
            page.tiles.forEach { tile ->
                Image(
                    tile.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Retângulo com left ≤ right e top ≤ bottom (o arrasto pode ir pra qualquer direção). */
private fun Rect.normalized() = Rect(
    minOf(left, right), minOf(top, bottom), maxOf(left, right), maxOf(top, bottom),
)

private fun Rect.toPagePixels(page: Page, scale: Float): PixelRect {
    val r = normalized()
    return PixelRect(
        (r.left * scale).toInt().coerceIn(0, page.width),
        (r.top * scale).toInt().coerceIn(0, page.height),
        (r.right * scale).toInt().coerceIn(0, page.width),
        (r.bottom * scale).toInt().coerceIn(0, page.height),
    )
}
