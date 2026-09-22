package com.antigravity.screensaver.ui.dream

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.view.WindowManager
import android.widget.Toast

/**
 * Trampoline activity to bridge between the DreamService (screensaver) / Lockscreen
 * and the system Clock / Alarm app.
 *
 * Configured with showWhenLocked and turnScreenOn, allowing it to display over
 * the lockscreen and request keyguard dismissal so the user can authenticate
 * (PIN/fingerprint/face) and land directly in the Clock app / alarm settings.
 */
class LaunchClockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lockscreen so we can request authentication
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

        if (keyguardManager != null && keyguardManager.isKeyguardLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        launchClockTarget()
                        finish()
                    }

                    override fun onDismissCancelled() {
                        finish()
                    }

                    override fun onDismissError() {
                        launchClockTarget()
                        finish()
                    }
                })
            } else {
                launchClockTarget()
                finish()
            }
        } else {
            launchClockTarget()
            finish()
        }
    }

    private fun launchClockTarget() {
        val pm = packageManager

        // 1. Google Clock (Pixel / stock Android) - explicit intent for alarms tab
        try {
            val googleClockIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                setPackage("com.google.android.deskclock")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (googleClockIntent.resolveActivity(pm) != null) {
                startActivity(googleClockIntent)
                Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("LaunchClockActivity", "Failed to start Google Clock via ACTION_SHOW_ALARMS: ${e.message}")
        }

        // 2. Google Clock launch intent
        val googleClockLaunch = pm.getLaunchIntentForPackage("com.google.android.deskclock")
        if (googleClockLaunch != null) {
            try {
                googleClockLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(googleClockLaunch)
                Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                android.util.Log.e("LaunchClockActivity", "Failed to start Google Clock launch intent: ${e.message}")
            }
        }

        // 3. Samsung Clock launch intent
        val samsungClockLaunch = pm.getLaunchIntentForPackage("com.sec.android.app.clockpackage")
        if (samsungClockLaunch != null) {
            try {
                samsungClockLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(samsungClockLaunch)
                Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                android.util.Log.e("LaunchClockActivity", "Failed to start Samsung Clock: ${e.message}")
            }
        }

        // 4. Generic ACTION_SHOW_ALARMS
        try {
            val showAlarmsIntent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (showAlarmsIntent.resolveActivity(pm) != null) {
                startActivity(showAlarmsIntent)
                Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("LaunchClockActivity", "Failed generic ACTION_SHOW_ALARMS: ${e.message}")
        }

        // 5. Generic ACTION_SET_ALARM
        try {
            val setAlarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (setAlarmIntent.resolveActivity(pm) != null) {
                startActivity(setAlarmIntent)
                Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("LaunchClockActivity", "Failed ACTION_SET_ALARM: ${e.message}")
        }

        // 6. Generic Clock packages launch fallback
        val otherPackages = listOf(
            "com.android.deskclock",
            "com.oneplus.deskclock",
            "com.coloros.alarm",
            "com.huawei.deskclock"
        )
        for (pkg in otherPackages) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                try {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(launchIntent)
                    Toast.makeText(this, "Opening Clock…", Toast.LENGTH_SHORT).show()
                    return
                } catch (_: Exception) {}
            }
        }

        Toast.makeText(this, "Clock app not found", Toast.LENGTH_SHORT).show()
    }
}
