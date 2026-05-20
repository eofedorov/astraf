package com.astraf.hrgpslogger

import android.content.Context

/**
 * Сохраняет состояние активной записи, чтобы восстановить её после убийства процесса.
 */
object LoggingStateStore {

    private const val PREFS_NAME = "logging_state"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PAUSED = "paused"
    private const val KEY_MANUAL_PAUSE = "manual_pause"
    private const val KEY_CSV_FILE = "csv_file"
    private const val KEY_BLE_ADDRESS = "ble_address"

    fun isActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACTIVE, false)

    fun isPaused(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PAUSED, false)

    /**
     * Ручная пауза пользователем. Для активных поездок без ключа считается ручной (старые версии приложения).
     */
    fun isManualPauseWhilePaused(context: Context): Boolean {
        if (!isPaused(context)) return false
        val p = prefs(context)
        return if (!p.contains(KEY_MANUAL_PAUSE)) true else p.getBoolean(KEY_MANUAL_PAUSE, true)
    }

    fun getCsvFileName(context: Context): String? =
        prefs(context).getString(KEY_CSV_FILE, null)

    fun getBleAddress(context: Context): String? =
        prefs(context).getString(KEY_BLE_ADDRESS, null)

    fun save(
        context: Context,
        csvFileName: String?,
        bleAddress: String?,
        paused: Boolean = false,
        manualPauseWhilePaused: Boolean = false,
    ) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putBoolean(KEY_PAUSED, paused)
            .putBoolean(KEY_MANUAL_PAUSE, paused && manualPauseWhilePaused)
            .putString(KEY_CSV_FILE, csvFileName)
            .putString(KEY_BLE_ADDRESS, bleAddress)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
