package com.paperink.testing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.io.OutputStream

@Implements(PdfDocument::class)
class ShadowPdfDocument {

    @RealObject
    private lateinit var realPdfDocument: PdfDocument

    private val writtenPages = mutableListOf<PdfDocument.PageInfo>()

    @Implementation
    protected fun __constructor__() {
        ReflectionHelpers.setField(realPdfDocument, "mNativeDocument", 1L)
    }

    @Implementation
    protected fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        val bitmap = Bitmap.createBitmap(
            pageInfo.pageWidth.coerceAtLeast(1),
            pageInfo.pageHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        return ReflectionHelpers.callConstructor(
            PdfDocument.Page::class.java,
            ReflectionHelpers.ClassParameter.from(Canvas::class.java, canvas),
            ReflectionHelpers.ClassParameter.from(PdfDocument.PageInfo::class.java, pageInfo)
        )
    }

    @Implementation
    protected fun finishPage(page: PdfDocument.Page) {
        writtenPages.add(page.info)
    }

    @Implementation
    protected fun writeTo(out: OutputStream) {
        // Output a standard valid mock PDF header and structure
        val header = "%PDF-1.4\n%mock robolectric generated pdf content with ${writtenPages.size} pages\n"
        out.write(header.toByteArray(Charsets.UTF_8))
        for (i in 1..writtenPages.size) {
            out.write("Page $i dummy stream data\n".toByteArray(Charsets.UTF_8))
        }
        out.write("%%EOF\n".toByteArray(Charsets.UTF_8))
        out.flush()
    }

    @Implementation
    protected fun close() {
        ReflectionHelpers.setField(realPdfDocument, "mNativeDocument", 0L)
    }
}

@Implements(PdfRenderer::class)
class ShadowPdfRenderer {

    @RealObject
    private lateinit var realPdfRenderer: PdfRenderer

    private var pageCount: Int = 1

    @Implementation
    protected fun __constructor__(input: ParcelFileDescriptor) {
        try {
            val fis = java.io.FileInputStream(input.fileDescriptor)
            val channel = fis.channel
            val buf = java.nio.ByteBuffer.allocate(4096)
            channel.position(0)
            val bytesRead = channel.read(buf)
            channel.position(0)
            if (bytesRead > 0) {
                val text = String(buf.array(), 0, bytesRead, Charsets.UTF_8)
                val match = Regex("/Count\\s+(\\d+)|(\\d+)\\s+pages").find(text)
                pageCount = match?.groupValues?.get(1)?.toIntOrNull()
                    ?: match?.groupValues?.get(2)?.toIntOrNull()
                    ?: 1
            } else {
                pageCount = 1
            }
        } catch (_: Exception) {
            pageCount = 1
        }
    }

    @Implementation
    protected fun getPageCount(): Int = pageCount

    @Implementation
    protected fun openPage(index: Int): PdfRenderer.Page {
        val page = Shadow.newInstanceOf(PdfRenderer.Page::class.java)
        val shadowPage = Shadow.extract<ShadowPdfRendererPage>(page)
        shadowPage.internalPageIndex = index
        return page
    }

    @Implementation
    protected fun close() {}
}

@Implements(PdfRenderer.Page::class)
class ShadowPdfRendererPage {

    var internalPageIndex: Int = 0
    var internalWidth: Int = 612
    var internalHeight: Int = 792

    @Implementation
    protected fun getIndex(): Int = internalPageIndex

    @Implementation
    protected fun getWidth(): Int = internalWidth

    @Implementation
    protected fun getHeight(): Int = internalHeight

    @Implementation
    protected fun render(
        destination: Bitmap,
        destClip: Rect?,
        transform: Matrix?,
        renderMode: Int
    ) {
        // Enforce the strict Android framework requirement: destination MUST be ARGB_8888!
        if (destination.config != Bitmap.Config.ARGB_8888) {
            throw IllegalArgumentException("Unsupported pixel format: ${destination.config}, PdfRenderer requires ARGB_8888")
        }
        // Draw test pixels onto the destination
        val canvas = Canvas(destination)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 20f
        }
        canvas.drawText("Shadow Rendered Page $internalPageIndex", 50f, 100f, paint)
    }

    @Implementation
    protected fun close() {}
}
