package com.mahimeta.sdk.analytics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.mahimeta.sdk.BuildConfig
import com.mahimeta.sdk.analytics.model.AnalyticsEvent
import com.mahimeta.sdk.analytics.model.AnalyticsEventBuilder
import com.mahimeta.sdk.utils.DeviceInfo
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages analytics events for the Mahimeta SDK.
 * This class is responsible for collecting and storing analytics events in memory.
 * In the future, it can be extended to send events to a server.
 */
internal object AnalyticsManager : Application.ActivityLifecycleCallbacks {
    private const val MAX_EVENTS_IN_MEMORY = 1000
    private val SESSION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30) // 30 minutes

    private val events = CopyOnWriteArrayList<AnalyticsEvent>()
    private var isInitialized = false
    private var applicationContext: Context? = null
    private var analyticsApiClient: AnalyticsApiClient? = null

    // Session tracking
    private val isAppInForeground = AtomicBoolean(false)
    private val lastActivityPauseTime = AtomicLong(0)
    private val handler = Handler(Looper.getMainLooper())
    private val sessionEndRunnable = Runnable { endSession() }

    // Counters
    private val adClicksThisSession = AtomicInteger(0)
    private val adImpressionsThisSession = AtomicInteger(0)
    private var sessionStartTime: Long = 0
    private var lastActivityTime: Long = 0
    private var totalActiveTimeMs: Long = 0

    /**
     * Initialize the analytics manager.
     * @param context Application context
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        val appContext = context.applicationContext
        this.applicationContext = appContext

        try {
            // Initialize the API client with the base URL from AdConfigClient
            val baseUrl = "https://mahimeta.com/api/" // Default base URL
            analyticsApiClient = AnalyticsApiClient.getInstance(baseUrl)

            // Register for activity lifecycle callbacks
            if (appContext is Application) {
                appContext.registerActivityLifecycleCallbacks(this)
            }

            // Start a new session
            startNewSession()

            isInitialized = true

            // Track SDK initialization
            trackEvent(
                AnalyticsEventBuilder(appContext)
                    .setEventType(AnalyticsEvent.EventType.SDK_INITIALIZED)
                    .setAdUnitId("sdk")
                    .withDeviceInfo(true)
                    .withNetworkInfo(true)
                    .withLocaleInfo(true)
                    .build()
            )
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.e("Analytics", "Failed to initialize AnalyticsManager", e)
            }
            throw IllegalStateException("Failed to initialize AnalyticsManager", e)
        }
    }

    private fun startNewSession() {
        // End any existing session
        if (sessionStartTime > 0) {
            endSession()
        }

        // Start new session
        sessionStartTime = System.currentTimeMillis()
        lastActivityTime = sessionStartTime
        adClicksThisSession.set(0)
        adImpressionsThisSession.set(0)

        val context = applicationContext ?: return

        // Track session start
        trackEvent(
            AnalyticsEventBuilder(context)
                .setEventType(AnalyticsEvent.EventType.SESSION_STARTED)
                .setAdUnitId("session")
                .withDeviceInfo(true)
                .withNetworkInfo(true)
                .withLocaleInfo(true)
                .build()
        )

        if (BuildConfig.DEBUG) {
            android.util.Log.d("AnalyticsManager", "New session started")
        }
    }

    private fun endSession() {
        if (sessionStartTime == 0L) return
        
        val now = System.currentTimeMillis()
        val sessionDuration = now - sessionStartTime

        // Update total active time if app is in foreground
        if (isAppInForeground.get() && lastActivityTime > 0) {
            totalActiveTimeMs += (now - lastActivityTime)
        }

        val context = applicationContext ?: return

        // Track session metrics
        trackEvent(
            AnalyticsEventBuilder(context)
                .setEventType(AnalyticsEvent.EventType.SESSION_METRICS)
                .setAdUnitId("session_metrics")
                .addMetadata("session_duration_ms", sessionDuration)
                .addMetadata("active_time_ms", totalActiveTimeMs)
                .addMetadata("ad_clicks", adClicksThisSession.get())
                .addMetadata("ad_impressions", adImpressionsThisSession.get())
                .addMetadata("avg_time_per_click", if (adClicksThisSession.get() > 0) {
                    totalActiveTimeMs / adClicksThisSession.get()
                } else 0)
                .build()
        )

        // Send session end event with context for locale info
        trackEvent(
            AnalyticsEventBuilder(context)
                .setEventType(AnalyticsEvent.EventType.SESSION_ENDED)
                .setAdUnitId("session")
                .addMetadata("session_duration_ms", sessionDuration)
                .addMetadata("active_time_ms", totalActiveTimeMs)
                .addMetadata("ad_clicks", adClicksThisSession.get())
                .addMetadata("ad_impressions", adImpressionsThisSession.get())
                .build()
        )
    }

    /**
     * Track an analytics event using an existing builder.
     * @param builder The AnalyticsEventBuilder to use for creating the event
     */
    fun trackEvent(builder: AnalyticsEventBuilder) {
        if (!isInitialized) return
        trackEvent(builder.build())
    }

    /**
     * Track an analytics event.
     * @param eventType Type of the event
     * @param adUnitId The ad unit ID associated with the event
     * @param metadata Additional metadata for the event
     */
    fun trackEvent(
        eventType: AnalyticsEvent.EventType,
        adUnitId: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        if (!isInitialized || applicationContext == null) return

        val event = AnalyticsEventBuilder(applicationContext!!)
            .setEventType(eventType)
            .setAdUnitId(adUnitId)
            .apply {
                metadata.forEach { (key, value) -> addMetadata(key, value) }
            }
            .build()

        trackEvent(event)
    }

    /**
     * Track an analytics event.
     * @param event The event to track
     */
    internal fun trackEvent(event: AnalyticsEvent) {
        if (!isInitialized) return

        // Update session counters
        when (event.eventType) {
            AnalyticsEvent.EventType.AD_CLICKED -> adClicksThisSession.incrementAndGet()
            AnalyticsEvent.EventType.AD_IMPRESSION -> adImpressionsThisSession.incrementAndGet()
            else -> {}
        }

        // Add event to the queue
        events.add(event)

        // Remove oldest events if we exceed the maximum
        while (events.size > MAX_EVENTS_IN_MEMORY) {
            events.removeAt(0)
        }

        // Send event to server if API client is available
        try {
            analyticsApiClient?.sendEvent(event)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                android.util.Log.e("Analytics", "Failed to send event to server", e)
            }
            // Don't rethrow - we want to continue even if sending fails
        }

        // Log the event for debugging
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                "Analytics",
                "Tracked event: ${event.eventType} for ad unit: ${event.adUnitId}"
            )
        }
    }

    /**
     * Get all tracked events (for testing/debugging purposes).
     * In a production environment, this would be used to send events to a server.
     */
    fun getEvents(): List<AnalyticsEvent> = events.toList()

    /**
     * Clear all tracked events.
     */
    fun clearEvents() {
        events.clear()
    }

    fun cleanup() {
        // Send any remaining events
        if (events.isNotEmpty()) {
            try {
                // Try to send all remaining events
                events.forEach { event ->
                    analyticsApiClient?.sendEvent(event)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e(
                        "Analytics",
                        "Error sending remaining events during cleanup",
                        e
                    )
                }
            }
        }

        // Clear all data
        events.clear()
        isInitialized = false

        // Clean up resources
        analyticsApiClient?.shutdown()
        analyticsApiClient = null

        // Reset state
        applicationContext = null
        lastActivityPauseTime.set(0)
        isAppInForeground.set(false)
        handler.removeCallbacks(sessionEndRunnable)
    }

    // Activity Lifecycle Callbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (!isAppInForeground.getAndSet(true)) {
            // App came to foreground
            startNewSession()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        val now = System.currentTimeMillis()
        lastActivityTime = now
        // Reset the session timeout
        handler.removeCallbacks(sessionEndRunnable)
    }

    override fun onActivityPaused(activity: Activity) {
        val now = System.currentTimeMillis()
        // Update active time
        if (lastActivityTime > 0) {
            totalActiveTimeMs += (now - lastActivityTime)
            lastActivityTime = 0
        }
        // Set a timeout to end the session after 30 minutes of inactivity
        handler.postDelayed(sessionEndRunnable, SESSION_TIMEOUT_MS)
    }

    override fun onActivityStopped(activity: Activity) {
        // Check if the app is in the background
        if (!isAppInForeground.getAndSet(false)) {
            // App went to background
            endSession()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
