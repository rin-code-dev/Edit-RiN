package com.hikariatelier.app

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

internal enum class PipSizePreset(val baseDp: Int, val shortLabel: String) {
    SMALL(138, "S"),
    MEDIUM(178, "M"),
    LARGE(228, "L")
}

internal enum class PipCorner {
    TOP_END,
    TOP_START,
    BOTTOM_END,
    BOTTOM_START
}

/**
 * Flat rounded-rectangle icon button designed specifically for Edit:RiN's PiP & Editor chrome:
 * - Clips to [RoundedCornerShape] BEFORE [clickable] so the touch ripple strictly matches the
 *   button's flat rectangular geometry instead of bleeding out as a circle.
 * - Uses a 1dp border + subtle translucent tint when active rather than heavy opaque blobs.
 */
@Composable
private fun PipFlatIconButton(
    @DrawableRes iconRes: Int,
    description: String,
    colors: ColorScheme,
    active: Boolean = false,
    accentColor: Color = colors.primary,
    badgeText: String? = null,
    height: Dp = 24.dp,
    minWidth: Dp = 26.dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(7.dp)
    val bgColor = if (active) accentColor.copy(alpha = 0.15f) else Color.Transparent
    val borderColor = if (active) accentColor.copy(alpha = 0.8f) else Color.Transparent
    val tintColor = if (active) accentColor else colors.onSurfaceVariant

    Box(
        modifier = Modifier
            .height(height)
            .widthIn(min = minWidth)
            .clip(shape)
            .background(bgColor)
            .then(if (active) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = if (badgeText != null) 5.dp else 4.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(14.dp)
            )
            if (badgeText != null) {
                Text(
                    text = badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tintColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Stabilized Floating Picture-in-Picture (PiP) preview window (v3.3):
 * - Opens in SMALL (`S`) size by default.
 * - Guarantees a minimum card width of `138.dp` even on tall portrait ratios (`9:16` / `device`)
 *   so the 4 header buttons (Run/Auto, Opacity, Size S/M/L, Minimize) are NEVER clipped or hidden.
 * - Uses 4-corner anchor snapping ([PipCorner]) + full-window drag support.
 * - Uses crisp flat vector icons and shape-clipped touch ripples matching Edit:RiN's flat aesthetic.
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
    sizePreset: PipSizePreset = PipSizePreset.SMALL,
    onSizePresetChange: (PipSizePreset) -> Unit = {},
    translucent: Boolean = false,
    onTranslucentChange: (Boolean) -> Unit = {},
    minimized: Boolean = false,
    onMinimizedChange: (Boolean) -> Unit = {},
    snapCorner: PipCorner = PipCorner.TOP_END,
    onSnapCornerChange: (PipCorner) -> Unit = {},
    previewContent: @Composable (Modifier) -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    fun uiText(source: String, vararg args: Any?): String = textTranslator(source, args)

    val safeRatio = aspectRatio.coerceIn(0.45f, 2.2f)
    val baseSize = sizePreset.baseDp.dp
    val headerHeight = 32.dp
    val minHeaderWidth = 138.dp

    val rawCanvasWidth = if (safeRatio >= 1f) baseSize else (baseSize.value * safeRatio).dp
    val rawCanvasHeight = if (safeRatio >= 1f) (baseSize.value / safeRatio).dp else baseSize

    // Guarantee minimum width of 138.dp so the 4 buttons never clip in S size or 9:16 aspect ratio
    val targetWidth = if (minimized) 138.dp else maxOf(minHeaderWidth, rawCanvasWidth)
    val targetHeight = if (minimized) headerHeight else rawCanvasHeight + headerHeight

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
        targetValue = if (translucent) 0.48f else 0.98f,
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
                    onSnapCornerChange(nextCorner)
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

        val outerShape = RoundedCornerShape(12.dp)
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (if (isDragging) targetX else animatedX).roundToInt(),
                        y = (if (isDragging) targetY else animatedY).roundToInt()
                    )
                }
                .size(animatedWidth, animatedHeight)
                .shadow(elevation = 4.dp, shape = outerShape, clip = false)
                .graphicsLayer { this.alpha = alpha }
                .clip(outerShape)
                .background(colors.surface)
                .border(1.dp, colors.outlineVariant, outerShape)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Flat Header Bar with 4 Unified Vector Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .background(colors.surface)
                        .then(dragGestureModifier)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Button 1: Run Now (when pending changes & auto-run off) or Toggle Auto-Run
                    val showRunPending = previewHasChanges && !autoRunEnabled
                    PipFlatIconButton(
                        iconRes = if (showRunPending) R.drawable.ic_play else R.drawable.ic_auto_run,
                        description = uiText(
                            if (showRunPending) "変更を実行" else "自動実行を切り替え"
                        ),
                        colors = colors,
                        active = autoRunEnabled || showRunPending,
                        accentColor = if (showRunPending) colors.tertiary else colors.primary,
                        badgeText = when {
                            minimized -> "PiP"
                            showRunPending -> "RUN"
                            autoRunEnabled -> "AUTO"
                            else -> null
                        },
                        onClick = {
                            if (showRunPending) onRunNow() else onToggleAutoRun()
                        }
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (!minimized) {
                            // Button 2: Opacity / Ghost Toggle
                            PipFlatIconButton(
                                iconRes = R.drawable.ic_pip_opacity,
                                description = uiText("半透明表示を切り替え"),
                                colors = colors,
                                active = translucent,
                                onClick = { onTranslucentChange(!translucent) }
                            )

                            // Button 3: Size Cycle (S -> M -> L)
                            PipFlatIconButton(
                                iconRes = R.drawable.ic_pip_resize,
                                description = uiText("小窓サイズを変更"),
                                colors = colors,
                                active = false,
                                badgeText = sizePreset.shortLabel,
                                onClick = {
                                    val nextPreset = when (sizePreset) {
                                        PipSizePreset.SMALL -> PipSizePreset.MEDIUM
                                        PipSizePreset.MEDIUM -> PipSizePreset.LARGE
                                        PipSizePreset.LARGE -> PipSizePreset.SMALL
                                    }
                                    onSizePresetChange(nextPreset)
                                }
                            )
                        }

                        // Button 4: Minimize / Expand
                        PipFlatIconButton(
                            iconRes = if (minimized) R.drawable.ic_pip_expand else R.drawable.ic_pip_minimize,
                            description = uiText(if (minimized) "小窓を展開" else "小窓を最小化"),
                            colors = colors,
                            active = minimized,
                            onClick = { onMinimizedChange(!minimized) }
                        )
                    }
                }

                if (!minimized) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = colors.outlineVariant.copy(alpha = 0.6f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Keep the WebView mounted even when minimized (clipped to 0dp height)
                    // so minimizing/restoring PiP never detaches or pauses the WebView
                    previewContent(
                        Modifier.size(rawCanvasWidth, rawCanvasHeight)
                    )

                    // Transparent drag shield over the canvas so dragging anywhere on the PiP window moves it smoothly
                    if (!minimized) {
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
