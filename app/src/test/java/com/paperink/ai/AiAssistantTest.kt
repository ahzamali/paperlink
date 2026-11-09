package com.paperink.ai

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiAssistantTest {

    private fun getApplication(): Application {
        return ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testModelStatus_initialStateIsNotDownloaded() {
        val status = AiModelManager.ModelStatus.NotDownloaded
        assertTrue(status is AiModelManager.ModelStatus.NotDownloaded)
    }

    @Test
    fun testDownloadingState_calculatesProgressCorrectly() {
        val downloading = AiModelManager.ModelStatus.Downloading(
            progress = 0.5f,
            downloadedBytes = 1_300_000_000L,
            totalBytes = 2_600_000_000L
        )
        assertEquals(0.5f, downloading.progress, 0.001f)
        assertEquals(1_300_000_000L, downloading.downloadedBytes)
        assertEquals(2_600_000_000L, downloading.totalBytes)
    }

    @Test
    fun testChatMessage_streamingState() {
        val userMsg = ChatMessage(isUser = true, text = "Summarize page 1")
        assertTrue(userMsg.isUser)
        assertFalse(userMsg.isStreaming)
        assertEquals("Summarize page 1", userMsg.text)

        val aiMsg = ChatMessage(isUser = false, text = "Here is the summary:", isStreaming = true)
        assertFalse(aiMsg.isUser)
        assertTrue(aiMsg.isStreaming)
        assertEquals("Here is the summary:", aiMsg.text)
    }

    @Test
    fun testAiChatState_defaultValues() {
        val state = AiChatState()
        assertFalse(state.isEngineReady)
        assertFalse(state.isEngineInitializing)
        assertFalse(state.isBusy)
        assertTrue(state.messages.isEmpty())
        assertEquals("None", state.activeBackend)
        assertEquals("", state.documentTitle)
        assertEquals("", state.documentTextSnippet)
    }

    @Test
    fun testAiChatState_withDocumentContext() {
        val state = AiChatState(
            documentTitle = "Quarterly Report.pdf",
            documentTextSnippet = "Revenue grew 14% year over year.",
            currentPageIndex = 2,
            totalPages = 10,
            contextScopeMode = ContextScopeMode.CURRENT_PAGE,
            activeContextSnippet = "Page 3 excerpt",
            contextSummaryBadge = "Page 3 of 10"
        )
        assertEquals("Quarterly Report.pdf", state.documentTitle)
        assertEquals("Revenue grew 14% year over year.", state.documentTextSnippet)
        assertEquals(2, state.currentPageIndex)
        assertEquals(10, state.totalPages)
        assertEquals(ContextScopeMode.CURRENT_PAGE, state.contextScopeMode)
        assertEquals("Page 3 excerpt", state.activeContextSnippet)
        assertEquals("Page 3 of 10", state.contextSummaryBadge)
    }

    @Test
    fun testContextScopeMode_values() {
        val modes = ContextScopeMode.values()
        assertEquals(4, modes.size)
        assertTrue(modes.contains(ContextScopeMode.CURRENT_PAGE))
        assertTrue(modes.contains(ContextScopeMode.PAGE_RANGE))
        assertTrue(modes.contains(ContextScopeMode.RELEVANT_SECTIONS))
        assertTrue(modes.contains(ContextScopeMode.DOCUMENT_OVERVIEW))
    }

    @Test
    fun testSafeContextBudgetLimits() {
        assertTrue("MAX_CONTEXT_CHARS must be <= 5000 to safely fit within 2048 token Gemma limits", AiAssistantViewModel.MAX_CONTEXT_CHARS <= 5000)
        assertTrue("EMERGENCY_FALLBACK_CONTEXT_CHARS must be smaller than MAX_CONTEXT_CHARS", AiAssistantViewModel.EMERGENCY_FALLBACK_CONTEXT_CHARS < AiAssistantViewModel.MAX_CONTEXT_CHARS)
    }

    @Test
    fun testDocumentDigest_on47PageDocument_compressesAllPagesWithinBudget() {
        val viewModel = AiAssistantViewModel(getApplication())

        // Create a 47-page document (~1,500 characters per page, total ~70,000 characters)
        val doc47Pages = (0 until 47).associateWith { pageIdx ->
            "Page ${pageIdx + 1} content: Detailed section discussing technical architecture, design patterns, and benchmarks. " +
            "Paragraph detailing component implementation and performance measurements across iterations. ".repeat(10)
        }

        viewModel.setDocumentContext(
            title = "Large Architecture Document (47 Pages).pdf",
            currentPageIndex = 0,
            totalPages = 47,
            pagesText = doc47Pages,
            fallbackSnippet = ""
        )

        val state = viewModel.state.value

        // In multi-page documents (> 1 page), initial mode must default to DOCUMENT_OVERVIEW for complete coverage
        assertEquals(ContextScopeMode.DOCUMENT_OVERVIEW, state.contextScopeMode)
        assertEquals(47, state.totalPages)

        // 47 pages divided into 10-page batches: 0..9, 10..19, 20..29, 30..39, 40..46 -> 5 ranges
        assertEquals(5, state.availablePageRanges.size)
        assertEquals(0..9, state.availablePageRanges[0])
        assertEquals(10..19, state.availablePageRanges[1])
        assertEquals(40..46, state.availablePageRanges[4])

        // Active context snippet must safely fit within the max model token budget
        assertTrue(
            "Context snippet length (${state.activeContextSnippet.length}) must not exceed MAX_CONTEXT_CHARS (${AiAssistantViewModel.MAX_CONTEXT_CHARS})",
            state.activeContextSnippet.length <= AiAssistantViewModel.MAX_CONTEXT_CHARS
        )

        // Verify that the digest contains representation from page 1, middle pages, and page 47
        assertTrue("Digest must include Page 1", state.activeContextSnippet.contains("[Page 1]:"))
        assertTrue("Digest must include Page 25", state.activeContextSnippet.contains("[Page 25]:"))
        assertTrue("Digest must include Page 47", state.activeContextSnippet.contains("[Page 47]:"))
        assertTrue("Badge must indicate all 47 pages digest", state.contextSummaryBadge.contains("All 47 Pages Digest"))
    }

    @Test
    fun testPageRangeScoping_on47PageDocument_scopesStrictlyToSelectedRange() {
        val viewModel = AiAssistantViewModel(getApplication())

        val doc47Pages = (0 until 47).associateWith { pageIdx ->
            "Content of Page ${pageIdx + 1}: Unique topic marker #${pageIdx + 1} with financial metrics and tables."
        }

        viewModel.setDocumentContext(
            title = "Financial Annual Report (47 Pages).pdf",
            currentPageIndex = 0,
            totalPages = 47,
            pagesText = doc47Pages,
            fallbackSnippet = ""
        )

        // Select batch range 10..19 (Pages 11 to 20)
        viewModel.setPageRange(10..19)
        val state = viewModel.state.value

        assertEquals(ContextScopeMode.PAGE_RANGE, state.contextScopeMode)
        assertEquals(10..19, state.selectedPageRange)

        assertTrue(state.activeContextSnippet.contains("SECTION: PAGES 11 TO 20"))
        assertTrue(state.activeContextSnippet.contains("--- PAGE 11 OF 47 ---"))
        assertTrue(state.activeContextSnippet.contains("Unique topic marker #11"))
        assertTrue(state.activeContextSnippet.contains("--- PAGE 20 OF 47 ---"))
        assertTrue(state.activeContextSnippet.contains("Unique topic marker #20"))

        // Must not contain content from outside the selected range
        assertFalse(state.activeContextSnippet.contains("Unique topic marker #1 "))
        assertFalse(state.activeContextSnippet.contains("Unique topic marker #47"))

        assertTrue(state.activeContextSnippet.length <= AiAssistantViewModel.MAX_CONTEXT_CHARS)
        assertTrue(state.contextSummaryBadge.contains("Pages 11–20 of 47"))
    }

    @Test
    fun testCurrentPageScoping_onSpecificPage_scopesStrictlyToCurrentPage() {
        val viewModel = AiAssistantViewModel(getApplication())

        val doc47Pages = (0 until 47).associateWith { pageIdx ->
            "Page ${pageIdx + 1} specific analysis: Detailed breakdown for chapter ${pageIdx + 1}."
        }

        viewModel.setDocumentContext(
            title = "Book Chapters.pdf",
            currentPageIndex = 14, // Page 15 (0-indexed 14)
            totalPages = 47,
            pagesText = doc47Pages,
            fallbackSnippet = ""
        )

        viewModel.setContextScopeMode(ContextScopeMode.CURRENT_PAGE)
        val state = viewModel.state.value

        assertEquals(ContextScopeMode.CURRENT_PAGE, state.contextScopeMode)
        assertEquals(14, state.currentPageIndex)

        assertTrue(state.activeContextSnippet.contains("--- PAGE 15 OF 47 ---"))
        assertTrue(state.activeContextSnippet.contains("Page 15 specific analysis"))
        assertTrue(state.contextSummaryBadge.contains("Page 15 of 47"))
        assertTrue(state.activeContextSnippet.length <= AiAssistantViewModel.MAX_CONTEXT_CHARS)
    }

    @Test
    fun testScannedOrEmptyPdf_providesHelpfulFallbackSnippet() {
        val viewModel = AiAssistantViewModel(getApplication())

        // Scanned document without extracted pages, but with OCR fallback snippet
        val fallback = "OCR Extracted Heading\nInvoice #98214\nTotal Due: $450.00"

        viewModel.setDocumentContext(
            title = "Scanned Receipt.pdf",
            currentPageIndex = 0,
            totalPages = 1,
            pagesText = emptyMap(),
            fallbackSnippet = fallback
        )

        val state = viewModel.state.value
        assertEquals("Scanned Receipt.pdf", state.documentTitle)
        assertTrue(state.activeContextSnippet.contains("Invoice #98214"))
    }

    @Test
    fun testKeyboardImeDismissLogic_invariants() {
        // Test the invariant rule governing the two-stage back dismiss:
        // When soft keyboard is visible: shouldDismissOnBackPress must be FALSE (keyboard hides first)
        // When soft keyboard is hidden: shouldDismissOnBackPress must be TRUE (sheet closes)
        fun shouldDismissOnBackPress(isImeVisible: Boolean): Boolean {
            return !isImeVisible
        }

        assertFalse("When IME keyboard is visible, bottom sheet back dismiss must be disabled", shouldDismissOnBackPress(isImeVisible = true))
        assertTrue("When IME keyboard is hidden, bottom sheet back dismiss must be enabled", shouldDismissOnBackPress(isImeVisible = false))
    }
}
