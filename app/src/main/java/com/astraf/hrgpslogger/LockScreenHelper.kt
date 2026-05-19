package com.astraf.hrgpslogger

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.view.WindowManager

object LockScreenHelper {

    fun enable(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
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
        } else {
            @Suppress("DEPRECATION")
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
