package com.astraf.hrgpslogger

import android.content.Context
import java.io.File

data class TrackMetadata(
    val totalClimbMeters: Float,
    val pointsWithAltitude: Int,
    val pointsWithoutAltitude: Int,
    val displayName: String? = null,
)

object TrackMetadataStore {

    fun metadataFileName(csvFileName: String): String =
        csvFileName.removeSuffix(".csv") + ".metadata.json"

    fun metadataFile(context: Context, csvFileName: String): File =
        File(context.filesDir, metadataFileName(csvFileName))

    fun save(context: Context, csvFileName: String, metadata: TrackMetadata) {
        saveToFile(metadataFile(context, csvFileName), metadata)
    }

    fun load(context: Context, csvFileName: String): TrackMetadata? =
        loadFromFile(metadataFile(context, csvFileName))

    fun saveToFile(file: File, metadata: TrackMetadata) {
        file.writeText(encode(metadata))
    }

    fun loadFromFile(file: File): TrackMetadata? {
        if (!file.exists()) return null
        return decode(file.readText())
    }

    fun updateDisplayName(context: Context, csvFileName: String, displayName: String?) {
        val trimmed = displayName?.trim()?.takeIf { it.isNotEmpty() }
        val existing = load(context, csvFileName)
        val metadata = if (existing != null) {
            existing.copy(displayName = trimmed)
        } else {
            TrackMetadata(
                totalClimbMeters = 0f,
                pointsWithAltitude = 0,
                pointsWithoutAltitude = 0,
                displayName = trimmed,
            )
        }
        save(context, csvFileName, metadata)
    }

    internal fun encode(metadata: TrackMetadata): String = buildString {
        appendLine("totalClimbMeters=${metadata.totalClimbMeters}")
        appendLine("pointsWithAltitude=${metadata.pointsWithAltitude}")
        appendLine("pointsWithoutAltitude=${metadata.pointsWithoutAltitude}")
        metadata.displayName?.let { appendLine("displayName=$it") }
    }

    internal fun decode(text: String): TrackMetadata? = try {
        val values = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains('=') }
            .associate { line ->
                val parts = line.split('=', limit = 2)
                parts[0] to parts[1]
            }
        TrackMetadata(
            totalClimbMeters = values.getValue("totalClimbMeters").toFloat(),
            pointsWithAltitude = values.getValue("pointsWithAltitude").toInt(),
            pointsWithoutAltitude = values.getValue("pointsWithoutAltitude").toInt(),
            displayName = values["displayName"]?.takeIf { it.isNotEmpty() },
        )
    } catch (_: Exception) {
        null
    }

    fun delete(context: Context, csvFileName: String) {
        metadataFile(context, csvFileName).delete()
    }
}
