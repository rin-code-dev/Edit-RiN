package com.hikariatelier.app

internal data class PreviewSize(
    val width: Float,
    val height: Float
)

/** Fits the preview inside both bounds without cropping or changing its aspect ratio. */
internal fun fitPreviewSize(
    maxWidth: Float,
    maxHeight: Float,
    aspectRatio: Float
): PreviewSize {
    val widthBound = maxWidth.takeIf { it.isFinite() && it > 0f } ?: 0f
    val heightBound = maxHeight.takeIf { it.isFinite() && it > 0f } ?: 0f
    if (widthBound == 0f || heightBound == 0f) return PreviewSize(0f, 0f)

    val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    // Double intermediates avoid overflow for finite but very large dimensions or ratios.
    val height = minOf(heightBound.toDouble(), widthBound.toDouble() / ratio.toDouble())
    val width = height * ratio.toDouble()
    val fittedWidth = width.toFloat().coerceAtMost(widthBound)
    val fittedHeight = height.toFloat().coerceAtMost(heightBound)

    // A dimension below Float's representable range cannot form a drawable rectangle.
    if (fittedWidth == 0f || fittedHeight == 0f) return PreviewSize(0f, 0f)
    return PreviewSize(fittedWidth, fittedHeight)
}

/** Presentation only: hiding must not recreate the WebView or resize the artwork. */
internal fun portraitPreviewHeight(width: Float, heightLimit: Float, ratio: Float, hidden: Boolean, compact: Boolean): Float {
    if (hidden) return 0f
    val fitted = fitPreviewSize(width, heightLimit, if (compact) 2.5f else ratio)
    return fitted.height
}
