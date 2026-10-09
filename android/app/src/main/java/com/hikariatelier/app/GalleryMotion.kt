package com.hikariatelier.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer

internal const val GALLERY_MOTION_DURATION_MS = 420

internal enum class GalleryPart(val start: Float, val end: Float) {
    EDITOR(0.12f, 0.68f), TITLE(0.18f, 0.72f), PRIMARY(0.26f, 0.82f),
    SECONDARY(0.34f, 0.92f), CONTROLS(0.40f, 0.98f)
}

/** One reversible timeline: no delayed jobs or independent animations can outlive navigation. */
internal fun galleryExposure(progress: Float, part: GalleryPart, order: Int = 0): Float {
    val editing = 1f - (if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f)
    val delay = order.coerceIn(0, 4) * 0.025f
    return smoothGalleryPhase(editing, part.start + delay, (part.end + delay).coerceAtMost(1f))
}

internal fun galleryLayerAlpha(progress: Float): Float {
    val editing = 1f - (if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f)
    return 1f - smoothGalleryPhase(editing, 0f, 0.52f)
}

private fun smoothGalleryPhase(value: Float, start: Float, end: Float): Float {
    val fraction = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return fraction * fraction * (3f - 2f * fraction)
}

/** Near-position scale and a small lift preserve the toolbar's measured slots. */
internal fun Modifier.galleryChrome(progress: Float, part: GalleryPart, order: Int = 0): Modifier =
    graphicsLayer {
        val exposure = galleryExposure(progress, part, order)
        alpha = exposure
        scaleX = 0.92f + 0.08f * exposure
        scaleY = scaleX
        translationY = 6f * density * (1f - exposure)
    }

/** Reveal the measured editor from the preview edge without reflowing code or the WebView. */
internal fun Modifier.galleryEditorReveal(progress: Float, landscape: Boolean, editorOnLeft: Boolean): Modifier =
    graphicsLayer {
        val exposure = galleryExposure(progress, GalleryPart.EDITOR)
        alpha = 0.35f + 0.65f * exposure
        transformOrigin = TransformOrigin(if (landscape && editorOnLeft) 1f else 0f, 0f)
        scaleX = if (landscape) 0.98f + 0.02f * exposure else 1f
        scaleY = if (landscape) 1f else 0.98f + 0.02f * exposure
        translationX = if (landscape) (if (editorOnLeft) 1f else -1f) * 18f * density * (1f - exposure) else 0f
        translationY = if (landscape) 0f else -18f * density * (1f - exposure)
    }.drawWithContent {
        val exposure = galleryExposure(progress, GalleryPart.EDITOR)
        if (landscape) {
            clipRect(left = if (editorOnLeft) size.width * (1f - exposure) else 0f,
                right = if (editorOnLeft) size.width else size.width * exposure) { this@drawWithContent.drawContent() }
        } else {
            clipRect(bottom = size.height * exposure) { this@drawWithContent.drawContent() }
        }
    }
