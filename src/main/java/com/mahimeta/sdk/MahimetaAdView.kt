package com.mahimeta.sdk

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.mahimeta.sdk.analytics.AnalyticsManager
import com.mahimeta.sdk.analytics.model.AnalyticsEvent

class MahimetaAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), LifecycleEventObserver {

    private var adView: AdView? = null
    private var mahimetaAdSize: MahimetaAdSize = MahimetaAdSize.BANNER
    private var adUnitId: String =
        "ca-app-pub-3940256099942544/6300978111" // Default test ad unit ID
    private var isAdLoading = false
    private var isAdViewInitialized = false
    private var adImpressionTime: Long = 0
    private val TAG = "MahimetaAdView"

    init {
        // Process XML attributes
        context.withStyledAttributes(attrs, R.styleable.MahimetaAdView) {
            // Get ad unit ID from XML or use default
            getString(R.styleable.MahimetaAdView_mahimetaAdUnitId)?.let {
                adUnitId = it
            }

            // Get ad size from XML or use default
            val sizeAttr = getInt(R.styleable.MahimetaAdView_mahimetaAdSize, 0)
            mahimetaAdSize = when (sizeAttr) {
                1 -> MahimetaAdSize.LARGE_BANNER
                2 -> MahimetaAdSize.MEDIUM_RECTANGLE
                3 -> MahimetaAdSize.FULL_BANNER
                4 -> MahimetaAdSize.LEADERBOARD
                5 -> MahimetaAdSize.SMART_BANNER
                else -> MahimetaAdSize.BANNER
            }
        }
        setupAdView()
        setupAdListener()
        loadAd()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Clean up resources when view is detached
        adView?.destroy()
        adView = null
    }

    /**
     * Sets the ad size for this ad view
     * @param adSize The new ad size to use
     */
    fun setAdSize(adSize: MahimetaAdSize) {
        if (mahimetaAdSize != adSize) {
            mahimetaAdSize = adSize
            setupAdView()
        }
    }

    /**
     * Sets the ad unit ID programmatically
     * @param adUnitId The ad unit ID to use
     */
    fun setAdUnitId(adUnitId: String) {
        if (this.adUnitId != adUnitId) {
            this.adUnitId = adUnitId

            // If adView is already created, update its ad unit ID and reload
            adView?.let { view ->
                view.adUnitId = adUnitId
                loadAd()
            } ?: run {
                // If adView is not created yet, it will use the new adUnitId when created
                isAdViewInitialized = false
                setupAdView()
                loadAd()
            }
        }
    }

    /**
     * Gets the current ad unit ID
     */
    fun getAdUnitId(): String = adUnitId

    /**
     * Loads an ad into the view
     */
    fun loadAd() {
        if (isAdLoading || adUnitId.isBlank()) return

        // Ensure ad view is initialized
        if (adView == null) {
            setupAdView()
        }

        adView?.let { adView ->
            try {
                isAdLoading = true

                // Create ad request
                val adRequest = com.google.android.gms.ads.AdRequest.Builder().build()

                // Load the ad
                adView.loadAd(adRequest)

                // Track ad requested event
                AnalyticsManager.trackEvent(
                    AnalyticsEvent.EventType.AD_REQUESTED,
                    adUnitId
                )

                Log.d(TAG, "Loading ad with unit ID: $adUnitId")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading ad: ${e.message}", e)
                isAdLoading = false
            }
        } ?: run {
            Log.e(TAG, "Failed to initialize AdView")
        }
    }

    private fun setupAdView() {
        if (isAdViewInitialized) return

        // Remove existing ad view if any
        adView?.let {
            removeView(it)
            adView?.destroy()
            adView = null
        }

        // Create new AdView with the current context
        adView = AdView(context).apply {
            // Set the ad unit ID
            this.adUnitId = this@MahimetaAdView.adUnitId

            // Set the ad size
            setAdSize(mahimetaAdSize.toGoogleAdSize(context))

            // Add the AdView to the layout
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        // Add the AdView to our layout
        addView(adView)

        isAdViewInitialized = true
        setupAdListener()
    }

    private fun setupAdListener() {
        adView?.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                Log.d(TAG, "Ad loaded successfully")
                isAdLoading = false

                // Ad loaded successfully, no need to track AD_LOADED event anymore
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                super.onAdFailedToLoad(error)
                Log.e(TAG, "Ad failed to load: ${error.message}")
                isAdLoading = false

                // Track ad load failed event
                AnalyticsManager.trackEvent(
                    AnalyticsEvent.EventType.AD_FAILED_TO_LOAD,
                    adView?.adUnitId ?: "unknown",
                    mapOf(
                        "error_code" to error.code,
                        "error_domain" to error.domain,
                        "error_message" to (error.message ?: "Unknown error")
                    )
                )
            }

            override fun onAdOpened() {
                super.onAdOpened()
                Log.d(TAG, "Ad opened")

                // Track ad opened event
                AnalyticsManager.trackEvent(
                    AnalyticsEvent.EventType.AD_OPENED,
                    adView?.adUnitId ?: "unknown"
                )
            }

            override fun onAdClicked() {
                super.onAdClicked()
                Log.d(TAG, "Ad clicked")

                // Track ad clicked event
                AnalyticsManager.trackEvent(
                    AnalyticsEvent.EventType.AD_CLICKED,
                    adView?.adUnitId ?: "unknown"
                )
            }

            override fun onAdImpression() {
                super.onAdImpression()
                Log.d(TAG, "Ad impression recorded")
                
                // Record the time when ad is shown
                adImpressionTime = System.currentTimeMillis()

                // Track ad impression event
                AnalyticsManager.trackEvent(
                    AnalyticsEvent.EventType.AD_IMPRESSION,
                    adView?.adUnitId ?: "unknown"
                )
            }

            override fun onAdClosed() {
                super.onAdClosed()
                Log.d(TAG, "Ad closed")
                
                // Calculate view duration if impression time was recorded
                val viewDurationMs = if (adImpressionTime > 0) {
                    System.currentTimeMillis() - adImpressionTime
                } else {
                    0L
                }
                
                // Reset the impression time
                adImpressionTime = 0

                // Track ad closed event with view duration
                AnalyticsManager.trackEvent(
                    AnalyticsEvent.EventType.AD_CLOSED,
                    adView?.adUnitId ?: "unknown",
                    mapOf(
                        "view_duration_ms" to viewDurationMs,
                        "view_duration_seconds" to (viewDurationMs / 1000.0)
                    )
                )
            }
        }
    }

    /**
     * Reloads the ad after the specified delay in milliseconds
     */
    fun reloadAd(delayMillis: Long = 30000) {
        postDelayed({ loadAd() }, delayMillis)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> adView?.resume()
            Lifecycle.Event.ON_PAUSE -> adView?.pause()
            Lifecycle.Event.ON_DESTROY -> {
                adView?.destroy()
                source.lifecycle.removeObserver(this)
            }

            else -> {}
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Auto-load ad when attached to window if not already loaded
        if (adView?.adListener == null) {
            setupAdListener()
        }
        // Don't call loadAd() here as it will be called after fetchAndLoadAd()
    }

    companion object {
        /**
         * Helper method to get the adaptive banner size for the current screen width
         */
        fun getAdaptiveBannerSize(context: Context): MahimetaAdSize {
            val displayMetrics = context.resources.displayMetrics
            val widthPixels = displayMetrics.widthPixels.toFloat()
            val density = displayMetrics.density
            val adWidth = (widthPixels / density).toInt()
            return MahimetaAdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                context,
                adWidth
            )
        }
    }
}