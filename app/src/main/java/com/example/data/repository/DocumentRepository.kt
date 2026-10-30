package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.local.PaperInkDao
import com.example.data.model.DocumentEntity
import com.example.data.model.FolderEntity
import com.example.data.model.PageAnnotationEntity
import com.example.pdf.PdfEngine
import com.example.data.model.StrokeAnnotation
import com.example.data.model.TextAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class DocumentRepository(
    val dao: PaperInkDao,
    private val context: Context
) {
    val allFolders: Flow<List<FolderEntity>> = dao.getAllFolders()
    val allDocuments: Flow<List<DocumentEntity>> = dao.getAllDocuments()
    val favoriteDocuments: Flow<List<DocumentEntity>> = dao.getFavoriteDocuments()
    val lastOpenedDocument: Flow<DocumentEntity?> = dao.getLastOpenedDocument()

    suspend fun insertDocument(doc: DocumentEntity): Long = withContext(Dispatchers.IO) {
        dao.insertDocument(doc)
    }

    suspend fun removeDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        dao.deleteDocumentById(doc.id)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getDocumentsByFolder(folderId: Long): Flow<List<DocumentEntity>> {
        return dao.getDocumentsByFolder(folderId)
    }

    fun searchDocuments(query: String, folderId: Long?): Flow<List<DocumentEntity>> {
        return if (folderId == -1L) {
            dao.searchFavoriteDocuments(query)
        } else {
            dao.searchDocuments(query, folderId)
        }
    }

    fun searchMatchingAnnotations(query: String): Flow<List<PageAnnotationEntity>> {
        return dao.searchMatchingAnnotations(query)
    }

    fun getDocumentFlow(id: Long): Flow<DocumentEntity?> {
        return dao.getDocumentFlow(id)
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? = withContext(Dispatchers.IO) {
        dao.getDocumentById(id)
    }

    suspend fun insertFolder(name: String, colorHex: String, iconName: String): Long = withContext(Dispatchers.IO) {
        dao.insertFolder(
            FolderEntity(
                name = name,
                colorHex = colorHex,
                iconName = iconName
            )
        )
    }

    suspend fun updateFolder(folder: FolderEntity) = withContext(Dispatchers.IO) {
        dao.updateFolder(folder)
    }

    suspend fun deleteFolder(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteFolderById(id)
    }

    suspend fun updateReadingPosition(docId: Long, page: Int) = withContext(Dispatchers.IO) {
        dao.updateReadingPosition(docId, page)
    }

    suspend fun toggleFavorite(docId: Long, current: Boolean) = withContext(Dispatchers.IO) {
        dao.updateFavorite(docId, !current)
    }

    suspend fun moveToFolder(docId: Long, folderId: Long?) = withContext(Dispatchers.IO) {
        dao.moveToFolder(docId, folderId)
    }

    suspend fun deleteDocument(doc: DocumentEntity) = withContext(Dispatchers.IO) {
        try {
            val file = File(doc.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
        dao.deleteDocumentById(doc.id)
    }

    suspend fun savePageAnnotations(
        documentId: Long,
        pageIndex: Int,
        strokes: List<StrokeAnnotation>,
        textNotes: List<TextAnnotation>
    ) = withContext(Dispatchers.IO) {
        dao.deleteAnnotationsForPage(documentId, pageIndex)
        val entities = mutableListOf<PageAnnotationEntity>()

        strokes.forEach { stroke ->
            entities.add(
                PageAnnotationEntity(
                    documentId = documentId,
                    pageIndex = pageIndex,
                    isHighlighter = stroke.isHighlighter,
                    colorArgb = stroke.colorArgb,
                    strokeWidth = stroke.strokeWidthDp,
                    pointsData = stroke.toSerializedString()
                )
            )
        }

        textNotes.forEach { note ->
            entities.add(
                PageAnnotationEntity(
                    documentId = documentId,
                    pageIndex = pageIndex,
                    isHighlighter = false,
                    colorArgb = note.colorArgb,
                    strokeWidth = note.fontSizeSp,
                    pointsData = "",
                    textContent = note.text,
                    textX = note.x,
                    textY = note.y
                )
            )
        }

        if (entities.isNotEmpty()) {
            dao.insertAnnotations(entities)
        }
    }

    suspend fun loadPageAnnotations(
        documentId: Long,
        pageIndex: Int
    ): Pair<List<StrokeAnnotation>, List<TextAnnotation>> = withContext(Dispatchers.IO) {
        val list = dao.getAnnotationsForPage(documentId, pageIndex).firstOrNull() ?: emptyList()
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

        Pair(strokes, texts)
    }

    suspend fun getAllAnnotationsForDocument(documentId: Long): List<PageAnnotationEntity> =
        withContext(Dispatchers.IO) {
            dao.getAllAnnotationsForDocument(documentId)
        }

    suspend fun openPdfFromUri(uri: Uri, folderId: Long? = null): DocumentEntity? = withContext(Dispatchers.IO) {
        try {
            // Persist URI permission so the app can continue reading the mass storage file across restarts
            try {
                val readWriteFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, readWriteFlags)
            } catch (_: Exception) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            val uriString = uri.toString()

            // Check if this mass storage document is already in our history
            val existing = dao.getDocumentByFilePath(uriString)
            if (existing != null) {
                dao.updateReadingPosition(existing.id, existing.currentPage, System.currentTimeMillis())
                return@withContext existing
            }

            // Determine page count directly from SAF file descriptor without copying
            var pageCount = 1
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: PdfEngine.openPfd(uriString, "r")
                if (pfd != null) {
                    pfd.use { descriptor ->
                        val renderer = android.graphics.pdf.PdfRenderer(descriptor)
                        try {
                            pageCount = renderer.pageCount
                        } finally {
                            renderer.close()
                        }
                    }
                }
            } catch (_: Exception) {}

            // Extract display name and file size directly from storage provider
            var title = "PDF Document"
            var displayLocation = uri.path ?: "Mass Storage"
            var fileSizeBytes = 0L

            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex >= 0) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrBlank()) {
                                title = name.removeSuffix(".pdf")
                                displayLocation = "Storage: $name"
                            }
                        }
                        if (sizeIndex >= 0) {
                            fileSizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {}

            val entity = DocumentEntity(
                title = title,
                fileName = "$title.pdf",
                filePath = uriString, // Direct mass storage URI, NO COPY!
                sourceLocation = displayLocation,
                folderId = folderId,
                pageCount = pageCount,
                fileSizeBytes = fileSizeBytes,
                currentPage = 0,
                isFavorite = false,
                tags = "Mass Storage"
            )
            val newId = dao.insertDocument(entity)
            entity.copy(id = newId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun importPdfFromUri(uri: Uri, folderId: Long? = null): DocumentEntity? =
        openPdfFromUri(uri, folderId)

    suspend fun savePdfFileDirect(
        title: String,
        inputStream: InputStream,
        folderId: Long? = null,
        tags: String = ""
    ): DocumentEntity? = withContext(Dispatchers.IO) {
        try {
            val docsDir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
            val fileName = "doc_${System.currentTimeMillis()}_${(100..999).random()}.pdf"
            val destFile = File(docsDir, fileName)

            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }

            var pageCount = 1
            try {
                ParcelFileDescriptor.open(destFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                    }
                }
            } catch (_: Exception) {}

            val entity = DocumentEntity(
                title = title,
                fileName = fileName,
                filePath = destFile.absolutePath,
                sourceLocation = destFile.absolutePath,
                folderId = folderId,
                pageCount = pageCount,
                fileSizeBytes = destFile.length(),
                currentPage = 0,
                isFavorite = false,
                tags = tags
            )
            val id = dao.insertDocument(entity)
            entity.copy(id = id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun openPdfFromUrl(
        url: String,
        customTitle: String? = null,
        folderId: Long? = null
    ): DocumentEntity? = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            require(cleanUrl.startsWith("https://", ignoreCase = true)) {
                "Only secure HTTPS URLs are allowed"
            }

            val request = Request.Builder().url(cleanUrl).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body ?: return@withContext null
            val maxDownloadBytes = 100 * 1024 * 1024L // 100 MB limit
            if (body.contentLength() > maxDownloadBytes) {
                return@withContext null
            }

            val docsDir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
            val fileName = "url_${System.currentTimeMillis()}.pdf"
            val destFile = File(docsDir, fileName)

            var totalBytesRead = 0L
            FileOutputStream(destFile).use { out ->
                val buffer = ByteArray(8192)
                val inStream = body.byteStream()
                var bytesRead: Int
                while (inStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytesRead += bytesRead
                    if (totalBytesRead > maxDownloadBytes) {
                        destFile.delete()
                        return@withContext null
                    }
                    out.write(buffer, 0, bytesRead)
                }
            }

            var pageCount = 1
            try {
                ParcelFileDescriptor.open(destFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                    }
                }
            } catch (_: Exception) {}

            val parsedTitle = customTitle?.takeIf { it.isNotBlank() }
                ?: cleanUrl.substringAfterLast("/").substringBefore("?").takeIf { it.endsWith(".pdf") }?.removeSuffix(".pdf")
                ?: "Web Document (${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date())})"

            val entity = DocumentEntity(
                title = parsedTitle,
                fileName = fileName,
                filePath = destFile.absolutePath,
                sourceLocation = cleanUrl,
                folderId = folderId,
                pageCount = pageCount,
                fileSizeBytes = destFile.length(),
                currentPage = 0,
                isFavorite = false,
                tags = "URL,Web,Online"
            )
            val id = dao.insertDocument(entity)
            entity.copy(id = id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveAllDocumentAnnotations(
        documentId: Long,
        allStrokes: Map<Int, List<StrokeAnnotation>>,
        allTexts: Map<Int, List<TextAnnotation>>
    ) = withContext(Dispatchers.IO) {
        val allPages = (allStrokes.keys + allTexts.keys).toSet()
        for (pageIdx in allPages) {
            savePageAnnotations(
                documentId = documentId,
                pageIndex = pageIdx,
                strokes = allStrokes[pageIdx] ?: emptyList(),
                textNotes = allTexts[pageIdx] ?: emptyList()
            )
        }
    }
}
