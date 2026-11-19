package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Shared visual metrics for bordered card components ("Units") across the workspace. */
internal val UNIT_CORNER_RADIUS = 18.dp
internal val UNIT_BORDER_WIDTH = 1.dp
internal val UNIT_CONTROL_SHAPE = RoundedCornerShape(10.dp)
internal val UNIT_COMPACT_CONTROL_SHAPE = RoundedCornerShape(8.dp)

@Composable
internal fun unitSwitchColors(colors: ColorScheme = MaterialTheme.colorScheme): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = colors.onPrimary,
        checkedTrackColor = colors.primary,
        checkedBorderColor = colors.primary,
        uncheckedThumbColor = colors.outline,
        uncheckedTrackColor = colors.surface,
        uncheckedBorderColor = colors.outlineVariant,
        disabledCheckedThumbColor = colors.onPrimary.copy(alpha = 0.6f),
        disabledCheckedTrackColor = colors.primary.copy(alpha = 0.38f),
        disabledCheckedBorderColor = colors.primary.copy(alpha = 0.38f),
        disabledUncheckedThumbColor = colors.outline.copy(alpha = 0.38f),
        disabledUncheckedTrackColor = colors.surface,
        disabledUncheckedBorderColor = colors.outlineVariant.copy(alpha = 0.38f)
    )

@Composable
internal fun unitFilterChipColors(colors: ColorScheme = MaterialTheme.colorScheme): SelectableChipColors =
    FilterChipDefaults.filterChipColors(
        containerColor = colors.surface,
        labelColor = colors.onSurfaceVariant,
        iconColor = colors.onSurfaceVariant,
        selectedContainerColor = colors.primary.copy(alpha = 0.12f),
        selectedLabelColor = colors.primary,
        selectedLeadingIconColor = colors.primary,
        selectedTrailingIconColor = colors.primary
    )

@Composable
internal fun unitFilterChipBorder(
    selected: Boolean,
    enabled: Boolean = true,
    colors: ColorScheme = MaterialTheme.colorScheme
): BorderStroke = BorderStroke(
    width = 1.dp,
    color = when {
        !enabled -> colors.outlineVariant.copy(alpha = 0.38f)
        selected -> colors.primary
        else -> colors.outlineVariant
    }
)

@Composable
internal fun unitTonalButtonColors(colors: ColorScheme = MaterialTheme.colorScheme): ButtonColors =
    ButtonDefaults.filledTonalButtonColors(
        containerColor = colors.primary.copy(alpha = 0.12f),
        contentColor = colors.primary
    )

/**
 * Modal surface styled as a floating Unit (all-around border, rounded corners, and outer margins)
 * in both portrait and landscape orientations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnitModal(
    onDismissRequest: () -> Unit,
    dismissEnabled: Boolean = true,
    minimal: Boolean = false,
    maxWidth: Dp = adaptiveSheetMaxWidth(),
    maxHeight: Dp = adaptiveSheetMaxHeight(),
    windowSetup: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val landscape = isLandscapeLayout()

    Dialog(
        onDismissRequest = { if (dismissEnabled) onDismissRequest() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = dismissEnabled,
            dismissOnClickOutside = dismissEnabled
        )
    ) {
        windowSetup()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (dismissEnabled) onDismissRequest()
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(
                    horizontal = if (landscape) 20.dp else 14.dp,
                    vertical = if (landscape) 12.dp else 20.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                shape = RoundedCornerShape(if (minimal) 12.dp else UNIT_CORNER_RADIUS),
                color = colors.surface,
                contentColor = colors.onSurface,
                border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}
