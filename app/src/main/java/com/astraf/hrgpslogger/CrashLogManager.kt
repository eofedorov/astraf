package com.astraf.hrgpslogger

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object CrashLogManager {

    private val isoFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    @Volatile
    private var installed = false

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    CrashLogStore.save(appContext, buildReport(appContext, thread, throwable))
                } catch (_: Exception) {
                    // ignore secondary failures while crashing
                }
                defaultHandler?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    internal fun buildReport(
        context: Context,
        thread: Thread,
        throwable: Throwable,
    ): String = buildString {
        appendLine("timestamp=${isoFormatter.format(Instant.now())}")
        appendAppInfo(context)
        appendDeviceInfo()
        appendLine("thread=${thread.name} (id=${thread.threadId()})")
        appendRecordingState(context)
        appendLine()
        appendThrowable(throwable)
    }

    private fun StringBuilder.appendAppInfo(context: Context) {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0),
        )
        appendLine("package=${context.packageName}")
        appendLine("versionName=${packageInfo.versionName}")
        appendLine("versionCode=${packageInfo.longVersionCode}")
    }

    private fun StringBuilder.appendDeviceInfo() {
        appendLine("androidSdk=${Build.VERSION.SDK_INT}")
        appendLine("device=${Build.DEVICE}")
        appendLine("manufacturer=${Build.MANUFACTURER}")
        appendLine("model=${Build.MODEL}")
        appendLine("product=${Build.PRODUCT}")
    }

    private fun StringBuilder.appendRecordingState(context: Context) {
        val active = LoggingStateStore.isActive(context)
        val paused = LoggingStateStore.isPaused(context)
        val csvFile = LoggingStateStore.getCsvFileName(context)
        appendLine("recordingActive=$active")
        appendLine("recordingPaused=$paused")
        appendLine("recordingCsvFile=${csvFile ?: ""}")
    }

    private fun StringBuilder.appendThrowable(throwable: Throwable) {
        var current: Throwable? = throwable
        var depth = 0
        while (current != null) {
            if (depth > 0) {
                appendLine()
                appendLine("Caused by:")
            }
            appendLine("${current.javaClass.name}: ${current.message}")
            appendStackTrace(current)
            current = current.cause
            depth++
        }
    }

    private fun StringBuilder.appendStackTrace(throwable: Throwable) {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        append(writer.toString().trimEnd())
        appendLine()
    }
}
