package com.astraf.hrgpslogger.strava

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.astraf.hrgpslogger.R
import com.astraf.hrgpslogger.TrackCsvParser
import com.astraf.hrgpslogger.TrackMetadataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class StravaIntegration(context: Context) {

    private val appContext = context.applicationContext
    private val credentialsStore = StravaCredentialsStore(appContext)
    private val tokenStore = StravaTokenStore(appContext)

    private val _linkState = MutableStateFlow(readLinkState())
    val linkState: StateFlow<StravaLinkState> = _linkState.asStateFlow()

    private val _uploadStatus = MutableStateFlow<StravaUploadUiStatus?>(null)
    val uploadStatus: StateFlow<StravaUploadUiStatus?> = _uploadStatus.asStateFlow()

    private val _linkMessage = MutableStateFlow<String?>(null)
    val linkMessage: StateFlow<String?> = _linkMessage.asStateFlow()

    fun getClientId(): String = credentialsStore.getClientId()

    fun getClientSecret(): String = credentialsStore.getClientSecret()

    fun hasApiCredentials(): Boolean = credentialsStore.hasCredentials()

    fun isLinked(): Boolean = tokenStore.isLinked()

    fun saveApiCredentials(clientId: String, clientSecret: String) {
        credentialsStore.save(clientId, clientSecret)
        _linkState.value = readLinkState()
    }

    fun clearLinkMessage() {
        _linkMessage.value = null
    }

    fun setLinkMessage(message: String?) {
        _linkMessage.value = message
    }

    fun buildAuthorizationIntent(clientId: String, clientSecret: String): Intent? {
        clearLinkMessage()
        val trimmedId = clientId.trim()
        val trimmedSecret = clientSecret.trim()
        if (trimmedId.isBlank() || trimmedSecret.isBlank()) {
            setLinkMessage(appContext.getString(R.string.strava_credentials_required))
            return null
        }
        saveApiCredentials(trimmedId, trimmedSecret)
        val client = createClient()
        val state = UUID.randomUUID().toString()
        pendingOAuthState = state
        val uri = Uri.parse(client.buildAuthorizationUri(state))
        return Intent(Intent.ACTION_VIEW, uri)
    }

    suspend fun handleAuthorizationCallback(uri: Uri?): StravaLinkResult = withContext(Dispatchers.IO) {
        if (!hasApiCredentials()) {
            val message = appContext.getString(R.string.strava_credentials_required)
            _linkMessage.value = message
            return@withContext StravaLinkResult.Failure(message)
        }
        if (uri == null) {
            return@withContext StravaLinkResult.Failure("Пустой ответ Strava")
        }
        uri.getQueryParameter("error")?.let { error ->
            pendingOAuthState = null
            val message = "Strava: $error"
            _linkMessage.value = message
            return@withContext StravaLinkResult.Failure(message)
        }
        val state = uri.getQueryParameter("state")
        val expectedState = pendingOAuthState
        pendingOAuthState = null
        if (expectedState == null || state != expectedState) {
            return@withContext StravaLinkResult.Failure("Неверный state OAuth")
        }
        val code = uri.getQueryParameter("code")
            ?: return@withContext StravaLinkResult.Failure("Код авторизации не получен")
        try {
            val token = createClient().exchangeAuthorizationCode(code)
            tokenStore.saveTokens(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresAtEpochSeconds = token.expiresAtEpochSeconds,
                scopes = token.scopes,
                athleteFirstName = token.athleteFirstName,
                athleteLastName = token.athleteLastName,
            )
            _linkState.value = readLinkState()
            _linkMessage.value = null
            StravaLinkResult.Success
        } catch (e: Exception) {
            val message = e.message ?: "Ошибка привязки Strava"
            _linkMessage.value = message
            StravaLinkResult.Failure(message)
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        val accessToken = resolveAccessToken()
        if (accessToken != null) {
            try {
                createClient().deauthorize(accessToken)
            } catch (_: Exception) {
            }
        }
        tokenStore.clear()
        _linkState.value = readLinkState()
    }

    suspend fun uploadTrack(csvFileName: String): StravaUploadResult = withContext(Dispatchers.IO) {
        if (!isLinked()) {
            val message = appContext.getString(R.string.strava_link_required)
            _uploadStatus.value = StravaUploadUiStatus.Failed(message)
            return@withContext StravaUploadResult.Failure(message)
        }
        if (!hasApiCredentials()) {
            val message = appContext.getString(R.string.strava_credentials_required)
            _uploadStatus.value = StravaUploadUiStatus.Failed(message)
            return@withContext StravaUploadResult.Failure(message)
        }
        _uploadStatus.value = StravaUploadUiStatus.Exporting
        val csvFile = File(appContext.filesDir, csvFileName)
        if (!csvFile.exists()) {
            val message = "Файл трека не найден"
            _uploadStatus.value = StravaUploadUiStatus.Failed(message)
            return@withContext StravaUploadResult.Failure(message)
        }
        val samples = TrackCsvParser.parseSamples(csvFile)
        if (samples.isEmpty()) {
            val message = "В треке нет точек для загрузки"
            _uploadStatus.value = StravaUploadUiStatus.Failed(message)
            return@withContext StravaUploadResult.Failure(message)
        }

        val accessToken = try {
            resolveAccessToken()
        } catch (e: Exception) {
            val message = e.message ?: "Strava не привязана"
            _uploadStatus.value = StravaUploadUiStatus.Failed(message)
            return@withContext StravaUploadResult.Failure(message)
        } ?: run {
            val message = appContext.getString(R.string.strava_link_required)
            _uploadStatus.value = StravaUploadUiStatus.Failed(message)
            return@withContext StravaUploadResult.Failure(message)
        }

        val tcxDir = File(appContext.cacheDir, "strava_uploads")
        val externalId = TcxExporter.externalIdForCsv(csvFileName)
        val tcxFile = File(tcxDir, externalId)
        try {
            TcxExporter.exportToFile(samples, tcxFile)
            _uploadStatus.value = StravaUploadUiStatus.Uploading
            val startedAtMillis = samples.first().point.timestampMillis
            val activityName = formatActivityName(startedAtMillis)
            val upload = createClient().uploadActivity(
                accessToken = accessToken,
                tcxFile = tcxFile,
                externalId = externalId,
                name = activityName,
            )
            if (upload.error != null) {
                val message = upload.error
                _uploadStatus.value = StravaUploadUiStatus.Failed(message)
                return@withContext StravaUploadResult.Failure(message)
            }
            upload.activityId?.let { activityId ->
                persistStravaActivityId(csvFileName, activityId)
                _uploadStatus.value = StravaUploadUiStatus.Success(activityId)
                return@withContext StravaUploadResult.Success(activityId)
            }

            _uploadStatus.value = StravaUploadUiStatus.Processing
            val result = pollUploadCompletion(accessToken, upload.uploadId)
            when (result) {
                is StravaUploadResult.Success -> {
                    persistStravaActivityId(csvFileName, result.activityId)
                    _uploadStatus.value = StravaUploadUiStatus.Success(result.activityId)
                }
                is StravaUploadResult.Failure ->
                    _uploadStatus.value = StravaUploadUiStatus.Failed(result.message)
            }
            result
        } catch (e: Exception) {
            val message = e.message ?: "Ошибка загрузки в Strava"
            _uploadStatus.value = StravaUploadUiStatus.Failed(message)
            StravaUploadResult.Failure(message)
        } finally {
            tcxFile.delete()
        }
    }

    fun clearUploadStatus() {
        _uploadStatus.value = null
    }

    fun buildActivityUri(activityId: Long): Uri =
        Uri.parse("https://www.strava.com/activities/$activityId")

    private fun persistStravaActivityId(csvFileName: String, activityId: Long) {
        TrackMetadataStore.updateStravaActivityId(appContext, csvFileName, activityId)
    }

    private fun createClient(): StravaClient = StravaClient(
        clientId = credentialsStore.getClientId(),
        clientSecret = credentialsStore.getClientSecret(),
    )

    private suspend fun pollUploadCompletion(
        accessToken: String,
        uploadId: Long,
    ): StravaUploadResult {
        repeat(MAX_POLL_ATTEMPTS) {
            delay(POLL_INTERVAL_MS)
            val status = createClient().getUploadStatus(accessToken, uploadId)
            status.activityId?.let { activityId ->
                return StravaUploadResult.Success(activityId)
            }
            status.error?.let { error ->
                return StravaUploadResult.Failure(error)
            }
            val message = status.status.orEmpty()
            if (message.contains("ready", ignoreCase = true)) {
                return StravaUploadResult.Failure("Strava обработала файл, но activity_id не получен")
            }
            if (message.contains("error", ignoreCase = true)) {
                return StravaUploadResult.Failure(message)
            }
        }
        return StravaUploadResult.Failure("Таймаут ожидания обработки Strava")
    }

    private suspend fun resolveAccessToken(): String? {
        if (!hasApiCredentials()) return null
        val existing = tokenStore.getAccessToken() ?: return null
        val refreshToken = tokenStore.getRefreshToken() ?: return null
        val expiresAt = tokenStore.getExpiresAtEpochSeconds()
        val now = System.currentTimeMillis() / 1000L
        if (expiresAt - now > TOKEN_REFRESH_LEAD_SECONDS) {
            return existing
        }
        val refreshed = createClient().refreshAccessToken(refreshToken)
        tokenStore.updateAccessToken(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken,
            expiresAtEpochSeconds = refreshed.expiresAtEpochSeconds,
        )
        return refreshed.accessToken
    }

    private fun formatActivityName(startedAtMillis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        return formatter.format(
            Instant.ofEpochMilli(startedAtMillis).atZone(ZoneId.systemDefault()),
        )
    }

    private fun readLinkState(): StravaLinkState {
        if (!isLinked()) {
            return StravaLinkState.NotLinked
        }
        return StravaLinkState.Linked(
            athleteName = tokenStore.getAthleteDisplayName(),
            scopes = tokenStore.getScopes(),
        )
    }

    companion object {
        private const val TOKEN_REFRESH_LEAD_SECONDS = 600L
        private const val POLL_INTERVAL_MS = 1_000L
        private const val MAX_POLL_ATTEMPTS = 45

        @Volatile
        private var pendingOAuthState: String? = null
    }
}

sealed class StravaLinkState {
    data object NotLinked : StravaLinkState()
    data class Linked(val athleteName: String?, val scopes: String?) : StravaLinkState()
}

sealed class StravaLinkResult {
    data object Success : StravaLinkResult()
    data class Failure(val message: String) : StravaLinkResult()
}

sealed class StravaUploadUiStatus {
    data object Exporting : StravaUploadUiStatus()
    data object Uploading : StravaUploadUiStatus()
    data object Processing : StravaUploadUiStatus()
    data class Success(val activityId: Long) : StravaUploadUiStatus()
    data class Failed(val message: String) : StravaUploadUiStatus()
}
