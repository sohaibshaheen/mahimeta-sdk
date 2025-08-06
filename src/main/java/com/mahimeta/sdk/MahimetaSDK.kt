package com.mahimeta.sdk

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener
import com.mahimeta.sdk.analytics.AnalyticsManager
import com.mahimeta.sdk.api.AdConfigClient
import com.mahimeta.sdk.model.AdConfig
import com.mahimeta.sdk.network.NetworkMonitor
import com.mahimeta.sdk.utils.DeviceInfo
import com.mahimeta.sdk.utils.ManifestUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main entry point for the Mahimeta SDK.
 * This SDK provides a simple way to integrate and manage AdMob ads with dynamic configuration.
 *
 * @property isInitialized Indicates whether the SDK has been successfully initialized.
 * @property adConfigClient The API client used to fetch ad configurations.
 */

/**
 * Main Mahimeta SDK class for managing AdMob ads with dynamic configuration
 */
object MahimetaSDK {
    private const val TAG = "MahimetaSDK"

    // AdMob configuration
    @Volatile
    private var _adConfig: AdConfig? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    // API client
    internal val adConfigClient by lazy {
        AdConfigClient.getInstance()
    }

    /**
     * Initialize the Mahimeta SDK with the application context.
     * This must be called in your Application class's onCreate() method.
     *
     * @sample
     * ```kotlin
     * class MyApplication : Application() {
     *     override fun onCreate() {
     *         super.onCreate()
     *         MahimetaSDK.initialize(applicationContext)
     *     }
     * }
     * ```
     *
     * @param context The application context
     * @throws IllegalStateException if the SDK is already initialized
     * @throws IllegalArgumentException if context is not an Application context
     */
    fun initialize(context: Context) {
        // Check if already initialized
        // Enable test mode
        val testDeviceIds = listOf(AdRequest.DEVICE_ID_EMULATOR)
        val configuration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(configuration)

        if (_adConfig != null) {
            Log.d(TAG, "MahimetaSDK already initialized")
            return
        }

        // Initialize analytics
        AnalyticsManager.initialize(context)

        // Initialize network monitoring
        NetworkMonitor.getInstance(context).start()

        // Log SDK initialization
        Log.d(TAG, "MahimetaSDK initialized with device: ${DeviceInfo.getDeviceInfo(context)}")
        Log.d(TAG, "OS info: ${DeviceInfo.getOSInfo()}")
        Log.d(TAG, "Locale info: ${DeviceInfo.getLocaleInfo()}")

        if (context !is android.app.Application) {
            throw IllegalArgumentException("MahimetaSDK must be initialized with Application context.")
        }

        try {
            val publisherId = ManifestUtils.getPublisherId(context)

            scope.launch {
                try {
                    val response = adConfigClient.getAdConfig(publisherId)
                    if (response.success) {
                        _adConfig = AdConfig(
                            id = response.data.id,
                            pubId = response.data.pubId,
                            adId = response.data.adId
                        )

                        withContext(Dispatchers.Main) {
                            initializeAdMobWithAppId(
                                context = context,
                                appId = response.data.pubId,
                                listener = { status ->
                                    Log.d(
                                        TAG,
                                        "AdMob SDK initialized with app ID: ${response.data.pubId}"
                                    )
                                    notifyInitializationComplete()
                                }
                            )
                        }
                    } else {
                        Log.e(TAG, "Failed to fetch AdMob configuration")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing MahimetaSDK", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting publisher ID", e)
        }
    }

    /**
     * Initializes the AdMob SDK with the specified app ID using reflection.
     * This is an internal method and should not be called directly.
     *
     * @param context The application context
     * @param appId The AdMob app ID to initialize with
     * @param listener Callback to be invoked when initialization is complete
     */
    private fun initializeAdMobWithAppId(
        context: Context,
        appId: String,
        listener: OnInitializationCompleteListener
    ) {
        try {
            val zzejClass = Class.forName("com.google.android.gms.ads.internal.client.zzej")
            val zzejInstance = zzejClass.getMethod("zzf").invoke(null)

            zzejInstance.javaClass.getMethod(
                "zzm",
                Context::class.java,
                String::class.java,
                OnInitializationCompleteListener::class.java
            ).invoke(zzejInstance, context, appId, listener)

            Log.d(TAG, "AdMob initialized with app ID: $appId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AdMob with app ID: $appId", e)
        }
    }

    /**
     * Creates a new banner ad request with default settings.
     *
     * @return A new [AdRequest] instance
     */
    fun createBannerAdRequest(): AdRequest = AdRequest.Builder().build()

    /**
     * Cleans up resources used by the SDK.
     * Call this when the app is being terminated or when you need to reinitialize the SDK.
     *
     * @sample
     * ```kotlin
     * override fun onTerminate() {
     *     MahimetaSDK.cleanup()
     *     super.onTerminate()
     * }
     * ```
     */
    /**
     * Cleans up all resources used by the SDK.
     * Call this when the app is being terminated or when you need to reinitialize the SDK.
     * This will:
     * 1. Cancel any pending coroutines
     * 2. Clean up the analytics manager
     * 3. Stop network monitoring
     * 4. Clear any cached configurations
     * 5. Clear all initialization observers
     *
     * @sample
     * ```kotlin
     * override fun onTerminate() {
     *     MahimetaSDK.cleanup()
     *     super.onTerminate()
     * }
     * ```
     */
    @Synchronized
    fun cleanup(applicationContext: Context) {
        try {
            // 1. Cancel any pending coroutines
            scope.cancel()

            // 2. Clean up analytics manager (this will flush any pending events)
            AnalyticsManager.cleanup()

            // 3. Stop network monitoring
            applicationContext.let { context ->
                NetworkMonitor.getInstance(context).stop()
            }

            // 4. Clear cached configurations
            _adConfig = null
            initializationObservers.clear()

            // 5. Clear any cached data in DeviceInfo
            DeviceInfo.clearCache()

            Log.d(TAG, "MahimetaSDK cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during SDK cleanup", e)
        }
    }

    private val initializationObservers = mutableListOf<() -> Unit>()

    /**
     * Indicates whether the SDK has been successfully initialized with a valid configuration.
     */
    val isInitialized: Boolean
        get() = _adConfig != null

    /**
     * Registers a callback to be invoked when the SDK is initialized.
     *
     * @param listener The callback to be invoked when initialization is complete.
     * If the SDK is already initialized, the listener will be invoked immediately.
     */
    fun addInitializationListener(listener: () -> Unit) {
        if (isInitialized) {
            listener()
        } else {
            initializationObservers.add(listener)
        }
    }

    /**
     * Unregisters a previously registered initialization listener.
     *
     * @param listener The callback to be unregistered.
     */
    fun removeInitializationListener(listener: () -> Unit) {
        initializationObservers.remove(listener)
    }

    /**
     * Notifies all registered listeners that initialization is complete.
     * This is called internally after the SDK has been successfully initialized.
     */
    private fun notifyInitializationComplete() {
        initializationObservers.forEach { it() }
        initializationObservers.clear()
    }
}
