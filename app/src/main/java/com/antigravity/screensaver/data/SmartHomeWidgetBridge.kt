package com.antigravity.screensaver.data

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView

/**
 * Bridges our OLED-safe Smart Home "Lights Out" pill button to Google Home's native
 * Favorites Widget (`com.google.android.apps.chromecast.app/.appwidget.favorites.FavoritesWidgetReceiver`).
 *
 * Why this is needed:
 * - `googlehome://automations` only opens the Automations list tab without running the routine.
 * - `https://assistant.google.com/services/invoke/...` is blocked when Gemini is the default assistant
 *   ("Gemini doesn't support this feature").
 * - Google Home's Favorites Widget executes `RunAutomation(accountName, homeId, automationId)`
 *   directly in the background via Google Home's internal SDK without opening an activity or calling Gemini.
 */
object SmartHomeWidgetBridge {
    private const val TAG = "SmartHomeWidgetBridge"
    const val APPWIDGET_HOST_ID = 0x5348
    const val REQUEST_CODE_BIND_WIDGET = 4410
    const val REQUEST_CODE_CONFIGURE_WIDGET = 4411

    const val GOOGLE_HOME_PKG = "com.google.android.apps.chromecast.app"
    const val GOOGLE_FAVORITES_WIDGET_RECEIVER =
        "com.google.android.apps.chromecast.app.appwidget.favorites.FavoritesWidgetReceiver"
    const val ALEXA_PKG = "com.amazon.dee.app"

    @Volatile
    private var appWidgetHost: AppWidgetHost? = null

    fun getHost(context: Context): AppWidgetHost {
        return appWidgetHost ?: synchronized(this) {
            appWidgetHost ?: AppWidgetHost(context.applicationContext, APPWIDGET_HOST_ID).also {
                appWidgetHost = it
            }
        }
    }

    fun isWidgetValid(context: Context, appWidgetId: Int): Boolean {
        if (appWidgetId <= 0) return false
        val mgr = AppWidgetManager.getInstance(context)
        return mgr.getAppWidgetInfo(appWidgetId) != null
    }

    /**
     * Finds Google Home's FavoritesWidgetReceiver (or Alexa's widget provider).
     */
    fun findWidgetProvider(context: Context, providerKey: String): AppWidgetProviderInfo? {
        val mgr = AppWidgetManager.getInstance(context)
        val installed = mgr.installedProviders

        if (providerKey.equals("alexa", ignoreCase = true)) {
            return installed.firstOrNull { it.provider.packageName == ALEXA_PKG }
        }

        // Prefer Google Home's FavoritesWidgetReceiver which runs automations in-place
        val exactFavorites = installed.firstOrNull {
            it.provider.packageName == GOOGLE_HOME_PKG &&
                it.provider.className == GOOGLE_FAVORITES_WIDGET_RECEIVER
        }
        if (exactFavorites != null) return exactFavorites

        return installed.firstOrNull {
            it.provider.packageName == GOOGLE_HOME_PKG &&
                it.provider.className.contains("favorites", ignoreCase = true)
        } ?: installed.firstOrNull {
            it.provider.packageName == GOOGLE_HOME_PKG
        }
    }

