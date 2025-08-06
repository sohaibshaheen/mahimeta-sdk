package com.mahimeta.sdk

import android.content.Context
import com.google.android.gms.ads.AdSize

/**
 * Custom AdSize implementation with support for standard, adaptive and fluid ad sizes
 */
class MahimetaAdSize constructor(
    internal val width: Int,
    internal val height: Int,
    internal val type: AdSizeType = AdSizeType.STANDARD
) {
    enum class AdSizeType {
        STANDARD,    // Fixed size ads (BANNER, LARGE_BANNER, etc.)
        ADAPTIVE,    // Size changes based on device width
        FLUID        // Fluid ads (width MATCH_PARENT, height WRAP_CONTENT)
    }

    companion object {
        // Standard sizes (matches Google's AdSize constants)
        @JvmField
        val BANNER = MahimetaAdSize(320, 50)

        @JvmField
        val LARGE_BANNER = MahimetaAdSize(320, 100)

        @JvmField
        val MEDIUM_RECTANGLE = MahimetaAdSize(300, 250)

        @JvmField
        val FULL_BANNER = MahimetaAdSize(468, 60)

        @JvmField
        val LEADERBOARD = MahimetaAdSize(728, 90)

        @JvmField
        val SMART_BANNER = MahimetaAdSize(-1, -2) // Width will be set to screen width, height auto

        @JvmField
        val FLUID = MahimetaAdSize(-1, -1, AdSizeType.FLUID)

        /**
         * Creates an adaptive banner size anchored to current orientation
         */
        @JvmStatic
        fun getCurrentOrientationAnchoredAdaptiveBannerAdSize(
            context: Context,
            widthPx: Int
        ): MahimetaAdSize {
            val metrics = context.resources.displayMetrics
            val widthDp = (widthPx / metrics.density).toInt()

            return when {
                widthDp <= 360 -> MahimetaAdSize(widthDp, 50, AdSizeType.ADAPTIVE)
                widthDp <= 720 -> MahimetaAdSize(widthDp, 90, AdSizeType.ADAPTIVE)
                else -> MahimetaAdSize(widthDp, 90, AdSizeType.ADAPTIVE)
            }
        }

        /**
         * Creates a portrait-oriented adaptive banner size
         */
        @JvmStatic
        fun getPortraitAnchoredAdaptiveBannerAdSize(
            context: Context,
            widthPx: Int
        ): MahimetaAdSize {
            return getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthPx)
        }

        /**
         * Creates a landscape-oriented adaptive banner size
         */
        @JvmStatic
        fun getLandscapeAnchoredAdaptiveBannerAdSize(
            context: Context,
            widthPx: Int
        ): MahimetaAdSize {
            val metrics = context.resources.displayMetrics
            val widthDp = (widthPx / metrics.density).toInt()

            return when {
                widthDp <= 360 -> MahimetaAdSize(widthDp, 32, AdSizeType.ADAPTIVE)
                widthDp <= 720 -> MahimetaAdSize(widthDp, 50, AdSizeType.ADAPTIVE)
                else -> MahimetaAdSize(widthDp, 90, AdSizeType.ADAPTIVE)
            }
        }
    }

    val isAutoHeight: Boolean
        get() = type == AdSizeType.ADAPTIVE || type == AdSizeType.FLUID

    val isFluid: Boolean
        get() = type == AdSizeType.FLUID

    fun getWidthInPixels(context: Context): Int {
        return (width * context.resources.displayMetrics.density).toInt()
    }

    fun getHeightInPixels(context: Context): Int {
        return (height * context.resources.displayMetrics.density).toInt()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MahimetaAdSize) return false
        return width == other.width &&
                height == other.height &&
                type == other.type
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + type.hashCode()
        return result
    }

    override fun toString(): String {
        return when (type) {
            AdSizeType.STANDARD -> "STANDARD($width x $height)"
            AdSizeType.ADAPTIVE -> "ADAPTIVE($width x $height)"
            AdSizeType.FLUID -> "FLUID"
        }
    }
}

// Extension function to convert Google AdSize to MahimetaAdSize
private fun AdSize.toMahimetaAdSize(context: Context): MahimetaAdSize {
    return when (this) {
        AdSize.BANNER -> MahimetaAdSize.BANNER
        AdSize.LARGE_BANNER -> MahimetaAdSize.LARGE_BANNER
        AdSize.MEDIUM_RECTANGLE -> MahimetaAdSize.MEDIUM_RECTANGLE
        AdSize.FULL_BANNER -> MahimetaAdSize.FULL_BANNER
        AdSize.LEADERBOARD -> MahimetaAdSize.LEADERBOARD
        AdSize.FLUID -> MahimetaAdSize.FLUID
        AdSize.SMART_BANNER -> MahimetaAdSize.SMART_BANNER
        else -> {
            if (isAdaptiveBanner()) {
                val metrics = context.resources.displayMetrics
                val widthDp = (getWidthInPixels(context) / metrics.density).toInt()
                val heightDp = (getHeightInPixels(context) / metrics.density).toInt()
                MahimetaAdSize(widthDp, heightDp, MahimetaAdSize.AdSizeType.ADAPTIVE)
            } else {
                MahimetaAdSize(getWidth(), getHeight(), MahimetaAdSize.AdSizeType.STANDARD)
            }
        }
    }
}

// Helper function to check if AdSize is adaptive
private fun AdSize.isAdaptiveBanner(): Boolean {
    return try {
        val method = AdSize::class.java.getMethod("isAdaptiveBanner")
        method.invoke(this) as Boolean
    } catch (e: Exception) {
        false
    }
}

// Extension function to convert MahimetaAdSize to Google's AdSize
internal fun MahimetaAdSize.toGoogleAdSize(context: Context): AdSize {
    return when (this) {
        MahimetaAdSize.BANNER -> AdSize.BANNER
        MahimetaAdSize.LARGE_BANNER -> AdSize.LARGE_BANNER
        MahimetaAdSize.MEDIUM_RECTANGLE -> AdSize.MEDIUM_RECTANGLE
        MahimetaAdSize.FULL_BANNER -> AdSize.FULL_BANNER
        MahimetaAdSize.LEADERBOARD -> AdSize.LEADERBOARD
        MahimetaAdSize.SMART_BANNER -> AdSize.SMART_BANNER
        MahimetaAdSize.FLUID -> AdSize.FLUID
        else -> {
            if (isAutoHeight) {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    context,
                    getWidthInPixels(context)
                ) ?: AdSize.BANNER // Fallback to BANNER if null
            } else {
                AdSize(width, height)
            }
        }
    }
}