package com.mahimeta.sdk.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mahimeta.sdk.BuildConfig
import com.mahimeta.sdk.analytics.AnalyticsManager
import com.mahimeta.sdk.analytics.model.AnalyticsEvent
import com.mahimeta.sdk.analytics.model.AnalyticsEventBuilder
import com.mahimeta.sdk.utils.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicReference

/**
 * Monitors network state and IP address changes using the modern NetworkCallback API.
 */
internal class NetworkMonitor private constructor(private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            // Only check IP when network becomes available
            checkIpAddress()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            // Clear current IP when network is lost
            val oldIp = currentIp.getAndSet("")
            if (oldIp.isNotEmpty()) {
                onIpChanged(oldIp, "")
            }
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            super.onLinkPropertiesChanged(network, linkProperties)
            // Check for IP changes when link properties change
            checkIpAddress()
        }
    }

    private val networkRequest = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private val currentIp = AtomicReference<String>("")

    private val ipCheckRunnable = object : Runnable {
        override fun run() {
            checkIpAddress()
            mainHandler.postDelayed(this, 10000)
        }
    }

    init {
        // Initial IP check
        CoroutineScope(Dispatchers.IO).launch {
            checkIpAddress()
        }
    }

    /**
     * Start monitoring network changes
     */
    fun start() {
        if (isMonitoring) return

        try {
            // Register network callback
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isMonitoring = true

            // Do an initial IP check
            checkIpAddress()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("NetworkMonitor", "Error starting network monitoring", e)
            }
        }
    }

    /**
     * Stop monitoring network changes
     */
    fun stop() {
        if (!isMonitoring) return

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            mainHandler.removeCallbacks(ipCheckRunnable)
            isMonitoring = false
        } catch (e: Exception) {
            // Already unregistered, ignore
        }
    }

    private fun checkIpAddress() {
        try {
            val oldIp = currentIp.get()
            val newIp = getIPv4Address()

            // Only proceed if IP has actually changed
            if (oldIp != newIp) {
                currentIp.set(newIp)

                // Only log if we had a previous IP (avoid logging initial IP set)
                if (oldIp.isNotEmpty()) {
                    onIpChanged(oldIp, newIp)
                }

                // Update device info with new IP
                DeviceInfo.setIpAddress(newIp)

                // Log the network change
                val event = AnalyticsEventBuilder(context)
                    .setEventType(AnalyticsEvent.EventType.NETWORK_CHANGE)
                    .addMetadata("ip_address", newIp)
                    .addMetadata("previous_ip", oldIp)
                    .addMetadata("network_type", getCurrentNetworkType())
                    .build()

                AnalyticsManager.trackEvent(event)
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("NetworkMonitor", "Error checking IP address", e)
            }
        }
    }

    private fun getCurrentNetworkType(): String {
        return try {
            val network = connectivityManager.activeNetwork ?: return "disconnected"
            val capabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return "unknown"

            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Get the current IP address
     */
    fun getCurrentIp(): String = currentIp.get()


    private fun onIpChanged(oldIp: String, newIp: String) {
        // Log IP change event
        AnalyticsManager.trackEvent(
            AnalyticsEventBuilder.createIpChangedEvent(context, oldIp, newIp)
        )
    }

    private fun getIPv4Address(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()

            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                // Skip loopback and inactive interfaces
                if (intf.isLoopback || !intf.isUp) continue

                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    // Skip loopback and link-local addresses
                    if (address.isLoopbackAddress || address.isLinkLocalAddress) continue

                    // Only return IPv4 addresses
                    val hostAddress = address.hostAddress ?: continue
                    if (hostAddress.contains('.')) {
                        return hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("NetworkMonitor", "Error getting IPv4 address", e)
            }
        }

        return ""
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: NetworkMonitor? = null

        fun getInstance(context: Context): NetworkMonitor {
            return instance ?: synchronized(this) {
                instance ?: NetworkMonitor(context.applicationContext).also { instance = it }
            }
        }
    }
}
