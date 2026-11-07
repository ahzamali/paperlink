package com.example

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.CompressionLevel
import com.example.pdf.PdfEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

  @Test
  fun testCompressionWithNewEngine() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    PdfEngine.init(context)

    val testFiles = listOf(
        "receipt.pdf" to File("/data/local/tmp/receipt.pdf"),
        "eroll.pdf" to File("/data/local/tmp/eroll.pdf")
    )

    for ((label, src) in testFiles) {
      if (!src.exists()) continue

      val pfd = ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY)
      val srcRenderer = PdfRenderer(pfd)
      val pageCount = srcRenderer.pageCount
      srcRenderer.close()
      pfd.close()

      println("=================================================================")
      println("VALIDATING NEW PDFENGINE COMPRESSION: $label (${src.name})")
      println("Original Size: ${src.length()} bytes (${src.length() / 1024} KB), Pages: $pageCount")

      for (level in CompressionLevel.values()) {
        val destFile = File(context.cacheDir, "new_engine_${label}_${level.name}.pdf")
        val startTime = System.currentTimeMillis()

        val result = PdfEngine.compressPdf(
            sourceLocation = src.absolutePath,
            destFile = destFile,
            level = level,
            convertToGrayscale = false,
            revertIfLarger = false
        )
        val elapsed = System.currentTimeMillis() - startTime

        assertNotNull("Result should not be null", result)
        assertTrue("Destination file should exist", destFile.exists())
        val compSize = destFile.length()
        assertTrue("Compressed size should be > 0", compSize > 0L)

        // Validate that output uses /Filter /DCTDecode
        val text = String(destFile.readBytes(), Charsets.ISO_8859_1)
        assertTrue("Must contain /Filter /DCTDecode", text.contains("/Filter /DCTDecode"))
        assertTrue("Must NOT contain /Filter /FlateDecode", !text.contains("/Filter /FlateDecode"))

        // Validate that Android's native PdfRenderer can open all pages
        val checkPfd = ParcelFileDescriptor.open(destFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val checkRenderer = PdfRenderer(checkPfd)
        assertEquals("Page count must match source", pageCount, checkRenderer.pageCount)
        checkRenderer.close()
        checkPfd.close()

        val savingsPct = ((src.length() - compSize).toDouble() / src.length()) * 100.0

        println("-----------------------------------------------------------------")
        println("Level: ${level.name}")
        println("  Actual Size: $compSize bytes (${compSize / 1024} KB) in ${elapsed}ms")
        println("  Savings vs Original: ${String.format("%.1f", savingsPct)}%")
        println("  Verified via native PdfRenderer: $pageCount pages ok")

        destFile.delete()
      }
      println("=================================================================\n")
    }
  }
}