    /**
     * Starts Google Home's official Favorites Widget configure screen (`AppWidgetSetupActivity`)
     * using `AppWidgetHost.startAppWidgetConfigureActivityForResult` (required by Android OS
     * to grant temporary launch permission for unexported widget configure activities).
     */
    fun startConfigureActivity(activity: Activity, appWidgetId: Int): Boolean {
        return try {
            getHost(activity).startAppWidgetConfigureActivityForResult(
                activity,
                appWidgetId,
                0,
                REQUEST_CODE_CONFIGURE_WIDGET,
                null
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start widget configure activity: ${e.message}", e)
            false
        }
    }

    /**
     * Applies 180x110dp widget options so Jetpack Glance composes the compact Favorites tile
     * and requests an immediate RemoteViews update from Google Home.
     */
    fun applyStandardWidgetOptions(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo) {
        try {
            val mgr = AppWidgetManager.getInstance(context)
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 220)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 140)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    putParcelableArrayList(
                        AppWidgetManager.OPTION_APPWIDGET_SIZES,
                        arrayListOf(SizeF(180f, 110f), SizeF(220f, 140f))
                    )
                }
            }
            mgr.updateAppWidgetOptions(appWidgetId, options)

            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = info.provider
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            context.sendBroadcast(updateIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update widget options: ${e.message}")
        }
    }

    /**
     * Creates and configures an [AppWidgetHostView] for the bound Google Home Favorites widget.
     */
    fun createPreparedHostView(context: Context, appWidgetId: Int): AppWidgetHostView? {
        val mgr = AppWidgetManager.getInstance(context)
        val info = mgr.getAppWidgetInfo(appWidgetId) ?: return null
        return try {
            val host = getHost(context)
            val hostView = host.createView(context, appWidgetId, info)
            hostView.setAppWidget(appWidgetId, info)
            applyStandardWidgetOptions(context, appWidgetId, info)
            hostView
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AppWidgetHostView: ${e.message}", e)
            null
        }
    }

    /**
     * Searches the mounted [AppWidgetHostView] for the automation action button (e.g. "Lights Out" Run/Play button)
     * and triggers its broadcast handler so Google Home executes `RunAutomation` in the background.
     *
     * In Google Home's Favorites Widget:
     * - The card/label container has an Activity PendingIntent which opens the Google Home app (`googlehome://homeview/...`).
     * - The action button (Play icon circle) has a Broadcast PendingIntent which executes the automation in the background!
     *
     * We inspect each clickable view's [PendingIntent]:
     * - If it's a Broadcast intent, it executes the automation in the background (+10,000 pts).
     * - If it's an Activity intent, it opens the Google Home app (-10,000 pts).
     * - Leaf clickable views are prioritized over outer card containers (+2,000 pts).
     */
    fun triggerAutomationInHostView(hostView: AppWidgetHostView?, targetLabel: String): TriggerResult {
        if (hostView == null || hostView.childCount == 0) {
            return TriggerResult.NOT_READY
        }

        val normalizedTarget = targetLabel.trim().lowercase()
        val candidates = mutableListOf<ScoredCandidate>()
        collectClickableCandidates(hostView, normalizedTarget, candidates)

        if (candidates.isEmpty()) {
            val adapterTriggered = triggerAdapterViewItem(hostView, normalizedTarget)
            return if (adapterTriggered) TriggerResult.TRIGGERED else TriggerResult.NOT_READY
        }

        // Log candidates for diagnostic visibility
        for (c in candidates) {
            Log.d(
                TAG,
                "Candidate: class=${c.view.javaClass.simpleName}, " +
                    "score=${c.score}, " +
                    "isBroadcast=${c.isBroadcast}, " +
                    "isActivity=${c.isActivity}, " +
                    "isLeaf=${c.isLeaf}, " +
                    "desc='${c.contentDescription}', " +
                    "text='${c.text}'"
            )
        }

        val best = candidates.maxByOrNull { it.score }
        if (best != null && best.score > 0) {
            Log.d(
                TAG,
                "Selecting BEST action view (score=${best.score}, class=${best.view.javaClass.simpleName}, isBroadcast=${best.isBroadcast})"
            )
            // If we have a verified Broadcast PendingIntent, send it directly for instant background execution
            if (best.pendingIntent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && best.isBroadcast) {
                try {
                    Log.d(TAG, "Directly sending Broadcast PendingIntent to Google Home!")
                    best.pendingIntent.send()
                    return TriggerResult.TRIGGERED
                } catch (e: Exception) {
                    Log.w(TAG, "pendingIntent.send() threw: ${e.message}, falling back to performClick()")
                }
            }

            val clicked = best.view.performClick()
            if (clicked) return TriggerResult.TRIGGERED
        }

        val adapterTriggered = triggerAdapterViewItem(hostView, normalizedTarget)
        if (adapterTriggered) {
            return TriggerResult.TRIGGERED
        }

        return TriggerResult.NO_MATCH
    }

    enum class TriggerResult {
        TRIGGERED,
        NOT_READY,
        NO_MATCH
    }

    private data class ScoredCandidate(
        val view: View,
        val pendingIntent: android.app.PendingIntent?,
        val isBroadcast: Boolean,
        val isActivity: Boolean,
        val isLeaf: Boolean,
        val score: Int,
        val text: String,
        val contentDescription: String
    )

    private fun collectClickableCandidates(
        view: View,
        normalizedTarget: String,
        out: MutableList<ScoredCandidate>
    ) {
        if (view.visibility == View.VISIBLE && view !is AppWidgetHostView) {
            val clickable = view.isClickable && view.hasOnClickListeners()
            if (clickable) {
                val directText = (view as? TextView)?.text?.toString()?.trim() ?: ""
                val directDesc = view.contentDescription?.toString()?.trim() ?: ""
                val allTexts = mutableListOf<String>()
                collectSubtreeTexts(view, allTexts)
                val combined = allTexts.joinToString(" ").trim().lowercase()

                val isLeaf = !hasClickableDescendant(view)
                val pi = extractPendingIntent(view)
                val isBroadcast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pi?.isBroadcast == true
                } else {
                    false
                }
                val isActivity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pi?.isActivity == true
                } else {
                    false
                }

                val lowerDesc = directDesc.lowercase()
                val lowerDirectText = directText.lowercase()

                val isSystemOrSetup = combined == "edit" ||
                    combined == "settings" ||
                    combined.contains("select favorites") ||
                    combined.contains("add favorites") ||
                    combined.contains("sign in")

                var score = 0
                if (isSystemOrSetup) {
                    score = -50000
                } else {
                    // 1. PendingIntent type is the strongest signal
                    if (isBroadcast) {
                        score += 10000
                    } else if (isActivity) {
                        // Activity intent opens Google Home app - penalize heavily!
                        score -= 10000
                    }

                    // 2. Leaf clickable vs outer container
                    if (isLeaf) {
                        score += 2000
                    } else {
                        // Outer container that wraps inner buttons
                        score -= 2000
                    }

                    // 3. Target Automation text match
                    val targetMatches = normalizedTarget.isNotEmpty() && combined.contains(normalizedTarget)
                    if (targetMatches) {
                        score += 1500
                    }

                    // 4. Action keywords on the button itself
                    val isActionKeyword = lowerDesc.contains("run") ||
                        lowerDesc.contains("play") ||
                        lowerDesc.contains("start") ||
                        lowerDesc.contains("execute") ||
                        lowerDirectText.contains("run") ||
                        lowerDirectText.contains("play")
                    if (isActionKeyword) {
                        score += 2500
                    }

                    // 5. If this view has long descriptive card text (e.g. "Lights Out 6 actions"),
                    // it is the info label / details card body that opens the app, NOT the action button!
                    if (combined.contains("actions") || combined.contains("devices") || combined.length > 20) {
                        score -= 1500
                    } else if (directText.isEmpty() && isLeaf) {
                        // Icon-only button inside the card (typical Play/Run button in Glance)
                        score += 1000
                    }
                }

                out.add(
                    ScoredCandidate(
                        view = view,
                        pendingIntent = pi,
                        isBroadcast = isBroadcast,
                        isActivity = isActivity,
                        isLeaf = isLeaf,
                        score = score,
                        text = directText,
                        contentDescription = directDesc
                    )
                )
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectClickableCandidates(view.getChildAt(i), normalizedTarget, out)
            }
        }
    }

    private fun hasClickableDescendant(view: View): Boolean {
        if (view !is ViewGroup) return false
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i) ?: continue
            if (child.visibility == View.VISIBLE && child.isClickable && child.hasOnClickListeners()) {
                return true
            }
            if (hasClickableDescendant(child)) {
                return true
            }
        }
        return false
    }

    /**
     * Extracts the [android.app.PendingIntent] attached to a View by RemoteViews through reflection.
     */
    private fun extractPendingIntent(view: View): android.app.PendingIntent? {
        try {
            // Check view tag
            val tag = view.tag
            if (tag is android.app.PendingIntent) return tag
            if (tag != null) {
                val fromTag = findPendingIntentInObject(tag, 0, mutableSetOf())
                if (fromTag != null) return fromTag
            }

            // Check View.mListenerInfo.mOnClickListener
            val listenerInfoField = View::class.java.getDeclaredField("mListenerInfo").apply { isAccessible = true }
            val listenerInfo = listenerInfoField.get(view) ?: return null
            val clickListenerField = listenerInfo.javaClass.getDeclaredField("mOnClickListener").apply { isAccessible = true }
            val clickListener = clickListenerField.get(listenerInfo) ?: return null

            return findPendingIntentInObject(clickListener, 0, mutableSetOf())
        } catch (_: Throwable) {
            return null
        }
    }

    private fun findPendingIntentInObject(obj: Any, depth: Int, visited: MutableSet<Any>): android.app.PendingIntent? {
        if (depth > 5 || !visited.add(obj)) return null
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (field in clazz.declaredFields) {
                try {
                    field.isAccessible = true
                    val value = field.get(obj) ?: continue
                    if (value is android.app.PendingIntent) {
                        return value
                    }
                    val typeName = value.javaClass.name
                    if (typeName.startsWith("android.widget.RemoteViews") ||
                        typeName.startsWith("androidx.glance") ||
                        typeName.contains("Response") ||
                        typeName.contains("Action") ||
                        typeName.contains("Lambda") ||
                        typeName.contains("$")
                    ) {
                        val nested = findPendingIntentInObject(value, depth + 1, visited)
                        if (nested != null) return nested
                    }
                } catch (_: Throwable) {}
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun collectSubtreeTexts(view: View, out: MutableList<String>) {
        view.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        if (view is TextView) {
            view.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectSubtreeTexts(view.getChildAt(i), out)
            }
        }
    }

    private fun triggerAdapterViewItem(root: View, normalizedTarget: String): Boolean {
        if (root is AdapterView<*>) {
            val count = root.childCount
            var fallbackIndex = -1
            for (i in 0 until count) {
                val child = root.getChildAt(i) ?: continue
                val texts = mutableListOf<String>()
                collectSubtreeTexts(child, texts)
                val combined = texts.joinToString(" ").lowercase()
                if (normalizedTarget.isNotEmpty() && combined.contains(normalizedTarget)) {
                    val pos = root.firstVisiblePosition + i
                    val id = root.adapter?.getItemId(pos) ?: 0L
                    return root.performItemClick(child, pos, id)
                }
                if (fallbackIndex == -1) fallbackIndex = i
            }
            if (fallbackIndex != -1) {
                val child = root.getChildAt(fallbackIndex)
                val pos = root.firstVisiblePosition + fallbackIndex
                val id = root.adapter?.getItemId(pos) ?: 0L
                return root.performItemClick(child, pos, id)
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                if (triggerAdapterViewItem(root.getChildAt(i), normalizedTarget)) return true
            }
        }
        return false
    }
}
