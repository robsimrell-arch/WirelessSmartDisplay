package com.antigravity.screensaver.data

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class DoNotDisturbController(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isManagingDnd: Boolean
        get() = prefs.getBoolean(KEY_IS_MANAGING_DND, false)
        private set(value) = prefs.edit().putBoolean(KEY_IS_MANAGING_DND, value).apply()

    var originalFilter: Int
        get() = prefs.getInt(KEY_ORIGINAL_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        private set(value) = prefs.edit().putInt(KEY_ORIGINAL_FILTER, value).apply()

    /**
     * Checks if the app has been granted Notification Policy Access (Do Not Disturb access).
     */
    fun hasPermission(): Boolean {
        return try {
            notificationManager?.isNotificationPolicyAccessGranted == true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking notification policy access", e)
            false
        }
    }

    /**
     * Opens the system settings screen where the user can grant Do Not Disturb access.
     */
    fun openPermissionSettings(activityContext: Context? = null) {
        val target = activityContext ?: context
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            target.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification policy access settings", e)
        }
    }

    /**
     * Activates Do Not Disturb (Priority Only).
     *
     * State preservation: If DND was already active (e.g., PRIORITY, ALARMS, or NONE) before
     * this call, we record that state but do NOT mark `isManagingDnd = true`, so we will NOT
     * disable the user's pre-existing DND when night mode ends.
     *
     * @return true if Priority DND was activated or already active; false if permission is missing.
     */
    fun activateDnd(): Boolean {
        if (!hasPermission() || notificationManager == null) {
            Log.w(TAG, "Cannot activate DND: permission not granted or NotificationManager null")
            return false
        }

        try {
            val currentFilter = notificationManager.currentInterruptionFilter

            // If already managing, keep state
            if (isManagingDnd) {
                return true
            }

            // Check if DND is already enabled by the user before screensaver
            val isAlreadyDnd = currentFilter != NotificationManager.INTERRUPTION_FILTER_ALL

            if (isAlreadyDnd) {
                Log.i(TAG, "DND was already active ($currentFilter). Will not disable on exit.")
                originalFilter = currentFilter
                isManagingDnd = false
                return true
            }

            // DND is currently OFF (INTERRUPTION_FILTER_ALL). Activate PRIORITY mode.
            originalFilter = currentFilter
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            isManagingDnd = true
            Log.i(TAG, "Activated DND (Priority Only). Original filter was: $currentFilter")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error activating DND", e)
            return false
        }
    }

    /**
     * Deactivates DND and restores the previous interruption filter if and only if
     * this screensaver was the one that activated DND.
     */
    fun deactivateDnd() {
        if (!hasPermission() || notificationManager == null) {
            return
        }

        if (!isManagingDnd) {
            Log.i(TAG, "Skipping DND deactivation: screensaver was not managing DND")
            return
        }

        try {
            val restoreFilter = originalFilter
            notificationManager.setInterruptionFilter(restoreFilter)
            Log.i(TAG, "Deactivated DND. Restored filter to: $restoreFilter")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring interruption filter", e)
        } finally {
            isManagingDnd = false
            originalFilter = NotificationManager.INTERRUPTION_FILTER_ALL
        }
    }

    /**
     * Reset on cleanup/destroy. Guarantees that any DND turned on by the screensaver is restored.
     */
    fun reset() {
        deactivateDnd()
    }

    companion object {
        private const val TAG = "DoNotDisturbController"
        private const val PREFS_NAME = "dnd_controller_prefs"
        private const val KEY_IS_MANAGING_DND = "key_is_managing_dnd"
        private const val KEY_ORIGINAL_FILTER = "key_original_filter"

        @Volatile
        private var instance: DoNotDisturbController? = null

        fun getInstance(context: Context): DoNotDisturbController {
            return instance ?: synchronized(this) {
                instance ?: DoNotDisturbController(context.applicationContext).also { instance = it }
            }
        }

        /**
         * Pure logic evaluator for unit testing: decides whether DND should be turned off on exit.
         */
        fun evaluateShouldRestore(isManagingDnd: Boolean): Boolean {
            return isManagingDnd
        }

        /**
         * Pure logic evaluator for unit testing: determines if pre-existing state should take ownership.
         */
        fun shouldTakeOwnership(currentFilter: Int): Boolean {
            return currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        }
    }
}
