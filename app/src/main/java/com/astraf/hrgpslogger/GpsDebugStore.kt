package com.astraf.hrgpslogger

import android.content.Context
import java.io.File

object GpsDebugStore {

    const val SCHEMA_VERSION = 1
    const val FORMAT_JSONL = "jsonl"
    const val FORMAT_JSON = "json"

    fun debugFileName(csvFileName: String): String =
        csvFileName.removeSuffix(".csv") + ".gps-debug.json"

    fun debugFile(context: Context, csvFileName: String): File =
        debugFile(context.filesDir, csvFileName)

    fun debugFile(filesDir: File, csvFileName: String): File =
        File(filesDir, debugFileName(csvFileName))

    fun parseStartedAtMillis(csvFileName: String): Long? =
        csvFileName.removePrefix("hr_gps_").removeSuffix(".csv").toLongOrNull()
}
