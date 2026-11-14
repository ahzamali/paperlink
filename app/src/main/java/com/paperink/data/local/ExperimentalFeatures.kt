package com.paperink.data.local

import android.content.Context

/**
 * Manages preferences and flags for experimental features and onboarding in PaperInk PDF.
 * Experimental features (such as on-device AI Assistant powered by Gemma 4 2B)
 * are hidden and disabled by default, and can be enabled via user menu options or first-run onboarding.
 */
object ExperimentalFeatures {
    private const val PREFS_NAME = "paperink_experimental_features"
    const val KEY_AI_ASSISTANT_ENABLED = "experimental_ai_assistant_enabled"
    const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"

    /**
     * Returns whether the experimental AI Assistant feature is enabled.
     * Defaults to false (hidden by default).
     */
    fun isAiAssistantEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AI_ASSISTANT_ENABLED, false)
    }

    /**
     * Persists the user preference for enabling or disabling the experimental AI Assistant.
     */
    fun setAiAssistantEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AI_ASSISTANT_ENABLED, enabled).apply()
    }

    /**
     * Toggles the experimental AI Assistant feature and returns the new state.
     */
    fun toggleAiAssistantEnabled(context: Context): Boolean {
        val newState = !isAiAssistantEnabled(context)
        setAiAssistantEnabled(context, newState)
        return newState
    }

    /**
     * Returns whether the user has seen or completed the first-run welcome flash screen.
     * Defaults to false for fresh installations.
     */
    fun isFirstRunCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_RUN_COMPLETED, false)
    }

    /**
     * Persists the first-run completed flag so the flash screen is not shown again.
     */
    fun setFirstRunCompleted(context: Context, completed: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_FIRST_RUN_COMPLETED, completed).apply()
    }
}
