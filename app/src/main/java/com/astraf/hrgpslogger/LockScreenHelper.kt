package com.astraf.hrgpslogger

import android.app.Activity
import android.app.KeyguardManager
import android.view.WindowManager

object LockScreenHelper {

    fun enable(activity: Activity) {
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)
        val keyguardManager = activity.getSystemService(KeyguardManager::class.java)
        if (keyguardManager.isKeyguardLocked) {
            keyguardManager.requestDismissKeyguard(
                activity,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = Unit
                    override fun onDismissError() = Unit
                    override fun onDismissCancelled() = Unit
                },
            )
        }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
