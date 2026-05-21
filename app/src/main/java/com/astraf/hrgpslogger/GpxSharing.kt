package com.astraf.hrgpslogger

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object GpxSharing {

    fun buildShareIntent(context: Context, gpxFile: File, subject: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            gpxFile,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun exportCacheFile(context: Context, csvFileName: String): File {
        val dir = File(context.cacheDir, "gpx_exports").apply { mkdirs() }
        return File(dir, GpxExporter.fileNameForCsv(csvFileName))
    }
}
