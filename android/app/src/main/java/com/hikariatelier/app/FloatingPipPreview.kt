package com.hikariatelier.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Checks whether the current code has balanced delimiters and closed literals so Auto-Run
 * never triggers a jarring syntax error mid-typing.
 */
internal fun isCodeStructurallyReadyForAutoRun(source: String): Boolean {
    if (source.isBlank()) return false
    var braces = 0
    var parens = 0
    var brackets = 0
    var i = 0
    val len = source.length
    while (i < len) {
        val c = source[i]
        val next = source.getOrNull(i + 1)
        when {
            c == '/' && next == '/' -> {
                i = source.indexOf('\n', i + 2).let { if (it < 0) len else it + 1 }
            }
            c == '/' && next == '*' -> {
                val close = source.indexOf("*/", i + 2)
                if (close < 0) return false
                i = close + 2
            }
            c == '\'' || c == '"' || c == '`' -> {
                val quote = c
                i++
                var closed = false
                while (i < len) {
                    if (source[i] == '\\') i += 2
                    else if (source[i] == quote) {
                        i++
                        closed = true
                        break
                    } else if (quote != '`' && (source[i] == '\n' || source[i] == '\r')) {
                        return false
                    } else i++
                }
                if (!closed) return false
            }
            c == '{' -> { braces++; i++ }
            c == '}' -> { braces--; if (braces < 0) return false; i++ }
            c == '(' -> { parens++; i++ }
            c == ')' -> { parens--; if (parens < 0) return false; i++ }
            c == '[' -> { brackets++; i++ }
            c == ']' -> { brackets--; if (brackets < 0) return false; i++ }
            else -> i++
        }
    }
    return braces == 0 && parens == 0 && brackets == 0
}

/**
 * Debounced Auto-Run effect that triggers execution 320ms after typing pauses
 * when structural brackets/quotes are balanced.
 */
@Composable
internal fun AutoRunHotSwapEffect(
    enabled: Boolean,
    editingText: String,
    previewHasChanges: Boolean,
    readOnly: Boolean,
    onRunChanges: () -> Unit
) {
    val currentOnRun by rememberUpdatedState(onRunChanges)
    LaunchedEffect(enabled, editingText, previewHasChanges, readOnly) {
        if (!enabled || readOnly || !previewHasChanges) return@LaunchedEffect
        delay(320L)
        if (isCodeStructurallyReadyForAutoRun(editingText)) {
            currentOnRun()
        }
    }
}

internal enum class PipSizePreset(val baseDp: Int) {
    SMALL(124),
    MEDIUM(168),
    LARGE(224)
}

internal enum class PipCorner {
    TOP_END,
    TOP_START,
    BOTTOM_END,
    BOTTOM_START
}

/**
 * Stabilized Floating Picture-in-Picture (PiP) preview window (v3.2):
 * - Uses 4-corner anchor snapping ([PipCorner]) instead of raw pixel coordinates so keyboard IME
 *   animations and size preset changes never cause position drift or jumping.
 * - Separates Drag Mode (default: whole PiP card is smoothly draggable without WebView stealing touches)
 *   and Canvas Touch Mode (`👆` button: allows interacting with p5 sketch inside PiP without losing editor focus).
 */
