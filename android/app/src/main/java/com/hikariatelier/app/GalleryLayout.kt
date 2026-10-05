package com.hikariatelier.app

import kotlin.math.floor

/** Add columns on wide screens while leaving room for titles and their menu touch targets. */
internal fun galleryColumnCount(widthDp: Float, fontScale: Float, landscape: Boolean): Int {
    if (!widthDp.isFinite() || widthDp <= 0f) return 1
    val scale = if (fontScale.isFinite()) fontScale.coerceAtLeast(1f) else 1f
    if (!landscape && scale <= 1.3f) return 2
    val minimumCardWidth = if (landscape) 160.0 * scale else 168.0
    val availableWidth = (widthDp.toDouble() - 32.0).coerceAtLeast(0.0)
    return floor((availableWidth + 14.0) / (minimumCardWidth + 14.0)).toInt().coerceIn(1, 12)
}
