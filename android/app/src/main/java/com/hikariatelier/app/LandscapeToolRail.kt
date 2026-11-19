package com.hikariatelier.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration

/* ------------------------------------------------------------------------- */
/* Shared landscape metrics                                                   */
/* ------------------------------------------------------------------------- */

/** Phones in landscape: height is the scarce dimension, so chrome moves to the sides. */
@Composable
internal fun isLandscapeLayout(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** Bottom sheets may use more of a landscape screen's width and height. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun adaptiveSheetMaxWidth(): Dp {
    val configuration = LocalConfiguration.current
    return if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        (configuration.screenWidthDp - 48).coerceIn(360, 880).dp
    else BottomSheetDefaults.SheetMaxWidth
}

@Composable
internal fun adaptiveSheetMaxHeight(portraitFraction: Float = 0.85f): Dp {
    val configuration = LocalConfiguration.current
    val fraction = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 0.94f else portraitFraction
    return (configuration.screenHeightDp * fraction).dp
}

/* ------------------------------------------------------------------------- */
/* Vertical tool rail used beside the landscape editor                        */
/* ------------------------------------------------------------------------- */

internal val LANDSCAPE_RAIL_WIDTH = 48.dp

/** Size every rail button for the available height, so the rail scrolls only on very short screens. */
internal val LocalRailButtonSize = compositionLocalOf { 38.dp }

internal fun railButtonSize(availableHeight: Dp, itemCount: Int): Dp {
    if (itemCount <= 0) return 38.dp
    val fixed = 12.dp + 4.dp * (itemCount - 1).coerceAtLeast(0)
    return ((availableHeight - fixed) / itemCount).coerceIn(32.dp, 38.dp)
}

@Composable
internal fun LandscapeToolRail(
    itemCount: Int,
    modifier: Modifier = Modifier,
    top: @Composable ColumnScope.() -> Unit,
    bottom: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.width(LANDSCAPE_RAIL_WIDTH).fillMaxHeight(),
        shape = RoundedCornerShape(UNIT_CORNER_RADIUS),
        color = colors.surface,
        border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val buttonSize = railButtonSize(maxHeight, itemCount)
            val neededHeight = buttonSize * itemCount + 4.dp * (itemCount - 1).coerceAtLeast(0) + 12.dp
            val fitsWithoutScroll = neededHeight <= maxHeight
            CompositionLocalProvider(LocalRailButtonSize provides buttonSize) {
                Column(
                    modifier = if (fitsWithoutScroll) {
                        Modifier
                            .fillMaxWidth()
                            .height(maxHeight)
                            .padding(vertical = 4.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (fitsWithoutScroll) {
                        Arrangement.SpaceEvenly
                    } else {
                        Arrangement.spacedBy(4.dp)
                    }
                ) {
                    top()
                    bottom()
                }
            }
        }
    }
}

/** Icon button for the rail: long press shows the label, a badge or dot reports state. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun RailButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    tint: Color? = null,
    badge: String? = null,
    dotColor: Color? = null,
    outlineColor: Color? = null,
    state: String? = null,
    onLongClick: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val size = LocalRailButtonSize.current
    val contentColor = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        tint != null -> tint
        active -> colors.primary
        else -> colors.onSurface
    }
    val effectiveOutline = outlineColor ?: if (active) colors.primary.copy(alpha = 0.7f) else null

    @Composable
    fun ButtonSurface(interactiveModifier: Modifier) {
        Box(
            modifier = modifier
                .size(size)
                .clip(UNIT_CONTROL_SHAPE)
                .background(if (active) colors.primary.copy(alpha = 0.14f) else Color.Transparent)
                .then(
                    if (effectiveOutline != null) Modifier.border1(effectiveOutline) else Modifier
                )
                .then(interactiveModifier)
                .semantics {
                    contentDescription = label
                    if (state != null) stateDescription = state
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(iconRes), null, Modifier.size(if (size >= 36.dp) 19.dp else 17.dp), tint = contentColor)
            if (badge != null) {
                Text(
                    badge,
                    Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 1.dp),
                    fontSize = 8.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold,
                    color = tint ?: colors.primary, maxLines = 1
                )
            }
            if (dotColor != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp)
                    .size(7.dp).clip(CircleShape).background(dotColor))
            }
        }
    }

    if (onLongClick != null) {
        ButtonSurface(
            Modifier.combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
        )
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState()
        ) {
            ButtonSurface(
                Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick
                )
            )
        }
    }
}

private fun Modifier.border1(color: Color): Modifier =
    this.border(1.dp, color, UNIT_CONTROL_SHAPE)

/* ------------------------------------------------------------------------- */
/* Work title merged into the landscape document bar                          */
/* ------------------------------------------------------------------------- */

/** Compact work name + save state; the landscape replacement for the work selector chip. */
@Composable
internal fun CompactWorkTitleButton(
    title: String?,
    hasUnsavedChanges: Boolean,
    saveLabel: String?,
    saveFailed: Boolean,
    onClick: () -> Unit,
    onRetry: (() -> Unit)?,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    fun uiText(source: String) = textTranslator(source, emptyArray())
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onClick,
            shape = UNIT_CONTROL_SHAPE,
            color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant),
            modifier = Modifier
                .heightIn(min = 32.dp)
                .widthIn(max = 180.dp)
                .semantics {
                    contentDescription = uiText("作品一覧を開く") + ": " + (title ?: uiText("作品を選択"))
                    if (saveLabel != null) stateDescription = saveLabel
                }
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(painterResource(R.drawable.ic_folder_code), null, Modifier.size(14.dp),
                    tint = colors.onSurfaceVariant)
                Text(
                    title ?: uiText("作品を選択"),
                    Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                when {
                    saveFailed -> Box(Modifier.size(7.dp).clip(CircleShape).background(colors.error))
                    hasUnsavedChanges -> Box(Modifier.size(6.dp).clip(CircleShape).background(colors.tertiary))
                }
            }
        }
        if (onRetry != null) {
            TextButton(
                onClick = onRetry,
                shape = UNIT_COMPACT_CONTROL_SHAPE,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(uiText("再試行"), style = MaterialTheme.typography.labelSmall, color = colors.error)
            }
        }
        Text("/", Modifier.padding(start = 6.dp), color = colors.outline, style = MaterialTheme.typography.labelMedium)
    }
}
