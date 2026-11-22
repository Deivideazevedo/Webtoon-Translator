package com.deivide.webtoontranslator.ui.translation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deivide.webtoontranslator.domain.model.Gloss
import kotlin.math.roundToInt

/** Palavra do texto com a tradução literal dela (posições no texto). */
data class GlossSpan(val start: Int, val end: Int, val meaning: String)

private val WORD = Regex("[A-Za-z]+(?:['’][A-Za-z]+)*")

private fun norm(s: String) = s.lowercase().replace('’', '\'')

/**
 * Casa a glosa do modelo ([palavra, tradução] na ordem do original) com as palavras do texto,
 * igual ao balão do desktop: tolera o modelo pular ou juntar uma palavra (procura nas próximas
 * 3 posições); palavra sem par fica sem pontilhado.
 */
fun alignGloss(text: String, gloss: List<Gloss>): List<GlossSpan> {
    val pairs = gloss.map { norm(it.word) to it.meaning }
    val spans = mutableListOf<GlossSpan>()
    var next = 0
    for (match in WORD.findAll(text)) {
        val key = norm(match.value)
        for (k in next until minOf(next + 3, pairs.size)) {
            if (pairs[k].first == key) {
                next = k + 1
                spans += GlossSpan(match.range.first, match.range.last + 1, pairs[k].second)
                break
            }
        }
    }
    return spans
}

/**
 * Trechos do "Aprender" são pedaços do original: reaproveita a glosa das mesmas posições; se o
 * trecho não aparece literalmente, procura palavra por palavra (como no desktop).
 */
fun segmentGloss(original: String, aligned: List<GlossSpan>, segment: String): List<GlossSpan> {
    val idx = norm(original).indexOf(norm(segment))
    if (idx >= 0) {
        return aligned.filter { it.start >= idx && it.end <= idx + segment.length }
            .map { it.copy(start = it.start - idx, end = it.end - idx) }
    }
    val byWord = HashMap<String, String>()
    for (s in aligned) byWord.putIfAbsent(norm(original.substring(s.start, s.end)), s.meaning)
    return WORD.findAll(segment).mapNotNull { m ->
        byWord[norm(m.value)]?.let { GlossSpan(m.range.first, m.range.last + 1, it) }
    }.toList()
}

/** Etiqueta aberta: de qual texto ([owner]), qual palavra e onde ela está (coordenadas da raiz). */
private data class Tip(val owner: Any, val span: GlossSpan, val word: Rect)

/** Uma etiqueta por cartão: abrir numa palavra fecha a que estava aberta em outra. */
private class GlossTips {
    var open by mutableStateOf<Tip?>(null)
}

private val LocalGlossTips = staticCompositionLocalOf<GlossTips?> { null }

/**
 * Camada das etiquetas de tradução das palavras: desenha a etiqueta POR CIMA de todo o conteúdo
 * (senão a linha de baixo, desenhada depois, passa por cima dela). Envolve o cartão inteiro.
 */
@Composable
fun GlossTooltipHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val tips = remember { GlossTips() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var tipHeight by remember { mutableIntStateOf(0) }
    val gap = with(LocalDensity.current) { 4.dp.toPx() }
    Box(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        CompositionLocalProvider(LocalGlossTips provides tips) { content() }
        val tip = tips.open ?: return@Box
        val word = tip.word.translate(-origin)
        // Em cima da palavra; sem espaço em cima (topo do cartão), embaixo dela.
        val y = if (word.top - tipHeight - gap >= 0) word.top - tipHeight - gap else word.bottom + gap
        Text(
            tip.span.meaning,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            modifier = Modifier
                .offset { IntOffset(word.left.roundToInt(), y.roundToInt()) }
                .onSizeChanged { tipHeight = it.height }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Texto em inglês com pontilhado embaixo das palavras que têm tradução; tocar numa palavra mostra
 * a tradução numa etiqueta em cima dela (tocar de novo fecha; em outra palavra, troca) e, com
 * [onSpeakWord], fala a palavra (a voz do Android sai na hora). A etiqueta é desenhada pelo
 * [GlossTooltipHost] em volta do cartão.
 */
@Composable
fun GlossText(
    text: String,
    spans: List<GlossSpan>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontSize: TextUnit = 14.sp,
    onSpeakWord: ((String) -> Unit)? = null,
) {
    val tips = LocalGlossTips.current
    val owner = remember { Any() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val selected = tips?.open?.takeIf { it.owner === owner }?.span
    val dots = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Text(
        text,
        color = color,
        fontSize = fontSize,
        onTextLayout = { layout = it },
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                val l = layout ?: return@drawBehind
                val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                for (span in spans) {
                    for (rect in l.lineRects(span)) {
                        val y = rect.bottom - 1.dp.toPx()
                        drawLine(
                            if (span == selected) accent else dots,
                            Offset(rect.left, y), Offset(rect.right, y),
                            strokeWidth = 1.2.dp.toPx(), pathEffect = dash,
                        )
                    }
                }
            }
            .pointerInput(spans, tips) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    val offset = l.getOffsetForPosition(pos)
                    val hit = spans.firstOrNull { offset in it.start until it.end }
                    val opening = hit != null && hit != selected
                    tips?.open = if (opening) Tip(owner, hit!!, l.lineRects(hit).first().translate(origin)) else null
                    if (opening) onSpeakWord?.invoke(text.substring(hit!!.start, hit.end))
                }
            },
    )
}

/** Retângulo da palavra em cada linha que ela ocupa (palavra não quebra, mas por garantia). */
private fun TextLayoutResult.lineRects(span: GlossSpan): List<Rect> {
    val first = getLineForOffset(span.start)
    val last = getLineForOffset(span.end - 1)
    return (first..last).map { line ->
        val start = maxOf(span.start, getLineStart(line))
        val end = minOf(span.end, getLineEnd(line, visibleEnd = true))
        val a = getBoundingBox(start)
        val b = getBoundingBox(maxOf(start, end - 1))
        Rect(minOf(a.left, b.left), a.top, maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    }
}
