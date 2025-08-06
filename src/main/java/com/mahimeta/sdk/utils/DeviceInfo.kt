package com.mahimeta.sdk.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.util.Locale
import java.util.UUID

/**
 * Utility class to collect device and network information.
 */
internal object DeviceInfo {
    private const val TAG = "DeviceInfo"
    private var sessionId: String = ""
    private var ipAddress: String = "unknown"
    
    // Custom UI detection
    private val CUSTOM_UI_UNKNOWN = "unknown"
    private val CUSTOM_UI_ONE_UI = "oneui"
    private val CUSTOM_UI_MIUI = "miui"
    private val CUSTOM_UI_EMUI = "emui"
    private val CUSTOM_UI_COLOROS = "coloros"
    private val CUSTOM_UI_OXYGENOS = "oxygenos"
    private val CUSTOM_UI_SAMSUNG_EXPERIENCE = "samsung_experience"

    init {
        // Generate a new session ID when the class is loaded
        generateNewSessionId()
    }

    /**
     * Generate a new session ID
     */
    fun generateNewSessionId() {
        sessionId = UUID.randomUUID().toString()
    }

    /**
     * Get the current session ID
     */
    fun getSessionId(): String = sessionId

    /**
     * Get device manufacturer, model, and display information
     */
    fun getDeviceInfo(context: Context): Map<String, Any> {
        val displayMetrics = context.resources.displayMetrics
        val widthPx = displayMetrics.widthPixels
        val heightPx = displayMetrics.heightPixels
        val density = displayMetrics.density
        val densityDpi = displayMetrics.densityDpi
        val scaledDensity = displayMetrics.scaledDensity
        val xdpi = displayMetrics.xdpi
        val ydpi = displayMetrics.ydpi
        
        // Get OS info including custom UI detection
        val osInfo = getOSInfo()
        
        return mapOf(
            // Basic device info
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "product" to Build.PRODUCT,
            "device" to Build.DEVICE,
            "hardware" to Build.HARDWARE,
            "brand" to Build.BRAND,
            "display" to Build.DISPLAY,
            "is_emulator" to isEmulator().toString(),
            
            // OS info
            "os_version" to (osInfo["os_version"] ?: ""),
            "sdk_int" to (osInfo["sdk_int"] ?: 0),
            "custom_ui" to (osInfo["custom_ui"] ?: ""),
            "custom_ui_version" to (osInfo["custom_ui_version"] ?: ""),
            "is_custom_ui" to (osInfo["is_custom_ui"] ?: false),
            
            // Screen resolution and metrics
            "screen_resolution" to "${widthPx}x$heightPx",
            "screen_width_px" to widthPx,
            "screen_height_px" to heightPx,
            "screen_density" to density,
            "density_dpi" to densityDpi,
            "scaled_density" to scaledDensity,
            "xdpi" to xdpi,
            "ydpi" to ydpi
        )
    }

    /**
     * Get operating system information
     */
    /**
     * Get operating system information including custom UI detection
     */
    fun getOSInfo(): Map<String, Any> {
        val customUI = detectCustomUI()
        
        return mapOf(
            "os_version" to Build.VERSION.RELEASE,
            "sdk_int" to Build.VERSION.SDK_INT,
            "build_id" to Build.ID,
            "build_type" to Build.TYPE,
            "build_tags" to Build.TAGS,
            "build_time" to Build.TIME,
            "custom_ui" to customUI.first,
            "custom_ui_version" to customUI.second,
            "is_custom_ui" to (customUI.first != CUSTOM_UI_UNKNOWN)
        )
    }
    
