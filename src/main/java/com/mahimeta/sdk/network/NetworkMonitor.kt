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
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.NetworkInterface
import kotlinx.coroutines.withTimeoutOrNull
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val oldIp = currentIp.get()
                val newIp = getPublicIpAddress()

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
                        .addMetadata("is_public_ip", !newIp.startsWith("10.") && 
                                                  !newIp.startsWith("192.168.") && 
                                                  !newIp.startsWith("172."))
                        .build()

                    AnalyticsManager.trackEvent(event)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("NetworkMonitor", "Error checking IP address", e)
                }
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

    private suspend fun getPublicIpAddress(): String = withContext(Dispatchers.IO) {
        // First try to get the public IP from network interfaces (works for mobile data)
        val mobileIp = getMobileDataIpAddress()
        if (mobileIp.isNotEmpty() && isPublicIp(mobileIp)) {
            return@withContext mobileIp
        }
        
        // If no public IP found from mobile data, try WiFi interface
        val wifiIp = getWifiIpAddress()
        if (wifiIp.isNotEmpty() && isPublicIp(wifiIp)) {
            return@withContext wifiIp
        }
        
        // If still no public IP, try external services as last resort
        try {
            // Try multiple IP checking services as fallback
            val ipServices = listOf(
                "https://api.ipify.org?format=text",
                "https://api64.ipify.org?format=text",
                "https://ipinfo.io/ip",
                "https://ifconfig.me/ip",
                "https://icanhazip.com"
            )

            for (service in ipServices) {
                try {
                    val url = URL(service)
                    val connection = withTimeoutOrNull(2000) {
                        url.openConnection() as HttpURLConnection
                    } ?: continue
                    
                    connection.connectTimeout = 2000 // 2 seconds timeout
                    connection.readTimeout = 2000
                    
                    val ip = withTimeoutOrNull(2000) {
                        connection.inputStream.bufferedReader().use { it.readLine() }.trim()
                    }
                    
                    if (!ip.isNullOrEmpty() && isPublicIp(ip)) {
                        return@withContext ip
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) {
                        Log.d("NetworkMonitor", "Failed to get IP from $service: ${e.message}")
                    }
                    // Try next service
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("NetworkMonitor", "Error in getPublicIpAddress", e)
            }
        }
        
        // Fallback to any available IP if no public IP found
        return@withContext getAnyAvailableIpAddress()
    }
    
    private fun getMobileDataIpAddress(): String {
        return try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                // Check if this is a mobile data interface
                if (networkInterface.name.startsWith("rmnet") || 
                    networkInterface.name.startsWith("rmnet_data") ||
                    networkInterface.name.startsWith("p2p")) {
                    
                    for (address in networkInterface.inetAddresses) {
                        val hostAddress = address.hostAddress ?: continue
                        if (hostAddress.contains(':')) continue // Skip IPv6
                        return hostAddress
                    }
                }
            }
            ""
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("NetworkMonitor", "Error getting mobile data IP", e)
            }
            ""
        }
    }
    
    private fun getWifiIpAddress(): String {
        return try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                // Check if this is a WiFi interface
                if (networkInterface.name.startsWith("wlan") || 
                    networkInterface.name.startsWith("ap") ||
                    networkInterface.name.startsWith("eth")) {
                    
                    for (address in networkInterface.inetAddresses) {
                        val hostAddress = address.hostAddress ?: continue
                        if (hostAddress.contains(':')) continue // Skip IPv6
                        if (hostAddress.startsWith("192.168.") || 
                            hostAddress.startsWith("172.") || 
                            hostAddress.startsWith("10.")) {
                            continue // Skip private IPs
                        }
                        return hostAddress
                    }
                }
            }
            ""
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("NetworkMonitor", "Error getting WiFi IP", e)
            }
            ""
        }
    }
    
    private fun getAnyAvailableIpAddress(): String {
        return try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                
                for (address in networkInterface.inetAddresses) {
                    val hostAddress = address.hostAddress ?: continue
                    if (hostAddress.contains(':')) continue // Skip IPv6
                    if (hostAddress == "127.0.0.1") continue
                    return hostAddress
                }
            }
            ""
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("NetworkMonitor", "Error getting any IP address", e)
            }
            ""
        }
    }
    
    private fun isPublicIp(ip: String): Boolean {
        return ip.isNotBlank() && 
               ip != "127.0.0.1" && 
               ip != "0.0.0.0" && 
               !ip.startsWith("10.") && 
               !ip.startsWith("192.168.") &&
               !ip.startsWith("172.16.") && !ip.startsWith("172.17.") &&
               !ip.startsWith("172.18.") && !ip.startsWith("172.19.") &&
               !ip.startsWith("172.20.") && !ip.startsWith("172.21.") &&
               !ip.startsWith("172.22.") && !ip.startsWith("172.23.") &&
               !ip.startsWith("172.24.") && !ip.startsWith("172.25.") &&
               !ip.startsWith("172.26.") && !ip.startsWith("172.27.") &&
               !ip.startsWith("172.28.") && !ip.startsWith("172.29.") &&
               !ip.startsWith("172.30.") && !ip.startsWith("172.31.")
    }
    
    private fun getLocalIPv4Address(): String {
        return try {
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
            ""
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("NetworkMonitor", "Error getting local IPv4 address", e)
            }
            ""
        }
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
