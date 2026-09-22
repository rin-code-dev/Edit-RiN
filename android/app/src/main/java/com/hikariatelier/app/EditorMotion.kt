package com.hikariatelier.app

import android.view.Window
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/** A rate hint only: keep resolution selection and power/thermal policy under Android's control. */
internal fun Window.preferHighRefreshRate() {
    val screen = decorView.display ?: return
    val current = screen.mode
    val rate = screen.supportedModes.asSequence()
        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
        .map { it.refreshRate }.filter { it.isFinite() && it > 0f }.maxOrNull() ?: return
    if (attributes.preferredRefreshRate != rate) {
        attributes = attributes.apply { preferredRefreshRate = rate }
    }
}

/** Height-only remeasurement does not change line positions or the line-number gutter. */
internal fun TextLayoutResult.hasSameEditorLines(other: TextLayoutResult?): Boolean {
    if (other == null) return false
    return layoutInput.text == other.layoutInput.text &&
        layoutInput.style == other.layoutInput.style &&
        layoutInput.density == other.layoutInput.density &&
        layoutInput.layoutDirection == other.layoutInput.layoutDirection &&
        layoutInput.constraints.maxWidth == other.layoutInput.constraints.maxWidth &&
        size.width == other.size.width && lineCount == other.lineCount &&
        (lineCount == 0 || (getLineTop(0) == other.getLineTop(0) &&
            getLineBottom(lineCount - 1) == other.getLineBottom(lineCount - 1)))
}

/** Animate the viewport in measurement, while retaining the WebView's size as it hides. */
@Composable
internal fun PortraitPreviewViewport(
    availableWidth: Dp,
    heightLimit: Dp,
    ratio: Float,
    hidden: Boolean,
    compact: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    val height = portraitPreviewHeight(availableWidth.value, heightLimit.value, ratio, false, compact)
    val fitted = fitPreviewSize(availableWidth.value, height, ratio)
    val targetSize = if (compact) PreviewSize(availableWidth.value.coerceAtLeast(0f), height) else fitted
    var visibleSize by remember { mutableStateOf(targetSize) }
    SideEffect { if (!hidden && visibleSize != targetSize) visibleSize = targetSize }
    val childSize = if (hidden) visibleSize else targetSize
    val animatedHeight = animateFloatAsState(
        targetValue = if (hidden) 0f else height,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "boundedPortraitPreview"
    )
    Layout(
        modifier = Modifier.fillMaxWidth().clipToBounds(),
        content = { Box(Modifier.fillMaxSize()) { content(Modifier.fillMaxSize()) } }
    ) { measurables, constraints ->
        val childWidth = (childSize.width * density).roundToInt().coerceIn(0, constraints.maxWidth)
        val childHeight = (childSize.height * density).roundToInt().coerceAtLeast(0)
        val child = measurables.single().measure(Constraints.fixed(childWidth, childHeight))
        // No animation State is read in composition; siblings only need remeasurement.
        val viewportHeight = constraints.constrainHeight(
            (animatedHeight.value.coerceAtMost(heightLimit.value) * density).roundToInt().coerceAtLeast(0)
        )
        layout(constraints.maxWidth, viewportHeight) {
            child.placeRelative((constraints.maxWidth - child.width) / 2, (viewportHeight - child.height) / 2)
        }
    }
}
