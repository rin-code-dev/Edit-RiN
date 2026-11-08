package com.hikariatelier.app

import android.graphics.Bitmap
import android.net.Uri

internal val PREVIEW_ASPECT_RATIOS = listOf("device_landscape", "16:9", "3:2", "4:3", "1:1", "4:5", "3:4", "9:16", "device")
internal val LANDSCAPE_PREVIEW_SPLITS = listOf(0.35f, 0.5f, 0.65f)
internal val MP4_BITRATE_OPTIONS = listOf(2, 5, 10)
internal val RECORDING_COUNTDOWN_OPTIONS = listOf(0, 3, 5)
internal const val DEFAULT_X_SHARE_TEXT = "Created with Edit:RiN\n\n#EditRiN #p5js"
private val NUMERIC_ASPECT_RATIO = Regex("^([0-9]{1,4}):([0-9]{1,4})$")

internal fun customCanvasDimensionsValid(width: Int?, height: Int?): Boolean =
    width != null && height != null && width in 1..8192 && height in 1..8192 && width.toLong() * height <= 16_000_000L

internal fun ratioFromDimensions(width: Int, height: Int): String {
    require(width in 1..8192 && height in 1..8192)
    var a = width
    var b = height
    while (b != 0) { val remainder = a % b; a = b; b = remainder }
    return "${width / a}:${height / a}"
}

internal fun normalizedPreviewAspectRatio(value: String?): String {
    if (value == "device" || value == "device_landscape") return value
    val match = value?.let(NUMERIC_ASPECT_RATIO::matchEntire) ?: return "1:1"
    val width = match.groupValues[1].toIntOrNull() ?: return "1:1"
    val height = match.groupValues[2].toIntOrNull() ?: return "1:1"
    return if (width in 1..8192 && height in 1..8192) ratioFromDimensions(width, height) else "1:1"
}

internal fun previewAspectRatioOptions(current: String): List<String> {
    val normalized = normalizedPreviewAspectRatio(current)
    return if (normalized in PREVIEW_ASPECT_RATIOS) PREVIEW_ASPECT_RATIOS else PREVIEW_ASPECT_RATIOS + normalized
}

internal fun previewAspectRatioLabel(ratio: String, uiText: (String) -> String = { it }): String = when (ratio) {
    "device_landscape" -> uiText("端末・横")
    "device" -> uiText("端末・縦")
    else -> ratio
}

internal fun previewAspectRatioValue(value: String, deviceRatio: Float = 1f): Float {
    val validDeviceRatio = deviceRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val landscapeDeviceRatio = if (validDeviceRatio >= 1f) validDeviceRatio else 1f / validDeviceRatio
    val portraitDeviceRatio = if (validDeviceRatio <= 1f) validDeviceRatio else 1f / validDeviceRatio
    return when (val ratio = normalizedPreviewAspectRatio(value)) {
        "device_landscape" -> landscapeDeviceRatio
        "device" -> portraitDeviceRatio
        else -> ratio.substringBefore(':').toFloat() / ratio.substringAfter(':').toFloat()
    }
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
