package com.mahimeta.sdk.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object ManifestUtils {
    private const val META_DATA_PUBLISHER_ID = "com.mahimeta.sdk.PUBLISHER_ID"

    fun getPublisherId(context: Context): String {
        try {
            val appInfo: ApplicationInfo = context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            
            return appInfo.metaData?.getString(META_DATA_PUBLISHER_ID)
                ?: throw IllegalStateException("Publisher ID not found in AndroidManifest. Add the following meta-data tag to your application tag:\n" +
                        "<meta-data android:name=\"com.mahimeta.sdk.PUBLISHER_ID\" android:value=\"YOUR_PUBLISHER_ID\"/>")
        } catch (e: PackageManager.NameNotFoundException) {
            throw IllegalStateException("Failed to load meta-data, NameNotFoundException: ${e.message}")
        } catch (e: NullPointerException) {
            throw IllegalStateException("Failed to load meta-data, NullPointer: ${e.message}")
        }
    }
}
