package com.astraf.hrgpslogger.strava

data class StravaTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val scopes: String?,
    val athleteFirstName: String?,
    val athleteLastName: String?,
)

data class StravaUploadResponse(
    val uploadId: Long,
    val externalId: String?,
    val error: String?,
    val status: String?,
    val activityId: Long?,
)

sealed class StravaUploadResult {
    data class Success(val activityId: Long) : StravaUploadResult()
    data class Failure(val message: String) : StravaUploadResult()
}
