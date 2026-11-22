package com.deivide.webtoontranslator.data.translation

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * OCR do Google (ML Kit, modelo embutido no app, roda no aparelho): devolve as linhas com a
 * posição e a confiança de cada uma; o agrupamento em balões é o mesmo do ocrs (ponte Rust).
 */
class GoogleOcr {
    /** Uma linha lida, com a caixa (pixels) e a confiança (0–1) do ML Kit. */
    data class Line(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val confidence: Float)

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun lines(image: Bitmap): List<Line> = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(image, 0))
            .addOnSuccessListener { text ->
                continuation.resume(
                    text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        Line(line.text.trim(), box.left, box.top, box.right, box.bottom, line.confidence)
                    }
                )
            }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
}
