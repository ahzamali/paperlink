package com.paperink.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Serializable span data model for Moshi JSON storage in Room.
 */
data class OcrPositionedSpan(
    val text: String,
    val normLeft: Float,
    val normTop: Float,
    val normRight: Float,
    val normBottom: Float
) {
    fun toPositionedTextSpan(): PdfEngine.PositionedTextSpan {
        return PdfEngine.PositionedTextSpan(
            text = text,
            normLeft = normLeft,
            normTop = normTop,
            normRight = normRight,
            normBottom = normBottom
        )
    }

    companion object {
        fun fromPositionedTextSpan(span: PdfEngine.PositionedTextSpan): OcrPositionedSpan {
            return OcrPositionedSpan(
                text = span.text,
                normLeft = span.normLeft,
                normTop = span.normTop,
                normRight = span.normRight,
                normBottom = span.normBottom
            )
        }
    }
}

/**
 * On-device OCR Engine powered by Google ML Kit Text Recognition (Latin script).
 * Accurately extracts text and normalized bounding boxes for image-based PDFs,
 * allowing full searchability, text highlighting, and AI ingestion.
 */
object OcrEngine {

    // Lazy initialization of the Latin script TextRecognizer
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val spansListType = Types.newParameterizedType(List::class.java, OcrPositionedSpan::class.java)
    private val spansAdapter = moshi.adapter<List<OcrPositionedSpan>>(spansListType)

    fun serializeSpans(spans: List<PdfEngine.PositionedTextSpan>): String {
        val ocrSpans = spans.map { OcrPositionedSpan.fromPositionedTextSpan(it) }
        return spansAdapter.toJson(ocrSpans)
    }

    fun deserializeSpans(json: String): List<PdfEngine.PositionedTextSpan> {
        return try {
            spansAdapter.fromJson(json)?.map { it.toPositionedTextSpan() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Determines whether a PDF appears to be image-based (scanned) by inspecting
     * the native text stream length. If the document has pages but total characters
     * extracted across pages is very low (< 50 chars), it is considered image-based.
     */
    fun isImageBased(pageDataMap: Map<Int, PdfEngine.ExtractedPageData>): Boolean {
        if (pageDataMap.isEmpty()) return false
        val totalChars = pageDataMap.values.sumOf { it.fullText.trim().length }
        // If average text per page is less than 30 characters, it's almost certainly an image/scanned PDF
        return totalChars < (pageDataMap.size * 30).coerceAtLeast(50)
    }

    /**
     * Runs OCR on a single rendered page bitmap and returns [PdfEngine.ExtractedPageData]
     * with normalized bounding coordinates matching the PDF coordinate space (0f to 1f).
     */
    suspend fun recognizePage(bitmap: Bitmap): PdfEngine.ExtractedPageData = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = processImageAsync(image)

        val fullTextBuilder = StringBuilder()
        val spans = mutableListOf<PdfEngine.PositionedTextSpan>()

        val bWidth = bitmap.width.toFloat().coerceAtLeast(1f)
        val bHeight = bitmap.height.toFloat().coerceAtLeast(1f)

        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                if (lineText.isNotBlank()) {
                    if (fullTextBuilder.isNotEmpty()) fullTextBuilder.append(" ")
                    fullTextBuilder.append(lineText)

                    // Line bounding box mapped to normalized 0f..1f coordinates
                    val box: Rect? = line.boundingBox
                    if (box != null) {
                        spans.add(
                            PdfEngine.PositionedTextSpan(
                                text = lineText,
                                normLeft = (box.left.toFloat() / bWidth).coerceIn(0f, 1f),
                                normTop = (box.top.toFloat() / bHeight).coerceIn(0f, 1f),
                                normRight = (box.right.toFloat() / bWidth).coerceIn(0f, 1f),
                                normBottom = (box.bottom.toFloat() / bHeight).coerceIn(0f, 1f)
                            )
                        )
                    }
                }
            }
        }

        PdfEngine.ExtractedPageData(
            fullText = fullTextBuilder.toString().trim(),
            spans = spans
        )
    }

    private suspend fun processImageAsync(image: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    continuation.resume(text)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
}
