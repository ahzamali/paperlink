package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.PaperInkDatabase
import com.example.data.model.DocumentEntity
import com.example.data.model.NormalizedPoint
import com.example.data.model.PageAnnotationEntity
import com.example.data.model.StrokeAnnotation
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
@Config(sdk = [36])
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
        0 to listOf(com.example.data.model.TextAnnotation(text = "Security review pending", x = 0.2f, y = 0.3f)),
        2 to listOf(com.example.data.model.TextAnnotation(text = "Confirmed performance regression on benchmark", x = 0.1f, y = 0.5f))
    )

    val matches = com.example.pdf.PdfEngine.searchInPdf(
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

      val matches = com.example.pdf.PdfEngine.searchInPdf(
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
      val result = com.example.data.model.CompressionResult(
          originalSizeBytes = 10_000_000L,
          compressedSizeBytes = 2_500_000L,
          savedPercentage = 75,
          outputFile = dummyFile
      )
      assertEquals(75, result.savedPercentage)
      assertTrue(result.originalSizeBytes - result.compressedSizeBytes == 7_500_000L)

      // Test level enum properties
      assertEquals(3, com.example.data.model.CompressionLevel.values().size)
      assertEquals("Extreme Compression", com.example.data.model.CompressionLevel.HIGH.title)
      assertEquals("Balanced (Recommended)", com.example.data.model.CompressionLevel.BALANCED.title)
      assertEquals("Light Compression", com.example.data.model.CompressionLevel.LIGHT.title)
    } finally {
      dummyFile.delete()
    }
  }
}
