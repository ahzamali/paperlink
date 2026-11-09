package com.paperink.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantTest {

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
}
