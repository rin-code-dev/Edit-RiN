package com.hikariatelier.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
 * Debounced Auto-Run effect that triggers Hot-Swap execution 280ms after typing pauses
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
        delay(280L)
        if (isCodeStructurallyReadyForAutoRun(editingText)) {
            currentOnRun()
        }
    }
}

internal enum class PipSizePreset(val baseDp: Int) {
    SMALL(128),
    MEDIUM(176),
    LARGE(236)
}

/**
 * Floating Picture-in-Picture (PiP) preview window used during portrait keyboard editing
 * or when PiP overlay mode is active. Supports corner snapping, size cycling, and ghost opacity.
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
    var sizePreset by rememberSaveable { mutableStateOf(PipSizePreset.MEDIUM) }
    var translucent by rememberSaveable { mutableStateOf(false) }
    var minimized by rememberSaveable { mutableStateOf(false) }

    val safeRatio = aspectRatio.coerceIn(0.45f, 2.2f)
    val baseSize = sizePreset.baseDp.dp
    val targetWidth = if (minimized) 108.dp else if (safeRatio >= 1f) baseSize else (baseSize.value * safeRatio).dp
    val targetHeight = if (minimized) 36.dp else if (safeRatio >= 1f) (baseSize.value / safeRatio).dp else baseSize

    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pipWidth"
    )
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight + if (minimized) 0.dp else 28.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pipHeight"
    )
    val alpha by animateFloatAsState(
        targetValue = if (translucent) 0.52f else 0.98f,
        label = "pipAlpha"
    )

    val maxOffsetX = with(density) {
        (containerWidthDp.dp - animatedWidth - 16.dp).toPx().coerceAtLeast(0f)
    }
    val maxOffsetY = with(density) {
        (containerHeightDp.dp - animatedHeight - 16.dp).toPx().coerceAtLeast(0f)
    }

    var offsetX by remember { mutableFloatStateOf(maxOffsetX) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(maxOffsetX, maxOffsetY) {
        offsetX = offsetX.coerceIn(0f, maxOffsetX)
        offsetY = offsetY.coerceIn(0f, maxOffsetY)
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(animatedWidth, animatedHeight)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(16.dp))
            .pointerInput(maxOffsetX, maxOffsetY) {
                detectDragGestures(
                    onDragEnd = {
                        // Snap horizontally to nearest edge
                        offsetX = if (offsetX > maxOffsetX * 0.5f) maxOffsetX else 0f
                    }
                ) { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxOffsetX)
                    offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxOffsetY)
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            // Compact PiP header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(colors.surfaceContainerHighest.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Auto-Run / Run Now pill
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

            if (!minimized) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    previewContent(Modifier.fillMaxSize())
                }
            }
        }
    }
}
