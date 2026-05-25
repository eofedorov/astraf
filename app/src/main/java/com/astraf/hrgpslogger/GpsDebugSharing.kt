package com.astraf.hrgpslogger

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object GpsDebugSharing {

    fun buildShareIntent(context: Context, jsonFile: File, subject: String): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            jsonFile,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
