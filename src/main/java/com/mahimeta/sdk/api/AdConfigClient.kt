package com.mahimeta.sdk.api

import com.mahimeta.sdk.model.AdConfigResponse

class AdConfigClient private constructor() {
    private val httpClient = HttpClient("https://mahimeta.com/api/")

    suspend fun getAdConfig(publisherId: String) =
        httpClient.get<AdConfigResponse>(
            endpoint = "ad_serve.php",
            queryParams = mapOf("publisher_id" to publisherId)
        )

    fun shutdown() {
        httpClient.shutdown()
    }

    companion object {
        @Volatile
        private var instance: AdConfigClient? = null

        fun getInstance(): AdConfigClient {
            return instance ?: synchronized(this) {
                instance ?: AdConfigClient().also { instance = it }
            }
        }
    }
}
