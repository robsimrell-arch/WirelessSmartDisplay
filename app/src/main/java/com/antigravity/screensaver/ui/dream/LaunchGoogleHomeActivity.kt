package com.antigravity.screensaver.ui.dream

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast

/**
 * Trampoline activity to bridge between the DreamService (screensaver) / Lockscreen
 * and the Google Home app.
 *
 * Configured with showWhenLocked and turnScreenOn, allowing it to display over
 * the lockscreen and request keyguard dismissal so the user can authenticate
 * (PIN/fingerprint/face) and land directly in Google Home.
 */
class LaunchGoogleHomeActivity : Activity() {

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
                        launchGoogleHomeTarget()
                        finish()
                    }

                    override fun onDismissCancelled() {
                        finish()
                    }

                    override fun onDismissError() {
                        launchGoogleHomeTarget()
                        finish()
                    }
                })
            } else {
                launchGoogleHomeTarget()
                finish()
            }
        } else {
            launchGoogleHomeTarget()
            finish()
        }
    }

    private fun launchGoogleHomeTarget() {
        val pm = packageManager

        // 1. Official Google Home app
        val homeIntent = pm.getLaunchIntentForPackage("com.google.android.apps.chromecast.app")
        if (homeIntent != null) {
            homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            try {
                startActivity(homeIntent)
                Toast.makeText(this, "Opening Google Home…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                android.util.Log.e("LaunchGoogleHome", "Failed to start Google Home: ${e.message}")
            }
        }

        // 2. Google Nest app fallback
        val nestIntent = pm.getLaunchIntentForPackage("com.nest.android")
        if (nestIntent != null) {
            nestIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            try {
                startActivity(nestIntent)
                Toast.makeText(this, "Opening Google Nest…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                android.util.Log.e("LaunchGoogleHome", "Failed to start Nest: ${e.message}")
            }
        }

        // 3. Fallback: Google Home Web in browser
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://home.google.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(webIntent)
            Toast.makeText(this, "Opening Google Home Web…", Toast.LENGTH_SHORT).show()
            return
        } catch (e: Exception) {
            android.util.Log.e("LaunchGoogleHome", "Failed to start Web: ${e.message}")
        }

        // 4. Fallback: Google Play Store
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.chromecast.app")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(marketIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "Google Home app not found", Toast.LENGTH_LONG).show()
        }
    }
}
