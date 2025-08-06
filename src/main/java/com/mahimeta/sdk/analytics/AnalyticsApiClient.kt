package com.mahimeta.sdk.analytics

import com.mahimeta.sdk.BuildConfig
import com.mahimeta.sdk.analytics.model.AnalyticsEvent
import com.mahimeta.sdk.api.EventResponse
import com.mahimeta.sdk.api.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Client for sending analytics events to the server.
 */
internal class AnalyticsApiClient(private val baseUrl: String) {
    private val httpClient = HttpClient(baseUrl)
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Send an analytics event to the server.
     * @param event The event to send
     */
    fun sendEvent(event: AnalyticsEvent) {
        android.util.Log.d("Analytics sendEvent", "Event Log: $event")
        scope.launch {
            try {
                // Convert the event to a map for sending
//                val eventData = event.toMap()

                // Send the event to the server
                // Using POST request with the event data in the request body
                val response = httpClient.post<Map<String, EventResponse>>(
                    endpoint = "statistics.php",
                    body = event
                )

                // Log success (only in debug mode)
                if (BuildConfig.DEBUG) {
                    android.util.Log.d(
                        "Analytics",
                        "Event sent successfully: ${event.eventType}, Response: $response"
                    )
                }
            } catch (e: Exception) {
                // Log the error but don't crash
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("Analytics", "Failed to send event: ${e.message}")
                }
            }
        }
    }

    /**
     * Shutdown the HTTP client.
     */
    fun shutdown() {
        httpClient.shutdown()
    }

    companion object {
        @Volatile
        private var instance: AnalyticsApiClient? = null

        /**
         * Get the singleton instance of AnalyticsApiClient.
         * @param baseUrl The base URL for the analytics API
         */
        fun getInstance(baseUrl: String): AnalyticsApiClient {
            return instance ?: synchronized(this) {
                instance ?: AnalyticsApiClient(baseUrl).also { instance = it }
            }
        }
    }
}
