package com.astraf.hrgpslogger

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CrashLogEntry(
    val file: File,
    val timestampMillis: Long,
)

object CrashLogStore {

    private const val DIR_NAME = "crash_logs"
    private const val FILE_PREFIX = "crash_"
    private const val FILE_SUFFIX = ".txt"
    const val MAX_LOG_FILES = 20

    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")
        .withZone(ZoneOffset.UTC)

    fun crashLogsDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_NAME)

    fun saveToFile(dir: File, content: String, timestampMillis: Long = System.currentTimeMillis()): File {
        dir.mkdirs()
        val file = File(dir, fileNameFor(timestampMillis))
        file.writeText(content)
        pruneOldFiles(dir, MAX_LOG_FILES)
        return file
    }

    fun save(context: Context, content: String, timestampMillis: Long = System.currentTimeMillis()): File =
        saveToFile(crashLogsDir(context), content, timestampMillis)

    fun list(context: Context): List<CrashLogEntry> =
        listFromDir(crashLogsDir(context))

    fun listFromDir(dir: File): List<CrashLogEntry> {
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        }
            ?.map { file ->
                CrashLogEntry(
                    file = file,
                    timestampMillis = timestampFromFileName(file.name) ?: file.lastModified(),
                )
            }
            ?.sortedByDescending { it.timestampMillis }
            ?: emptyList()
    }

    fun latest(context: Context): CrashLogEntry? = list(context).firstOrNull()

    fun latestFile(context: Context): File? = latest(context)?.file

    fun readText(entry: CrashLogEntry): String = entry.file.readText()

    fun clear(context: Context) {
        val dir = crashLogsDir(context)
        if (!dir.exists()) return
        dir.listFiles()?.forEach { it.delete() }
    }

    fun clearDir(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { it.delete() }
    }

    internal fun fileNameFor(timestampMillis: Long): String {
        val instant = Instant.ofEpochMilli(timestampMillis)
        return FILE_PREFIX + fileNameFormatter.format(instant) + FILE_SUFFIX
    }

    internal fun timestampFromFileName(fileName: String): Long? {
        if (!fileName.startsWith(FILE_PREFIX) || !fileName.endsWith(FILE_SUFFIX)) return null
        val stem = fileName.removePrefix(FILE_PREFIX).removeSuffix(FILE_SUFFIX)
        return try {
            val parsed = fileNameFormatter.parse(stem, Instant::from)
            parsed.toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    internal fun pruneOldFiles(dir: File, maxFiles: Int) {
        if (!dir.exists() || maxFiles <= 0) return
        val files = dir.listFiles { file ->
            file.isFile && file.name.startsWith(FILE_PREFIX) && file.name.endsWith(FILE_SUFFIX)
        } ?: return
        if (files.size <= maxFiles) return
        files
            .sortedBy { it.lastModified() }
            .take(files.size - maxFiles)
            .forEach { it.delete() }
    }
}
