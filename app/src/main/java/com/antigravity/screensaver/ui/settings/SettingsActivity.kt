package com.antigravity.screensaver.ui.settings

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.antigravity.screensaver.data.DreamSystemSettingsController
import com.antigravity.screensaver.data.PreferencesManager
import com.antigravity.screensaver.data.SmartHomeWidgetBridge
import com.antigravity.screensaver.ui.theme.ScreenSaverTheme

class SettingsActivity : ComponentActivity() {
    private lateinit var dreamSystemSettingsController: DreamSystemSettingsController
    private lateinit var prefsManager: PreferencesManager
    private var pendingWidgetId: Int = -1

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
                SettingsScreen(
                    onLinkSmartHomeAutomation = { providerKey, label ->
                        startBindAndConfigureAutomationWidget(providerKey, label)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dreamSystemSettingsController.syncSystemScreensaverPolicy(prefsManager.getSettings().wirelessOnly)
    }

    fun startBindAndConfigureAutomationWidget(providerKey: String, label: String) {
        val providerInfo = SmartHomeWidgetBridge.findWidgetProvider(this, providerKey)
        if (providerInfo == null) {
            Toast.makeText(this, "Google Home widget not found on device", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val host = SmartHomeWidgetBridge.getHost(this)
            val appWidgetId = host.allocateAppWidgetId()
            pendingWidgetId = appWidgetId
            val mgr = AppWidgetManager.getInstance(this)

            if (mgr.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)) {
                launchWidgetConfigureOrFinish(appWidgetId)
            } else {
                val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                }
                @Suppress("DEPRECATION")
                startActivityForResult(bindIntent, SmartHomeWidgetBridge.REQUEST_CODE_BIND_WIDGET)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to bind widget: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchWidgetConfigureOrFinish(appWidgetId: Int) {
        val mgr = AppWidgetManager.getInstance(this)
        val info = mgr.getAppWidgetInfo(appWidgetId)
        if (info?.configure != null) {
            SmartHomeWidgetBridge.startConfigureActivity(this, appWidgetId)
        } else {
            onAutomationWidgetConfigured(appWidgetId)
        }
    }

    private fun onAutomationWidgetConfigured(appWidgetId: Int) {
        val oldId = prefsManager.getSettings().smartHomeAutomationWidgetId
        if (oldId > 0 && oldId != appWidgetId) {
            try {
                SmartHomeWidgetBridge.getHost(this).deleteAppWidgetId(oldId)
            } catch (_: Exception) {}
        }
        prefsManager.setSmartHomeAutomationWidgetId(appWidgetId)
        pendingWidgetId = -1
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
        if (info != null) {
            SmartHomeWidgetBridge.applyStandardWidgetOptions(this, appWidgetId, info)
        }
        val label = prefsManager.getSettings().smartHomeAutomationLabel.trim().ifEmpty { "Lights Out" }
        Toast.makeText(this, "$label linked for 1-tap execution!", Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId

        when (requestCode) {
            SmartHomeWidgetBridge.REQUEST_CODE_BIND_WIDGET -> {
                if (resultCode == Activity.RESULT_OK && widgetId > 0) {
                    launchWidgetConfigureOrFinish(widgetId)
                } else {
                    if (widgetId > 0) {
                        try {
                            SmartHomeWidgetBridge.getHost(this).deleteAppWidgetId(widgetId)
                        } catch (_: Exception) {}
                    }
                    pendingWidgetId = -1
                }
            }
            SmartHomeWidgetBridge.REQUEST_CODE_CONFIGURE_WIDGET -> {
                if (resultCode == Activity.RESULT_OK && widgetId > 0) {
                    onAutomationWidgetConfigured(widgetId)
                } else {
                    if (widgetId > 0) {
                        try {
                            SmartHomeWidgetBridge.getHost(this).deleteAppWidgetId(widgetId)
                        } catch (_: Exception) {}
                    }
                    pendingWidgetId = -1
                }
            }
        }
    }
}

