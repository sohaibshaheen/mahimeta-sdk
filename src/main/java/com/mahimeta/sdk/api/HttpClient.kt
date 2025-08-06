package com.mahimeta.sdk.api

import com.google.gson.Gson
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class HttpClient(private val baseUrl: String) {
    private val gson = Gson()
    private val executor = Executors.newSingleThreadExecutor()

    internal suspend inline fun <reified T> get(
        endpoint: String,
        queryParams: Map<String, String> = emptyMap()
    ): T = makeRequest("GET", endpoint, queryParams, null)

    internal suspend inline fun <reified T> post(
        endpoint: String,
        body: Any? = null,
        queryParams: Map<String, String> = emptyMap()
    ): T = makeRequest("POST", endpoint, queryParams, body)

    private suspend inline fun <reified T> makeRequest(
        method: String,
        endpoint: String,
        queryParams: Map<String, String> = emptyMap(),
        body: Any? = null
    ): T = suspendCoroutine { continuation ->
        executor.execute {
            try {
                val url = buildUrl(endpoint, queryParams)
                val connection = url.openConnection() as HttpURLConnection

                try {
                    connection.apply {
                        requestMethod = method
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                        connectTimeout = 30000
                        readTimeout = 30000

                        // For POST requests with body
                        if ((method == "POST" || method == "PUT") && body != null) {
                            doOutput = true
                            val output = OutputStreamWriter(outputStream)
                            gson.toJson(body, output)
                            output.flush()
                            output.close()
                        }
                    }

                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        val response = connection.inputStream.bufferedReader().use { reader ->
                            val responseText = reader.readText()
                            if (responseText.isBlank()) {
                                // Return empty response for 204 No Content
                                "{}"
                            } else {
                                responseText
                            }
                        }

                        // Try to parse the response as the expected type
                        try {
                            val result = gson.fromJson(response, T::class.java)
                            continuation.resume(result)
                        } catch (e: Exception) {
                            // If we can't parse the response as the expected type, but the request was successful,
                            // return a success response with the raw text
                            @Suppress("UNCHECKED_CAST")
                            val successResponse =
                                mapOf("success" to true, "message" to response) as T
                            continuation.resume(successResponse)
                        }
                    } else {
                        val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                            ?: connection.responseMessage ?: "Unknown error"
                        throw Exception("HTTP $responseCode: $error")
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    private fun buildUrl(endpoint: String, params: Map<String, String>): URL {
        val url = "$baseUrl$endpoint"
        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${key}=${java.net.URLEncoder.encode(value, "UTF-8")}"
        }
        return URL(if (params.isNotEmpty()) "$url?$queryString" else url)
    }

    fun shutdown() {
        executor.shutdown()
    }
}
