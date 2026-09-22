package com.antigravity.screensaver.ui.settings

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.antigravity.screensaver.data.DreamSystemSettingsController
import com.antigravity.screensaver.data.PreferencesManager
import com.antigravity.screensaver.ui.theme.ScreenSaverTheme

class SettingsActivity : ComponentActivity() {
    private lateinit var dreamSystemSettingsController: DreamSystemSettingsController
    private lateinit var prefsManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Allow opening settings directly over the lockscreen from the screensaver
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

        dreamSystemSettingsController = DreamSystemSettingsController.getInstance(this)
        prefsManager = PreferencesManager(this)
        dreamSystemSettingsController.syncSystemScreensaverPolicy(prefsManager.getSettings().wirelessOnly)

        setContent {
            ScreenSaverTheme {
                SettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dreamSystemSettingsController.syncSystemScreensaverPolicy(prefsManager.getSettings().wirelessOnly)
    }
}

