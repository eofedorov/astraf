package com.astraf.hrgpslogger

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

class GpsDebugRecorder(
    private val filesDir: File,
) {

    constructor(context: Context) : this(context.applicationContext.filesDir)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private var writer: BufferedWriter? = null
    private var activeCsvFileName: String? = null
    private var nextEventIndex = 0
    private var processingConfig: GpsProcessingConfig = GpsProcessingConfig()
    private var startedAtMillis: Long? = null

    fun configure(processingConfig: GpsProcessingConfig) {
        this.processingConfig = processingConfig
    }

    fun open(csvFileName: String, startedAtMillis: Long? = GpsDebugStore.parseStartedAtMillis(csvFileName)) {
        ioScope.launch {
            writeMutex.withLock {
                openLocked(csvFileName, startedAtMillis)
            }
        }
    }

    fun appendEvent(
        csvFileName: String,
        sample: LocationSample,
        result: GpsFilterResult,
        heartRateBpm: Int?,
        sessionContext: GpsDebugSessionContext? = null,
    ) {
        ioScope.launch {
            writeMutex.withLock {
                ensureOpenLocked(csvFileName)
                val line = GpsDebugJsonCodec.encodeEventLine(
                    index = nextEventIndex++,
                    sample = sample,
                    result = result,
                    heartRateBpm = heartRateBpm,
                    sessionContext = sessionContext,
                )
                writer?.apply {
                    write(line)
                    newLine()
                    flush()
                }
            }
        }
    }

    fun finalizeFooter(
        csvFileName: String,
        summary: GpsDebugSummary,
        sessionContext: GpsDebugSessionContext? = null,
    ) {
        ioScope.launch {
            writeMutex.withLock {
                if (activeCsvFileName != csvFileName) {
                    ensureOpenLocked(csvFileName)
                }
                val line = GpsDebugJsonCodec.encodeFooterLine(summary, sessionContext)
                writer?.apply {
                    write(line)
                    newLine()
                    flush()
                }
                closeLocked()
            }
        }
    }

    fun delete(csvFileName: String) {
        ioScope.launch {
            writeMutex.withLock {
                if (activeCsvFileName == csvFileName) {
                    closeLocked()
                }
                GpsDebugStore.debugFile(filesDir, csvFileName).delete()
            }
        }
    }

    fun close() {
        ioScope.launch {
            writeMutex.withLock {
                closeLocked()
            }
        }
    }

    fun hasDebugFile(csvFileName: String): Boolean {
        val file = GpsDebugStore.debugFile(filesDir, csvFileName)
        return file.exists() && file.length() > 0
    }

    fun flushBlocking() = runBlocking(Dispatchers.IO) {
        writeMutex.withLock {
            writer?.flush()
        }
    }

    fun finalizeFooterBlocking(
        csvFileName: String,
        summary: GpsDebugSummary,
        sessionContext: GpsDebugSessionContext? = null,
    ) = runBlocking(Dispatchers.IO) {
        writeMutex.withLock {
            ensureOpenLocked(csvFileName)
            val line = GpsDebugJsonCodec.encodeFooterLine(summary, sessionContext)
            writer?.apply {
                write(line)
                newLine()
                flush()
            }
            closeLocked()
        }
    }

    fun deleteBlocking(csvFileName: String) = runBlocking(Dispatchers.IO) {
        writeMutex.withLock {
            if (activeCsvFileName == csvFileName) {
                closeLocked()
            }
            GpsDebugStore.debugFile(filesDir, csvFileName).delete()
        }
    }

    fun openBlocking(csvFileName: String, startedAtMillis: Long? = GpsDebugStore.parseStartedAtMillis(csvFileName)) =
        runBlocking(Dispatchers.IO) {
            writeMutex.withLock {
                openLocked(csvFileName, startedAtMillis)
            }
        }

    private fun ensureOpenLocked(csvFileName: String) {
        if (activeCsvFileName == csvFileName && writer != null) return
        openLocked(csvFileName, GpsDebugStore.parseStartedAtMillis(csvFileName))
    }

    private fun openLocked(csvFileName: String, startedAtMillis: Long?) {
        if (activeCsvFileName == csvFileName && writer != null) return
        closeLocked()

        val file = GpsDebugStore.debugFile(filesDir, csvFileName)
        val append = file.exists() && file.length() > 0

        activeCsvFileName = csvFileName
        this.startedAtMillis = startedAtMillis ?: GpsDebugStore.parseStartedAtMillis(csvFileName)

        if (append) {
            nextEventIndex = GpsDebugJsonCodec.countEventLines(file)
            writer = BufferedWriter(
                OutputStreamWriter(FileOutputStream(file, true), StandardCharsets.UTF_8),
            )
        } else {
            nextEventIndex = 0
            writer = BufferedWriter(
                OutputStreamWriter(FileOutputStream(file, false), StandardCharsets.UTF_8),
            )
            val headerLine = GpsDebugJsonCodec.encodeHeaderLine(
                csvFileName = csvFileName,
                startedAtMillis = this.startedAtMillis,
                processingConfig = processingConfig,
            )
            writer?.apply {
                write(headerLine)
                newLine()
                flush()
            }
        }
    }

    private fun closeLocked() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {
        } finally {
            writer = null
            activeCsvFileName = null
        }
    }
}
