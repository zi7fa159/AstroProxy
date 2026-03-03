package com.astroproxy

data class Command(
    val type: String, // e.g., "CAPTURE", "CONFIG_UPDATE"
    val params: CaptureParams?
)

data class CaptureParams(
    val iso: Int?,
    val exposureTimeNs: Long?,
    val format: String? // "RAW" or "JPEG"
)

data class StatusUpdate(
    val status: String,
    val details: String?
)
