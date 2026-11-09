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
            documentTextSnippet = "Revenue grew 14% year over year."
        )
        assertEquals("Quarterly Report.pdf", state.documentTitle)
        assertEquals("Revenue grew 14% year over year.", state.documentTextSnippet)
    }
}
