package com.hikariatelier.app

import android.graphics.Bitmap
import android.net.Uri

internal val PREVIEW_ASPECT_RATIOS = listOf("16:9", "4:3", "1:1", "9:16", "device")
internal val LANDSCAPE_PREVIEW_SPLITS = listOf(0.35f, 0.5f, 0.65f)
internal val MP4_BITRATE_OPTIONS = listOf(2, 5, 10)
internal val RECORDING_COUNTDOWN_OPTIONS = listOf(0, 3, 5)
internal const val DEFAULT_X_SHARE_TEXT = "Created with Edit:RiN\n\n#EditRiN #p5js"

internal fun normalizedPreviewAspectRatio(value: String?): String =
    value?.takeIf(PREVIEW_ASPECT_RATIOS::contains) ?: "1:1"

internal fun previewAspectRatioValue(value: String, deviceRatio: Float = 1f): Float = when (value) {
    "4:3" -> 4f / 3f
    "16:9" -> 16f / 9f
    "9:16" -> 9f / 16f
    "device" -> deviceRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    else -> 1f
}

internal data class SavedPreviewMedia(
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
    val sizeBytes: Long = 0,
    val durationMillis: Long = 0,
    val thumbnail: Bitmap? = null
)

internal fun formatRecordingDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L)
}
