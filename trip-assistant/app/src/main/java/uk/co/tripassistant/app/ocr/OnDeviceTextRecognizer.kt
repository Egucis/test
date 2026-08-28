package uk.co.tripassistant.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import uk.co.tripassistant.core.text.OcrText
import uk.co.tripassistant.core.text.Rect01
import uk.co.tripassistant.core.text.TextLine
import uk.co.tripassistant.core.text.TextNormalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * On-device text recognition (spec section 11).
 *
 * Uses ML Kit's *bundled* Latin model, declared as a normal dependency rather than downloaded on
 * first use, so a driver starting a shift on a bad connection is not waiting for a model. No frame
 * ever leaves the device (spec sections 11 and 39).
 *
 * The result is converted straight into [OcrText] — normalised text and boxes in 0..1 screen
 * coordinates — because everything downstream is device independent and unit testable.
 */
@Singleton
class OnDeviceTextRecognizer @Inject constructor() {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrText = suspendCancellableCoroutine { continuation ->
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        if (width <= 0f || height <= 0f) {
            continuation.resume(OcrText.EMPTY)
            return@suspendCancellableCoroutine
        }

        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val lines = result.textBlocks
                    .flatMap { it.lines }
                    .mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        val text = TextNormalizer.normalizeLine(line.text)
                        if (text.isEmpty()) return@mapNotNull null
                        TextLine(
                            text = text,
                            box = Rect01(
                                left = box.left / width,
                                top = box.top / height,
                                right = box.right / width,
                                bottom = box.bottom / height
                            )
                        )
                    }
                continuation.resume(OcrText(lines))
            }
            .addOnFailureListener {
                // A failed frame is not an error worth surfacing; the next frame is milliseconds
                // away. Nothing about the frame is logged.
                continuation.resume(OcrText.EMPTY)
            }
    }

    fun close() {
        runCatching { recognizer.close() }
    }
}
