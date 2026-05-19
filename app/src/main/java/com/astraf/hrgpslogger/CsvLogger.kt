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
    private var lastBpm: Int? = null
    private var lastLocation: LocationSample? = null

    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

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
        lastBpm = null
        lastLocation = null
        _isLogging.value = true
        _phase.value = RecordingPhase.Recording
        _currentFilePath.value = file.absolutePath
        return file
    }

    fun currentFileName(): String? = currentFile()?.name

    fun hasActiveSession(): Boolean = _phase.value != RecordingPhase.Idle

    private fun openNewFile(file: File) {
        val output = BufferedWriter(
            OutputStreamWriter(FileOutputStream(file, false), StandardCharsets.UTF_8),
        )
        output.write("timestamp,bpm,latitude,longitude,accuracy_m\n")
        output.flush()
        writer = output
        lastBpm = null
        lastLocation = null
        _isLogging.value = true
        _phase.value = RecordingPhase.Recording
        _currentFilePath.value = file.absolutePath
    }

    fun restorePausedSession(fileName: String) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return
        closeWriter()
        lastBpm = null
        lastLocation = null
        _isLogging.value = false
        _phase.value = RecordingPhase.Paused
        _currentFilePath.value = file.absolutePath
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
        lastBpm = null
        lastLocation = null
        _isLogging.value = false
        _phase.value = RecordingPhase.Idle
        _currentFilePath.value = null
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

    fun writeIfChanged(bpm: Int?, location: LocationSample?) {
        if (!_isLogging.value) return

        val bpmChanged = bpm != null && bpm != lastBpm
        val locationChanged = location != null && location != lastLocation
        if (!bpmChanged && !locationChanged) return

        if (bpm != null) lastBpm = bpm
        if (location != null) lastLocation = location

        val timestamp = isoFormatter.format(Instant.now().atOffset(ZoneOffset.UTC))
        val lat = location?.latitude?.toString() ?: lastLocation?.latitude?.toString() ?: ""
        val lon = location?.longitude?.toString() ?: lastLocation?.longitude?.toString() ?: ""
        val accuracy = location?.accuracyMeters?.toString()
            ?: lastLocation?.accuracyMeters?.toString()
            ?: ""
        val bpmValue = bpm?.toString() ?: lastBpm?.toString() ?: ""

        val line = listOf(timestamp, bpmValue, lat, lon, accuracy).joinToString(",")
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
}
