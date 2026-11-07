package com.paperink.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {

    fun sharePdf(
        context: Context,
        uri: Uri,
        title: String,
        chooserTitle: String = "Share PDF"
    ) {
        shareUri(
            context = context,
            contentUri = uri,
            mimeType = "application/pdf",
            subject = title,
            chooserTitle = chooserTitle
        )
    }

    fun sharePdfFile(
        context: Context,
        file: File,
        title: String,
        chooserTitle: String = "Share PDF"
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        sharePdf(context, uri, title, chooserTitle)
    }

    fun shareImageFile(
        context: Context,
        file: File,
        title: String,
        chooserTitle: String = "Share Image"
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        shareUri(
            context = context,
            contentUri = uri,
            mimeType = "image/png",
            subject = title,
            chooserTitle = chooserTitle
        )
    }

    fun shareUri(
        context: Context,
        contentUri: Uri,
        mimeType: String,
        subject: String,
        chooserTitle: String
    ) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            clipData = ClipData.newRawUri(subject, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        try {
            val resInfoList = context.packageManager.queryIntentActivities(
                chooser,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(
                    packageName,
                    contentUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (_: Exception) {}

        context.startActivity(chooser)
    }

    fun savePdfToDownloads(context: Context, sourceFile: File): Uri? {
        return try {
            val resolver = context.contentResolver
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val downloadUri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (downloadUri != null) {
                    resolver.openOutputStream(downloadUri)?.use { outStream ->
                        sourceFile.inputStream().use { it.copyTo(outStream) }
                    }
                }
                downloadUri
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val publicFile = File(downloadsDir, sourceFile.name)
                    sourceFile.copyTo(publicFile, overwrite = true)
                    Uri.fromFile(publicFile)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
