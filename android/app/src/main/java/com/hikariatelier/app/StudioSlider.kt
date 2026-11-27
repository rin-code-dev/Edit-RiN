package com.hikariatelier.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** The website's thin rail and rectangular fader, with Material's input semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    steps: Int = 0,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    defaultValue: Float? = null
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val thumbColor = if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.38f)
    val railColor = if (enabled) colors.outlineVariant else colors.onSurface.copy(alpha = 0.12f)
    val activeRailColor = if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.28f)
    val tickColor = if (enabled) colors.outline else colors.onSurface.copy(alpha = 0.24f)
    val thumbShape = RoundedCornerShape(2.dp)
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f && span.isFinite()) {
        ((value - valueRange.start) / span).coerceIn(0f, 1f)
    } else 0f
    val defaultFraction = defaultValue
        ?.takeIf { it.isFinite() && span > 0f && span.isFinite() }
        ?.let { ((it - valueRange.start) / span).coerceIn(0f, 1f) }

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        steps = steps,
        valueRange = valueRange,
        interactionSource = interaction,
        thumb = {
            Box(
                modifier = Modifier
                    .size(width = 13.dp, height = 22.dp)
                    .clip(thumbShape)
                    .background(thumbColor)
                    .then(
                        if (enabled && (focused || pressed || dragged)) {
                            Modifier.border(2.dp, colors.onSurface, thumbShape)
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(10.dp)
                        .background(colors.onPrimary.copy(alpha = if (enabled) 0.7f else 0.35f))
                )
            }
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                val centerY = size.height / 2f
                drawLine(
                    color = railColor,
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 1.dp.toPx()
                )
                if (fraction > 0f) {
                    drawLine(
                        color = activeRailColor,
                        start = Offset(0f, centerY),
                        end = Offset(size.width * fraction, centerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                if (defaultFraction != null) {
                    val tickX = size.width * defaultFraction
                    val halfTick = 4.5.dp.toPx()
                    drawLine(
                        color = tickColor,
                        start = Offset(tickX, centerY - halfTick),
                        end = Offset(tickX, centerY + halfTick),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    )
}
