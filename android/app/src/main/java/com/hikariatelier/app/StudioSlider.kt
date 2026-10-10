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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    val thumbColor = if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.38f)
    val railColor = if (enabled) colors.outline else colors.onSurface.copy(alpha = 0.12f)

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
                Modifier.size(width = 13.dp, height = 22.dp)
                    .background(thumbColor)
                    .then(
                        if (enabled && (focused || pressed || dragged)) {
                            Modifier.border(2.dp, colors.onSurface)
                        } else Modifier
                    )
            )
        },
        track = {
            Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                drawLine(
                    color = railColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    )
}
