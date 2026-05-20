package com.astraf.hrgpslogger.strava

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class StravaClient(
    private val clientId: String,
    private val clientSecret: String,
    private val redirectUri: String = REDIRECT_URI,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    fun isConfigured(): Boolean = clientId.isNotBlank() && clientSecret.isNotBlank()

    fun buildAuthorizationUri(state: String): String {
        return buildUri(
            base = MOBILE_AUTHORIZE_URL,
            params = mapOf(
                "client_id" to clientId,
                "redirect_uri" to redirectUri,
                "response_type" to "code",
                "approval_prompt" to "auto",
                "scope" to REQUIRED_SCOPES,
                "state" to state,
            ),
        )
    }

    fun exchangeAuthorizationCode(code: String): StravaTokenResponse {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .build()
        return parseTokenResponse(postForm(TOKEN_URL, body))
    }

    fun refreshAccessToken(refreshToken: String): StravaTokenResponse {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()
        return parseTokenResponse(postForm(TOKEN_URL, body))
    }

    fun deauthorize(accessToken: String) {
        val body = FormBody.Builder()
            .add("access_token", accessToken)
            .build()
        postForm(DEAUTHORIZE_URL, body)
    }

    fun uploadActivity(
        accessToken: String,
        tcxFile: File,
        externalId: String,
        name: String?,
    ): StravaUploadResponse {
        val fileBody = tcxFile.asRequestBody("application/vnd.garmin.tcx+xml".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("data_type", "tcx")
            .addFormDataPart("sport_type", "Ride")
            .addFormDataPart("external_id", externalId)
            .addFormDataPart("trainer", "0")
            .addFormDataPart("commute", "0")
            .apply {
                if (!name.isNullOrBlank()) {
                    addFormDataPart("name", name)
                }
            }
            .addFormDataPart("file", tcxFile.name, fileBody)
            .build()

        val request = Request.Builder()
            .url(UPLOAD_URL)
            .header("Authorization", bearer(accessToken))
            .post(multipart)
            .build()

        return parseUploadResponse(execute(request))
    }

    fun getUploadStatus(accessToken: String, uploadId: Long): StravaUploadResponse {
        val request = Request.Builder()
            .url("$UPLOAD_URL/$uploadId")
            .header("Authorization", bearer(accessToken))
            .get()
            .build()
        return parseUploadResponse(execute(request))
    }

    private fun postForm(url: String, body: FormBody): JSONObject {
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()
        return execute(request)
    }

    private fun execute(request: Request): JSONObject {
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("Strava HTTP ${response.code}: $raw")
            }
            if (raw.isBlank()) {
                return JSONObject()
            }
            return JSONObject(raw)
        }
    }

    private fun parseTokenResponse(json: JSONObject): StravaTokenResponse {
        val athlete = json.optJSONObject("athlete")
        return StravaTokenResponse(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAtEpochSeconds = json.getLong("expires_at"),
            scopes = json.optString("scope").takeIf { it.isNotBlank() },
            athleteFirstName = athlete?.optString("firstname")?.takeIf { it.isNotBlank() },
            athleteLastName = athlete?.optString("lastname")?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseUploadResponse(json: JSONObject): StravaUploadResponse {
        val activityId = json.optLong("activity_id").takeIf { it > 0L }
        return StravaUploadResponse(
            uploadId = json.optLong("id"),
            externalId = json.optString("external_id").takeIf { it.isNotBlank() },
            error = json.optString("error").takeIf { it.isNotBlank() && it != "null" },
            status = json.optString("status").takeIf { it.isNotBlank() },
            activityId = activityId,
        )
    }

    private fun bearer(accessToken: String): String = "Bearer $accessToken"

    private fun buildUri(base: String, params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$base?$query"
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        const val REDIRECT_URI = "astraf://localhost/strava-auth"
        const val REQUIRED_SCOPES = "activity:write"
        private const val MOBILE_AUTHORIZE_URL = "https://www.strava.com/oauth/mobile/authorize"
        private const val TOKEN_URL = "https://www.strava.com/api/v3/oauth/token"
        private const val DEAUTHORIZE_URL = "https://www.strava.com/oauth/deauthorize"
        private const val UPLOAD_URL = "https://www.strava.com/api/v3/uploads"
    }
}
