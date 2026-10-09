package com.hikariatelier.app

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt


/** Both rectangles use boundsInRoot; easing and animation ownership stay with the caller. */
internal fun interpolateGalleryBounds(start: Rect, end: Rect, progress: Float): Rect {
    val usableStart = start.usableGalleryBounds()
    val usableEnd = end.usableGalleryBounds()
    if (!usableStart) return if (usableEnd) end else Rect.Zero
    if (!usableEnd) return start

    val fraction = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    if (fraction == 0f) return start
    if (fraction == 1f) return end
    return lerp(start, end, fraction).takeIf { it.usableGalleryBounds() } ?: start
}

internal fun Rect.usableGalleryBounds(): Boolean =
    isFinite && width.isFinite() && height.isFinite() && width > 0f && height > 0f

/** Decorative overlay only; the caller retains the WebView and owns input blocking and the bitmap. */
@Composable
internal fun WorkGalleryPreview(
    bitmap: Bitmap?,
    start: Rect?,
    end: Rect?,
    progress: Float,
    modifier: Modifier = Modifier,
    visible: Boolean = progress > 0f && progress < 1f,
    opacity: Float = 1f
) {
    var localOrigin by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val outline = MaterialTheme.colorScheme.onSurfaceVariant

    // Keep this container measured at both endpoints so root-relative bounds
    // always use the same body origin when the image enters or leaves composition.
    Box(modifier.onGloballyPositioned { localOrigin = it.boundsInRoot().topLeft }
        .clearAndSetSemantics { hideFromAccessibility() }) {
        val origin = localOrigin ?: return@Box
        if (bitmap == null || bitmap.isRecycled || start == null || end == null ||
            !start.usableGalleryBounds() || !end.usableGalleryBounds() ||
            !progress.isFinite() || !visible) return@Box

        val fraction = FastOutSlowInEasing.transform(progress.coerceIn(0f, 1f))
        val bounds = interpolateGalleryBounds(start, end, fraction)
        val shape = RoundedCornerShape((18f + (6f - 18f) * fraction).dp)
        Image(
            bitmap = remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset { IntOffset((bounds.left - origin.x).roundToInt(), (bounds.top - origin.y).roundToInt()) }
                // The IME can temporarily make the body smaller than the retained preview.
                // Measure the image at its interpolated size and keep overflow at its top-left.
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .graphicsLayer { alpha = opacity.coerceIn(0f, 1f) }
                .clip(shape)
                .background(Color.Black)
                .border(1.dp, outline, shape)
        )
    }
}


/** Keep foreground controls above the pinned image during its final handoff. */
@Composable
internal fun WorkGalleryPreviewChrome(
    bounds: Rect?, modifier: Modifier = Modifier, visible: Boolean,
    content: @Composable BoxWithConstraintsScope.() -> Unit
) {
    var localOrigin by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    Box(modifier.onGloballyPositioned { localOrigin = it.boundsInRoot().topLeft }
        .clearAndSetSemantics { hideFromAccessibility() }) {
        val origin = localOrigin ?: return@Box
        if (!visible || bounds?.usableGalleryBounds() != true) return@Box
        BoxWithConstraints(Modifier
            .offset { IntOffset((bounds.left - origin.x).roundToInt(), (bounds.top - origin.y).roundToInt()) }
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .requiredSize(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            .clip(RoundedCornerShape(18.dp)), content = content)
    }
}
