package com.astraf.hrgpslogger

import android.content.Context

object CrashLogPrefs {

    private const val PREFS_NAME = "crash_log_prefs"
    private const val KEY_CAPTURE_ENABLED = "capture_enabled"

    fun isCaptureEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CAPTURE_ENABLED, false)

    fun setCaptureEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CAPTURE_ENABLED, enabled).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
