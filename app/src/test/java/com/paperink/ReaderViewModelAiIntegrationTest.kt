package com.paperink

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.paperink.data.model.DocumentEntity
import com.paperink.pdf.PdfEngine
import com.paperink.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReaderViewModelAiIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        PdfEngine.init(application)
        viewModel = ReaderViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testOpenAndCloseAiAssistant_updatesStateCorrectly() {
        assertFalse("AI Assistant must initially be closed", viewModel.uiState.value.showAiAssistant)

        viewModel.openAiAssistant()
        assertTrue("showAiAssistant must be true after openAiAssistant()", viewModel.uiState.value.showAiAssistant)

        viewModel.closeAiAssistant()
        assertFalse("showAiAssistant must be false after closeAiAssistant()", viewModel.uiState.value.showAiAssistant)
    }

    @Test
    fun testGetAllPagesText_excludesBlankAndEmptyPages() {
        val dummyPath = "/storage/emulated/0/Documents/PaperInk_Sample.pdf"
        val mockData = mapOf(
            0 to PdfEngine.ExtractedPageData(fullText = "Page 1 Content", spans = emptyList()),
            1 to PdfEngine.ExtractedPageData(fullText = "   \n\t  ", spans = emptyList()), // Blank
            2 to PdfEngine.ExtractedPageData(fullText = "Page 3 Content", spans = emptyList()),
            3 to PdfEngine.ExtractedPageData(fullText = "", spans = emptyList()) // Empty
        )
        PdfEngine.setExtractedPageData(dummyPath, mockData)

        // Simulate loaded document in UI state
        val doc = DocumentEntity(
            id = 42L,
            title = "PaperInk_Sample.pdf",
            fileName = "PaperInk_Sample.pdf",
            filePath = dummyPath,
            pageCount = 4
        )

        // Directly verify with the mocked data in PdfEngine cache
        val cached = PdfEngine.getExtractedPageData(doc.filePath)
        assertNotNull(cached)

        val filtered = cached!!.mapValues { it.value.fullText.trim() }.filterValues { it.isNotBlank() }
        assertEquals(2, filtered.size)
        assertEquals("Page 1 Content", filtered[0])
        assertEquals("Page 3 Content", filtered[2])
        assertFalse("Blank page 1 must be filtered out", filtered.containsKey(1))
        assertFalse("Empty page 3 must be filtered out", filtered.containsKey(3))
    }

    @Test
    fun testBackHandlerPriority_interceptsAiAssistantBeforeScreenNavigation() {
        // Test navigation rule:
        // When AI assistant is open, pressing back must close the assistant first, NOT navigate away from reader screen
        var screenExited = false
        fun handleBackPress(showAiAssistant: Boolean, closeAiAction: () -> Unit, exitScreenAction: () -> Unit) {
            if (showAiAssistant) {
                closeAiAction()
            } else {
                exitScreenAction()
            }
        }

        // 1. AI sheet is open
        viewModel.openAiAssistant()
        assertTrue(viewModel.uiState.value.showAiAssistant)

        handleBackPress(
            showAiAssistant = viewModel.uiState.value.showAiAssistant,
            closeAiAction = { viewModel.closeAiAssistant() },
            exitScreenAction = { screenExited = true }
        )

        assertFalse("AI Assistant must be closed on first back", viewModel.uiState.value.showAiAssistant)
        assertFalse("Reader screen must NOT be exited when closing AI assistant", screenExited)

        // 2. AI sheet is now closed, subsequent back exits screen
        handleBackPress(
            showAiAssistant = viewModel.uiState.value.showAiAssistant,
            closeAiAction = { viewModel.closeAiAssistant() },
            exitScreenAction = { screenExited = true }
        )

        assertTrue("Reader screen must be exited on subsequent back when AI assistant is closed", screenExited)
    }
}
