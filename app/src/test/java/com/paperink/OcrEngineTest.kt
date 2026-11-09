package com.paperink

import com.paperink.pdf.OcrEngine
import com.paperink.pdf.PdfEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrEngineTest {

    @Test
    fun testIsImageBased_withScannedOrEmptyPdf_returnsTrue() {
        val emptyPdfMap = mapOf(
            0 to PdfEngine.ExtractedPageData(fullText = "", spans = emptyList()),
            1 to PdfEngine.ExtractedPageData(fullText = "  \n  ", spans = emptyList())
        )
        assertTrue(OcrEngine.isImageBased(emptyPdfMap))

        val scannedArtifactMap = mapOf(
            0 to PdfEngine.ExtractedPageData(fullText = "Page 1", spans = emptyList()),
            1 to PdfEngine.ExtractedPageData(fullText = "Scan 2026", spans = emptyList())
        )
        assertTrue(OcrEngine.isImageBased(scannedArtifactMap))
    }

    @Test
    fun testIsImageBased_withTextRichPdf_returnsFalse() {
        val textPdfMap = mapOf(
            0 to PdfEngine.ExtractedPageData(
                fullText = "Jetpack Compose is Android's recommended modern toolkit for building native UI. It simplifies and accelerates UI development on Android.",
                spans = emptyList()
            ),
            1 to PdfEngine.ExtractedPageData(
                fullText = "Google ML Kit Text Recognition enables on-device machine learning for detecting and extracting text from image based documents.",
                spans = emptyList()
            )
        )
        assertFalse(OcrEngine.isImageBased(textPdfMap))
    }

    @Test
    fun testSpanSerializationAndDeserialization_isLossless() {
        val spans = listOf(
            PdfEngine.PositionedTextSpan(
                text = "PaperInk",
                normLeft = 0.12f,
                normTop = 0.25f,
                normRight = 0.45f,
                normBottom = 0.28f
            ),
            PdfEngine.PositionedTextSpan(
                text = "Mobile PDF Editor & Reader",
                normLeft = 0.12f,
                normTop = 0.30f,
                normRight = 0.85f,
                normBottom = 0.34f
            )
        )

        val json = OcrEngine.serializeSpans(spans)
        val deserialized = OcrEngine.deserializeSpans(json)

        assertEquals(spans.size, deserialized.size)
        assertEquals("PaperInk", deserialized[0].text)
        assertEquals(0.12f, deserialized[0].normLeft, 0.001f)
        assertEquals(0.25f, deserialized[0].normTop, 0.001f)
        assertEquals(0.45f, deserialized[0].normRight, 0.001f)
        assertEquals(0.28f, deserialized[0].normBottom, 0.001f)

        assertEquals("Mobile PDF Editor & Reader", deserialized[1].text)
        assertEquals(0.85f, deserialized[1].normRight, 0.001f)
    }

    @Test
    fun testDeserializeInvalidJson_returnsEmptyListSafely() {
        val invalid = "{ bad json string }"
        val result = OcrEngine.deserializeSpans(invalid)
        assertTrue(result.isEmpty())
    }

    @Test
    fun testSpanCoordinateClamping() {
        val spans = listOf(
            PdfEngine.PositionedTextSpan(
                text = "Boundary Check",
                normLeft = 0f,
                normTop = 0f,
                normRight = 1f,
                normBottom = 1f
            )
        )
        val json = OcrEngine.serializeSpans(spans)
        val deserialized = OcrEngine.deserializeSpans(json)
        assertEquals(1, deserialized.size)
        assertEquals(0f, deserialized[0].normLeft, 0.001f)
        assertEquals(1f, deserialized[0].normRight, 0.001f)
    }
}
