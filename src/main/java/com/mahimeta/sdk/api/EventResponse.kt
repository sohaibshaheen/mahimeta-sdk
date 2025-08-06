package com.mahimeta.sdk.api

data class EventResponse(
    val success: Boolean,
    val message: String,
    val logged_at: String,
    val event_type: String?,
    val session_id: String?
)