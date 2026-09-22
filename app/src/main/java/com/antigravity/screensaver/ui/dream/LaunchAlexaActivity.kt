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
 * and the Amazon Alexa app.
 *
 * Configured with showWhenLocked and turnScreenOn, allowing it to display over
 * the lockscreen and request keyguard dismissal so the user can authenticate
 * (PIN/fingerprint/face) and land directly in Amazon Alexa.
 */
class LaunchAlexaActivity : Activity() {

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
                        launchAlexaTarget()
                        finish()
                    }

                    override fun onDismissCancelled() {
                        finish()
                    }

                    override fun onDismissError() {
                        launchAlexaTarget()
                        finish()
                    }
                })
            } else {
                launchAlexaTarget()
                finish()
            }
        } else {
            launchAlexaTarget()
            finish()
        }
    }

    private fun launchAlexaTarget() {
        val pm = packageManager

        // 1. Official Amazon Alexa app
        val alexaIntent = pm.getLaunchIntentForPackage("com.amazon.dee.app")
        if (alexaIntent != null) {
            alexaIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            try {
                startActivity(alexaIntent)
                Toast.makeText(this, "Opening Amazon Alexa…", Toast.LENGTH_SHORT).show()
                return
            } catch (e: Exception) {
                android.util.Log.e("LaunchAlexa", "Failed to start Amazon Alexa: ${e.message}")
            }
        }

        // 2. Fallback: Google Play Store
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.amazon.dee.app")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(marketIntent)
            Toast.makeText(this, "Opening Alexa on Google Play…", Toast.LENGTH_SHORT).show()
            return
        } catch (_: Exception) {
            try {
                val playWebIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.amazon.dee.app")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(playWebIntent)
                Toast.makeText(this, "Opening Alexa on Google Play…", Toast.LENGTH_SHORT).show()
                return
            } catch (_: Exception) {}
        }

        // 3. Fallback: Alexa Web portal
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://alexa.amazon.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(webIntent)
            Toast.makeText(this, "Opening Alexa Web…", Toast.LENGTH_SHORT).show()
            return
        } catch (e: Exception) {
            android.util.Log.e("LaunchAlexa", "Failed to start Web: ${e.message}")
        }

        Toast.makeText(this, "Amazon Alexa app not found", Toast.LENGTH_LONG).show()
    }
}
