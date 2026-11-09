package com.paperink

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.paperink.pdf.PdfEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfEngineTextExtractionTest {

    private lateinit var context: Context
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PdfEngine.init(context)
    }

    @After
    fun tearDown() {
        tempFiles.forEach { if (it.exists()) it.delete() }
        tempFiles.clear()
    }

    /**
     * Creates a valid, well-formed PDF with standard PDF object streams and page dictionaries.
     */
    private fun createValidPdfWithStreams(pages: List<String>): File {
        val file = File.createTempFile("test_valid_pdf_${pages.size}p_", ".pdf")
        tempFiles.add(file)

        val sb = StringBuilder()
        sb.append("%PDF-1.4\n")

        val totalPages = pages.size
        // 1: Catalog
        sb.append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // 2: Pages root
        val kids = (0 until totalPages).joinToString(" ") { "${3 + it * 2} 0 R" }
        sb.append("2 0 obj\n<< /Type /Pages /Kids [$kids] /Count $totalPages >>\nendobj\n")

        for (i in 0 until totalPages) {
            val pageObjId = 3 + i * 2
            val contentObjId = 4 + i * 2
            val pageText = pages[i]

            // Page object
            sb.append("$pageObjId 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents $contentObjId 0 R >>\nendobj\n")

            // Content stream object
            val streamContent = if (pageText.isNotBlank()) {
                "BT\n/F1 12 Tf\n50 750 Td\n($pageText) Tj\nET"
            } else {
                ""
            }
            sb.append("$contentObjId 0 obj\n<< >>\nstream\n")
            sb.append(streamContent)
            sb.append("\nendstream\nendobj\n")
        }

        sb.append("%%EOF\n")

        file.writeBytes(sb.toString().toByteArray(Charsets.ISO_8859_1))
        return file
    }

    @Test
    fun testTextExtraction_singlePageStandardPdf() = runBlocking {
        val textToFind = "PaperInk PDF High-Fidelity Text Rendering"
        val pdfFile = createValidPdfWithStreams(listOf(textToFind))

        val extracted = PdfEngine.getOrExtractText(pdfFile.absolutePath, 1)
        assertEquals(1, extracted.size)

        val page0 = extracted[0]
        assertNotNull(page0)
        assertTrue(
            "Page 0 extracted text must contain '$textToFind', but got: '${page0?.fullText}'",
            page0?.fullText?.contains(textToFind) == true
        )
        assertFalse(page0?.spans.isNullOrEmpty())
        assertEquals(textToFind, page0?.spans?.firstOrNull()?.text)
    }

    @Test
    fun testTextExtraction_multiPageDocument_extractsAllPages() = runBlocking {
        val pageTexts = listOf(
            "Chapter 1: Foundations of Android Architecture",
            "Chapter 2: Jetpack Compose State Management",
            "Chapter 3: LiteRT On-Device LLM Inference",
            "Chapter 4: Native PDF Rendering and Inking",
            "Chapter 5: Security and Edge-to-Edge Design"
        )

        val pdfFile = createValidPdfWithStreams(pageTexts)

        val extracted = PdfEngine.getOrExtractText(pdfFile.absolutePath, pageTexts.size)
        assertEquals(pageTexts.size, extracted.size)

        for (i in pageTexts.indices) {
            val pageData = extracted[i]
            assertNotNull("Page $i must exist", pageData)
            assertTrue(
                "Page $i must contain '${pageTexts[i]}', but got: '${pageData?.fullText}'",
                pageData?.fullText?.contains(pageTexts[i]) == true
            )
        }
    }

    @Test
    fun testTextExtraction_large47PageDocument_extractsEverySinglePageWithoutSkipping() = runBlocking {
        val totalPages = 47
        val pageTexts = (1..totalPages).map { pageNum ->
            "Section $pageNum: Detailed Page Content Marker #$pageNum with analytics"
        }
        val pdfFile = createValidPdfWithStreams(pageTexts)

        val extracted = PdfEngine.getOrExtractText(pdfFile.absolutePath, totalPages)
        assertEquals(47, extracted.size)

        // Verify key pages across the 47-page document
        val firstPage = extracted[0]
        assertNotNull(firstPage)
        assertTrue(firstPage?.fullText?.contains("Section 1: Detailed Page Content Marker #1") == true)

        val midPage = extracted[23]
        assertNotNull(midPage)
        assertTrue(midPage?.fullText?.contains("Section 24: Detailed Page Content Marker #24") == true)

        val lastPage = extracted[46]
        assertNotNull(lastPage)
        assertTrue(lastPage?.fullText?.contains("Section 47: Detailed Page Content Marker #47") == true)

        // Ensure no pages were skipped in the 47-page traversal
        for (i in 0 until totalPages) {
            val pageData = extracted[i]
            assertNotNull("Page $i data must not be null", pageData)
            assertFalse("Page $i must not have blank text", pageData?.fullText.isNullOrBlank())
        }
    }

    @Test
    fun testTextExtraction_emptyOrScannedPage_returnsEmptyExtractedPageDataWithoutCrashing() = runBlocking {
        // Page 0 has text, Page 1 is an empty blank page
        val pdfFile = createValidPdfWithStreams(listOf("Text on page one", ""))

        val extracted = PdfEngine.getOrExtractText(pdfFile.absolutePath, 2)
        assertEquals(2, extracted.size)

        val page0 = extracted[0]
        assertTrue(page0?.fullText?.contains("Text on page one") == true)

        val page1 = extracted[1]
        assertNotNull(page1)
        assertEquals("", page1?.fullText?.trim())
        assertTrue(page1?.spans?.isEmpty() == true)
    }

    @Test
    fun testSearchInPdf_returnsAccurateSnippetsAndMatches() = runBlocking {
        val pages = listOf(
            "Quarterly audit results are pending approval.",
            "Financial audit completed in Q4 2026 with 99.8% compliance rate.",
            "Summary appendix without target keywords."
        )
        val pdfFile = createValidPdfWithStreams(pages)

        val matches = PdfEngine.searchInPdf(pdfFile.absolutePath, "compliance", 3)
        assertEquals(1, matches.size)

        val match = matches[0]
        assertEquals(1, match.pageIndex) // 0-indexed page 1 (second page)
        assertTrue(match.snippet.contains("compliance"))
    }

    @Test
    fun testSearchInPdf_caseInsensitive() = runBlocking {
        val pdfFile = createValidPdfWithStreams(listOf("Gemma 4 2B performs on-device reasoning."))

        val upperMatch = PdfEngine.searchInPdf(pdfFile.absolutePath, "GEMMA", 1)
        assertEquals(1, upperMatch.size)

        val lowerMatch = PdfEngine.searchInPdf(pdfFile.absolutePath, "gemma", 1)
        assertEquals(1, lowerMatch.size)
    }

    @Test
    fun testExtractedTextCache_returnsSameInstanceOnRepeatedCalls() = runBlocking {
        val pdfFile = createValidPdfWithStreams(listOf("Cached Page 0", "Cached Page 1"))

        val firstCall = PdfEngine.getOrExtractText(pdfFile.absolutePath, 2)
        val secondCall = PdfEngine.getOrExtractText(pdfFile.absolutePath, 2)

        // Cache hit
        assertEquals(firstCall.size, secondCall.size)
        assertEquals(firstCall[0]?.fullText, secondCall[0]?.fullText)
    }

    @Test
    fun testLargeDictionaryLookback_handlesExtendedDictionaries() = runBlocking {
        // Simulates a PDF stream preceded by an extensive dictionary (> 1500 bytes of metadata/font definitions)
        val file = File.createTempFile("test_large_dict_", ".pdf")
        tempFiles.add(file)

        val bigComments = "% large font metrics and encoding metadata padding\n".repeat(40) // ~2,000 bytes

        val content = """
            %PDF-1.4
            1 0 obj
            << /Type /Catalog /Pages 2 0 R >>
            endobj
            2 0 obj
            << /Type /Pages /Kids [3 0 R] /Count 1 >>
            endobj
            3 0 obj
            << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R >>
            endobj
            4 0 obj
            <<
            /Type /XObject
            /Subtype /Form
            /BBox [0 0 595 842]
            $bigComments
            >>
            stream
            BT
            /F1 12 Tf
            50 750 Td
            (Extended Dictionary Lookback Success) Tj
            ET
            endstream
            endobj
            %%EOF
        """.trimIndent()

        file.writeBytes(content.toByteArray(Charsets.ISO_8859_1))

        val extracted = PdfEngine.getOrExtractText(file.absolutePath, 1)
        assertEquals(1, extracted.size)
        val page0 = extracted[0]
        assertNotNull(page0)
        assertTrue(
            "Should extract text even when dictionary lookback exceeds 1KB: '${page0?.fullText}'",
            page0?.fullText?.contains("Extended Dictionary Lookback Success") == true
        )
    }
}
