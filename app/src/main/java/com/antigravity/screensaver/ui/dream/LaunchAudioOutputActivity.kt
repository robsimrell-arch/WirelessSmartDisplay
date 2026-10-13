package com.antigravity.screensaver.ui.dream

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast

/**
 * Trampoline activity to dismiss the keyguard if needed and open the
 * system Media Output panel or Bluetooth settings to connect and route audio.
 */
class LaunchAudioOutputActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                        launchOutputSwitcher()
                        finish()
                    }

                    override fun onDismissCancelled() {
                        finish()
                    }

                    override fun onDismissError() {
                        launchOutputSwitcher()
                        finish()
                    }
                })
            } else {
                launchOutputSwitcher()
                finish()
            }
        } else {
            launchOutputSwitcher()
            finish()
        }
    }

    private fun launchOutputSwitcher() {
        // 1. Try Android Media Output Panel (Android 10+ / API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val panelIntent = Intent("android.settings.panel.action.MEDIA_OUTPUT").apply {
                    putExtra("android.provider.extra.PACKAGE_NAME", packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(panelIntent)
                return
            } catch (e: Exception) {
                android.util.Log.d("AudioOutput", "Media output panel not supported: ${e.message}")
            }
        }

        // 2. Fallback: Bluetooth Settings
        try {
            val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(btIntent)
            Toast.makeText(this, "Opening Bluetooth settings…", Toast.LENGTH_SHORT).show()
            return
        } catch (e: Exception) {
            android.util.Log.e("AudioOutput", "Bluetooth settings intent failed: ${e.message}")
        }

        // 3. Fallback: Wireless / System Settings
        try {
            val settingsIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(settingsIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open Bluetooth settings", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, LaunchAudioOutputActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
