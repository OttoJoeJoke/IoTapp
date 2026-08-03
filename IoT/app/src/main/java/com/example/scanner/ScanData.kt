package com.example.scanner

import android.net.Uri

enum class ScanStatus {
    IDLE,
    SCANNING,
    COMPLETED
}

data class CapturedFrame(
    val index: Int,             // 1 to 20
    val angleDegrees: Float,     // Target angle e.g. 0.0, 18.0, 36.0 ...
    val fileUri: Uri,
    val filePath: String,
    val timestampMs: Long = System.currentTimeMillis()
)
