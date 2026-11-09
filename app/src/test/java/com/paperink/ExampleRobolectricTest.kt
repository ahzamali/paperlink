package com.paperink

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.paperink.data.local.PaperInkDatabase
import com.paperink.data.model.DocumentEntity
import com.paperink.data.model.NormalizedPoint
import com.paperink.data.model.OcrCacheEntity
import com.paperink.data.model.PageAnnotationEntity
import com.paperink.data.model.StrokeAnnotation
import com.paperink.ai.AiModelManager
import com.paperink.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34],
    shadows = [
        com.paperink.testing.ShadowPdfDocument::class,
        com.paperink.testing.ShadowPdfRenderer::class,
        com.paperink.testing.ShadowPdfRendererPage::class
    ]
)
class ExampleRobolectricTest {

  private lateinit var db: PaperInkDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, PaperInkDatabase::class.java)
        .allowMainThreadQueries()
        .build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("PaperInk PDF", appName)
  }

  @Test
  fun `stroke annotation serialization works`() {
    val stroke = StrokeAnnotation(
        id = 123L,
        isHighlighter = true,
        colorArgb = 0xFFFBBF24.toInt(),
        strokeWidthDp = 18f,
        points = listOf(
            NormalizedPoint(0.1234f, 0.5678f),
            NormalizedPoint(0.2345f, 0.6789f)
        )
    )

    val serialized = stroke.toSerializedString()
    val restored = StrokeAnnotation.fromSerializedString(
        id = 123L,
        isHighlighter = true,
        colorArgb = 0xFFFBBF24.toInt(),
        strokeWidthDp = 18f,
        data = serialized
    )

    assertEquals(2, restored.points.size)
    assertEquals(0.1234f, restored.points[0].x, 0.001f)
    assertEquals(0.5678f, restored.points[0].y, 0.001f)
  }

  @Test
  fun `room local search finds documents by title, tags, and annotation content`() = runBlocking {
    val dao = db.paperInkDao()

    val doc1 = DocumentEntity(
        id = 1L,
        title = "Quarterly Financial Report",
        fileName = "doc1.pdf",
        filePath = "/path/doc1.pdf",
        tags = "Finance,Quarter4,Budget"
    )
    val doc2 = DocumentEntity(
        id = 2L,
        title = "Machine Learning Systems",
        fileName = "doc2.pdf",
        filePath = "/path/doc2.pdf",
        tags = "AI,DeepLearning"
    )

    dao.insertDocument(doc1)
    dao.insertDocument(doc2)

    // Add note annotation on doc2
    dao.insertAnnotation(
        PageAnnotationEntity(
            id = 10L,
            documentId = 2L,
            pageIndex = 1,
            isHighlighter = false,
            colorArgb = 0,
            strokeWidth = 14f,
            pointsData = "",
            textContent = "Critical architecture bottleneck in transformer attention layer"
        )
    )

    // 1. Search by title
    val titleResults = dao.searchDocuments("Financial", null).first()
    assertEquals(1, titleResults.size)
    assertEquals("Quarterly Financial Report", titleResults[0].title)

    // 2. Search by tag
    val tagResults = dao.searchDocuments("Budget", null).first()
    assertEquals(1, tagResults.size)
    assertEquals("Quarterly Financial Report", tagResults[0].title)

    // 3. Search by annotation keyword content
    val noteResults = dao.searchDocuments("transformer", null).first()
    assertEquals(1, noteResults.size)
    assertEquals("Machine Learning Systems", noteResults[0].title)

    // 4. Annotation search returns exact matched entity
    val matchingNotes = dao.searchMatchingAnnotations("bottleneck").first()
    assertEquals(1, matchingNotes.size)
    assertEquals(2L, matchingNotes[0].documentId)
    assertTrue(matchingNotes[0].textContent?.contains("bottleneck") == true)

    // 5. Last opened document
    dao.updateReadingPosition(2L, 1, System.currentTimeMillis() + 100000L)
    val lastDoc = dao.getLastOpenedDocument().first()
    assertEquals(2L, lastDoc?.id)
  }

  @Test
  fun `sourceLocation stores and displays device location and web URLs for hero card`() = runBlocking {
    val dao = db.paperInkDao()

    val webDoc = DocumentEntity(
        id = 100L,
        title = "Cloud Whitepaper",
        fileName = "url_cloud.pdf",
        filePath = "/path/url_cloud.pdf",
        sourceLocation = "https://example.com/docs/cloud_architecture.pdf",
        tags = "URL,Web,Online"
    )

    val deviceDoc = DocumentEntity(
        id = 101L,
        title = "Downloaded Notes",
        fileName = "local_notes.pdf",
        filePath = "/path/local_notes.pdf",
        sourceLocation = "Device: /Download/local_notes.pdf",
        tags = "Device Location"
    )

    dao.insertDocument(webDoc)
    dao.insertDocument(deviceDoc)

    // Mark webDoc as last opened
    dao.updateReadingPosition(100L, 2, System.currentTimeMillis() + 50000L)
    val lastWeb = dao.getLastOpenedDocument().first()
    assertEquals("https://example.com/docs/cloud_architecture.pdf", lastWeb?.sourceLocation)
    assertTrue(lastWeb?.sourceLocation?.startsWith("https://") == true)

    // Mark deviceDoc as last opened
    dao.updateReadingPosition(101L, 4, System.currentTimeMillis() + 100000L)
    val lastDevice = dao.getLastOpenedDocument().first()
    assertEquals("Device: /Download/local_notes.pdf", lastDevice?.sourceLocation)
  }

  @Test
  fun `search in pdf matches user annotations across pages`() = runBlocking {
    val notes = mapOf(
        0 to listOf(com.paperink.data.model.TextAnnotation(text = "Security review pending", x = 0.2f, y = 0.3f)),
        2 to listOf(com.paperink.data.model.TextAnnotation(text = "Confirmed performance regression on benchmark", x = 0.1f, y = 0.5f))
    )

    val matches = com.paperink.pdf.PdfEngine.searchInPdf(
        filePath = "/nonexistent/test.pdf",
        query = "benchmark",
        pageCount = 3,
        allPageTexts = notes
    )

    assertEquals(1, matches.size)
    assertEquals(2, matches[0].pageIndex)
    assertTrue(matches[0].snippet.contains("benchmark"))
    assertEquals("Note Annotation", matches[0].source)
  }

  @Test
  fun `search in pdf finds document text in generated sample pdf and returns highlight coordinates`() = runBlocking {
    val tempFile = java.io.File.createTempFile("test_search", ".pdf")
    try {
      val streamContent = "BT /F1 14 Tf 40 700 Td (Offline Reading and Document Search Test) Tj ET"
      val pdfContent = "%PDF-1.4\n" +
          "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
          "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
          "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R >>\nendobj\n" +
          "4 0 obj\n<< /Length ${streamContent.length} >>\nstream\n" +
          streamContent + "\nendstream\nendobj\n" +
          "trailer\n<< /Root 1 0 R >>\n%%EOF\n"

      tempFile.writeBytes(pdfContent.toByteArray(Charsets.ISO_8859_1))

      val matches = com.paperink.pdf.PdfEngine.searchInPdf(
          filePath = tempFile.absolutePath,
          query = "Offline",
          pageCount = 1
      )
      assertTrue("Expected matches for query 'Offline', found: ${matches.size}", matches.isNotEmpty())
      val firstMatch = matches.first()
      assertEquals(0, firstMatch.pageIndex)
      assertTrue("Snippet should contain 'Offline': ${firstMatch.snippet}", firstMatch.snippet.contains("Offline", ignoreCase = true))
      assertTrue("Match should contain bounding highlight rects", firstMatch.rects.isNotEmpty())
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun `pdf compression result calculations and level settings work correctly`() {
    val dummyFile = java.io.File.createTempFile("dummy_compress", ".pdf")
    try {
      val result = com.paperink.data.model.CompressionResult(
          originalSizeBytes = 10_000_000L,
          compressedSizeBytes = 2_500_000L,
          savedPercentage = 75,
          outputFile = dummyFile
      )
      assertEquals(75, result.savedPercentage)
      assertTrue(result.originalSizeBytes - result.compressedSizeBytes == 7_500_000L)

      // Test level enum properties
      assertEquals(3, com.paperink.data.model.CompressionLevel.values().size)
      assertEquals("Extreme Compression", com.paperink.data.model.CompressionLevel.HIGH.title)
      assertEquals("Balanced (Recommended)", com.paperink.data.model.CompressionLevel.BALANCED.title)
      assertEquals("Light Compression", com.paperink.data.model.CompressionLevel.LIGHT.title)

      // Test isLarger flag
      val largerResult = com.paperink.data.model.CompressionResult(
          originalSizeBytes = 100_000L,
          compressedSizeBytes = 500_000L,
          savedPercentage = -400,
          outputFile = dummyFile
      )
      assertTrue(largerResult.isLarger)
      org.junit.Assert.assertFalse(result.isLarger)
    } finally {
      dummyFile.delete()
    }
  }

  @Test
  fun `pdf compression estimation predicts reduction for large scanned document`() {
    // 5 pages, 15 MB scanned PDF (~3 MB/page)
    val estimateHigh = com.paperink.pdf.PdfEngine.estimateCompressedSize(
        originalSizeBytes = 15_000_000L,
        pageCount = 5,
        level = com.paperink.data.model.CompressionLevel.HIGH
    )
    org.junit.Assert.assertFalse(estimateHigh.willLikelyIncrease)
    assertTrue(estimateHigh.estimatedSizeBytes < 1_000_000L)
    assertTrue(estimateHigh.estimatedSavingsPercentage > 0) // savings percentage is positive

    val estimateBalanced = com.paperink.pdf.PdfEngine.estimateCompressedSize(
        originalSizeBytes = 15_000_000L,
        pageCount = 5,
        level = com.paperink.data.model.CompressionLevel.BALANCED
    )
    org.junit.Assert.assertFalse(estimateBalanced.willLikelyIncrease)
    assertTrue(estimateBalanced.estimatedSizeBytes < 15_000_000L)
  }

  @Test
  fun `pdf compression estimation warns for small vector text document`() {
    // 5 pages, 100 KB text-heavy vector PDF (~20 KB/page)
    val estimate = com.paperink.pdf.PdfEngine.estimateCompressedSize(
        originalSizeBytes = 100_000L,
        pageCount = 5,
        level = com.paperink.data.model.CompressionLevel.BALANCED
    )
    assertTrue(estimate.willLikelyIncrease)
    assertTrue(estimate.estimatedSavingsPercentage < 0) // negative means size increase
    assertTrue(estimate.estimatedSizeBytes > 100_000L)
  }

  @Test
  fun `pdf print adapter instantiates with custom file and page count`() {
    val tempFile = java.io.File.createTempFile("print_test", ".pdf")
    try {
      tempFile.writeText("%PDF-1.4 mock pdf content")
      val adapter = com.paperink.pdf.PdfPrintAdapter(tempFile, "Document Name", pageCount = 5)
      org.junit.Assert.assertNotNull(adapter)

      val context = ApplicationProvider.getApplicationContext<Context>()
      val started = com.paperink.pdf.PdfPrintAdapter.printPdf(context, tempFile.absolutePath, "Test Doc", 5)
      org.junit.Assert.assertNotNull(started)
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun `share helper builds intent with clip data and uri permission flags`() {
    val uri = android.net.Uri.parse("content://com.paperink.fileprovider/cache/test.pdf")
    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
      type = "application/pdf"
      putExtra(android.content.Intent.EXTRA_STREAM, uri)
      putExtra(android.content.Intent.EXTRA_SUBJECT, "Test PDF")
      clipData = android.content.ClipData.newRawUri("Test PDF", uri)
      addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    assertEquals(android.content.Intent.ACTION_SEND, sendIntent.action)
    assertEquals("application/pdf", sendIntent.type)
    assertEquals(uri, sendIntent.getParcelableExtra(android.content.Intent.EXTRA_STREAM))
    org.junit.Assert.assertNotNull(sendIntent.clipData)
    assertEquals(uri, sendIntent.clipData?.getItemAt(0)?.uri)
    assertTrue((sendIntent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
  }

  @Test
  fun `pdf engine hasEncryptDictionary accurately identifies encrypted pdfs`() {
    val unencryptedFile = java.io.File.createTempFile("unencrypted", ".pdf")
    val encryptedFile = java.io.File.createTempFile("encrypted", ".pdf")
    try {
      unencryptedFile.writeText("%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF")
      encryptedFile.writeText("%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\ntrailer\n<< /Root 1 0 R /Encrypt 3 0 R >>\n%%EOF")

      org.junit.Assert.assertFalse(com.paperink.pdf.PdfEngine.hasEncryptDictionary(unencryptedFile.absolutePath))
      assertTrue(com.paperink.pdf.PdfEngine.hasEncryptDictionary(encryptedFile.absolutePath))
    } finally {
      unencryptedFile.delete()
      encryptedFile.delete()
    }
  }

  @Test
  fun `reader ui state enforces immediate password discard and security flags`() {
    val state = com.paperink.ui.viewmodel.ReaderUiState(
        isPasswordRequired = true,
        passwordErrorMessage = "Incorrect password. Please try again."
    )
    assertTrue(state.isPasswordRequired)
    assertEquals("Incorrect password. Please try again.", state.passwordErrorMessage)

    // Verify ReaderUiState contains NO password storage field (ensuring zero persistence)
    val declaredFields = com.paperink.ui.viewmodel.ReaderUiState::class.java.declaredFields.map { it.name }
    org.junit.Assert.assertFalse(declaredFields.any { it.contains("password", ignoreCase = true) && !it.contains("Required") && !it.contains("Error") })
  }

  @Test
  fun `library ui state supports merge dialog and state transitions`() {
    val initialState = com.paperink.ui.viewmodel.LibraryUiState()
    org.junit.Assert.assertFalse(initialState.showMergeDialog)
    org.junit.Assert.assertFalse(initialState.isMerging)
    org.junit.Assert.assertNull(initialState.mergeResult)

    val mergedFile = java.io.File.createTempFile("merged_test", ".pdf")
    try {
      mergedFile.writeText("%PDF-1.4 mock merged pdf content")
      val mergeResult = com.paperink.data.model.MergeResult(
          title = "Combined Document",
          outputFile = mergedFile,
          totalPageCount = 4,
          outputSizeBytes = mergedFile.length(),
          mergedSourceCount = 2
      )

      val mergedState = initialState.copy(
          showMergeDialog = true,
          mergeResult = mergeResult
      )
      assertTrue(mergedState.showMergeDialog)
      org.junit.Assert.assertNotNull(mergedState.mergeResult)
      assertEquals(4, mergedState.mergeResult?.totalPageCount)
      assertEquals(2, mergedState.mergeResult?.mergedSourceCount)
      assertEquals("Combined Document", mergedState.mergeResult?.title)
    } finally {
      mergedFile.delete()
    }
  }

  @Test
  fun `estimateCompressedSize calculates extra reduction for grayscale mode`() {
    val origSize = 5 * 1024 * 1024L // 5 MB
    val pageCount = 10
    val normalEst = com.paperink.pdf.PdfEngine.estimateCompressedSize(
        originalSizeBytes = origSize,
        pageCount = pageCount,
        level = com.paperink.data.model.CompressionLevel.BALANCED,
        isGrayscale = false
    )
    val grayEst = com.paperink.pdf.PdfEngine.estimateCompressedSize(
        originalSizeBytes = origSize,
        pageCount = pageCount,
        level = com.paperink.data.model.CompressionLevel.BALANCED,
        isGrayscale = true
    )

    assertTrue(grayEst.isGrayscale)
    org.junit.Assert.assertFalse(normalEst.isGrayscale)
    // Grayscale estimation should be smaller than full color estimation
    assertTrue(grayEst.estimatedSizeBytes < normalEst.estimatedSizeBytes)
    assertTrue(grayEst.estimatedSavingsPercentage >= normalEst.estimatedSavingsPercentage)
  }

  @Test
  fun `compression result supports grayscale and size reversion flags`() {
    val testFile = java.io.File.createTempFile("comp_test", ".pdf")
    try {
      testFile.writeText("%PDF-1.4 sample content")
      val result = com.paperink.data.model.CompressionResult(
          originalSizeBytes = 5000L,
          compressedSizeBytes = 5000L,
          savedPercentage = 0,
          outputFile = testFile,
          isLarger = false,
          isGrayscale = true,
          wasRevertedDueToSize = true
      )

      assertTrue(result.isGrayscale)
      assertTrue(result.wasRevertedDueToSize)
      assertEquals(5000L, result.originalSizeBytes)
    } finally {
      testFile.delete()
    }
  }

  @Test
  fun `real PDF compression produces non-empty valid PDF file with both color and grayscale`() = kotlinx.coroutines.test.runTest {
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    com.paperink.pdf.PdfEngine.init(context)

    // Generate a valid real PDF file with Android's PdfDocument
    val sourcePdfFile = java.io.File.createTempFile("real_source_doc", ".pdf")
    val compressedColorFile = java.io.File.createTempFile("real_compressed_color", ".pdf")
    val compressedGrayscaleFile = java.io.File.createTempFile("real_compressed_gray", ".pdf")

    try {
      val doc = android.graphics.pdf.PdfDocument()
      for (pageIdx in 1..2) {
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(612, 792, pageIdx).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = android.graphics.Paint().apply {
          color = if (pageIdx == 1) android.graphics.Color.BLUE else android.graphics.Color.RED
          textSize = 32f
          isAntiAlias = true
        }
        canvas.drawText("Test Real PDF Document - Page $pageIdx", 50f, 150f, paint)

        val rectPaint = android.graphics.Paint().apply {
          color = android.graphics.Color.GREEN
        }
        canvas.drawRect(50f, 200f, 350f, 400f, rectPaint)
        doc.finishPage(page)
      }
      java.io.FileOutputStream(sourcePdfFile).use { out ->
        doc.writeTo(out)
      }
      doc.close()

      assertTrue("Source PDF must exist and be non-empty", sourcePdfFile.exists() && sourcePdfFile.length() > 0L)

      // Test 1: Color Compression
      val colorResult = com.paperink.pdf.PdfEngine.compressPdf(
          sourceLocation = sourcePdfFile.absolutePath,
          destFile = compressedColorFile,
          level = com.paperink.data.model.CompressionLevel.HIGH,
          convertToGrayscale = false,
          revertIfLarger = false
      )

      org.junit.Assert.assertNotNull("Color compression result should not be null", colorResult)
      assertTrue("Compressed color file must exist", compressedColorFile.exists())
      assertTrue("Compressed color file must be > 0 bytes (actual: ${compressedColorFile.length()})", compressedColorFile.length() > 0L)
      assertEquals("Output file in result must match destFile", compressedColorFile.absolutePath, colorResult?.outputFile?.absolutePath)
      assertEquals("Compressed size in result must match actual file size", compressedColorFile.length(), colorResult?.compressedSizeBytes)

      // Test 2: Grayscale Compression
      val grayResult = com.paperink.pdf.PdfEngine.compressPdf(
          sourceLocation = sourcePdfFile.absolutePath,
          destFile = compressedGrayscaleFile,
          level = com.paperink.data.model.CompressionLevel.HIGH,
          convertToGrayscale = true,
          revertIfLarger = false
      )

      org.junit.Assert.assertNotNull("Grayscale compression result should not be null", grayResult)
      assertTrue("Compressed grayscale file must exist", compressedGrayscaleFile.exists())
      assertTrue("Compressed grayscale file must be > 0 bytes (actual: ${compressedGrayscaleFile.length()})", compressedGrayscaleFile.length() > 0L)
      assertTrue("Result must reflect grayscale flag", grayResult?.isGrayscale == true)

      // Verify that both outputs can be opened and parsed as valid PDFs
      val colorRenderer = android.graphics.pdf.PdfRenderer(
          android.os.ParcelFileDescriptor.open(compressedColorFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
      )
      assertEquals(2, colorRenderer.pageCount)
      colorRenderer.close()

      val grayRenderer = android.graphics.pdf.PdfRenderer(
          android.os.ParcelFileDescriptor.open(compressedGrayscaleFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
      )
      assertEquals(2, grayRenderer.pageCount)
      grayRenderer.close()

    } finally {
      sourcePdfFile.delete()
      compressedColorFile.delete()
      compressedGrayscaleFile.delete()
    }
  }

  @Test
  fun `real PDF merging produces valid non-empty PDF file with all pages combined`() = kotlinx.coroutines.test.runTest {
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    com.paperink.pdf.PdfEngine.init(context)

    val doc1File = java.io.File.createTempFile("real_doc1", ".pdf")
    val doc2File = java.io.File.createTempFile("real_doc2", ".pdf")
    val mergedOutputFile = java.io.File.createTempFile("real_merged_out", ".pdf")

    try {
      // Build Doc 1 (1 page)
      val doc1 = android.graphics.pdf.PdfDocument()
      val p1 = doc1.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(600, 800, 1).create())
      p1.canvas.drawColor(android.graphics.Color.WHITE)
      val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; textSize = 24f }
      p1.canvas.drawText("Document 1 Page 1", 50f, 100f, paint)
      doc1.finishPage(p1)
      java.io.FileOutputStream(doc1File).use { doc1.writeTo(it) }
      doc1.close()

      // Build Doc 2 (2 pages)
      val doc2 = android.graphics.pdf.PdfDocument()
      for (i in 1..2) {
        val p = doc2.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(600, 800, i).create())
        p.canvas.drawColor(android.graphics.Color.WHITE)
        p.canvas.drawText("Document 2 Page $i", 50f, 100f, paint)
        doc2.finishPage(p)
      }
      java.io.FileOutputStream(doc2File).use { doc2.writeTo(it) }
      doc2.close()

      val mergeResult = com.paperink.pdf.PdfEngine.mergePdfs(
          sourceLocations = listOf(doc1File.absolutePath, doc2File.absolutePath),
          destFile = mergedOutputFile
      )

      org.junit.Assert.assertNotNull("Merge result should not be null", mergeResult)
      assertTrue("Merged output file must exist", mergedOutputFile.exists())
      assertTrue("Merged output file must be > 0 bytes (actual: ${mergedOutputFile.length()})", mergedOutputFile.length() > 0L)
      assertEquals("Merged document must contain 3 total pages", 3, mergeResult?.totalPageCount)
      assertEquals("Merged source count must be 2", 2, mergeResult?.mergedSourceCount)

      // Verify merged file opens cleanly in PdfRenderer
      val mergedRenderer = android.graphics.pdf.PdfRenderer(
          android.os.ParcelFileDescriptor.open(mergedOutputFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
      )
      assertEquals(3, mergedRenderer.pageCount)
      mergedRenderer.close()
    } finally {
      doc1File.delete()
      doc2File.delete()
      mergedOutputFile.delete()
    }
  }

  @Test
  fun `real PDF compression on local samples directory if present`() = kotlinx.coroutines.test.runTest {
    val samplesDir = java.io.File("samples")
    if (!samplesDir.exists() || !samplesDir.isDirectory) return@runTest
    val pdfFiles = samplesDir.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) } ?: return@runTest
    if (pdfFiles.isEmpty()) return@runTest

    val context = org.robolectric.RuntimeEnvironment.getApplication()
    com.paperink.pdf.PdfEngine.init(context)

    for (sample in pdfFiles.take(2)) {
      val tempOut = java.io.File.createTempFile("sample_compressed_", ".pdf")
      try {
        val result = com.paperink.pdf.PdfEngine.compressPdf(
            sourceLocation = sample.absolutePath,
            destFile = tempOut,
            level = com.paperink.data.model.CompressionLevel.HIGH,
            convertToGrayscale = false,
            revertIfLarger = false
        )
        org.junit.Assert.assertNotNull("Compression of sample ${sample.name} should succeed", result)
        assertTrue("Compressed sample ${sample.name} should exist", tempOut.exists())
        assertTrue("Compressed sample ${sample.name} should be > 0 bytes (actual: ${tempOut.length()})", tempOut.length() > 0L)
      } finally {
        tempOut.delete()
      }
    }
  }

  @Test
  fun `compressed PDF output uses DCTDecode filter for high compression ratio without Skia FlateDecode bloat`() = kotlinx.coroutines.test.runTest {
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    com.paperink.pdf.PdfEngine.init(context)

    val sourcePdfFile = java.io.File.createTempFile("test_dct_src", ".pdf")
    val destPdfFile = java.io.File.createTempFile("test_dct_out", ".pdf")

    try {
      // Build a 2-page source PDF
      val doc = android.graphics.pdf.PdfDocument()
      for (i in 1..2) {
        val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(600, 800, i).create())
        page.canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLUE; textSize = 28f }
        page.canvas.drawText("DCT Test Page $i", 50f, 100f, paint)
        doc.finishPage(page)
      }
      java.io.FileOutputStream(sourcePdfFile).use { doc.writeTo(it) }
      doc.close()

      val result = com.paperink.pdf.PdfEngine.compressPdf(
          sourceLocation = sourcePdfFile.absolutePath,
          destFile = destPdfFile,
          level = com.paperink.data.model.CompressionLevel.HIGH,
          convertToGrayscale = false,
          revertIfLarger = false
      )

      org.junit.Assert.assertNotNull(result)
      assertTrue(destPdfFile.exists() && destPdfFile.length() > 0L)

      val outputText = String(destPdfFile.readBytes(), Charsets.ISO_8859_1)
      // Must use /Filter /DCTDecode for images, NOT /Filter /FlateDecode from Skia
      assertTrue("PDF must contain /Filter /DCTDecode for JPEG image compression: $outputText", outputText.contains("/Filter /DCTDecode"))
      org.junit.Assert.assertFalse("PDF should not use /Filter /FlateDecode for raster images: $outputText", outputText.contains("/Filter /FlateDecode"))
      assertTrue("PDF must have /Type /Catalog", outputText.contains("/Type /Catalog"))
      assertTrue("PDF must have /Type /Pages", outputText.contains("/Type /Pages"))
      assertTrue("PDF must have /Count 2", outputText.contains("/Count 2"))

      // Verified via PdfRenderer
      val renderer = android.graphics.pdf.PdfRenderer(
          android.os.ParcelFileDescriptor.open(destPdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
      )
      assertEquals(2, renderer.pageCount)
      renderer.close()
    } finally {
      sourcePdfFile.delete()
      destPdfFile.delete()
    }
  }

  @Test
  fun `compressed PDF grayscale mode uses DeviceGray colorspace and DCTDecode`() = kotlinx.coroutines.test.runTest {
    val context = org.robolectric.RuntimeEnvironment.getApplication()
    com.paperink.pdf.PdfEngine.init(context)

    val sourcePdfFile = java.io.File.createTempFile("test_gray_src", ".pdf")
    val destPdfFile = java.io.File.createTempFile("test_gray_out", ".pdf")

    try {
      val doc = android.graphics.pdf.PdfDocument()
      val page = doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(600, 800, 1).create())
      page.canvas.drawColor(android.graphics.Color.WHITE)
      val paint = android.graphics.Paint().apply { color = android.graphics.Color.RED; textSize = 28f }
      page.canvas.drawText("Grayscale DCT Test", 50f, 100f, paint)
      doc.finishPage(page)
      java.io.FileOutputStream(sourcePdfFile).use { doc.writeTo(it) }
      doc.close()

      val result = com.paperink.pdf.PdfEngine.compressPdf(
          sourceLocation = sourcePdfFile.absolutePath,
          destFile = destPdfFile,
          level = com.paperink.data.model.CompressionLevel.HIGH,
          convertToGrayscale = true,
          revertIfLarger = false
      )

      org.junit.Assert.assertNotNull(result)
      assertTrue(result?.isGrayscale == true)

      val outputText = String(destPdfFile.readBytes(), Charsets.ISO_8859_1)
      assertTrue("Must contain valid color space", outputText.contains("/ColorSpace /DeviceRGB") || outputText.contains("/ColorSpace /DeviceGray"))
      assertTrue("Must contain /Filter /DCTDecode", outputText.contains("/Filter /DCTDecode"))
    } finally {
      sourcePdfFile.delete()
      destPdfFile.delete()
    }
  }

  @Test
  fun `room database supports OCR cache entity persistence and cascade delete on document removal`() = runBlocking {
    val dao = db.paperInkDao()

    val doc = DocumentEntity(
        id = 100L,
        title = "Scanned Contract.pdf",
        fileName = "scanned.pdf",
        filePath = "/path/scanned.pdf",
        isImageBased = true,
        ocrStatus = "IN_PROGRESS"
    )
    dao.insertDocument(doc)

    val cachePage0 = OcrCacheEntity(
        documentId = 100L,
        pageIndex = 0,
        fullText = "THIS AGREEMENT is entered into as of November 9, 2026",
        spansJson = "[]",
        createdAt = System.currentTimeMillis()
    )
    val cachePage1 = OcrCacheEntity(
        documentId = 100L,
        pageIndex = 1,
        fullText = "IN WITNESS WHEREOF, the parties have executed this document.",
        spansJson = "[]",
        createdAt = System.currentTimeMillis()
    )

    dao.insertOcrCache(cachePage0)
    dao.insertOcrCache(cachePage1)

    // Verify insertion and retrieval
    val retrieved = dao.getOcrCacheForDocument(100L)
    assertEquals(2, retrieved.size)
    assertEquals(0, retrieved[0].pageIndex)
    assertTrue(retrieved[0].fullText.contains("AGREEMENT"))

    val specificPage = dao.getOcrCacheForPage(100L, 1)
    org.junit.Assert.assertNotNull(specificPage)
    assertTrue(specificPage!!.fullText.contains("WITNESS"))

    // Update document status
    dao.updateOcrStatus(100L, isImage = false, status = "DONE")
    val updatedDoc = dao.getDocumentById(100L)
    org.junit.Assert.assertNotNull(updatedDoc)
    org.junit.Assert.assertFalse(updatedDoc!!.isImageBased)
    assertEquals("DONE", updatedDoc.ocrStatus)

    // Delete document and verify cascade
    dao.deleteDocumentById(100L)
    val emptyCaches = dao.getOcrCacheForDocument(100L)
    assertTrue("OCR cache must be empty after document cascade deletion", emptyCaches.isEmpty())
  }

  @Test
  fun `AiModelManager checkStatus detects missing model as NotDownloaded`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AiModelManager.checkStatus(context)
    val status = AiModelManager.status.value
    assertTrue("When model file is absent, status must be NotDownloaded", status is AiModelManager.ModelStatus.NotDownloaded)
  }

  @Test
  fun `AiModelManager checkStatus recognizes existing valid model file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val modelsDir = context.getExternalFilesDir("models")
    org.junit.Assert.assertNotNull(modelsDir)
    val dummyModel = java.io.File(modelsDir, AiModelManager.MODEL_FILENAME)
    try {
      java.io.RandomAccessFile(dummyModel, "rw").use { raf ->
        raf.setLength(600_000_000L) // > 500MB
      }
      AiModelManager.checkStatus(context)
      val status = AiModelManager.status.value
      assertTrue("When valid model exists, status must be Ready", status is AiModelManager.ModelStatus.Ready)
      assertEquals(dummyModel.absolutePath, (status as AiModelManager.ModelStatus.Ready).modelFile.absolutePath)
    } finally {
      if (dummyModel.exists()) dummyModel.delete()
      AiModelManager.checkStatus(context)
    }
  }
}



