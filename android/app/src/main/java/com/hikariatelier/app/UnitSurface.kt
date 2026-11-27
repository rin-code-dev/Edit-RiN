package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Shared visual metrics for bordered card components ("Units") across the workspace. */
internal val UNIT_CORNER_RADIUS = 18.dp
internal val UNIT_CARD_CORNER_RADIUS = 14.dp
internal val UNIT_CONTROL_CORNER_RADIUS = 10.dp
internal val UNIT_COMPACT_CONTROL_CORNER_RADIUS = 8.dp

internal val UNIT_BORDER_WIDTH = 1.dp
internal val UNIT_ACTIVE_BORDER_WIDTH = 1.5.dp
internal const val UNIT_HAIRLINE_ALPHA = 0.45f
internal const val UNIT_FRAME_ALPHA = 0.85f

internal val UNIT_SHAPE = RoundedCornerShape(UNIT_CORNER_RADIUS)
internal val UNIT_CARD_SHAPE = RoundedCornerShape(UNIT_CARD_CORNER_RADIUS)
internal val UNIT_CONTROL_SHAPE = RoundedCornerShape(UNIT_CONTROL_CORNER_RADIUS)
internal val UNIT_COMPACT_CONTROL_SHAPE = RoundedCornerShape(UNIT_COMPACT_CONTROL_CORNER_RADIUS)

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
                shape = if (minimal) RoundedCornerShape(12.dp) else UNIT_SHAPE,
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

/** Confirmation and compact prompt dialog styled as a floating Unit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnitAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    showActions: Boolean = true,
    properties: DialogProperties = DialogProperties()
) {
    val colors = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Surface(
            modifier = modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .fillMaxWidth(),
            shape = UNIT_SHAPE,
            color = colors.surface,
            contentColor = colors.onSurface,
            border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(Modifier.padding(24.dp)) {
                if (icon != null || title != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (text != null) 14.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (icon != null) {
                            CompositionLocalProvider(LocalContentColor provides colors.primary) {
                                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                    icon()
                                }
                            }
                        }
                        if (title != null) {
                            Box(Modifier.weight(1f)) {
                                CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
                                    ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                                        title()
                                    }
                                }
                            }
                        }
                    }
                }
                if (text != null) {
                    Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                        CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                text()
                            }
                        }
                    }
                }
                if (showActions) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** DropdownMenu styled as a bordered floating Unit. */
@Composable
internal fun UnitDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        scrollState = scrollState,
        shape = UNIT_CARD_SHAPE,
        containerColor = colors.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
        content = content
    )
}

/**
 * Single-line text that scales its font size down smoothly to [minFontSize] before ellipsizing.
 */
@Composable
internal fun AutoFitSingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    minFontSize: TextUnit = 10.sp
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val resolvedStyle = remember(style, fontWeight) {
        if (fontWeight != null) style.copy(fontWeight = fontWeight) else style
    }
    val baseSp = if (resolvedStyle.fontSize.isSp) resolvedStyle.fontSize.value else 14f
    val minSp = minOf(baseSp, if (minFontSize.isSp) minFontSize.value else 10f)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart
    ) {
        val maxWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val fittedSizeSp = remember(
            text,
            resolvedStyle,
            maxWidthPx,
            baseSp,
            minSp,
            density,
            fontFamilyResolver,
            textMeasurer
        ) {
            if (maxWidthPx == Int.MAX_VALUE || maxWidthPx <= 0 || baseSp <= minSp) {
                baseSp
            } else {
                var candidateSp = baseSp
                while (candidateSp > minSp) {
                    val measured = textMeasurer.measure(
                        text = text,
                        style = resolvedStyle.copy(fontSize = candidateSp.sp),
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                        maxLines = 1,
                        constraints = Constraints()
                    )
                    if (measured.size.width <= maxWidthPx) break
                    candidateSp = maxOf(minSp, candidateSp - 0.5f)
                }
                candidateSp
            }
        }
        Text(
            text = text,
            color = color,
            style = resolvedStyle.copy(fontSize = fittedSizeSp.sp),
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