    /**
     * Detect custom Android UI/ROM
     * @return Pair of (ui_name, ui_version)
     */
    private fun detectCustomUI(): Pair<String, String> {
        return when {
            // Samsung One UI / Samsung Experience
            Build.MANUFACTURER.equals("samsung", ignoreCase = true) -> {
                try {
                    // Try to detect One UI version
                    val pattern = "(\\d+\\.\\d+)\\s*\\w*$".toRegex()
                    val version = pattern.find(Build.DISPLAY)?.value ?: ""
                    
                    // One UI versions typically start with 1.0 (Android 9)
                    if (version.isNotEmpty() && version.matches("\\d+\\.\\d+".toRegex())) {
                        return Pair(CUSTOM_UI_ONE_UI, version.trim())
                    }
                    
                    // Fallback to Samsung Experience for older devices
                    Pair(CUSTOM_UI_SAMSUNG_EXPERIENCE, version.ifEmpty { "unknown" })
                } catch (e: Exception) {
                    Pair(CUSTOM_UI_SAMSUNG_EXPERIENCE, "unknown")
                }
            }
            
            // Xiaomi MIUI
            isSystemPropertyExist("ro.miui.ui.version.name") -> {
                val version = getSystemProperty("ro.miui.ui.version.name", "")
                Pair(CUSTOM_UI_MIUI, version.ifEmpty { "unknown" })
            }
            
            // Huawei EMUI
            isSystemPropertyExist("ro.build.version.emui") -> {
                val version = getSystemProperty("ro.build.version.emui", "")
                Pair(CUSTOM_UI_EMUI, version.replace("EmotionUI_", "").ifEmpty { "unknown" })
            }
            
            // OPPO ColorOS
            isSystemPropertyExist("ro.oppo.theme.version") -> {
                val version = getSystemProperty("ro.oppo.theme.version", "")
                Pair(CUSTOM_UI_COLOROS, version.ifEmpty { "unknown" })
            }
            
            // OnePlus OxygenOS
            isSystemPropertyExist("ro.oxygen.version") -> {
                val version = getSystemProperty("ro.oxygen.version", "")
                Pair(CUSTOM_UI_OXYGENOS, version.ifEmpty { "unknown" })
            }
            
            // Default case (stock Android or unknown custom ROM)
            else -> Pair(CUSTOM_UI_UNKNOWN, "")
        }
    }
    
    /**
     * Check if a system property exists
     */
    private fun isSystemPropertyExist(propName: String): Boolean {
        return try {
            val process = ProcessBuilder("/system/bin/getprop", propName).start()
            val reader = process.inputStream.bufferedReader()
            val value = reader.use { it.readLine() }
            value != null && value.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get system property value
     */
    private fun getSystemProperty(propName: String, defaultValue: String = ""): String {
        return try {
            val process = ProcessBuilder("/system/bin/getprop", propName).start()
            val reader = process.inputStream.bufferedReader()
            reader.use { it.readLine() } ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Check if the device is an emulator
     */
    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }

    /**
     * Get network information
     */
    fun getNetworkInfo(context: Context): Map<String, Any> {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)

        return mapOf(
            "is_connected" to (network != null && capabilities != null),
            "network_type" to getNetworkType(capabilities),
            "ip_address" to ipAddress
        )
    }

    /**
     * Set the current IP address (to be called when network changes)
     */
    fun setIpAddress(ip: String) {
        // Only update if the IP has changed
        if (ip != ipAddress) {
            ipAddress = ip
            // Generate a new session ID when IP changes
            generateNewSessionId()
        }
    }

    /**
     * Get the current IP address
     */
    fun getIpAddress(): String = ipAddress

    /**
     * Clear any cached data in the DeviceInfo class
     */
    fun clearCache() {
        // Reset the IP address to default
        ipAddress = "unknown"
        // Optionally: generate a new session ID
        // generateNewSessionId()
    }

    private fun getNetworkType(capabilities: NetworkCapabilities?): String {
        return when {
            capabilities == null -> "disconnected"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "unknown"
        }
    }

    /**
     * Get device locale information
     */
    fun getLocaleInfo(): Map<String, String> {
        val locale = Locale.getDefault()
        return mapOf(
            "country" to locale.country,
            "language" to locale.language,
            "display_country" to locale.displayCountry,
            "display_language" to locale.displayLanguage,
            "display_name" to locale.displayName,
            "display_variant" to locale.displayVariant,
            "iso3_country" to locale.isO3Country,
            "iso3_language" to locale.isO3Language,
            "variant" to locale.variant
        )
    }
}
