package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PaperInkDatabase
import com.example.data.model.CompressionLevel
import com.example.data.model.CompressionResult
import com.example.data.model.DocumentEntity
import com.example.data.model.PdfSearchMatch
import com.example.data.model.ReadingMode
import com.example.data.model.StrokeAnnotation
import com.example.data.model.TextAnnotation
import com.example.data.model.ToolType
import com.example.data.repository.DocumentRepository
import com.example.pdf.PdfEngine
import com.example.pdf.PdfPrintAdapter
import com.example.ui.theme.AnnotationBlack
import com.example.ui.theme.AnnotationBlue
import com.example.ui.theme.AnnotationYellow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

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
    // Compression
    val isCompressing: Boolean = false,
    val compressionResult: CompressionResult? = null,
    val showCompressDialog: Boolean = false
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
                    isPageLoading = true
                )
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
     * Explicit save option requested by user:
     * Saves annotations in database and burns the ink/highlights directly to the PDF file!
     */
    fun saveEdits(burnToPdfFile: Boolean = true) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val strokes = _uiState.value.allPageStrokes
                val texts = _uiState.value.allPageTexts

                // 1. Save in local Room Database
                repository.saveAllDocumentAnnotations(doc.id, strokes, texts)

                // 2. Burn into original PDF file if requested
                if (burnToPdfFile) {
                    val allPages = (strokes.keys + texts.keys).toSet()
                    val map = mutableMapOf<Int, Pair<List<StrokeAnnotation>, List<TextAnnotation>>>()
                    for (p in allPages) {
                        map[p] = Pair(strokes[p] ?: emptyList(), texts[p] ?: emptyList())
                    }
                    PdfEngine.saveAnnotatedPdfOverwriting(doc.filePath, map)
                }

                _uiState.update {
                    it.copy(
                        hasUnsavedChanges = false,
                        userMessage = "All edits & pen ink saved to PDF successfully!"
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
        saveEdits(burnToPdfFile = true)
        val file = File(doc.filePath)
        if (file.exists()) {
            PdfPrintAdapter.printPdf(context, file, doc.title)
        }
    }

    fun shareAnnotatedPdf(context: Context) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val strokes = _uiState.value.allPageStrokes
                val texts = _uiState.value.allPageTexts
                val allPages = (0 until doc.pageCount).toSet()
                val map = mutableMapOf<Int, Pair<List<StrokeAnnotation>, List<TextAnnotation>>>()
                for (p in allPages) {
                    map[p] = Pair(strokes[p] ?: emptyList(), texts[p] ?: emptyList())
                }

                val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
                val exportFile = File(exportDir, "${doc.title.replace(" ", "_")}_annotated.pdf")

                FileOutputStream(exportFile).use { out ->
                    PdfEngine.exportAnnotatedPdf(doc.filePath, out, map)
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    exportFile
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Annotated PDF: ${doc.title}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Annotated PDF").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
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

                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        imageFile
                    )

                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "${doc.title} - Page ${pageIndex + 1}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Page Image").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
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
        val matches = PdfEngine.searchInPdf(
            filePath = doc.filePath,
            query = q,
            pageCount = doc.pageCount,
            allPageTexts = _uiState.value.allPageTexts
        )
        _uiState.update {
            it.copy(
                pdfSearchResults = matches,
                currentSearchMatchIndex = 0,
                isSearchingInPdf = false
            )
        }
        if (matches.isNotEmpty()) {
            val first = matches.first()
            if (first.pageIndex != _uiState.value.currentPageIndex) {
                updateCurrentVisiblePage(first.pageIndex)
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

    fun compressDocument(level: CompressionLevel) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCompressing = true) }
            val context = getApplication<Application>()
            val exportDir = File(context.cacheDir, "compressed_pdf").apply { if (!exists()) mkdirs() }
            val cleanName = doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val destFile = File(exportDir, "${cleanName}_compressed_${System.currentTimeMillis()}.pdf")

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
                allAnnotationsByPage = allAnnotations
            )

            _uiState.update {
                it.copy(
                    isCompressing = false,
                    compressionResult = result,
                    userMessage = if (result != null) "Compressed: saved ${result.savedPercentage}%" else "Compression failed"
                )
            }
        }
    }

    fun shareCompressedPdf(context: Context, result: CompressionResult) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                result.outputFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Compressed PDF: ${result.outputFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Compressed PDF"))
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

                _uiState.update {
                    it.copy(
                        userMessage = "Exported: saved ${result.savedPercentage}% (${com.example.ui.components.formatFileSize(result.compressedSizeBytes)})",
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
