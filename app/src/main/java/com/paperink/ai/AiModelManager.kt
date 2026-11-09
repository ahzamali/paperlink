package com.paperink.ai

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manages downloading, locating, and caching the on-device LiteRT-LM model.
 * Default model: Gemma 4 2B (gemma-4-E2B-it.litertlm, ~2.6 GB).
 */
object AiModelManager {

    const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
    const val MODEL_DISPLAY_NAME = "Gemma 4 2B (On-Device AI)"
    const val ESTIMATED_SIZE_BYTES = 2_600_000_000L // ~2.6 GB

    // Hugging Face direct download endpoint
    const val HF_MODEL_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

    sealed class ModelStatus {
        object NotDownloaded : ModelStatus()
        data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : ModelStatus()
        data class Ready(val modelFile: File) : ModelStatus()
        data class Error(val message: String) : ModelStatus()
    }

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    private var activeDownloadId: Long = -1L
    private var progressPollingJob: Job? = null

    /**
     * Finds the model file if it exists either in external files dir, internal files dir,
     * or standard adb push path (/data/local/tmp/gemma-4-E2B-it.litertlm).
     */
    fun findExistingModelFile(context: Context): File? {
        // 1. Check external files models dir
        val extDir = context.getExternalFilesDir("models")
        if (extDir != null) {
            val file = File(extDir, MODEL_FILENAME)
            if (file.exists() && file.length() > 500_000_000L) {
                return file
            }
        }

        // 2. Check internal files dir
        val internalFile = File(context.filesDir, MODEL_FILENAME)
        if (internalFile.exists() && internalFile.length() > 500_000_000L) {
            return internalFile
        }

        // 3. Check public Downloads/models dir (in case user downloaded manually)
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val downloadFile = File(publicDownloads, MODEL_FILENAME)
        if (downloadFile.exists() && downloadFile.length() > 500_000_000L) {
            return downloadFile
        }

        return null
    }

    private const val PREFS_NAME = "ai_model_prefs"
    private const val KEY_DOWNLOAD_ID = "active_download_id"

    private fun getSavedDownloadId(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DOWNLOAD_ID, -1L)
    }

    private fun saveDownloadId(context: Context, id: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .apply()
    }

    fun checkStatus(context: Context, coroutineScope: CoroutineScope? = null) {
        val existing = findExistingModelFile(context)
        if (existing != null) {
            saveDownloadId(context, -1L)
            _status.value = ModelStatus.Ready(existing)
            return
        }

        val savedId = if (activeDownloadId != -1L) activeDownloadId else getSavedDownloadId(context)
        if (savedId != -1L) {
            activeDownloadId = savedId
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(savedId))
            if (cursor != null && cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                cursor.close()

                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) {
                    val targetDir = context.getExternalFilesDir("models") ?: context.filesDir
                    val targetFile = File(targetDir, MODEL_FILENAME)
                    if (coroutineScope != null) {
                        startPollingProgress(context, downloadManager, savedId, targetFile, coroutineScope)
                    }
                    return
                } else if (status == DownloadManager.STATUS_FAILED) {
                    saveDownloadId(context, -1L)
                    activeDownloadId = -1L
                    _status.value = ModelStatus.NotDownloaded
                    return
                }
            } else {
                cursor?.close()
                saveDownloadId(context, -1L)
                activeDownloadId = -1L
            }
        }

        _status.value = ModelStatus.NotDownloaded
    }

    fun startDownload(context: Context, coroutineScope: CoroutineScope) {
        val existing = findExistingModelFile(context)
        if (existing != null) {
            _status.value = ModelStatus.Ready(existing)
            return
        }

        val targetDir = context.getExternalFilesDir("models") ?: context.filesDir
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, MODEL_FILENAME)

        // Delete partial incomplete file
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(HF_MODEL_URL))
            .setTitle("PaperInk AI - Gemma 4 2B")
            .setDescription("Downloading on-device intelligence model (~2.6 GB)...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, "models", MODEL_FILENAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        try {
            val downloadId = downloadManager.enqueue(request)
            activeDownloadId = downloadId
            saveDownloadId(context, downloadId)
            _status.value = ModelStatus.Downloading(0f, 0L, ESTIMATED_SIZE_BYTES)

            startPollingProgress(context, downloadManager, downloadId, targetFile, coroutineScope)
        } catch (e: Exception) {
            e.printStackTrace()
            _status.value = ModelStatus.Error("Failed to initiate download: ${e.message}")
        }
    }

    private fun startPollingProgress(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
        targetFile: File,
        coroutineScope: CoroutineScope
    ) {
        progressPollingJob?.cancel()
        progressPollingJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1

                    val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val downloaded = if (downloadedIdx >= 0) cursor.getLong(downloadedIdx) else 0L
                    var total = if (totalIdx >= 0) cursor.getLong(totalIdx) else ESTIMATED_SIZE_BYTES
                    if (total <= 0) total = ESTIMATED_SIZE_BYTES

                    val progress = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            cursor.close()
                            activeDownloadId = -1L
                            saveDownloadId(context, -1L)
                            if (targetFile.exists() && targetFile.length() > 500_000_000L) {
                                _status.value = ModelStatus.Ready(targetFile)
                            } else {
                                _status.value = ModelStatus.Error("Downloaded file is incomplete or missing")
                            }
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> {
                            cursor.close()
                            activeDownloadId = -1L
                            saveDownloadId(context, -1L)
                            _status.value = ModelStatus.Error("Download failed. Please check internet connection.")
                            return@launch
                        }
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_PENDING -> {
                            _status.value = ModelStatus.Downloading(progress, downloaded, total)
                        }
                    }
                    cursor.close()
                }
                delay(1000)
            }
        }
    }

    fun cancelDownload(context: Context) {
        val savedId = if (activeDownloadId != -1L) activeDownloadId else getSavedDownloadId(context)
        if (savedId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(savedId)
            activeDownloadId = -1L
            saveDownloadId(context, -1L)
        }
        progressPollingJob?.cancel()
        progressPollingJob = null
        checkStatus(context)
    }

    /**
     * Manually configure an existing model file on storage (e.g. pushed via adb).
     */
    fun setCustomModelFile(file: File): Boolean {
        if (file.exists() && file.length() > 500_000_000L) {
            _status.value = ModelStatus.Ready(file)
            return true
        }
        return false
    }
}
