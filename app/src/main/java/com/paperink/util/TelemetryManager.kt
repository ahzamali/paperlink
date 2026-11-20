package com.paperink.util

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.paperink.data.local.ExperimentalFeatures

/**
 * Manages anonymous application telemetry, feature adoption tracking,
 * and crash/error reporting for PaperInk PDF.
 *
 * STRICT PRIVACY GUARANTEES:
 * - Document content, text, notes, and drawings are NEVER transmitted or collected.
 * - File names, document titles, and storage file paths are NEVER collected.
 * - Document sizes and page counts are grouped into coarse anonymous buckets.
 * - All telemetry calls are strictly gated by [ExperimentalFeatures.isAnalyticsEnabled].
 */
object TelemetryManager {

    private const val TAG = "TelemetryManager"

    // Coarse size buckets (protects document identity)
    const val BUCKET_UNDER_1MB = "under_1mb"
    const val BUCKET_1MB_TO_5MB = "1mb_to_5mb"
    const val BUCKET_5MB_TO_25MB = "5mb_to_25mb"
    const val BUCKET_25MB_TO_100MB = "25mb_to_100mb"
    const val BUCKET_OVER_100MB = "over_100mb"

    // Coarse page count buckets
    const val PAGES_1_TO_5 = "1_to_5"
    const val PAGES_6_TO_20 = "6_to_20"
    const val PAGES_21_TO_50 = "21_to_50"
    const val PAGES_OVER_50 = "over_50"

    fun getSizeBucket(sizeBytes: Long): String = when {
        sizeBytes < 1_000_000L -> BUCKET_UNDER_1MB
        sizeBytes < 5_000_000L -> BUCKET_1MB_TO_5MB
        sizeBytes < 25_000_000L -> BUCKET_5MB_TO_25MB
        sizeBytes < 100_000_000L -> BUCKET_25MB_TO_100MB
        else -> BUCKET_OVER_100MB
    }

    fun getPageBucket(pageCount: Int): String = when {
        pageCount <= 5 -> PAGES_1_TO_5
        pageCount <= 20 -> PAGES_6_TO_20
        pageCount <= 50 -> PAGES_21_TO_50
        else -> PAGES_OVER_50
    }

    /**
     * Initializes Firebase Analytics and Crashlytics collection state according to
     * user consent settings.
     */
    fun init(context: Context) {
        val enabled = ExperimentalFeatures.isAnalyticsEnabled(context)
        updateCollectionState(context, enabled)
    }

    /**
     * Updates Firebase Analytics and Crashlytics SDK collection state.
     */
    fun updateCollectionState(context: Context, enabled: Boolean) {
        try {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
            Log.d(TAG, "Telemetry collection state updated: enabled=$enabled")
        } catch (e: Throwable) {
            // Graceful fallback for test or mock environments
            Log.w(TAG, "Firebase SDK not ready or running in test runner: ${e.message}")
        }
    }

    /**
     * Logs an application launch / open event.
     */
    fun logAppOpen(context: Context) {
        if (!ExperimentalFeatures.isAnalyticsEnabled(context)) return
        try {
            FirebaseAnalytics.getInstance(context).logEvent(FirebaseAnalytics.Event.APP_OPEN, Bundle())
        } catch (e: Throwable) {
            Log.w(TAG, "logAppOpen: ${e.message}")
        }
    }

    /**
     * Logs anonymous document handling statistics using coarse size and page buckets.
     * Note: Absolutely NO file names, titles, or paths are collected.
     */
    fun logPdfOpened(context: Context, sizeBytes: Long, pageCount: Int, isImageBased: Boolean) {
        if (!ExperimentalFeatures.isAnalyticsEnabled(context)) return
        try {
            val bundle = Bundle().apply {
                putString("size_bucket", getSizeBucket(sizeBytes))
                putString("page_bucket", getPageBucket(pageCount))
                putBoolean("is_image_based", isImageBased)
            }
            FirebaseAnalytics.getInstance(context).logEvent("pdf_document_opened", bundle)
        } catch (e: Throwable) {
            Log.w(TAG, "logPdfOpened: ${e.message}")
        }
    }

    /**
     * Logs invocation of a feature (e.g. "ocr_scan", "compress_pdf", "merge_pdf", "ai_assistant").
     */
    fun logFeatureUsed(context: Context, featureName: String, extraKey: String? = null, extraValue: String? = null) {
        if (!ExperimentalFeatures.isAnalyticsEnabled(context)) return
        try {
            val bundle = Bundle().apply {
                putString("feature_name", featureName)
                if (extraKey != null && extraValue != null) {
                    putString(extraKey, extraValue)
                }
            }
            FirebaseAnalytics.getInstance(context).logEvent("feature_used", bundle)
        } catch (e: Throwable) {
            Log.w(TAG, "logFeatureUsed: ${e.message}")
        }
    }

    /**
     * Logs non-fatal exceptions to Crashlytics.
     */
    fun recordException(context: Context, throwable: Throwable, contextTag: String? = null) {
        if (!ExperimentalFeatures.isAnalyticsEnabled(context)) return
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            if (contextTag != null) {
                crashlytics.setCustomKey("context_tag", contextTag)
            }
            crashlytics.recordException(throwable)
        } catch (e: Throwable) {
            Log.w(TAG, "recordException: ${e.message}")
        }
    }

    /**
     * Records a user-submitted bug report into Crashlytics and Analytics.
     */
    fun logUserBugReport(context: Context, category: String, description: String) {
        if (!ExperimentalFeatures.isAnalyticsEnabled(context)) return
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("bug_category", category)
            crashlytics.log("User Bug Report [$category]: $description")
            crashlytics.recordException(Exception("UserBugReport: $category - ${description.take(120)}"))

            val bundle = Bundle().apply {
                putString("category", category)
            }
            FirebaseAnalytics.getInstance(context).logEvent("user_bug_reported", bundle)
        } catch (e: Throwable) {
            Log.w(TAG, "logUserBugReport: ${e.message}")
        }
    }

    /**
     * Generates a non-sensitive device and environment diagnostic text for bug reports.
     */
    fun buildDiagnosticInfo(context: Context): String {
        val appVersion = "1.0.0 (versionCode 9)"
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val aiEnabled = ExperimentalFeatures.isAiAssistantEnabled(context)
        val analyticsEnabled = ExperimentalFeatures.isAnalyticsEnabled(context)
        return """
            --- System Diagnostics ---
            App Version: $appVersion
            Device: $deviceModel
            OS: $osVersion
            Experimental AI: $aiEnabled
            Analytics Enabled: $analyticsEnabled
            --------------------------
        """.trimIndent()
    }
}