@Composable
internal fun FloatingPipPreviewHost(
    aspectRatio: Float,
    containerWidthDp: Float,
    containerHeightDp: Float,
    autoRunEnabled: Boolean,
    onToggleAutoRun: () -> Unit,
    previewHasChanges: Boolean,
    onRunNow: () -> Unit,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier,
    previewContent: @Composable (Modifier) -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    var sizePreset by rememberSaveable { mutableStateOf(PipSizePreset.MEDIUM) }
    var translucent by rememberSaveable { mutableStateOf(false) }
    var minimized by rememberSaveable { mutableStateOf(false) }
    var interactiveCanvas by rememberSaveable { mutableStateOf(false) }
    var snapCorner by rememberSaveable { mutableStateOf(PipCorner.TOP_END) }

    val safeRatio = aspectRatio.coerceIn(0.45f, 2.2f)
    val baseSize = sizePreset.baseDp.dp
    val targetWidth = if (minimized) 116.dp else if (safeRatio >= 1f) baseSize else (baseSize.value * safeRatio).dp
    val targetHeight = if (minimized) 32.dp else (if (safeRatio >= 1f) (baseSize.value / safeRatio).dp else baseSize) + 28.dp

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pipWidth"
    )
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pipHeight"
    )
    val alpha by animateFloatAsState(
        targetValue = if (translucent) 0.50f else 0.98f,
        label = "pipAlpha"
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val marginPx = with(density) { 8.dp.toPx() }
        val cardWidthPx = with(density) { animatedWidth.toPx() }
        val cardHeightPx = with(density) { animatedHeight.toPx() }
        val maxX = (constraints.maxWidth.toFloat() - cardWidthPx - marginPx).coerceAtLeast(marginPx)
        val maxY = (constraints.maxHeight.toFloat() - cardHeightPx - marginPx).coerceAtLeast(marginPx)

        val anchorX = when (snapCorner) {
            PipCorner.TOP_START, PipCorner.BOTTOM_START -> marginPx
            PipCorner.TOP_END, PipCorner.BOTTOM_END -> maxX
        }
        val anchorY = when (snapCorner) {
            PipCorner.TOP_START, PipCorner.TOP_END -> marginPx
            PipCorner.BOTTOM_START, PipCorner.BOTTOM_END -> maxY
        }

        var isDragging by remember { mutableStateOf(false) }
        var dragDx by remember { mutableFloatStateOf(0f) }
        var dragDy by remember { mutableFloatStateOf(0f) }

        val targetX = (anchorX + dragDx).coerceIn(marginPx, maxX)
        val targetY = (anchorY + dragDy).coerceIn(marginPx, maxY)

        val animatedX by animateFloatAsState(
            targetValue = targetX,
            animationSpec = if (isDragging) spring(stiffness = Spring.StiffnessHigh)
            else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "pipOffsetX"
        )
        val animatedY by animateFloatAsState(
            targetValue = targetY,
            animationSpec = if (isDragging) spring(stiffness = Spring.StiffnessHigh)
            else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            label = "pipOffsetY"
        )

        val currentMaxX by rememberUpdatedState(maxX)
        val currentMaxY by rememberUpdatedState(maxY)
        val currentMarginPx by rememberUpdatedState(marginPx)
        val currentAnchorX by rememberUpdatedState(anchorX)
        val currentAnchorY by rememberUpdatedState(anchorY)

        val dragGestureModifier = Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    isDragging = true
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDx += dragAmount.x
                    dragDy += dragAmount.y
                },
                onDragEnd = {
                    val finalX = (currentAnchorX + dragDx).coerceIn(currentMarginPx, currentMaxX)
                    val finalY = (currentAnchorY + dragDy).coerceIn(currentMarginPx, currentMaxY)
                    val preferRight = finalX >= (currentMarginPx + currentMaxX) * 0.5f
                    val preferBottom = finalY >= (currentMarginPx + currentMaxY) * 0.5f
                    val nextCorner = when {
                        preferBottom && preferRight -> PipCorner.BOTTOM_END
                        preferBottom && !preferRight -> PipCorner.BOTTOM_START
                        !preferBottom && preferRight -> PipCorner.TOP_END
                        else -> PipCorner.TOP_START
                    }
                    if (nextCorner != snapCorner) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    snapCorner = nextCorner
                    dragDx = 0f
                    dragDy = 0f
                    isDragging = false
                },
                onDragCancel = {
                    dragDx = 0f
                    dragDy = 0f
                    isDragging = false
                }
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (if (isDragging) targetX else animatedX).roundToInt(),
                        y = (if (isDragging) targetY else animatedY).roundToInt()
                    )
                }
                .size(animatedWidth, animatedHeight)
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp), clip = false)
                .graphicsLayer { this.alpha = alpha }
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceContainerHigh)
                .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
        ) {
            Column(Modifier.fillMaxSize()) {
                // Draggable Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(colors.surfaceContainerHighest.copy(alpha = 0.95f))
                        .then(dragGestureModifier)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        onClick = {
                            if (previewHasChanges && !autoRunEnabled) onRunNow()
                            else onToggleAutoRun()
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = when {
                            autoRunEnabled -> colors.primaryContainer
                            previewHasChanges -> colors.tertiaryContainer
                            else -> Color.Transparent
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (autoRunEnabled) "⚡LIVE" else if (previewHasChanges) "▶ RUN" else "PiP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    autoRunEnabled -> colors.onPrimaryContainer
                                    previewHasChanges -> colors.onTertiaryContainer
                                    else -> colors.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!minimized) {
                            // Touch Pass-Through vs Drag Shield Toggle
                            Surface(
                                onClick = { interactiveCanvas = !interactiveCanvas },
                                shape = CircleShape,
                                color = if (interactiveCanvas) colors.primaryContainer else Color.Transparent,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (interactiveCanvas) "👆" else "✥",
                                        fontSize = 10.sp,
                                        color = if (interactiveCanvas) colors.onPrimaryContainer else colors.onSurfaceVariant
                                    )
                                }
                            }
                            // Opacity toggle
                            Surface(
                                onClick = { translucent = !translucent },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (translucent) "◐" else "●",
                                        fontSize = 11.sp,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            // Size cycle button
                            Surface(
                                onClick = {
                                    sizePreset = when (sizePreset) {
                                        PipSizePreset.SMALL -> PipSizePreset.MEDIUM
                                        PipSizePreset.MEDIUM -> PipSizePreset.LARGE
                                        PipSizePreset.LARGE -> PipSizePreset.SMALL
                                    }
                                },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = when (sizePreset) {
                                            PipSizePreset.SMALL -> "S"
                                            PipSizePreset.MEDIUM -> "M"
                                            PipSizePreset.LARGE -> "L"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        // Minimize / Restore toggle
                        Surface(
                            onClick = { minimized = !minimized },
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (minimized) "▢" else "−",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Keep the WebView mounted even when minimized (clipped to 0dp height)
                    // so minimizing/restoring PiP never detaches or pauses the WebView!
                    previewContent(Modifier.fillMaxSize())

                    // When not in interactive canvas touch mode, place a transparent drag shield
                    // over the canvas so dragging anywhere on the PiP window moves it smoothly.
                    if (!interactiveCanvas && !minimized) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .then(dragGestureModifier)
                        )
                    }
                }
            }
        }
    }
}
