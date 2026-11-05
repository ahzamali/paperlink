package com.example.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class ToolType {
    READ,        // Pan & pinch zoom mode
    PEN,         // Freehand handwriting/drawing
    HIGHLIGHTER, // Semi-transparent text highlighter
    TEXT,        // Tap to type note/comment
    ERASER       // Erase strokes
}

enum class ReadingMode {
    NORMAL, // Standard white page
    NIGHT,  // Inverted dark colors for night reading
    SEPIA   // Warm paper tone for eye comfort
}

data class NormalizedPoint(
    val x: Float, // 0.0f to 1.0f relative to page width
    val y: Float  // 0.0f to 1.0f relative to page height
)

data class StrokeAnnotation(
    val id: Long = java.util.UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE,
    val isHighlighter: Boolean = false,
    val colorArgb: Int = Color(0xFFFF5722).toArgb(),
    val strokeWidthDp: Float = 3f,
    val points: List<NormalizedPoint> = emptyList()
) {
    fun toSerializedString(): String {
        return points.joinToString(";") { "${"%.4f".format(it.x)},${"%.4f".format(it.y)}" }
    }

    companion object {
        fun fromSerializedString(
            id: Long,
            isHighlighter: Boolean,
            colorArgb: Int,
            strokeWidthDp: Float,
            data: String
        ): StrokeAnnotation {
            val pts = if (data.isBlank()) emptyList() else {
                data.split(";").mapNotNull { pair ->
                    val parts = pair.split(",")
                    if (parts.size == 2) {
                        val x = parts[0].toFloatOrNull()
                        val y = parts[1].toFloatOrNull()
                        if (x != null && y != null) NormalizedPoint(x, y) else null
                    } else null
                }
            }
            return StrokeAnnotation(
                id = id,
                isHighlighter = isHighlighter,
                colorArgb = colorArgb,
                strokeWidthDp = strokeWidthDp,
                points = pts
            )
        }
    }
}

data class TextAnnotation(
    val id: Long = System.currentTimeMillis() + (0..9999).random(),
    val text: String,
    val x: Float, // 0.0 to 1.0
    val y: Float, // 0.0 to 1.0
    val colorArgb: Int = Color.Black.toArgb(),
    val fontSizeSp: Float = 14f
)

data class SearchHighlightRect(
    val left: Float,   // 0.0f to 1.0f relative to page width
    val top: Float,    // 0.0f to 1.0f relative to page height
    val right: Float,  // 0.0f to 1.0f relative to page width
    val bottom: Float  // 0.0f to 1.0f relative to page height
)

data class PdfSearchMatch(
    val matchId: Long = System.currentTimeMillis() + (0..99999).random(),
    val pageIndex: Int,
    val snippet: String,
    val matchTerm: String,
    val source: String = "Document Text",
    val rects: List<SearchHighlightRect> = emptyList()
)

enum class CompressionLevel(
    val title: String,
    val description: String,
    val targetWidth: Int,
    val jpegQuality: Int,
    val estReduction: String,
    val estimatedBytesPerPage: Long
) {
    HIGH(
        title = "Extreme Compression",
        description = "Smallest size, ideal for email & messaging",
        targetWidth = 850,
        jpegQuality = 60,
        estReduction = "60-80%",
        estimatedBytesPerPage = 95 * 1024L
    ),
    BALANCED(
        title = "Balanced (Recommended)",
        description = "Great balance of crisp text and small file size",
        targetWidth = 1200,
        jpegQuality = 80,
        estReduction = "40-60%",
        estimatedBytesPerPage = 210 * 1024L
    ),
    LIGHT(
        title = "Light Compression",
        description = "High resolution preserved with minor size reduction",
        targetWidth = 1600,
        jpegQuality = 90,
        estReduction = "20-35%",
        estimatedBytesPerPage = 420 * 1024L
    )
}

data class CompressionEstimate(
    val estimatedSizeBytes: Long,
    val estimatedSavingsPercentage: Int,
    val willLikelyIncrease: Boolean,
    val recommendation: String
)

data class CompressionResult(
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val savedPercentage: Int,
    val outputFile: java.io.File,
    val isLarger: Boolean = compressedSizeBytes > originalSizeBytes
)

data class MergeResult(
    val title: String,
    val outputFile: java.io.File,
    val totalPageCount: Int,
    val outputSizeBytes: Long,
    val mergedSourceCount: Int
)
