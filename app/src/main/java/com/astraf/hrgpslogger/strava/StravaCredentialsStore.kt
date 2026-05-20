package com.astraf.hrgpslogger.strava

import android.content.Context

class StravaCredentialsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getClientId(): String = prefs.getString(KEY_CLIENT_ID, "").orEmpty().trim()

    fun getClientSecret(): String = prefs.getString(KEY_CLIENT_SECRET, "").orEmpty().trim()

    fun hasCredentials(): Boolean = getClientId().isNotBlank() && getClientSecret().isNotBlank()

    fun save(clientId: String, clientSecret: String) {
        prefs.edit()
            .putString(KEY_CLIENT_ID, clientId.trim())
            .putString(KEY_CLIENT_SECRET, clientSecret.trim())
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_CLIENT_ID)
            .remove(KEY_CLIENT_SECRET)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "strava_credentials"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_CLIENT_SECRET = "client_secret"
    }
}
