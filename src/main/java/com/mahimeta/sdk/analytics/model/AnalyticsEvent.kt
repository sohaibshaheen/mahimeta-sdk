package com.mahimeta.sdk.analytics.model

import com.mahimeta.sdk.utils.DeviceInfo

/**
 * Represents an analytics event to be tracked.
 * @property eventType Type of the event (e.g., AD_LOADED, AD_CLICKED)
 * @property adUnitId The ad unit ID associated with the event
 * @property timestamp When the event occurred
 * @property sessionId The current session ID
 * @property deviceInfo Information about the device
 * @property networkInfo Information about the network
 * @property localeInfo Information about the device locale
 * @property metadata Additional data associated with the event
 */
data class AnalyticsEvent(
    val eventType: EventType,
    val adUnitId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val deviceInfo: Map<String, Any> = emptyMap(),
    val networkInfo: Map<String, Any> = emptyMap(),
    val localeInfo: Map<String, String> = emptyMap(),
    val metadata: Map<String, Any> = emptyMap()
) {
    enum class EventType {
        SDK_INITIALIZED,
        AD_REQUESTED,
        AD_FAILED_TO_LOAD,
        AD_CLICKED,
        AD_IMPRESSION,
        AD_OPENED,
        AD_CLOSED,
        SESSION_STARTED,
        SESSION_ENDED,
        IP_CHANGED,
        SESSION_METRICS,
        NETWORK_CHANGE
    }

    /**
     * Convert the event to a map for easy serialization
     */
    fun toMap(): Map<String, Any> {
        return mapOf(
            "event_type" to eventType.name,
            "ad_unit_id" to adUnitId,
            "timestamp" to timestamp,
            "session_id" to sessionId,
            "device_info" to deviceInfo,
            "network_info" to networkInfo,
            "locale_info" to localeInfo,
            "metadata" to metadata
        )
    }
}

/**
 * Builder class for creating [AnalyticsEvent] instances
 */
class AnalyticsEventBuilder(private val context: android.content.Context) {
    private var eventType: AnalyticsEvent.EventType? = null
    private var adUnitId: String = ""
    private val metadata: MutableMap<String, Any> = mutableMapOf()
    private var includeDeviceInfo: Boolean = true
    private var includeNetworkInfo: Boolean = true
    private var includeLocaleInfo: Boolean = true

    fun setEventType(eventType: AnalyticsEvent.EventType) = apply { this.eventType = eventType }
    fun setAdUnitId(adUnitId: String) = apply { this.adUnitId = adUnitId }
    fun addMetadata(key: String, value: Any) = apply { this.metadata[key] = value }
    fun withDeviceInfo(include: Boolean) = apply { this.includeDeviceInfo = include }
    fun withNetworkInfo(include: Boolean) = apply { this.includeNetworkInfo = include }
    fun withLocaleInfo(include: Boolean) = apply { this.includeLocaleInfo = include }

    fun build(): AnalyticsEvent {
        return AnalyticsEvent(
            eventType = eventType ?: throw IllegalStateException("Event type is required"),
            adUnitId = adUnitId.ifEmpty { "unknown" },
            sessionId = DeviceInfo.getSessionId(),
            deviceInfo = if (includeDeviceInfo) DeviceInfo.getDeviceInfo(context) else emptyMap(),
            networkInfo = if (includeNetworkInfo) DeviceInfo.getNetworkInfo(context) else emptyMap(),
            localeInfo = if (includeLocaleInfo) DeviceInfo.getLocaleInfo() else emptyMap(),
            metadata = metadata
        )
    }

    companion object {
        /**
         * Create a session start event
         */
        fun createSessionStartEvent(context: android.content.Context): AnalyticsEvent {
            return AnalyticsEventBuilder(context)
                .setEventType(AnalyticsEvent.EventType.SESSION_STARTED)
                .setAdUnitId("session")
                .build()
        }

        /**
         * Create a session end event
         */
        fun createSessionEndEvent(context: android.content.Context): AnalyticsEvent {
            return AnalyticsEventBuilder(context)
                .setEventType(AnalyticsEvent.EventType.SESSION_ENDED)
                .setAdUnitId("session")
                .build()
        }

        /**
         * Create an IP changed event
         */
        fun createIpChangedEvent(
            context: android.content.Context,
            oldIp: String,
            newIp: String
        ): AnalyticsEvent {
            return AnalyticsEventBuilder(context)
                .setEventType(AnalyticsEvent.EventType.IP_CHANGED)
                .setAdUnitId("session")
                .addMetadata("old_ip", oldIp)
                .addMetadata("new_ip", newIp)
                .build()
        }
    }
}
