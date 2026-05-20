package com.astraf.hrgpslogger

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class CsvLogger(private val context: Context) {

    private val _phase = MutableStateFlow(RecordingPhase.Idle)
    val phase: StateFlow<RecordingPhase> = _phase.asStateFlow()

    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

    private val _currentFilePath = MutableStateFlow<String?>(null)
    val currentFilePath: StateFlow<String?> = _currentFilePath.asStateFlow()

    private var writer: BufferedWriter? = null
    private var pendingFileName: String? = null

    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

    fun beginWaitingForGps() {
        if (_phase.value != RecordingPhase.Idle) return
        pendingFileName = "hr_gps_${System.currentTimeMillis()}.csv"
        _phase.value = RecordingPhase.WaitingForGps
        _isLogging.value = false
        _currentFilePath.value = null
    }

    fun startLoggingAfterFirstFix(): File? {
        if (_phase.value != RecordingPhase.WaitingForGps) return currentFile()
        val fileName = pendingFileName ?: "hr_gps_${System.currentTimeMillis()}.csv"
        val file = File(context.filesDir, fileName)
        openNewFile(file)
        pendingFileName = null
        return file
    }

    fun startLogging(): File? {
        if (_phase.value != RecordingPhase.Idle) return currentFile()
        val fileName = "hr_gps_${System.currentTimeMillis()}.csv"
        val file = File(context.filesDir, fileName)
        openNewFile(file)
        return file
    }

    fun resumeLogging(fileName: String): File? {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            return null
        }
        writer = BufferedWriter(
            OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.UTF_8),
        )
        _isLogging.value = true
        _phase.value = RecordingPhase.Recording
        _currentFilePath.value = file.absolutePath
        pendingFileName = null
        return file
    }

    fun currentFileName(): String? = currentFile()?.name ?: pendingFileName

    fun hasActiveSession(): Boolean = _phase.value != RecordingPhase.Idle

    private fun openNewFile(file: File) {
        val output = BufferedWriter(
            OutputStreamWriter(FileOutputStream(file, false), StandardCharsets.UTF_8),
        )
        output.write(CSV_HEADER)
        output.newLine()
        output.flush()
        writer = output
        _isLogging.value = true
        _phase.value = RecordingPhase.Recording
        _currentFilePath.value = file.absolutePath
        pendingFileName = null
    }

    fun restorePausedSession(fileName: String) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return
        closeWriter()
        _isLogging.value = false
        _phase.value = RecordingPhase.Paused
        _currentFilePath.value = file.absolutePath
        pendingFileName = null
    }

    fun pauseLogging() {
        if (_phase.value != RecordingPhase.Recording) return
        closeWriter()
        _isLogging.value = false
        _phase.value = RecordingPhase.Paused
    }

    fun resumeWriting() {
        if (_phase.value != RecordingPhase.Paused) return
        val file = currentFile() ?: return
        writer = BufferedWriter(
            OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.UTF_8),
        )
        _isLogging.value = true
        _phase.value = RecordingPhase.Recording
    }

    fun finishLogging() {
        closeWriter()
        _isLogging.value = false
        _phase.value = RecordingPhase.Idle
        _currentFilePath.value = null
        pendingFileName = null
    }

    private fun closeWriter() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {
            // ignore close errors
        } finally {
            writer = null
        }
    }

    fun stopLogging() {
        finishLogging()
    }

    fun writeAcceptedPoint(point: AcceptedGpsPoint, bpm: Int?) {
        if (!_isLogging.value) return

        val gpsTimestamp = isoFormatter.format(
            Instant.ofEpochMilli(point.timestampMillis).atOffset(ZoneOffset.UTC),
        )
        val line = listOf(
            gpsTimestamp,
            point.segmentId.toString(),
            point.latitude.toString(),
            point.longitude.toString(),
            point.accuracyMeters.toString(),
            point.derivedSpeedKmh?.toString().orEmpty(),
            point.altitudeMeters?.toString().orEmpty(),
            bpm?.toString().orEmpty(),
        ).joinToString(",")

        try {
            writer?.apply {
                write(line)
                newLine()
                flush()
            }
        } catch (e: Exception) {
            _currentFilePath.value = "Ошибка записи: ${e.message}"
        }
    }

    fun release() {
        stopLogging()
    }

    fun currentFile(): File? {
        val path = _currentFilePath.value ?: return null
        return File(path)
    }

    companion object {
        const val CSV_HEADER =
            "gps_timestamp,segment_id,latitude,longitude,accuracy_m,derived_speed_kmh,altitude,bpm"
    }
}
