package com.paperink.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.paperink.data.local.PaperInkDatabase
import com.paperink.data.model.CompressionLevel
import com.paperink.data.model.CompressionResult
import com.paperink.data.model.DocumentEntity
import com.paperink.data.model.PdfSearchMatch
import com.paperink.data.model.ReadingMode
import com.paperink.data.model.StrokeAnnotation
import com.paperink.data.model.TextAnnotation
import com.paperink.data.model.ToolType
import com.paperink.data.repository.DocumentRepository
import com.paperink.pdf.OcrEngine
import com.paperink.pdf.PdfEngine
import com.paperink.pdf.PdfPrintAdapter
import com.paperink.ui.theme.AnnotationBlack
import com.paperink.ui.theme.AnnotationBlue
import com.paperink.ui.theme.AnnotationYellow
import com.paperink.ui.components.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import com.paperink.util.ShareHelper

data class ReaderUiState(
    val document: DocumentEntity? = null,
    val currentPageIndex: Int = 0,
    val totalPages: Int = 1,
    val isVerticalScroll: Boolean = true, // Default vertical continuous scrolling
    val isPageLoading: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.NORMAL,
    val activeTool: ToolType = ToolType.READ,
    val penColor: Color = AnnotationBlack, // Customizable pen ink color
    val highlighterColor: Color = AnnotationYellow,
    val strokeWidth: Float = 4f, // 4dp for pen, 18dp for highlighter
    val allPageStrokes: Map<Int, List<StrokeAnnotation>> = emptyMap(),
    val allPageTexts: Map<Int, List<TextAnnotation>> = emptyMap(),
    val hasUnsavedChanges: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isExporting: Boolean = false,
    val userMessage: String? = null,
    // Search in PDF
    val isSearchActive: Boolean = false,
    val pdfSearchQuery: String = "",
    val pdfSearchResults: List<PdfSearchMatch> = emptyList(),
    val currentSearchMatchIndex: Int = 0,
    val isSearchingInPdf: Boolean = false,
    // OCR status
    val isOcrScanning: Boolean = false,
    val ocrProgress: Float = 0f,
    val ocrCurrentPage: Int = 0,
    val showOcrPrompt: Boolean = false,
    // AI Assistant (Gemma 4 2B)
    val showAiAssistant: Boolean = false,
    val documentText: String = "",
    // Compression
    val isCompressing: Boolean = false,
    val compressionResult: CompressionResult? = null,
    val showCompressDialog: Boolean = false,
    // Password protection
    val isPasswordRequired: Boolean = false,
    val passwordErrorMessage: String? = null
) {
    val activeColor: Color
        get() = if (activeTool == ToolType.HIGHLIGHTER) highlighterColor else penColor

    val currentMatch: PdfSearchMatch?
        get() = pdfSearchResults.getOrNull(currentSearchMatchIndex)

    fun getStrokesForPage(pageIndex: Int): List<StrokeAnnotation> =
        allPageStrokes[pageIndex] ?: emptyList()

    fun getTextsForPage(pageIndex: Int): List<TextAnnotation> =
        allPageTexts[pageIndex] ?: emptyList()

    fun getSearchMatchesForPage(pageIndex: Int): List<PdfSearchMatch> =
        pdfSearchResults.filter { it.pageIndex == pageIndex }
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // Undo / Redo history stacks (stores snapshots of allPageStrokes capped at MAX_STACK_SIZE)
    private val undoStack = mutableListOf<Map<Int, List<StrokeAnnotation>>>()
    private val redoStack = mutableListOf<Map<Int, List<StrokeAnnotation>>>()
    private var searchJob: Job? = null
    private var ocrJob: Job? = null

    companion object {
        private const val MAX_STACK_SIZE = 50
    }

    private fun pushUndo(state: Map<Int, List<StrokeAnnotation>>) {
        undoStack.add(state)
        if (undoStack.size > MAX_STACK_SIZE) {
            undoStack.removeAt(0)
        }
    }

    private fun pushRedo(state: Map<Int, List<StrokeAnnotation>>) {
        redoStack.add(state)
        if (redoStack.size > MAX_STACK_SIZE) {
            redoStack.removeAt(0)
        }
    }

    init {
        PdfEngine.init(application)
        val db = PaperInkDatabase.getDatabase(application)
        repository = DocumentRepository(db.paperInkDao(), application)
    }

    fun loadDocument(documentId: Long) {
        viewModelScope.launch {
            val doc = repository.getDocumentById(documentId) ?: return@launch
            _uiState.update {
                it.copy(
                    document = doc,
                    currentPageIndex = doc.currentPage.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0)),
                    totalPages = doc.pageCount.coerceAtLeast(1),
                    isPageLoading = true,
                    showOcrPrompt = doc.isImageBased && doc.ocrStatus != "DONE"
                )
            }

            // Quick non-blocking background check for cached OCR or text extraction
            checkAndLoadOcrCache(doc)

            // Check if password protected
            val isProtected = withContext(Dispatchers.IO) {
                doc.tags.contains("Protected") ||
                    PdfEngine.checkPdfProtection(doc.filePath) == PdfEngine.PdfProtectionStatus.PASSWORD_REQUIRED
            }

            if (isProtected) {
                _uiState.update {
                    it.copy(
                        isPasswordRequired = true,
                        passwordErrorMessage = null,
                        isPageLoading = false
                    )
                }
                return@launch
            }

            // Load all annotations for this document across all pages
            val allEntities = repository.getAllAnnotationsForDocument(doc.id)
            val strokeMap = mutableMapOf<Int, MutableList<StrokeAnnotation>>()
            val textMap = mutableMapOf<Int, MutableList<TextAnnotation>>()

            for (entity in allEntities) {
                if (!entity.textContent.isNullOrBlank() && entity.textX != null && entity.textY != null) {
                    val list = textMap.getOrPut(entity.pageIndex) { mutableListOf() }
                    list.add(
                        TextAnnotation(
                            id = entity.id,
                            text = entity.textContent,
                            x = entity.textX,
                            y = entity.textY,
                            colorArgb = entity.colorArgb,
                            fontSizeSp = entity.strokeWidth
                        )
                    )
                } else if (entity.pointsData.isNotBlank()) {
                    val list = strokeMap.getOrPut(entity.pageIndex) { mutableListOf() }
                    list.add(
                        StrokeAnnotation.fromSerializedString(
                            id = entity.id,
                            isHighlighter = entity.isHighlighter,
                            colorArgb = entity.colorArgb,
                            strokeWidthDp = entity.strokeWidth,
                            data = entity.pointsData
                        )
                    )
                }
            }

            undoStack.clear()
            redoStack.clear()

            _uiState.update {
                it.copy(
                    allPageStrokes = strokeMap,
                    allPageTexts = textMap,
                    isPageLoading = false,
                    hasUnsavedChanges = false,
                    canUndo = false,
                    canRedo = false
                )
            }
        }
    }

    private suspend fun checkAndLoadOcrCache(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        val ocrCaches = repository.getOcrCacheForDocument(doc.id)
        if (ocrCaches.isNotEmpty()) {
            val pageDataMap = ocrCaches.associate { cache ->
                cache.pageIndex to PdfEngine.ExtractedPageData(
                    fullText = cache.fullText,
                    spans = OcrEngine.deserializeSpans(cache.spansJson)
                )
            }
            PdfEngine.setExtractedPageData(doc.filePath, pageDataMap)
            val fullText = pageDataMap.toSortedMap().values
                .map { it.fullText.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            _uiState.update { it.copy(showOcrPrompt = false, documentText = fullText) }
        } else {
            // Check native text streams
            val extracted = PdfEngine.getOrExtractText(doc.filePath, doc.pageCount)
            val isImage = OcrEngine.isImageBased(extracted)
            val fullText = extracted.toSortedMap().values
                .map { it.fullText.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            if (isImage) {
                repository.updateDocumentOcrStatus(doc.id, isImageBased = true, ocrStatus = doc.ocrStatus)
                _uiState.update { it.copy(showOcrPrompt = doc.ocrStatus != "DONE", documentText = fullText) }
            } else {
                _uiState.update { it.copy(documentText = fullText) }
            }
        }
    }

    fun dismissOcrPrompt() {
        _uiState.update { it.copy(showOcrPrompt = false) }
    }

    fun runOcr() {
        val doc = _uiState.value.document ?: return
        if (_uiState.value.isOcrScanning) return
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOcrScanning = true,
                    ocrProgress = 0f,
                    ocrCurrentPage = 0,
                    showOcrPrompt = true
                )
            }
            try {
                val totalPages = doc.pageCount
                val pageDataMap = mutableMapOf<Int, PdfEngine.ExtractedPageData>()

                withContext(Dispatchers.IO) {
                    for (pageIdx in 0 until totalPages) {
                        _uiState.update {
                            it.copy(
                                ocrCurrentPage = pageIdx + 1,
                                ocrProgress = (pageIdx + 1).toFloat() / totalPages.toFloat()
                            )
                        }

                        val bitmap = PdfEngine.renderPageBitmapForOcr(doc.filePath, pageIdx, targetWidth = 1080)
                        if (bitmap != null) {
                            val extractedPage = OcrEngine.recognizePage(bitmap)
                            bitmap.recycle()

                            pageDataMap[pageIdx] = extractedPage

                            repository.saveOcrCache(
                                documentId = doc.id,
                                pageIndex = pageIdx,
                                fullText = extractedPage.fullText,
                                spans = extractedPage.spans
                            )
                        }
                    }

                    // Create new standardized searchable text-based PDF
                    val app = getApplication<Application>()
                    val searchableDir = File(app.filesDir, "searchable_pdfs").apply { if (!exists()) mkdirs() }
                    val baseName = File(doc.filePath).nameWithoutExtension
                    val searchableFile = File(searchableDir, "${baseName}_searchable.pdf")
                    val isCreated = PdfEngine.createSearchablePdf(doc.filePath, searchableFile, pageDataMap)

                    val finalPath = if (isCreated && searchableFile.exists() && searchableFile.length() > 0) {
                        searchableFile.absolutePath
                    } else {
                        doc.filePath
                    }

                    PdfEngine.setExtractedPageData(finalPath, pageDataMap)
                    val fullText = pageDataMap.toSortedMap().values
                        .map { it.fullText.trim() }
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")

                    val updatedDoc = doc.copy(
                        filePath = finalPath,
                        isImageBased = false,
                        ocrStatus = "DONE"
                    )
                    repository.updateDocument(updatedDoc)
                    PdfEngine.clearCache()

                    _uiState.update {
                        it.copy(
                            document = updatedDoc,
                            documentText = fullText,
                            isOcrScanning = false,
                            showOcrPrompt = false,
                            userMessage = if (isCreated) "OCR complete! Searchable text PDF created." else "OCR complete! Search enabled on document."
                        )
                    }
                }

                if (_uiState.value.pdfSearchQuery.isNotBlank()) {
                    performSearch(_uiState.value.pdfSearchQuery)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isOcrScanning = false,
                        userMessage = "OCR failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun openAiAssistant() {
        _uiState.update { it.copy(showAiAssistant = true) }
    }

    fun closeAiAssistant() {
        _uiState.update { it.copy(showAiAssistant = false) }
    }

    fun getDocumentTextSnippet(): String {
        val currentText = _uiState.value.documentText
        if (currentText.isNotBlank()) return currentText

        val doc = _uiState.value.document ?: return ""
        val cached = PdfEngine.getExtractedPageData(doc.filePath)
        if (!cached.isNullOrEmpty()) {
            return cached.toSortedMap().values
                .map { it.fullText.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
        }
        return ""
    }

    /**
     * Unlocks document with user password, decrypts session or writes unlocked copy,
     * and discards the password immediately from memory.
     */
    fun unlockDocumentWithPassword(password: String) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isPageLoading = true, passwordErrorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                PdfEngine.openWithPassword(doc.filePath, password)
            }
            // Discard password immediately:
            // Notice: 'password' is not stored or assigned to any property or state.
            when (result) {
                is PdfEngine.OpenPasswordResult.Success -> {
                    val newPath = result.unlockedFilePath ?: doc.filePath
                    val cleanTags = doc.tags.replace("Protected", "").trim().trim(',').trim()
                    val updatedDoc = doc.copy(
                        filePath = newPath,
                        pageCount = result.pageCount,
                        tags = cleanTags
                    )
                    _uiState.update {
                        it.copy(
                            document = updatedDoc,
                            totalPages = result.pageCount.coerceAtLeast(1),
                            isPasswordRequired = false,
                            passwordErrorMessage = null,
                            isPageLoading = false,
                            userMessage = "Document unlocked"
                        )
                    }
                    repository.updateDocument(updatedDoc)
                    getPageBitmap(0)
                }
                is PdfEngine.OpenPasswordResult.IncorrectPassword -> {
                    _uiState.update {
                        it.copy(
                            isPasswordRequired = true,
                            passwordErrorMessage = "Incorrect password. Please try again.",
                            isPageLoading = false
                        )
                    }
                }
                is PdfEngine.OpenPasswordResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isPasswordRequired = true,
                            passwordErrorMessage = result.message,
                            isPageLoading = false
                        )
                    }
                }
            }
        }
    }

    suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        val doc = _uiState.value.document ?: return null
        return PdfEngine.renderPageBitmap(doc.filePath, pageIndex)
    }

    fun updateCurrentVisiblePage(pageIndex: Int) {
        val doc = _uiState.value.document ?: return
        val validPage = pageIndex.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
        if (_uiState.value.currentPageIndex != validPage) {
            _uiState.update { it.copy(currentPageIndex = validPage) }
            viewModelScope.launch {
                repository.updateReadingPosition(doc.id, validPage)
            }
        }
    }

    fun toggleScrollMode() {
        _uiState.update { it.copy(isVerticalScroll = !it.isVerticalScroll) }
    }

    fun setReadingMode(mode: ReadingMode) {
        _uiState.update { it.copy(readingMode = mode) }
    }

    fun cycleReadingMode() {
        val nextMode = when (_uiState.value.readingMode) {
            ReadingMode.NORMAL -> ReadingMode.NIGHT
            ReadingMode.NIGHT -> ReadingMode.SEPIA
            ReadingMode.SEPIA -> ReadingMode.NORMAL
        }
        _uiState.update { it.copy(readingMode = nextMode) }
    }

    fun selectTool(tool: ToolType) {
        val newWidth = when (tool) {
            ToolType.HIGHLIGHTER -> 18f
            ToolType.PEN -> 4f
            ToolType.ERASER -> 20f
            else -> _uiState.value.strokeWidth
        }
        _uiState.update {
            it.copy(
                activeTool = tool,
                strokeWidth = newWidth
            )
        }
    }

    /**
     * Change pen ink color specifically
     */
    fun setPenColor(color: Color) {
        _uiState.update { it.copy(penColor = color) }
    }

    /**
     * Change highlighter ink color specifically
     */
    fun setHighlighterColor(color: Color) {
        _uiState.update { it.copy(highlighterColor = color) }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update { it.copy(strokeWidth = width) }
    }

    fun addStroke(pageIndex: Int, stroke: StrokeAnnotation) {
        val currentAll = _uiState.value.allPageStrokes
        pushUndo(currentAll)
        redoStack.clear()

        val pageList = currentAll[pageIndex] ?: emptyList()
        val updatedPageList = pageList + stroke
        val updatedAll = currentAll + (pageIndex to updatedPageList)

        _uiState.update {
            it.copy(
                allPageStrokes = updatedAll,
                hasUnsavedChanges = true,
                canUndo = true,
                canRedo = false
            )
        }
    }

    fun deleteStroke(pageIndex: Int, strokeId: Long) {
        val currentAll = _uiState.value.allPageStrokes
        pushUndo(currentAll)
        redoStack.clear()

        val pageList = currentAll[pageIndex] ?: emptyList()
        val updatedPageList = pageList.filterNot { it.id == strokeId }
        val updatedAll = currentAll + (pageIndex to updatedPageList)

        _uiState.update {
            it.copy(
                allPageStrokes = updatedAll,
                hasUnsavedChanges = true,
                canUndo = true,
                canRedo = false
            )
        }
    }

    fun addTextNote(pageIndex: Int, note: TextAnnotation) {
        val currentAll = _uiState.value.allPageTexts
        val pageList = currentAll[pageIndex] ?: emptyList()
        val updatedPageList = pageList + note
        val updatedAll = currentAll + (pageIndex to updatedPageList)

        _uiState.update {
            it.copy(
                allPageTexts = updatedAll,
                hasUnsavedChanges = true
            )
        }
    }

    fun deleteTextNote(pageIndex: Int, noteId: Long) {
        val currentAll = _uiState.value.allPageTexts
        val pageList = currentAll[pageIndex] ?: emptyList()
        val updatedPageList = pageList.filterNot { it.id == noteId }
        val updatedAll = currentAll + (pageIndex to updatedPageList)

        _uiState.update {
            it.copy(
                allPageTexts = updatedAll,
                hasUnsavedChanges = true
            )
        }
    }

    fun updateTextNote(pageIndex: Int, note: TextAnnotation) {
        val currentAll = _uiState.value.allPageTexts
        val pageList = currentAll[pageIndex] ?: emptyList()
        val updatedPageList = pageList.map { if (it.id == note.id) note else it }
        val updatedAll = currentAll + (pageIndex to updatedPageList)

        _uiState.update {
            it.copy(
                allPageTexts = updatedAll,
                hasUnsavedChanges = true
            )
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.lastIndex)
            pushRedo(_uiState.value.allPageStrokes)
            _uiState.update {
                it.copy(
                    allPageStrokes = previous,
                    hasUnsavedChanges = true,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = true
                )
            }
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            pushUndo(_uiState.value.allPageStrokes)
            _uiState.update {
                it.copy(
                    allPageStrokes = next,
                    hasUnsavedChanges = true,
                    canUndo = true,
                    canRedo = redoStack.isNotEmpty()
                )
            }
        }
    }

    fun clearPageAnnotations(pageIndex: Int) {
        val currentAll = _uiState.value.allPageStrokes
        pushUndo(currentAll)
        val updatedStrokes = currentAll + (pageIndex to emptyList())
        val updatedTexts = _uiState.value.allPageTexts + (pageIndex to emptyList())

        _uiState.update {
            it.copy(
                allPageStrokes = updatedStrokes,
                allPageTexts = updatedTexts,
                hasUnsavedChanges = true,
                canUndo = true,
                canRedo = false
            )
        }
    }

    /**
     * Non-destructive save:
     * Saves annotations in database and updates state without altering the original PDF file unless explicitly requested.
     */
    fun saveEdits(burnToPdfFile: Boolean = false) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val strokes = _uiState.value.allPageStrokes
                val texts = _uiState.value.allPageTexts

                // 1. Save in local Room Database non-destructively
                repository.saveAllDocumentAnnotations(doc.id, strokes, texts)

                // 2. Only burn into original PDF file if explicitly confirmed by user
                if (burnToPdfFile) {
                    val allPages = (strokes.keys + texts.keys).toSet()
                    val map = mutableMapOf<Int, Pair<List<StrokeAnnotation>, List<TextAnnotation>>>()
                    for (p in allPages) {
                        map[p] = Pair(strokes[p] ?: emptyList(), texts[p] ?: emptyList())
                    }
                    val burned = PdfEngine.saveAnnotatedPdfOverwriting(doc.filePath, map)
                    if (!burned) {
                        _uiState.update { it.copy(userMessage = "Annotations saved, but could not overwrite original file.") }
                        return@launch
                    }
                }

                _uiState.update {
                    it.copy(
                        hasUnsavedChanges = false,
                        userMessage = if (burnToPdfFile) "Edits permanently saved to original PDF file!" else "Edits saved to library successfully!"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(userMessage = "Save failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    /**
     * User explicitly confirmed saving and burning modifications to the original PDF file.
     */
    fun saveToOriginalPdfConfirmed() {
        saveEdits(burnToPdfFile = true)
    }

    fun toggleFavorite() {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            repository.toggleFavorite(doc.id, doc.isFavorite)
            _uiState.update {
                it.copy(document = doc.copy(isFavorite = !doc.isFavorite))
            }
        }
    }

    fun printCurrentDocument(context: Context) {
        val doc = _uiState.value.document ?: return
        // Non-destructively save annotations to database
        saveEdits(burnToPdfFile = false)
        val success = PdfPrintAdapter.printPdf(context, doc.filePath, doc.title, doc.pageCount)
        if (!success) {
            _uiState.update { it.copy(userMessage = "Could not initialize print service") }
        }
    }

    fun shareAnnotatedPdf(context: Context) {
        val doc = _uiState.value.document ?: return
        val strokes = _uiState.value.allPageStrokes
        val texts = _uiState.value.allPageTexts
        val hasAnnotations = strokes.values.any { it.isNotEmpty() } || texts.values.any { it.isNotEmpty() }

        // If there are no annotations, share the original file directly with zero re-rendering
        if (!hasAnnotations) {
            if (doc.filePath.startsWith("content://")) {
                ShareHelper.sharePdf(context, android.net.Uri.parse(doc.filePath), doc.title, "Share PDF")
            } else {
                ShareHelper.sharePdfFile(context, File(doc.filePath), doc.title, "Share PDF")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val allPages = (0 until doc.pageCount).toSet()
                val map = mutableMapOf<Int, Pair<List<StrokeAnnotation>, List<TextAnnotation>>>()
                for (p in allPages) {
                    map[p] = Pair(strokes[p] ?: emptyList(), texts[p] ?: emptyList())
                }

                val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
                val exportFile = File(exportDir, "${doc.title.replace(" ", "_")}_annotated.pdf")

                val success = FileOutputStream(exportFile).use { out ->
                    PdfEngine.exportAnnotatedPdf(doc.filePath, out, map)
                }

                if (success && exportFile.exists() && exportFile.length() > 0) {
                    ShareHelper.sharePdfFile(
                        context = context,
                        file = exportFile,
                        title = "Annotated: ${doc.title}",
                        chooserTitle = "Share Annotated PDF"
                    )
                } else {
                    _uiState.update { it.copy(userMessage = "Failed to export annotated PDF") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(userMessage = "Export failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun exportCurrentPageImage(context: Context) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val pageIndex = _uiState.value.currentPageIndex
                val strokes = _uiState.value.getStrokesForPage(pageIndex)
                val texts = _uiState.value.getTextsForPage(pageIndex)

                val bitmap = PdfEngine.exportPageAsPng(doc.filePath, pageIndex, strokes, texts)
                if (bitmap != null) {
                    val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
                    val imageFile = File(exportDir, "${doc.title.replace(" ", "_")}_page_${pageIndex + 1}.png")

                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    ShareHelper.shareImageFile(
                        context = context,
                        file = imageFile,
                        title = "${doc.title} - Page ${pageIndex + 1}",
                        chooserTitle = "Share Page Image"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(userMessage = "Image export failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    // In-PDF Search implementation
    fun openSearch() {
        _uiState.update { it.copy(isSearchActive = true) }
    }

    fun closeSearch() {
        _uiState.update {
            it.copy(
                isSearchActive = false,
                pdfSearchQuery = "",
                pdfSearchResults = emptyList(),
                currentSearchMatchIndex = 0,
                isSearchingInPdf = false
            )
        }
    }

    fun setPdfSearchQuery(query: String) {
        _uiState.update { it.copy(pdfSearchQuery = query) }
        searchJob?.cancel()
        val q = query.trim()
        if (q.isBlank()) {
            _uiState.update {
                it.copy(
                    pdfSearchResults = emptyList(),
                    currentSearchMatchIndex = 0,
                    isSearchingInPdf = false
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            executeSearch(q)
        }
    }

    fun performSearch(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        if (q.isBlank()) {
            _uiState.update {
                it.copy(
                    pdfSearchResults = emptyList(),
                    currentSearchMatchIndex = 0,
                    isSearchingInPdf = false
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            executeSearch(q)
        }
    }

    private suspend fun executeSearch(q: String) {
        val doc = _uiState.value.document ?: return
        _uiState.update { it.copy(isSearchingInPdf = true) }
        try {
            val matches = PdfEngine.searchInPdf(
                filePath = doc.filePath,
                query = q,
                pageCount = doc.pageCount,
                allPageTexts = _uiState.value.allPageTexts
            )
            val shouldPromptOcr = matches.isEmpty() && doc.isImageBased && doc.ocrStatus != "DONE"
            _uiState.update {
                it.copy(
                    pdfSearchResults = matches,
                    currentSearchMatchIndex = 0,
                    isSearchingInPdf = false,
                    showOcrPrompt = if (shouldPromptOcr) true else it.showOcrPrompt
                )
            }
            if (matches.isNotEmpty()) {
                val first = matches.first()
                if (first.pageIndex != _uiState.value.currentPageIndex) {
                    updateCurrentVisiblePage(first.pageIndex)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            _uiState.update {
                it.copy(
                    pdfSearchResults = emptyList(),
                    currentSearchMatchIndex = 0,
                    isSearchingInPdf = false,
                    userMessage = "Search error or out of memory"
                )
            }
        }
    }

    fun nextSearchMatch(): Int? {
        val results = _uiState.value.pdfSearchResults
        if (results.isEmpty()) return null
        val nextIdx = (_uiState.value.currentSearchMatchIndex + 1) % results.size
        _uiState.update { it.copy(currentSearchMatchIndex = nextIdx) }
        val targetPage = results[nextIdx].pageIndex
        updateCurrentVisiblePage(targetPage)
        return targetPage
    }

    fun prevSearchMatch(): Int? {
        val results = _uiState.value.pdfSearchResults
        if (results.isEmpty()) return null
        val prevIdx = if (_uiState.value.currentSearchMatchIndex - 1 < 0) results.size - 1 else _uiState.value.currentSearchMatchIndex - 1
        _uiState.update { it.copy(currentSearchMatchIndex = prevIdx) }
        val targetPage = results[prevIdx].pageIndex
        updateCurrentVisiblePage(targetPage)
        return targetPage
    }

    fun jumpToSearchMatch(index: Int): Int? {
        val results = _uiState.value.pdfSearchResults
        if (index !in results.indices) return null
        _uiState.update { it.copy(currentSearchMatchIndex = index) }
        val targetPage = results[index].pageIndex
        updateCurrentVisiblePage(targetPage)
        return targetPage
    }

    fun jumpToSearchMatchById(matchId: Long): Int? {
        val results = _uiState.value.pdfSearchResults
        val index = results.indexOfFirst { it.matchId == matchId }
        if (index == -1) return null
        return jumpToSearchMatch(index)
    }

    fun selectPenWithColor(color: Color) {
        _uiState.update {
            it.copy(
                activeTool = ToolType.PEN,
                penColor = color,
                strokeWidth = 4f
            )
        }
    }

    fun setCompressDialogVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                showCompressDialog = visible,
                compressionResult = if (visible) it.compressionResult else null
            )
        }
    }

    fun compressDocument(
        level: CompressionLevel,
        isGrayscale: Boolean = false,
        revertIfLarger: Boolean = false
    ) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCompressing = true) }
            val context = getApplication<Application>()
            val exportDir = File(context.cacheDir, "compressed_pdf").apply { if (!exists()) mkdirs() }
            val cleanName = doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val suffix = if (isGrayscale) "_bw" else ""
            val destFile = File(exportDir, "${cleanName}${suffix}_compressed_${System.currentTimeMillis()}.pdf")

            val allAnnotations = mutableMapOf<Int, Pair<List<StrokeAnnotation>, List<TextAnnotation>>>()
            for (p in 0 until _uiState.value.totalPages) {
                allAnnotations[p] = Pair(
                    _uiState.value.getStrokesForPage(p),
                    _uiState.value.getTextsForPage(p)
                )
            }

            val result = PdfEngine.compressPdf(
                sourceLocation = doc.filePath,
                destFile = destFile,
                level = level,
                allAnnotationsByPage = allAnnotations,
                convertToGrayscale = isGrayscale,
                revertIfLarger = revertIfLarger
            )

            _uiState.update {
                val msg = if (result != null) {
                    if (result.wasRevertedDueToSize) {
                        "Protected original document: recompression would have increased size."
                    } else if (result.isLarger) {
                        "Compressed file is larger (+${-result.savedPercentage}%)"
                    } else {
                        "Compressed: saved ${result.savedPercentage}%" + if (result.isGrayscale) " (Grayscale)" else ""
                    }
                } else {
                    "Compression failed"
                }
                it.copy(
                    isCompressing = false,
                    compressionResult = result,
                    userMessage = msg
                )
            }
        }
    }

    fun shareCompressedPdf(context: Context, result: CompressionResult) {
        try {
            ShareHelper.sharePdfFile(
                context = context,
                file = result.outputFile,
                title = "Compressed PDF: ${result.outputFile.name}",
                chooserTitle = "Share Compressed PDF"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(userMessage = "Could not share compressed file") }
        }
    }

    fun exportCompressedPdf(result: CompressionResult) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val doc = _uiState.value.document
                if (!result.outputFile.exists() || result.outputFile.length() == 0L) {
                    _uiState.update { it.copy(userMessage = "Compressed file is empty or missing") }
                    return@launch
                }
                val exportsDir = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }
                val exportFile = File(exportsDir, result.outputFile.name)
                result.outputFile.copyTo(exportFile, overwrite = true)

                val title = "${doc?.title ?: "Document"} (Compressed)"
                repository.insertDocument(
                    DocumentEntity(
                        title = title,
                        fileName = exportFile.name,
                        filePath = exportFile.absolutePath,
                        sourceLocation = exportFile.absolutePath,
                        folderId = doc?.folderId,
                        pageCount = _uiState.value.totalPages,
                        fileSizeBytes = exportFile.length(),
                        currentPage = 0,
                        tags = "Compressed, Exported"
                    )
                )

                // Also save to device's public Downloads directory
                ShareHelper.savePdfToDownloads(context, exportFile)

                _uiState.update {
                    val statusText = if (result.isLarger) {
                        "Saved to Downloads: ${formatFileSize(result.compressedSizeBytes)} (+${-result.savedPercentage}%)"
                    } else {
                        "Saved to Downloads: ${result.savedPercentage}% smaller (${formatFileSize(result.compressedSizeBytes)})"
                    }
                    it.copy(
                        userMessage = statusText,
                        showCompressDialog = false
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(userMessage = "Failed to export compressed file") }
            }
        }
    }
}
