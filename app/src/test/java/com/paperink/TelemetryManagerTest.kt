package com.paperink

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.paperink.data.local.ExperimentalFeatures
import com.paperink.util.TelemetryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelemetryManagerTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testSizeBucket_categorizesCorrectly() {
        assertEquals(TelemetryManager.BUCKET_UNDER_1MB, TelemetryManager.getSizeBucket(500_000L)) // 500 KB
        assertEquals(TelemetryManager.BUCKET_1MB_TO_5MB, TelemetryManager.getSizeBucket(2_500_000L)) // 2.5 MB
        assertEquals(TelemetryManager.BUCKET_5MB_TO_25MB, TelemetryManager.getSizeBucket(15_000_000L)) // 15 MB
        assertEquals(TelemetryManager.BUCKET_25MB_TO_100MB, TelemetryManager.getSizeBucket(50_000_000L)) // 50 MB
        assertEquals(TelemetryManager.BUCKET_OVER_100MB, TelemetryManager.getSizeBucket(150_000_000L)) // 150 MB
    }

    @Test
    fun testPageBucket_categorizesCorrectly() {
        assertEquals(TelemetryManager.PAGES_1_TO_5, TelemetryManager.getPageBucket(1))
        assertEquals(TelemetryManager.PAGES_1_TO_5, TelemetryManager.getPageBucket(5))
        assertEquals(TelemetryManager.PAGES_6_TO_20, TelemetryManager.getPageBucket(12))
        assertEquals(TelemetryManager.PAGES_6_TO_20, TelemetryManager.getPageBucket(20))
        assertEquals(TelemetryManager.PAGES_21_TO_50, TelemetryManager.getPageBucket(45))
        assertEquals(TelemetryManager.PAGES_OVER_50, TelemetryManager.getPageBucket(51))
        assertEquals(TelemetryManager.PAGES_OVER_50, TelemetryManager.getPageBucket(500))
    }

    @Test
    fun testDiagnosticInfo_containsNoPiiOrFilePaths() {
        val diagnostic = TelemetryManager.buildDiagnosticInfo(application)
        assertTrue("Diagnostic info must mention app version", diagnostic.contains("1.0.0"))
        assertTrue("Diagnostic info must mention Android OS", diagnostic.contains("Android"))
        assertFalse("Diagnostic info must never contain raw file paths", diagnostic.contains("/storage/"))
        assertFalse("Diagnostic info must never contain private documents", diagnostic.contains(".pdf"))
    }

    @Test
    fun testAnalyticsConsent_defaultsToTrue_andPersistsToggles() {
        // Reset to default
        val prefs = application.getSharedPreferences("paperink_experimental_features", android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(ExperimentalFeatures.KEY_ANALYTICS_ENABLED).apply()

        // 1. Must default to true (opt-in enabled by default)
        assertTrue(
            "Anonymous analytics must be enabled by default",
            ExperimentalFeatures.isAnalyticsEnabled(application)
        )

        // 2. User toggles it off
        ExperimentalFeatures.setAnalyticsEnabled(application, false)
        assertFalse(
            "Analytics must be disabled after user opt-out",
            ExperimentalFeatures.isAnalyticsEnabled(application)
        )

        // 3. Toggle back on
        val toggled = ExperimentalFeatures.toggleAnalyticsEnabled(application)
        assertTrue("Toggling disabled analytics must return true", toggled)
        assertTrue(
            "Analytics must be enabled after toggle",
            ExperimentalFeatures.isAnalyticsEnabled(application)
        )
    }

    @Test
    fun testTelemetryLogging_doesNotThrowExceptionsWhenDisabledOrNoFirebase() {
        // Gated call tests in unit testing environment
        ExperimentalFeatures.setAnalyticsEnabled(application, false)
        TelemetryManager.logAppOpen(application)
        TelemetryManager.logPdfOpened(application, 12_000_000L, 15, false)
        TelemetryManager.logFeatureUsed(application, "ocr_scan")
        TelemetryManager.recordException(application, RuntimeException("Test Exception"), "test")
        TelemetryManager.logUserBugReport(application, "General", "Test bug description")

        // Enable and call again - all should execute without unhandled crashes
        ExperimentalFeatures.setAnalyticsEnabled(application, true)
        TelemetryManager.logAppOpen(application)
        TelemetryManager.logPdfOpened(application, 12_000_000L, 15, false)
        TelemetryManager.logFeatureUsed(application, "ocr_scan")
        TelemetryManager.recordException(application, RuntimeException("Test Exception"), "test")
        TelemetryManager.logUserBugReport(application, "General", "Test bug description")
    }
}

