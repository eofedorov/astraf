package com.astraf.hrgpslogger

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider

object CrashLogSharing {

    fun buildShareIntent(context: Context): Intent? {
        val file = CrashLogStore.latestFile(context) ?: return null
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_logs_share_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
