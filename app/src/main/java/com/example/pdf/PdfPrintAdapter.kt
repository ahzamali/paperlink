package com.example.pdf

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class PdfPrintAdapter(
    private val location: String,
    private val documentName: String,
    private val pageCount: Int = -1
) : PrintDocumentAdapter() {

    constructor(file: File, documentName: String, pageCount: Int = -1) : this(
        location = file.absolutePath,
        documentName = documentName,
        pageCount = pageCount
    )

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val pdi = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(if (pageCount > 0) pageCount else PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()

        val changed = newAttributes != oldAttributes
        callback?.onLayoutFinished(pdi, changed)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (destination == null) {
            callback?.onWriteFailed("Destination is null")
            return
        }

        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            input = PdfEngine.openInputStreamForLocation(location)
            if (input == null) {
                // Fallback attempt directly with File if location is a file path
                val file = File(location)
                if (file.exists()) {
                    input = FileInputStream(file)
                }
            }

            if (input == null) {
                callback?.onWriteFailed("Cannot open document for printing: $location")
                return
            }

            output = FileOutputStream(destination.fileDescriptor)

            val buf = ByteArray(16384)
            var bytesRead: Int
            while (input.read(buf).also { bytesRead = it } > 0) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                output.write(buf, 0, bytesRead)
            }
            output.flush()

            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            e.printStackTrace()
            callback?.onWriteFailed(e.message ?: "Print failed")
        } finally {
            try {
                input?.close()
                output?.close()
            } catch (_: Exception) {}
        }
    }

    companion object {
        fun printPdf(
            context: Context,
            location: String,
            docTitle: String,
            pageCount: Int = -1
        ): Boolean {
            return try {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                if (printManager == null) return false
                val jobName = "${docTitle.replace(" ", "_")}_Print"
                val adapter = PdfPrintAdapter(location, docTitle, pageCount)
                printManager.print(jobName, adapter, PrintAttributes.Builder().build())
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        fun printPdf(
            context: Context,
            file: File,
            docTitle: String,
            pageCount: Int = -1
        ): Boolean = printPdf(context, file.absolutePath, docTitle, pageCount)
    }
}
