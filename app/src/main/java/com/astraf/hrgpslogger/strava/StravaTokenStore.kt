package com.astraf.hrgpslogger.strava

import android.content.Context

class StravaTokenStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLinked(): Boolean = !getRefreshToken().isNullOrBlank()

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getExpiresAtEpochSeconds(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)

    fun getScopes(): String? = prefs.getString(KEY_SCOPES, null)

    fun getAthleteDisplayName(): String? = prefs.getString(KEY_ATHLETE_NAME, null)

    fun saveTokens(
        accessToken: String,
        refreshToken: String,
        expiresAtEpochSeconds: Long,
        scopes: String?,
        athleteFirstName: String?,
        athleteLastName: String?,
    ) {
        val athleteName = listOfNotNull(athleteFirstName, athleteLastName)
            .joinToString(" ")
            .trim()
            .ifBlank { null }
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAtEpochSeconds)
            .putString(KEY_SCOPES, scopes)
            .putString(KEY_ATHLETE_NAME, athleteName)
            .apply()
    }

    fun updateAccessToken(accessToken: String, refreshToken: String, expiresAtEpochSeconds: Long) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_EXPIRES_AT, expiresAtEpochSeconds)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "strava_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_SCOPES = "scopes"
        private const val KEY_ATHLETE_NAME = "athlete_name"
    }
}
