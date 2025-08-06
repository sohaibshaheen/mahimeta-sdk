package com.mahimeta.sdk.model

import com.google.gson.annotations.SerializedName

data class AdConfig(
    val id: String,
    val pubId: String,
    val adId: String
)

data class AdConfigResponse(
    val success: Boolean,
    val data: AdConfigData
)

data class AdConfigData(
    val id: String,
    @SerializedName("pub_id") val pubId: String,
    @SerializedName("ad_id") val adId: String
)
