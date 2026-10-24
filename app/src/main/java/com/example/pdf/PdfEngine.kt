package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.example.data.model.CompressionLevel
import com.example.data.model.CompressionResult
import com.example.data.model.NormalizedPoint
import com.example.data.model.PdfSearchMatch
import com.example.data.model.SearchHighlightRect
import com.example.data.model.StrokeAnnotation
import com.example.data.model.TextAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.zip.Inflater

object PdfEngine {

    // Cache rendered pages in memory to ensure 60fps scrolling and navigation
    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 6
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
    }

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun openPfd(location: String, mode: String = "r"): ParcelFileDescriptor? {
        return try {
            if (location.startsWith("content://")) {
                val uri = android.net.Uri.parse(location)
                appContext?.contentResolver?.openFileDescriptor(uri, mode)
            } else {
                val file = File(location)
                if (file.exists()) {
                    val pfdMode = if (mode.contains("w")) {
                        ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
                    } else {
                        ParcelFileDescriptor.MODE_READ_ONLY
                    }
                    ParcelFileDescriptor.open(file, pfdMode)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun readBytesFromLocation(location: String): ByteArray? {
        return try {
            if (location.startsWith("content://")) {
                val uri = android.net.Uri.parse(location)
                appContext?.contentResolver?.openInputStream(uri)?.use { it.readBytes() }
            } else {
                val file = File(location)
                if (file.exists()) file.readBytes() else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openOutputStreamForLocation(location: String): OutputStream? {
        return try {
            if (location.startsWith("content://")) {
                val uri = android.net.Uri.parse(location)
                appContext?.contentResolver?.openOutputStream(uri, "wt")
            } else {
                FileOutputStream(File(location))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun renderPageBitmap(
        filePath: String,
        pageIndex: Int,
        targetWidth: Int = 1200
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${filePath}_${pageIndex}_$targetWidth"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val pfd = openPfd(filePath, "r") ?: return@withContext null

        try {
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

                    renderer.openPage(pageIndex).use { page ->
                        val originalWidth = page.width
                        val originalHeight = page.height
                        val scale = targetWidth.toFloat() / originalWidth.toFloat()
                        val targetHeight = (originalHeight * scale).toInt()

                        val bitmap = Bitmap.createBitmap(
                            targetWidth,
                            targetHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)

                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        memoryCache.put(cacheKey, bitmap)
                        bitmap
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportAnnotatedPdf(
        sourceFilePath: String,
        outputStream: OutputStream,
        allAnnotationsByPage: Map<Int, Pair<List<StrokeAnnotation>, List<TextAnnotation>>>
    ): Boolean = withContext(Dispatchers.IO) {
        val pfd = openPfd(sourceFilePath, "r") ?: return@withContext false

        try {
            val pdfDoc = PdfDocument()

            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val totalPages = renderer.pageCount
                    val targetWidth = 1200

                    for (pageIdx in 0 until totalPages) {
                        renderer.openPage(pageIdx).use { page ->
                            val origW = page.width
                            val origH = page.height
                            val scale = targetWidth.toFloat() / origW.toFloat()
                            val targetHeight = (origH * scale).toInt()

                            val pageInfo = PdfDocument.PageInfo.Builder(
                                targetWidth,
                                targetHeight,
                                pageIdx + 1
                            ).create()
                            val pdfPage = pdfDoc.startPage(pageInfo)
                            val canvas = pdfPage.canvas

                            // 1. Render base PDF page
                            val baseBitmap = Bitmap.createBitmap(
                                targetWidth,
                                targetHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            val baseCanvas = Canvas(baseBitmap)
                            baseCanvas.drawColor(Color.WHITE)
                            page.render(
                                baseBitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                            )
                            canvas.drawBitmap(baseBitmap, 0f, 0f, null)
                            baseBitmap.recycle()

                            // 2. Render overlays (Strokes and Text Annotations)
                            val pageData = allAnnotationsByPage[pageIdx]
                            if (pageData != null) {
                                val (strokes, texts) = pageData
                                drawAnnotationsOnCanvas(canvas, targetWidth, targetHeight, strokes, texts)
                            }

                            pdfDoc.finishPage(pdfPage)
                        }
                    }
                }
            }

            pdfDoc.writeTo(outputStream)
            pdfDoc.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun compressPdf(
        sourceLocation: String,
        destFile: File,
        level: CompressionLevel,
        allAnnotationsByPage: Map<Int, Pair<List<StrokeAnnotation>, List<TextAnnotation>>> = emptyMap()
    ): CompressionResult? = withContext(Dispatchers.IO) {
        val pfd = openPfd(sourceLocation, "r") ?: return@withContext null

        try {
            var originalSize = 0L
            if (sourceLocation.startsWith("content://")) {
                val uri = android.net.Uri.parse(sourceLocation)
                appContext?.contentResolver?.openFileDescriptor(uri, "r")?.use {
                    originalSize = it.statSize
                }
            } else {
                originalSize = File(sourceLocation).length()
            }
            if (originalSize <= 0L) {
                originalSize = (readBytesFromLocation(sourceLocation)?.size ?: 0).toLong()
            }

            val pdfDoc = PdfDocument()

            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val totalPages = renderer.pageCount
                    val targetWidth = level.targetWidth

                    for (pageIdx in 0 until totalPages) {
                        renderer.openPage(pageIdx).use { page ->
                            val origW = page.width
                            val origH = page.height
                            val scale = targetWidth.toFloat() / origW.toFloat()
                            val targetHeight = (origH * scale).toInt()

                            val fullBitmap = Bitmap.createBitmap(
                                targetWidth,
                                targetHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            val baseCanvas = Canvas(fullBitmap)
                            baseCanvas.drawColor(Color.WHITE)
                            page.render(
                                fullBitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                            )

                            val pageData = allAnnotationsByPage[pageIdx]
                            if (pageData != null) {
                                val (strokes, texts) = pageData
                                drawAnnotationsOnCanvas(baseCanvas, targetWidth, targetHeight, strokes, texts)
                            }

                            val compressedStream = ByteArrayOutputStream()
                            fullBitmap.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, compressedStream)
                            fullBitmap.recycle()

                            val compressedBytes = compressedStream.toByteArray()
                            val compressedBitmap = android.graphics.BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                            val pageInfo = PdfDocument.PageInfo.Builder(
                                origW,
                                origH,
                                pageIdx + 1
                            ).create()
                            val pdfPage = pdfDoc.startPage(pageInfo)
                            val canvas = pdfPage.canvas

                            val destRect = RectF(0f, 0f, origW.toFloat(), origH.toFloat())
                            if (compressedBitmap != null) {
                                canvas.drawBitmap(compressedBitmap, null, destRect, null)
                                compressedBitmap.recycle()
                            }

                            pdfDoc.finishPage(pdfPage)
                        }
                    }
                }
            }

            java.io.FileOutputStream(destFile).use { out ->
                pdfDoc.writeTo(out)
            }
            pdfDoc.close()

            val compressedSize = destFile.length()
            val savedPct = if (originalSize > 0) {
                (((originalSize - compressedSize).toFloat() / originalSize) * 100).toInt().coerceAtLeast(0)
            } else 0

            CompressionResult(
                originalSizeBytes = originalSize,
                compressedSizeBytes = compressedSize,
                savedPercentage = savedPct,
                outputFile = destFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveAnnotatedPdfOverwriting(
        filePath: String,
        allAnnotationsByPage: Map<Int, Pair<List<StrokeAnnotation>, List<TextAnnotation>>>
    ): Boolean = withContext(Dispatchers.IO) {
        if (filePath.startsWith("content://")) {
            val out = openOutputStreamForLocation(filePath) ?: return@withContext false
            val success = try {
                out.use { stream ->
                    exportAnnotatedPdf(filePath, stream, allAnnotationsByPage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            if (success) clearCache()
            return@withContext success
        }

        val originalFile = File(filePath)
        if (!originalFile.exists()) return@withContext false

        val tempFile = File(originalFile.parentFile, "tmp_${System.currentTimeMillis()}.pdf")
        val success = try {
            FileOutputStream(tempFile).use { out ->
                exportAnnotatedPdf(filePath, out, allAnnotationsByPage)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

        if (success && tempFile.exists() && tempFile.length() > 0) {
            clearCache()
            originalFile.delete()
            tempFile.renameTo(originalFile)
            true
        } else {
            tempFile.delete()
            false
        }
    }

    suspend fun exportPageAsPng(
        sourceFilePath: String,
        pageIndex: Int,
        strokes: List<StrokeAnnotation>,
        texts: List<TextAnnotation>,
        targetWidth: Int = 1400
    ): Bitmap? = withContext(Dispatchers.IO) {
        val pfd = openPfd(sourceFilePath, "r") ?: return@withContext null

        try {
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = targetWidth.toFloat() / page.width.toFloat()
                        val targetHeight = (page.height * scale).toInt()

                        val result = Bitmap.createBitmap(
                            targetWidth,
                            targetHeight,
                            Bitmap.Config.ARGB_8888
                        )
                        val canvas = Canvas(result)
                        canvas.drawColor(Color.WHITE)

                        page.render(
                            result,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )

                        drawAnnotationsOnCanvas(canvas, targetWidth, targetHeight, strokes, texts)
                        result
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    data class PositionedTextSpan(
        val text: String,
        val normLeft: Float,
        val normTop: Float,
        val normRight: Float,
        val normBottom: Float
    )

    data class ExtractedPageData(
        val fullText: String,
        val spans: List<PositionedTextSpan> = emptyList()
    )

    private val extractedTextCache = LruCache<String, Map<Int, ExtractedPageData>>(15)

    suspend fun searchInPdf(
        filePath: String,
        query: String,
        pageCount: Int,
        allPageTexts: Map<Int, List<TextAnnotation>> = emptyMap()
    ): List<PdfSearchMatch> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isBlank()) return@withContext emptyList()

        val pageContentMap = extractedTextCache.get(filePath) ?: run {
            val extracted = extractTextFromPdf(filePath, pageCount)
            extractedTextCache.put(filePath, extracted)
            extracted
        }

        val results = mutableListOf<PdfSearchMatch>()

        for (p in 0 until pageCount) {
            val pageData = pageContentMap[p] ?: ExtractedPageData("", emptyList())
            val pageText = pageData.fullText
            val lowerText = pageText.lowercase()

            // 1. Search extracted document page text
            var startIndex = 0
            while (startIndex < lowerText.length) {
                val foundIdx = lowerText.indexOf(q, startIndex)
                if (foundIdx < 0) break

                val snippetStart = (foundIdx - 28).coerceAtLeast(0)
                val snippetEnd = (foundIdx + q.length + 32).coerceAtMost(pageText.length)
                val snippet = pageText.substring(snippetStart, snippetEnd).trim()
                val prefix = if (snippetStart > 0) "..." else ""
                val suffix = if (snippetEnd < pageText.length) "..." else ""

                // Compute bounding highlight rects for this match
                val matchRects = mutableListOf<SearchHighlightRect>()

                // Check positioned spans on this page
                val matchingSpans = pageData.spans.filter { it.text.lowercase().contains(q) }
                for (span in matchingSpans) {
                    val sLower = span.text.lowercase()
                    var spanOffset = 0
                    while (spanOffset < sLower.length) {
                        val subIdx = sLower.indexOf(q, spanOffset)
                        if (subIdx < 0) break
                        val charW = (span.normRight - span.normLeft) / span.text.length.coerceAtLeast(1)
                        val left = (span.normLeft + subIdx * charW).coerceIn(0f, 1f)
                        val right = (left + q.length * charW).coerceIn(left + 0.005f, 1f)
                        matchRects.add(
                            SearchHighlightRect(
                                left = left,
                                top = span.normTop,
                                right = right,
                                bottom = span.normBottom
                            )
                        )
                        spanOffset = subIdx + q.length.coerceAtLeast(1)
                    }
                }

                // If spans had no direct match, provide smooth proportional highlight based on text position
                if (matchRects.isEmpty()) {
                    val charProgress = (foundIdx.toFloat() / pageText.length.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val estTop = (0.07f + 0.82f * charProgress).coerceIn(0.05f, 0.92f)
                    val estBottom = (estTop + 0.026f).coerceIn(0.06f, 0.95f)
                    matchRects.add(
                        SearchHighlightRect(
                            left = 0.07f,
                            top = estTop,
                            right = 0.65f,
                            bottom = estBottom
                        )
                    )
                }

                results.add(
                    PdfSearchMatch(
                        pageIndex = p,
                        snippet = "$prefix$snippet$suffix",
                        matchTerm = query,
                        source = "Document Text",
                        rects = matchRects
                    )
                )

                startIndex = foundIdx + q.length.coerceAtLeast(1)
            }

            // 2. Search user text notes & annotations on this page
            val notes = allPageTexts[p] ?: emptyList()
            for (note in notes) {
                if (note.text.lowercase().contains(q)) {
                    val noteRect = SearchHighlightRect(
                        left = (note.x - 0.02f).coerceIn(0f, 1f),
                        top = (note.y - 0.035f).coerceIn(0f, 1f),
                        right = (note.x + 0.35f).coerceIn(0f, 1f),
                        bottom = (note.y + 0.035f).coerceIn(0f, 1f)
                    )
                    results.add(
                        PdfSearchMatch(
                            pageIndex = p,
                            snippet = "Note: \"${note.text}\"",
                            matchTerm = query,
                            source = "Note Annotation",
                            rects = listOf(noteRect)
                        )
                    )
                }
            }
        }

        results
    }

    private fun extractTextFromPdf(filePath: String, pageCount: Int): Map<Int, ExtractedPageData> {
        val bytes = readBytesFromLocation(filePath) ?: return emptyMap()

        val textByPage = mutableMapOf<Int, StringBuilder>()
        val spansByPage = mutableMapOf<Int, MutableList<PositionedTextSpan>>()
        for (i in 0 until pageCount) {
            textByPage[i] = StringBuilder()
            spansByPage[i] = mutableListOf()
        }

        try {
            val textContent = String(bytes, Charsets.ISO_8859_1)

            // Extract all streams with their object IDs and stream byte offsets
            data class PdfStreamInfo(val objId: Int, val streamBytes: ByteArray, val startCharOffset: Int)
            val allStreams = mutableListOf<PdfStreamInfo>()

            // Find all "obj ... stream ... endstream" in the PDF
            var scanIdx = 0
            while (scanIdx < textContent.length) {
                val streamIdx = textContent.indexOf("stream", scanIdx)
                if (streamIdx < 0) break

                // Find the obj header before "stream"
                val objHeaderStart = (streamIdx - 200).coerceAtLeast(0)
                val objHeaderChunk = textContent.substring(objHeaderStart, streamIdx)
                val objIdMatch = Regex("""(\d+)\s+\d+\s+obj""").findAll(objHeaderChunk).lastOrNull()
                val objId = objIdMatch?.groupValues?.get(1)?.toIntOrNull() ?: (allStreams.size + 1)

                // Check for /Length <num>
                val lenMatch = Regex("""/Length\s+(\d+)""").find(objHeaderChunk)
                val explicitLen = lenMatch?.groupValues?.get(1)?.toIntOrNull()

                // Calculate stream byte start (skip "stream\r\n" or "stream\n")
                val streamStart = if (streamIdx + 7 < textContent.length && textContent[streamIdx + 6] == '\r' && textContent[streamIdx + 7] == '\n') {
                    streamIdx + 8
                } else if (streamIdx + 6 < textContent.length && textContent[streamIdx + 6] == '\n') {
                    streamIdx + 7
                } else {
                    streamIdx + 6
                }

                val streamEnd = if (explicitLen != null && explicitLen > 0 && streamStart + explicitLen <= bytes.size) {
                    streamStart + explicitLen
                } else {
                    val endstreamIdx = textContent.indexOf("endstream", streamStart)
                    if (endstreamIdx > streamStart) endstreamIdx else streamStart
                }

                if (streamEnd > streamStart && streamEnd <= bytes.size) {
                    val rawStream = bytes.copyOfRange(streamStart, streamEnd)
                    val isFlate = objHeaderChunk.contains("/FlateDecode") || objHeaderChunk.contains("/Fl")
                    val inflated = if (isFlate) inflateStream(rawStream) else rawStream
                    allStreams.add(PdfStreamInfo(objId, inflated, streamStart))
                }

                scanIdx = (streamEnd + 9).coerceAtLeast(streamIdx + 6)
            }

            // Find /Type /Page objects to associate streams with exact pages
            val pageObjRegex = Regex("""(\d+)\s+(\d+)\s+obj\b(?:(?!endobj).)*?/Type\s*/Page\b.*?endobj""", setOf(RegexOption.DOT_MATCHES_ALL))
            val pageObjects = pageObjRegex.findAll(textContent).toList()

            if (pageObjects.isNotEmpty() && pageCount > 0) {
                for (pIdx in 0 until pageCount.coerceAtMost(pageObjects.size)) {
                    val pageObj = pageObjects[pIdx].value

                    var pW = 595f
                    var pH = 842f
                    val mbMatch = Regex("""/MediaBox\s*\[\s*([\d.-]+)\s+([\d.-]+)\s+([\d.-]+)\s+([\d.-]+)\s*\]""").find(pageObj)
                    if (mbMatch != null) {
                        val wVal = mbMatch.groupValues[3].toFloatOrNull()?.minus(mbMatch.groupValues[1].toFloatOrNull() ?: 0f)
                        val hVal = mbMatch.groupValues[4].toFloatOrNull()?.minus(mbMatch.groupValues[2].toFloatOrNull() ?: 0f)
                        if (wVal != null && wVal > 50f) pW = wVal
                        if (hVal != null && hVal > 50f) pH = hVal
                    }

                    val targetObjIds = mutableListOf<Int>()
                    val contentsRef = Regex("""/Contents\s*(\d+)\s+\d+\s+R""").find(pageObj)?.groupValues?.get(1)?.toIntOrNull()
                    if (contentsRef != null) targetObjIds.add(contentsRef)

                    val arrayRefs = Regex("""/Contents\s*\[([^\]]+)\]""").find(pageObj)?.groupValues?.get(1)
                    if (arrayRefs != null) {
                        Regex("""(\d+)\s+\d+\s+R""").findAll(arrayRefs).forEach { m ->
                            m.groupValues[1].toIntOrNull()?.let { targetObjIds.add(it) }
                        }
                    }

                    val matchingStreams = allStreams.filter { it.objId in targetObjIds }
                    for (streamInfo in matchingStreams) {
                        val (streamText, streamSpans) = parsePdfTextStreamWithPositions(streamInfo.streamBytes, pW, pH)
                        if (streamText.isNotBlank()) {
                            textByPage[pIdx]?.append(" ")?.append(streamText)
                            spansByPage[pIdx]?.addAll(streamSpans)
                        }
                    }
                }
            }

            // If any page still has no text, fallback to distributing all streams evenly across pages
            val hasExtractedAny = textByPage.values.any { it.isNotBlank() }
            if (!hasExtractedAny && allStreams.isNotEmpty()) {
                for ((idx, streamInfo) in allStreams.withIndex()) {
                    val p = if (pageCount > 0) idx % pageCount else 0
                    val (streamText, streamSpans) = parsePdfTextStreamWithPositions(streamInfo.streamBytes, 595f, 842f)
                    if (streamText.isNotBlank()) {
                        textByPage[p]?.append(" ")?.append(streamText)
                        spansByPage[p]?.addAll(streamSpans)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return (0 until pageCount).associateWith { p ->
            val fullStr = textByPage[p]?.toString()?.trim() ?: ""
            val spans = spansByPage[p] ?: emptyList()
            ExtractedPageData(fullStr, spans)
        }
    }

    private fun parsePdfTextStreamWithPositions(
        data: ByteArray,
        pageWidth: Float,
        pageHeight: Float
    ): Pair<String, List<PositionedTextSpan>> {
        val text = String(data, Charsets.ISO_8859_1)
        val sb = StringBuilder()
        val spans = mutableListOf<PositionedTextSpan>()

        var curX = 40f
        var curY = 780f
        var lineStartX = 40f
        var lineStartY = 780f
        var fontSize = 12f
        var leading = 14f

        val len = text.length
        var i = 0

        while (i < len) {
            // Check for operator keywords
            if (i + 1 < len && text[i] == 'B' && text[i + 1] == 'T') {
                curX = 40f
                curY = 780f
                lineStartX = 40f
                lineStartY = 780f
                i += 2
                continue
            }

            if (text[i] == '(') {
                // String literal (text)
                i++
                val str = StringBuilder()
                var parenDepth = 1
                while (i < len && parenDepth > 0) {
                    val c = text[i]
                    if (c == '\\' && i + 1 < len) {
                        i++
                        when (val esc = text[i]) {
                            'n' -> str.append('\n')
                            'r' -> str.append('\r')
                            't' -> str.append('\t')
                            '(' -> str.append('(')
                            ')' -> str.append(')')
                            '\\' -> str.append('\\')
                            else -> str.append(esc)
                        }
                    } else if (c == '(') {
                        parenDepth++
                        str.append('(')
                    } else if (c == ')') {
                        parenDepth--
                        if (parenDepth > 0) str.append(')')
                    } else {
                        str.append(c)
                    }
                    i++
                }

                val extractedStr = str.toString()
                if (extractedStr.isNotBlank()) {
                    sb.append(extractedStr).append(" ")

                    val avgCharW = fontSize * 0.52f
                    val strW = (extractedStr.length * avgCharW).coerceAtLeast(fontSize * 0.4f)
                    val strH = fontSize * 1.25f

                    val normLeft = (curX / pageWidth).coerceIn(0f, 1f)
                    val normTop = ((pageHeight - curY - fontSize) / pageHeight).coerceIn(0f, 1f)
                    val normRight = ((curX + strW) / pageWidth).coerceIn(normLeft + 0.005f, 1f)
                    val normBottom = (normTop + (strH / pageHeight)).coerceIn(normTop + 0.008f, 1f)

                    spans.add(
                        PositionedTextSpan(
                            text = extractedStr,
                            normLeft = normLeft,
                            normTop = normTop,
                            normRight = normRight,
                            normBottom = normBottom
                        )
                    )
                    curX += strW + avgCharW
                }
            } else if (text[i] == '<' && i + 1 < len && text[i + 1] != '<') {
                // Hex string <... >
                i++
                val hex = StringBuilder()
                while (i < len && text[i] != '>') {
                    if (text[i].isLetterOrDigit()) hex.append(text[i])
                    i++
                }
                if (hex.length >= 2) {
                    val hexStr = hex.toString()
                    val decoded = StringBuilder()
                    for (k in 0 until hexStr.length - 1 step 2) {
                        val byteVal = hexStr.substring(k, k + 2).toIntOrNull(16)
                        if (byteVal != null && byteVal in 32..126) {
                            decoded.append(byteVal.toChar())
                        }
                    }
                    val extractedStr = decoded.toString()
                    if (extractedStr.isNotBlank()) {
                        sb.append(extractedStr).append(" ")

                        val avgCharW = fontSize * 0.52f
                        val strW = (extractedStr.length * avgCharW).coerceAtLeast(fontSize * 0.4f)
                        val strH = fontSize * 1.25f

                        val normLeft = (curX / pageWidth).coerceIn(0f, 1f)
                        val normTop = ((pageHeight - curY - fontSize) / pageHeight).coerceIn(0f, 1f)
                        val normRight = ((curX + strW) / pageWidth).coerceIn(normLeft + 0.005f, 1f)
                        val normBottom = (normTop + (strH / pageHeight)).coerceIn(normTop + 0.008f, 1f)

                        spans.add(
                            PositionedTextSpan(
                                text = extractedStr,
                                normLeft = normLeft,
                                normTop = normTop,
                                normRight = normRight,
                                normBottom = normBottom
                            )
                        )
                        curX += strW + avgCharW
                    }
                }
                i++
            } else {
                // Check operators like Tm, Td, TD, T*, Tf
                // Fast token checks
                if (i + 2 < len && text[i] == 'T' && text[i + 1] == 'm' && (text[i + 2].isWhitespace() || text[i + 2] == '\n' || text[i + 2] == '\r')) {
                    val prevChunk = text.substring((i - 60).coerceAtLeast(0), i).trim()
                    val nums = Regex("""([\d.-]+)""").findAll(prevChunk).mapNotNull { it.value.toFloatOrNull() }.toList()
                    if (nums.size >= 6) {
                        curX = nums[nums.size - 2]
                        curY = nums[nums.size - 1]
                        lineStartX = curX
                        lineStartY = curY
                    }
                    i += 2
                } else if (i + 2 < len && text[i] == 'T' && text[i + 1] == 'd' && (text[i + 2].isWhitespace() || text[i + 2] == '\n' || text[i + 2] == '\r')) {
                    val prevChunk = text.substring((i - 30).coerceAtLeast(0), i).trim()
                    val nums = Regex("""([\d.-]+)""").findAll(prevChunk).mapNotNull { it.value.toFloatOrNull() }.toList()
                    if (nums.size >= 2) {
                        val dx = nums[nums.size - 2]
                        val dy = nums[nums.size - 1]
                        lineStartX += dx
                        lineStartY += dy
                        curX = lineStartX
                        curY = lineStartY
                    }
                    i += 2
                } else if (i + 2 < len && text[i] == 'T' && text[i + 1] == 'D' && (text[i + 2].isWhitespace() || text[i + 2] == '\n' || text[i + 2] == '\r')) {
                    val prevChunk = text.substring((i - 30).coerceAtLeast(0), i).trim()
                    val nums = Regex("""([\d.-]+)""").findAll(prevChunk).mapNotNull { it.value.toFloatOrNull() }.toList()
                    if (nums.size >= 2) {
                        val dx = nums[nums.size - 2]
                        val dy = nums[nums.size - 1]
                        leading = -dy
                        lineStartX += dx
                        lineStartY += dy
                        curX = lineStartX
                        curY = lineStartY
                    }
                    i += 2
                } else if (i + 2 < len && text[i] == 'T' && text[i + 1] == '*' && (text[i + 2].isWhitespace() || text[i + 2] == '\n' || text[i + 2] == '\r')) {
                    lineStartY -= leading
                    curX = lineStartX
                    curY = lineStartY
                    i += 2
                } else if (i + 2 < len && text[i] == 'T' && text[i + 1] == 'f' && (text[i + 2].isWhitespace() || text[i + 2] == '\n' || text[i + 2] == '\r')) {
                    val prevChunk = text.substring((i - 20).coerceAtLeast(0), i).trim()
                    val nums = Regex("""([\d.-]+)""").findAll(prevChunk).mapNotNull { it.value.toFloatOrNull() }.toList()
                    if (nums.isNotEmpty()) {
                        fontSize = nums.last().coerceIn(6f, 72f)
                    }
                    i += 2
                } else {
                    i++
                }
            }
        }

        val cleanedText = sb.toString().replace(Regex("""\s+"""), " ").trim()
        return Pair(cleanedText, spans)
    }

    private fun inflateStream(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) return bytes
        try {
            val streamBytes = java.util.zip.InflaterInputStream(java.io.ByteArrayInputStream(bytes)).readBytes()
            if (streamBytes.isNotEmpty()) return streamBytes
        } catch (_: Exception) {}

        try {
            val inflater = java.util.zip.Inflater(true)
            val streamBytes = java.util.zip.InflaterInputStream(java.io.ByteArrayInputStream(bytes), inflater).readBytes()
            if (streamBytes.isNotEmpty()) return streamBytes
        } catch (_: Exception) {}

        return bytes
    }

    private fun drawAnnotationsOnCanvas(
        canvas: Canvas,
        width: Int,
        height: Int,
        strokes: List<StrokeAnnotation>,
        texts: List<TextAnnotation>
    ) {
        val strokePaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Draw Strokes
        for (stroke in strokes) {
            if (stroke.points.size < 2) continue

            strokePaint.color = stroke.colorArgb
            // Scale stroke width to page dimensions
            val baseScale = width / 400f
            strokePaint.strokeWidth = stroke.strokeWidthDp * baseScale

            if (stroke.isHighlighter) {
                // Ensure semi-transparent alpha for highlighting
                val origColor = stroke.colorArgb
                val a = 110 // ~43% alpha
                val r = Color.red(origColor)
                val g = Color.green(origColor)
                val b = Color.blue(origColor)
                strokePaint.color = Color.argb(a, r, g, b)
                strokePaint.strokeWidth = (stroke.strokeWidthDp * 2.8f) * baseScale
            }

            val path = Path()
            val first = stroke.points[0]
            path.moveTo(first.x * width, first.y * height)

            for (i in 1 until stroke.points.size) {
                val pt = stroke.points[i]
                path.lineTo(pt.x * width, pt.y * height)
            }
            canvas.drawPath(path, strokePaint)
        }

        // Draw Text Annotations
        val textPaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val bgPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.argb(235, 255, 255, 255)
        }
        val borderPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(180, 59, 130, 246)
        }

        for (text in texts) {
            val px = text.x * width
            val py = text.y * height
            val scaledFontSize = text.fontSizeSp * (width / 360f)
            textPaint.textSize = scaledFontSize
            textPaint.color = text.colorArgb

            val bounds = android.graphics.Rect()
            textPaint.getTextBounds(text.text, 0, text.text.length, bounds)

            val padX = 16f
            val padY = 12f
            val rect = RectF(
                px - padX,
                py - bounds.height() - padY,
                px + bounds.width() + padX,
                py + padY
            )

            // Draw rounded note card
            canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
            canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
            canvas.drawText(text.text, px, py, textPaint)
        }
    }

    /**
     * Generates beautiful, offline sample PDF documents using Android's native PdfDocument.
     * Ready immediately on the device without downloading any files!
     */
    fun generateSamplePdf(destFile: File, sampleType: Int) {
        val document = PdfDocument()

        val pageWidth = 595 // Standard A4 points at 72dpi
        val pageHeight = 842

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(15, 23, 42)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(37, 99, 235)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(30, 41, 59)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(71, 85, 105)
            textSize = 11f
            typeface = Typeface.DEFAULT
        }
        val accentBoxPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(238, 242, 255)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.rgb(199, 210, 254)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val highlightSimPaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(120, 251, 191, 36)
            style = Paint.Style.FILL
        }

        if (sampleType == 1) {
            // Document 1: PaperInk User Guide (2 Pages)
            // Page 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            // Decorative top header bar
            val topBarPaint = Paint().apply { color = Color.rgb(37, 99, 235) }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 12f, topBarPaint)

            canvas.drawText("PaperInk PDF Reader & Editor", 40f, 60f, titlePaint)
            canvas.drawText("Offline Reading, Annotation & Document Management Guide", 40f, 85f, subtitlePaint)

            canvas.drawRoundRect(RectF(40f, 105f, (pageWidth - 40).toFloat(), 185f), 10f, 10f, accentBoxPaint)
            canvas.drawRoundRect(RectF(40f, 105f, (pageWidth - 40).toFloat(), 185f), 10f, 10f, borderPaint)

            val boxTextPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(30, 58, 138)
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("Quick Overview:", 55f, 128f, boxTextPaint)
            canvas.drawText("• 100% Offline: All files & annotations stay entirely on your device with cloud-free privacy.", 55f, 148f, bodyPaint)
            canvas.drawText("• Seamless Highlighting & Pen: Highlight important sentences or handwrite notes effortlessly.", 55f, 166f, bodyPaint)

            // Section 1
            canvas.drawText("1. Text Highlighting & Annotations", 40f, 225f, headerPaint)
            canvas.drawText("Select the Highlighter tool from the bottom toolbar to emphasize important text.", 40f, 248f, bodyPaint)

            // Draw a highlighted sentence example
            val demoLine = "Highlighting is rendered with transparent blending to keep text clearly legible."
            canvas.drawRect(40f, 260f, 470f, 276f, highlightSimPaint)
            canvas.drawText(demoLine, 40f, 272f, bodyPaint)

            canvas.drawText("You can customize colors (Yellow, Cyan, Emerald, Orange, Pink) and stroke thickness.", 40f, 296f, bodyPaint)

            // Section 2
            canvas.drawText("2. Freehand Writing & Drawing", 40f, 335f, headerPaint)
            canvas.drawText("Tap the Pen tool to write margin notes, signatures, arrows, or diagrams directly on", 40f, 358f, bodyPaint)
            canvas.drawText("any PDF page. The strokes are saved automatically with sub-pixel precision.", 40f, 376f, bodyPaint)

            // Section 3
            canvas.drawText("3. Night Reading Mode & Eye Protection", 40f, 415f, headerPaint)
            canvas.drawText("Switch seamlessly between Normal, Night (Inverted Dark Theme), and Sepia paper tone", 40f, 438f, bodyPaint)
            canvas.drawText("using the moon icon in the reader top bar. Night mode protects your eyes in dark environments.", 40f, 456f, bodyPaint)

            // Section 4
            canvas.drawText("4. Custom Folders & Organization", 40f, 495f, headerPaint)
            canvas.drawText("Group your files into folders like 'Work', 'Study', 'Personal', or 'Receipts'.", 40f, 518f, bodyPaint)
            canvas.drawText("Create custom colored folders to keep your offline library well structured.", 40f, 536f, bodyPaint)

            // Section 5
            canvas.drawText("5. Mobile Direct Printing & PDF Export", 40f, 575f, headerPaint)
            canvas.drawText("Tap the Print icon in the toolbar to send your PDF straight to any Wi-Fi or Bluetooth", 40f, 598f, bodyPaint)
            canvas.drawText("printer connected to your Android device, or export your annotated document to storage.", 40f, 616f, bodyPaint)

            // Footer
            val footerPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(148, 163, 184)
                textSize = 9.5f
            }
            canvas.drawText("PaperInk PDF • Page 1 of 2", (pageWidth / 2f) - 50f, 810f, footerPaint)
            document.finishPage(page)

            // Page 2
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas

            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 12f, topBarPaint)
            canvas.drawText("Annotation Practice & Sample Form", 40f, 60f, titlePaint)
            canvas.drawText("Try testing the highlighter, pen, and text notes on this page!", 40f, 85f, subtitlePaint)

            // Form Fields simulation
            val formBoxPaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(248, 250, 252)
                style = Paint.Style.FILL
            }
            val formLinePaint = Paint().apply {
                isAntiAlias = true
                color = Color.rgb(226, 232, 240)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }

            var currentY = 130f
            val fields = listOf(
                "Document Reviewer Name:",
                "Review Date & Signature:",
                "Priority Status [High / Medium / Low]:",
                "Action Items & Feedback:"
            )

            for (field in fields) {
                canvas.drawText(field, 40f, currentY, headerPaint)
                currentY += 15f
                val boxH = if (field.contains("Action")) 110f else 40f
                canvas.drawRoundRect(RectF(40f, currentY, (pageWidth - 40).toFloat(), currentY + boxH), 6f, 6f, formBoxPaint)
                canvas.drawRoundRect(RectF(40f, currentY, (pageWidth - 40).toFloat(), currentY + boxH), 6f, 6f, formLinePaint)
                currentY += boxH + 25f
            }

            canvas.drawText("Tip: Tap the Pen icon at the bottom and sign your name in the signature box above!", 40f, currentY + 15f, subtitlePaint)
            canvas.drawText("PaperInk PDF • Page 2 of 2", (pageWidth / 2f) - 50f, 810f, footerPaint)
            document.finishPage(page)
        } else {
            // Document 2: Research & Architecture Plan (3 Pages)
            for (p in 1..3) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, p).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                val accentBarPaint = Paint().apply { color = Color.rgb(16, 185, 129) }
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), 12f, accentBarPaint)

                canvas.drawText("Architecture & Systems Report", 40f, 60f, titlePaint)
                canvas.drawText("Part $p of 3 • Cloud-Free Edge Architecture", 40f, 85f, subtitlePaint)

                canvas.drawText("Section $p.1: Offline First Storage Principles", 40f, 130f, headerPaint)
                canvas.drawText("Modern mobile applications often needlessly rely on external servers for reading", 40f, 155f, bodyPaint)
                canvas.drawText("and editing personal documents. By using device-native hardware acceleration and local", 40f, 175f, bodyPaint)
                canvas.drawText("sandboxed SQLite persistence, latency drops to zero and user privacy is guaranteed.", 40f, 195f, bodyPaint)

                // Highlighted callout
                canvas.drawRoundRect(RectF(40f, 225f, (pageWidth - 40).toFloat(), 300f), 8f, 8f, accentBoxPaint)
                canvas.drawText("Core Tenets:", 55f, 250f, subtitlePaint)
                canvas.drawText("1. Zero Network Latency: Reads happen at full memory bus bandwidth.", 55f, 270f, bodyPaint)
                canvas.drawText("2. Guaranteed Longevity: Content survives offline indefinitely.", 55f, 288f, bodyPaint)

                canvas.drawText("Section $p.2: Sub-pixel Coordinate Mapping", 40f, 340f, headerPaint)
                canvas.drawText("Drawing strokes are stored in unit coordinates [0.0..1.0] relative to the page.", 40f, 365f, bodyPaint)
                canvas.drawText("When rendered, they scale proportionally across all screen densities and rotations.", 40f, 385f, bodyPaint)

                val footerPaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.rgb(148, 163, 184)
                    textSize = 9.5f
                }
                canvas.drawText("PaperInk PDF • Page $p of 3", (pageWidth / 2f) - 50f, 810f, footerPaint)
                document.finishPage(page)
            }
        }

        FileOutputStream(destFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }
}
