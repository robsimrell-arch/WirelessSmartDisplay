package com.antigravity.screensaver.data

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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

    private var isManagingPolicy: Boolean
        get() = prefs.getBoolean(KEY_IS_MANAGING_POLICY, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_MANAGING_POLICY, value).apply()

    private var originalHeadsUp: Int
        get() = prefs.getInt(KEY_ORIGINAL_HEADS_UP, -1)
        set(value) = prefs.edit().putInt(KEY_ORIGINAL_HEADS_UP, value).apply()

    private var originalDozePulse: Int
        get() = prefs.getInt(KEY_ORIGINAL_DOZE_PULSE, -1)
        set(value) = prefs.edit().putInt(KEY_ORIGINAL_DOZE_PULSE, value).apply()

    private var originalLightPulse: Int
        get() = prefs.getInt(KEY_ORIGINAL_LIGHT_PULSE, -1)
        set(value) = prefs.edit().putInt(KEY_ORIGINAL_LIGHT_PULSE, value).apply()

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

    private fun hasSecureSettingsPermission(): Boolean {
        return try {
            context.checkSelfPermission(LocationController.PERMISSION_WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
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
     * Mutes OS-level notification screen wake pulses and heads-up banners when WRITE_SECURE_SETTINGS is available.
     */
    private fun muteNotificationWakeAndFlashSettings() {
        if (!hasSecureSettingsPermission()) return
        val resolver = context.contentResolver

        // 1. Disable Heads-Up Notification popups (prevents top-of-screen banner flash & light sensor feedback loop)
        try {
            val currentHeadsUp = Settings.Global.getInt(resolver, "heads_up_notifications_enabled", 1)
            if (currentHeadsUp != 0) {
                if (originalHeadsUp == -1) {
                    originalHeadsUp = currentHeadsUp
                }
                Settings.Global.putInt(resolver, "heads_up_notifications_enabled", 0)
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Could not mute heads_up_notifications_enabled: ${e.message}")
        }

        // 2. Disable Doze / Ambient Display wake pulse on incoming notifications
        try {
            val currentDozePulse = Settings.Secure.getInt(resolver, "doze_pulse_on_notifications", 1)
            if (currentDozePulse != 0) {
                if (originalDozePulse == -1) {
                    originalDozePulse = currentDozePulse
                }
                Settings.Secure.putInt(resolver, "doze_pulse_on_notifications", 0)
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Could not mute doze_pulse_on_notifications: ${e.message}")
        }

        // 3. Disable notification light pulse
        try {
            val currentLightPulse = Settings.Secure.getInt(resolver, "notification_light_pulse", 0)
            if (currentLightPulse != 0) {
                if (originalLightPulse == -1) {
                    originalLightPulse = currentLightPulse
                }
                Settings.Secure.putInt(resolver, "notification_light_pulse", 0)
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Could not mute notification_light_pulse: ${e.message}")
        }
    }

    private fun restoreNotificationWakeAndFlashSettings() {
        if (!hasSecureSettingsPermission()) return
        val resolver = context.contentResolver

        if (originalHeadsUp != -1) {
            try {
                Settings.Global.putInt(resolver, "heads_up_notifications_enabled", originalHeadsUp)
            } catch (_: Throwable) {}
            originalHeadsUp = -1
        }

        if (originalDozePulse != -1) {
            try {
                Settings.Secure.putInt(resolver, "doze_pulse_on_notifications", originalDozePulse)
            } catch (_: Throwable) {}
            originalDozePulse = -1
        }

        if (originalLightPulse != -1) {
            try {
                Settings.Secure.putInt(resolver, "notification_light_pulse", originalLightPulse)
            } catch (_: Throwable) {}
            originalLightPulse = -1
        }
    }

    /**
     * Saves the user's existing NotificationManager.Policy (if not already saved) and applies
     * the Priority Only night policy:
     * - Allows priority conversations, starred contact messages, starred contact calls, repeat callers,
     *   reminders, events, alarms, and media.
     * - Suppresses all visual notification effects (screen wake, peek banners, ambient pulse, lights)
     *   so the dark Night Mode clock never flashes.
     */
    private fun applyPriorityNightNotificationPolicy() {
        val nm = notificationManager ?: return
        if (!hasPermission()) return

        try {
            val currentPolicy = nm.notificationPolicy
            if (currentPolicy != null && !isManagingPolicy) {
                val editor = prefs.edit()
                    .putInt(KEY_ORIG_POLICY_CATEGORIES, currentPolicy.priorityCategories)
                    .putInt(KEY_ORIG_POLICY_CALL_SENDERS, currentPolicy.priorityCallSenders)
                    .putInt(KEY_ORIG_POLICY_MSG_SENDERS, currentPolicy.priorityMessageSenders)
                    .putInt(KEY_ORIG_POLICY_VISUAL_EFFECTS, currentPolicy.suppressedVisualEffects)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    editor.putInt(KEY_ORIG_POLICY_CONV_SENDERS, currentPolicy.priorityConversationSenders)
                }
                editor.putBoolean(KEY_IS_MANAGING_POLICY, true).apply()
            }

            val nightPolicy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                NotificationManager.Policy(
                    NIGHT_MODE_PRIORITY_CATEGORIES,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                    NIGHT_MODE_SUPPRESSED_VISUAL_EFFECTS,
                    NotificationManager.Policy.CONVERSATION_SENDERS_IMPORTANT
                )
            } else {
                NotificationManager.Policy(
                    NIGHT_MODE_PRIORITY_CATEGORIES,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                    NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                    NIGHT_MODE_SUPPRESSED_VISUAL_EFFECTS
                )
            }
            nm.setNotificationPolicy(nightPolicy)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to apply priority night notification policy: ${e.message}")
        }
    }

    private fun restoreNotificationPolicy() {
        val nm = notificationManager ?: return
        if (!hasPermission() || !isManagingPolicy) return

        try {
            // On Android 15+ (API 35+), setNotificationPolicy() configures only our app's dedicated
            // implicit ZenRule ("Do Not Disturb (Wireless Smart Display)"), which is deactivated via
            // setInterruptionFilter(ALL). Keeping the Priority Only + visual-suppression policy on our
            // implicit rule ensures Android Settings -> Modes always shows the intended rule config.
            if (Build.VERSION.SDK_INT < 35) {
                val categories = prefs.getInt(KEY_ORIG_POLICY_CATEGORIES, NIGHT_MODE_PRIORITY_CATEGORIES)
                val callSenders = prefs.getInt(KEY_ORIG_POLICY_CALL_SENDERS, NotificationManager.Policy.PRIORITY_SENDERS_STARRED)
                val msgSenders = prefs.getInt(KEY_ORIG_POLICY_MSG_SENDERS, NotificationManager.Policy.PRIORITY_SENDERS_STARRED)
                val visualEffects = prefs.getInt(KEY_ORIG_POLICY_VISUAL_EFFECTS, 0)

                val restoredPolicy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val convSenders = prefs.getInt(
                        KEY_ORIG_POLICY_CONV_SENDERS,
                        NotificationManager.Policy.CONVERSATION_SENDERS_IMPORTANT
                    )
                    NotificationManager.Policy(
                        categories,
                        callSenders,
                        msgSenders,
                        visualEffects,
                        convSenders
                    )
                } else {
                    NotificationManager.Policy(
                        categories,
                        callSenders,
                        msgSenders,
                        visualEffects
                    )
                }
                nm.setNotificationPolicy(restoredPolicy)
                Log.i(TAG, "Restored original NotificationManager.Policy")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error restoring NotificationManager.Policy: ${e.message}")
        } finally {
            isManagingPolicy = false
        }
    }

    /**
     * Activates Priority Only Do Not Disturb with full visual flash suppression.
     *
     * Allows priority conversations, messages/calls from starred contacts, repeat callers,
     * reminders, events, and alarms to come through audibly, while suppressing all visual popups,
     * heads-up banners, and ambient pulses so the dark Night Mode clock never flashes.
     *
     * State preservation: Always activates the screensaver's Priority Only policy in Night Mode
     * (even if a system DND schedule is already active). If DND was previously OFF (ALL), records
     * ownership so DND is turned back OFF when Night Mode ends.
     */
    fun activateDnd(): Boolean {
        muteNotificationWakeAndFlashSettings()

        if (!hasPermission() || notificationManager == null) {
            Log.w(TAG, "Cannot activate DND policy: permission not granted or NotificationManager null")
            return false
        }

        try {
            val currentFilter = notificationManager.currentInterruptionFilter

            // 1. Save original policy (if needed) and apply Priority Only + visual suppression policy
            applyPriorityNightNotificationPolicy()

            if (!isManagingDnd) {
                originalFilter = currentFilter
                isManagingDnd = shouldTakeOwnership(currentFilter)
            }

            // 2. Always enforce INTERRUPTION_FILTER_PRIORITY and re-apply policy so our implicit ZenRule
            // is active and consolidates visual suppression even if a system DND schedule was already running
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            applyPriorityNightNotificationPolicy()

            Log.i(
                TAG,
                "Activated Night Mode DND (Priority Only + Visual Suppression). Original filter=$originalFilter, managing=$isManagingDnd"
            )
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error activating DND", e)
            return false
        }
    }

    /**
     * Enforces Priority Only DND and visual notification suppression during Night Mode in case an
     * external schedule alters the interruption filter or policy while night mode is active.
     */
    fun enforceDnd() {
        muteNotificationWakeAndFlashSettings()
        if (!hasPermission() || notificationManager == null) return

        try {
            applyPriorityNightNotificationPolicy()
            val currentFilter = notificationManager.currentInterruptionFilter
            if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                if (!isManagingDnd) {
                    originalFilter = currentFilter
                    isManagingDnd = true
                }
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                applyPriorityNightNotificationPolicy()
                Log.i(TAG, "Re-enforced Night Mode DND (Priority Only) after external change from $currentFilter")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error enforcing DND", e)
        }
    }

    /**
     * Deactivates Night Mode DND and restores the previous interruption filter, notification policy,
     * and OS wake/heads-up settings.
     */
    fun deactivateDnd() {
        restoreNotificationWakeAndFlashSettings()

        if (!hasPermission() || notificationManager == null) {
            return
        }

        restoreNotificationPolicy()

        if (!isManagingDnd) {
            Log.i(TAG, "Skipping DND filter deactivation: screensaver was not managing DND filter")
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
     * Reset on cleanup/destroy. Guarantees that any DND or policy turned on by the screensaver is restored.
     */
    fun reset() {
        deactivateDnd()
    }

    companion object {
        private const val TAG = "DoNotDisturbController"
        private const val PREFS_NAME = "dnd_controller_prefs"
        private const val KEY_IS_MANAGING_DND = "key_is_managing_dnd"
        private const val KEY_ORIGINAL_FILTER = "key_original_filter"
        private const val KEY_IS_MANAGING_POLICY = "key_is_managing_policy"
        private const val KEY_ORIG_POLICY_CATEGORIES = "key_orig_policy_categories"
        private const val KEY_ORIG_POLICY_CALL_SENDERS = "key_orig_policy_call_senders"
        private const val KEY_ORIG_POLICY_MSG_SENDERS = "key_orig_policy_msg_senders"
        private const val KEY_ORIG_POLICY_VISUAL_EFFECTS = "key_orig_policy_visual_effects"
        private const val KEY_ORIG_POLICY_CONV_SENDERS = "key_orig_policy_conv_senders"
        private const val KEY_ORIGINAL_HEADS_UP = "key_original_heads_up"
        private const val KEY_ORIGINAL_DOZE_PULSE = "key_original_doze_pulse"
        private const val KEY_ORIGINAL_LIGHT_PULSE = "key_original_light_pulse"

        /**
         * Priority categories permitted during Night Mode:
         * Priority conversations, starred contact messages & calls, repeat callers, reminders, events, alarms, and media.
         */
        const val NIGHT_MODE_PRIORITY_CATEGORIES: Int =
            NotificationManager.Policy.PRIORITY_CATEGORY_CONVERSATIONS or
            NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES or
            NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
            NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS or
            NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS or
            NotificationManager.Policy.PRIORITY_CATEGORY_EVENTS or
            NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
            NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA

        /**
         * Bitmask suppressing all visual notification effects (screen wake, screen on peek/flash,
         * full-screen intents, notification lights, status bar icons, ambient pulse, and shade list).
         */
        const val NIGHT_MODE_SUPPRESSED_VISUAL_EFFECTS: Int =
            NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF or
            NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON or
            NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or
            NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or
            NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or
            NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR or
            NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE or
            NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT or
            NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST

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
         * Pure logic evaluator for unit testing: determines if pre-existing state was completely un-muted.
         */
        fun shouldTakeOwnership(currentFilter: Int): Boolean {
            return currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        }
    }
}
