package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PaperInkDatabase
import com.example.data.model.DocumentEntity
import com.example.data.model.PageAnnotationEntity
import com.example.data.repository.DocumentRepository
import com.example.pdf.PdfEngine
import com.example.util.ShareHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.CompressionLevel
import com.example.data.model.CompressionResult
import com.example.data.model.StrokeAnnotation
import com.example.data.model.TextAnnotation
import java.io.File

enum class SortMode {
    RECENT,
    NAME,
    SIZE
}

data class LibraryUiState(
    val documents: List<DocumentEntity> = emptyList(),
    val lastOpenedDocument: DocumentEntity? = null,
    val searchQuery: String = "",
    val sortMode: SortMode = SortMode.RECENT,
    val matchedSnippets: Map<Long, String> = emptyMap(), // docId -> keyword match description
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    val compressingDocument: DocumentEntity? = null,
    val isCompressing: Boolean = false,
    val compressionResult: com.example.data.model.CompressionResult? = null,
    val showMergeDialog: Boolean = false,
    val isMerging: Boolean = false,
    val mergeResult: com.example.data.model.MergeResult? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DocumentRepository
    private val _searchQuery = MutableStateFlow("")
    private val _sortMode = MutableStateFlow(SortMode.RECENT)
    private val _isLoading = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _compressingDoc = MutableStateFlow<DocumentEntity?>(null)
    private val _isCompressing = MutableStateFlow(false)
    private val _compressionResult = MutableStateFlow<com.example.data.model.CompressionResult?>(null)
    private val _showMergeDialog = MutableStateFlow(false)
    private val _isMerging = MutableStateFlow(false)
    private val _mergeResult = MutableStateFlow<com.example.data.model.MergeResult?>(null)

    val uiState: StateFlow<LibraryUiState>

    init {
        PdfEngine.init(application)
        val db = PaperInkDatabase.getDatabase(application)
        repository = DocumentRepository(db.paperInkDao(), application)

        // Reactive Room search query flow
        val documentsFlow = _searchQuery.flatMapLatest { query ->
            val q = query.trim()
            if (q.isBlank()) {
                repository.allDocuments
            } else {
                repository.searchDocuments(q, null)
            }
        }

        // Search matching annotations for keyword content snippets
        val matchingAnnotationsFlow = _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList<PageAnnotationEntity>())
            } else {
                repository.searchMatchingAnnotations(query.trim())
            }
        }

        val metaFlow = combine(
            combine(_sortMode, _searchQuery, _isLoading) { s, q, l -> Triple(s, q, l) },
            combine(_userMessage, _compressingDoc, _isCompressing, _compressionResult) { msg, cDoc, cLoading, cRes ->
                CompressTuple(msg, cDoc, cLoading, cRes)
            },
            combine(_showMergeDialog, _isMerging, _mergeResult) { sMerge, isM, mRes ->
                MergeTuple(sMerge, isM, mRes)
            }
        ) { tuple1, tuple2, tuple3 ->
            MetaTuple(
                sortMode = tuple1.first,
                searchQuery = tuple1.second,
                isLoading = tuple1.third,
                userMessage = tuple2.userMessage,
                compressingDoc = tuple2.compressingDoc,
                isCompressing = tuple2.isCompressing,
                compressionResult = tuple2.compressionResult,
                showMergeDialog = tuple3.showMergeDialog,
                isMerging = tuple3.isMerging,
                mergeResult = tuple3.mergeResult
            )
        }

        uiState = combine(
            documentsFlow,
            repository.lastOpenedDocument,
            matchingAnnotationsFlow,
            metaFlow
        ) { rawDocs, lastDoc, annotations, meta ->
            val query = meta.searchQuery.trim().lowercase()

            val sorted = when (meta.sortMode) {
                SortMode.RECENT -> rawDocs.sortedByDescending { it.lastReadAt }
                SortMode.NAME -> rawDocs.sortedBy { it.title.lowercase() }
                SortMode.SIZE -> rawDocs.sortedByDescending { it.fileSizeBytes }
            }

            // Build snippet explanations for why each document matched
            val snippets = mutableMapOf<Long, String>()
            if (query.isNotBlank()) {
                for (doc in sorted) {
                    val matchingNote = annotations.firstOrNull { it.documentId == doc.id && it.textContent?.lowercase()?.contains(query) == true }
                    if (matchingNote != null && !matchingNote.textContent.isNullOrBlank()) {
                        val text = matchingNote.textContent.trim()
                        val snippet = if (text.length > 40) text.take(37) + "..." else text
                        snippets[doc.id] = "Note on Page ${matchingNote.pageIndex + 1}: \"$snippet\""
                    } else if (doc.title.lowercase().contains(query)) {
                        snippets[doc.id] = "Matched Title"
                    } else if (doc.tags.lowercase().contains(query)) {
                        snippets[doc.id] = "Matched Tag: ${doc.tags}"
                    }
                }
            }

            LibraryUiState(
                documents = sorted,
                lastOpenedDocument = lastDoc,
                searchQuery = meta.searchQuery,
                sortMode = meta.sortMode,
                matchedSnippets = snippets,
                isLoading = meta.isLoading,
                userMessage = meta.userMessage,
                compressingDocument = meta.compressingDoc,
                isCompressing = meta.isCompressing,
                compressionResult = meta.compressionResult,
                showMergeDialog = meta.showMergeDialog,
                isMerging = meta.isMerging,
                mergeResult = meta.mergeResult
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryUiState(isLoading = true)
        )

        // Seed initial sample guide if app history is empty
        viewModelScope.launch {
            checkAndSeedSampleGuide()
        }
    }

    private data class CompressTuple(
        val userMessage: String?,
        val compressingDoc: DocumentEntity?,
        val isCompressing: Boolean,
        val compressionResult: com.example.data.model.CompressionResult?
    )

    private data class MergeTuple(
        val showMergeDialog: Boolean,
        val isMerging: Boolean,
        val mergeResult: com.example.data.model.MergeResult?
    )

    private data class MetaTuple(
        val sortMode: SortMode,
        val searchQuery: String,
        val isLoading: Boolean,
        val userMessage: String?,
        val compressingDoc: DocumentEntity?,
        val isCompressing: Boolean,
        val compressionResult: com.example.data.model.CompressionResult?,
        val showMergeDialog: Boolean,
        val isMerging: Boolean,
        val mergeResult: com.example.data.model.MergeResult?
    )

    private suspend fun checkAndSeedSampleGuide() {
        val app = getApplication<Application>()
        val sampleDir = File(app.cacheDir, "sample_docs").apply { if (!exists()) mkdirs() }
        val guideFile = File(sampleDir, "paperink_guide.pdf")

        if (!guideFile.exists()) {
            PdfEngine.generateSamplePdf(guideFile, 1)
        }

        val allDocs = repository.allDocuments.stateIn(viewModelScope).value
        if (allDocs.isEmpty() && guideFile.exists()) {
            val entity = DocumentEntity(
                title = "PaperInk User Guide & Tutorial",
                fileName = "paperink_guide.pdf",
                filePath = guideFile.absolutePath,
                sourceLocation = "Sample: /storage/paperink_guide.pdf",
                folderId = null,
                pageCount = 2,
                fileSizeBytes = guideFile.length(),
                currentPage = 0,
                isFavorite = false,
                tags = "Offline,Guide,Highlighting,Print"
            )
            val newId = repository.dao.insertDocument(entity)
            repository.savePageAnnotations(
                documentId = newId,
                pageIndex = 0,
                strokes = emptyList(),
                textNotes = listOf(
                    com.example.data.model.TextAnnotation(
                        text = "Important offline guide for annotations & mobile printing",
                        x = 0.15f,
                        y = 0.22f
                    )
                )
            )
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    /**
     * Opens PDF directly from mass storage location via Storage Access Framework / ContentResolver.
     * ZERO copies made: directly reads the user's PDF on mass storage.
     */
    fun openPdf(uri: Uri, onOpened: (Long) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val doc = repository.openPdfFromUri(uri)
            _isLoading.value = false
            if (doc != null) {
                onOpened(doc.id)
            } else {
                _userMessage.value = "Failed to open PDF file from storage"
            }
        }
    }

    /**
     * Opens PDF directly from a web URL.
     */
    fun openPdfFromUrl(url: String, customTitle: String? = null, onOpened: (Long) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val doc = repository.openPdfFromUrl(url.trim(), customTitle, null)
            _isLoading.value = false
            if (doc != null) {
                onOpened(doc.id)
            } else {
                _userMessage.value = "Could not download or open PDF from the provided URL"
            }
        }
    }

    fun toggleFavorite(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(doc.id, doc.isFavorite)
        }
    }

    fun removeDocumentFromHistory(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.removeDocument(doc)
            _userMessage.value = "Removed from recent list"
        }
    }

    fun showCompressDialog(doc: DocumentEntity) {
        _compressingDoc.value = doc
        _compressionResult.value = null
    }

    fun dismissCompressDialog() {
        _compressingDoc.value = null
        _compressionResult.value = null
    }

    fun compressDocument(
        doc: DocumentEntity,
        level: CompressionLevel,
        isGrayscale: Boolean = false,
        revertIfLarger: Boolean = false
    ) {
        viewModelScope.launch {
            _isCompressing.value = true
            val context = getApplication<Application>()
            val exportDir = File(context.cacheDir, "compressed_pdf").apply { if (!exists()) mkdirs() }
            val cleanName = doc.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val suffix = if (isGrayscale) "_bw" else ""
            val destFile = File(exportDir, "${cleanName}${suffix}_compressed_${System.currentTimeMillis()}.pdf")

            // fetch annotations for this doc to burn them into the compressed output
            val rawAnnotations = repository.getAllAnnotationsForDocument(doc.id)
            val allAnnotations = mutableMapOf<Int, Pair<List<StrokeAnnotation>, List<TextAnnotation>>>()
            val byPage = rawAnnotations.groupBy { it.pageIndex }
            for ((pageIdx, list) in byPage) {
                val strokes = mutableListOf<StrokeAnnotation>()
                val texts = mutableListOf<TextAnnotation>()
                for (item in list) {
                    if (!item.textContent.isNullOrBlank() && item.textX != null && item.textY != null) {
                        texts.add(
                            TextAnnotation(
                                id = item.id,
                                text = item.textContent,
                                x = item.textX,
                                y = item.textY,
                                colorArgb = item.colorArgb,
                                fontSizeSp = item.strokeWidth
                            )
                        )
                    } else if (item.pointsData.isNotBlank()) {
                        strokes.add(
                            StrokeAnnotation.fromSerializedString(
                                id = item.id,
                                isHighlighter = item.isHighlighter,
                                colorArgb = item.colorArgb,
                                strokeWidthDp = item.strokeWidth,
                                data = item.pointsData
                            )
                        )
                    }
                }
                allAnnotations[pageIdx] = Pair(strokes, texts)
            }

            val result = PdfEngine.compressPdf(
                sourceLocation = doc.filePath,
                destFile = destFile,
                level = level,
                allAnnotationsByPage = allAnnotations,
                convertToGrayscale = isGrayscale,
                revertIfLarger = revertIfLarger
            )

            _isCompressing.value = false
            _compressionResult.value = result
            _userMessage.value = if (result != null) {
                if (result.wasRevertedDueToSize) {
                    "Protected original document: recompression would have increased size."
                } else if (result.isLarger) {
                    "Document size increased (+${kotlin.math.abs(result.savedPercentage)}%) due to vector rasterization."
                } else {
                    "Compressed: saved ${result.savedPercentage}%" + if (result.isGrayscale) " (Grayscale)" else ""
                }
            } else "Compression failed"
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
            _userMessage.value = "Could not share compressed file"
        }
    }

    fun exportCompressedPdf(result: CompressionResult) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val doc = _compressingDoc.value
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
                        pageCount = doc?.pageCount ?: 1,
                        fileSizeBytes = exportFile.length(),
                        currentPage = 0,
                        tags = "Compressed, Exported"
                    )
                )

                // Also save to device's public Downloads directory
                ShareHelper.savePdfToDownloads(context, exportFile)

                _compressingDoc.value = null
                _compressionResult.value = null
                val savedText = if (result.isLarger) {
                    "rasterized (${com.example.ui.components.formatFileSize(result.compressedSizeBytes)})"
                } else {
                    "${result.savedPercentage}% smaller (${com.example.ui.components.formatFileSize(result.compressedSizeBytes)})"
                }
                _userMessage.value = "Saved to Downloads: $savedText"
            } catch (e: Exception) {
                e.printStackTrace()
                _userMessage.value = "Failed to export compressed file"
            }
        }
    }

    fun openMergeDialog() {
        _showMergeDialog.value = true
        _mergeResult.value = null
    }

    fun closeMergeDialog() {
        _showMergeDialog.value = false
        _isMerging.value = false
        _mergeResult.value = null
    }

    fun mergePdfDocuments(items: List<com.example.ui.components.MergeableItem>, outputTitle: String) {
        if (items.size < 2) {
            _userMessage.value = "Please select at least 2 documents to merge"
            return
        }

        viewModelScope.launch {
            _isMerging.value = true
            val context = getApplication<Application>()
            val exportDir = File(context.cacheDir, "merged_pdf").apply { if (!exists()) mkdirs() }
            val cleanName = outputTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val destFile = File(exportDir, "${cleanName}_${System.currentTimeMillis()}.pdf")

            val sourceLocations = items.map { it.uriString }
            val result = PdfEngine.mergePdfs(
                sourceLocations = sourceLocations,
                destFile = destFile
            )

            _isMerging.value = false
            if (result != null) {
                _mergeResult.value = result
                // Automatically register merged document in local library
                try {
                    val libDir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
                    val savedFile = File(libDir, destFile.name)
                    destFile.copyTo(savedFile, overwrite = true)

                    repository.insertDocument(
                        DocumentEntity(
                            title = outputTitle,
                            fileName = savedFile.name,
                            filePath = savedFile.absolutePath,
                            sourceLocation = "Merged (${items.size} files)",
                            folderId = null,
                            pageCount = result.totalPageCount,
                            fileSizeBytes = savedFile.length(),
                            currentPage = 0,
                            tags = "Merged"
                        )
                    )
                } catch (_: Exception) {}
            } else {
                _userMessage.value = "Failed to merge PDF documents"
            }
        }
    }

    fun shareMergedPdf(context: Context, result: com.example.data.model.MergeResult) {
        try {
            ShareHelper.sharePdfFile(
                context = context,
                file = result.outputFile,
                title = "Merged PDF: ${result.title}",
                chooserTitle = "Share Merged PDF"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _userMessage.value = "Could not share merged file"
        }
    }

    fun saveMergedPdfToDownloads(result: com.example.data.model.MergeResult) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val savedUri = ShareHelper.savePdfToDownloads(context, result.outputFile)
                if (savedUri != null) {
                    _userMessage.value = "Saved '${result.outputFile.name}' to Downloads!"
                    closeMergeDialog()
                } else {
                    _userMessage.value = "Failed to save to Downloads"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _userMessage.value = "Failed to save to Downloads"
            }
        }
    }
}
