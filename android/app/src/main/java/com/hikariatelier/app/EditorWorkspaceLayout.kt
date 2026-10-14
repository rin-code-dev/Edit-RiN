package com.hikariatelier.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.hikariatelier.app.ui.theme.AppThemeMode

/** Layout inputs; persistence and editing commands stay in their owning ViewModels. */
internal data class EditorWorkspaceState(
    val isLandscape: Boolean,
    val editorFocused: Boolean,
    val keyboardVisible: Boolean,
    val showResizeHandles: Boolean,
    val showConsole: Boolean,
    val showEditorAccessoryBar: Boolean,
    val compactPreview: Boolean = false,
    val hideEditingPreview: Boolean = true,
    val themeMode: AppThemeMode,
    val workPreviewRatio: Float,
    val animatedLandscapePreviewFraction: Float,
    val landscapeEditorOnLeft: Boolean,
    val previewActionsExpanded: Boolean,
    val landscapePreviewFraction: Float,
    val showLandscapeSplitLabel: Boolean,
    val activeWorkId: String,
    val previewRatioSelection: String,
    val portraitRatioDragging: Boolean,
    val codeFontFamily: FontFamily,
    val hasVisibleCompletions: Boolean,
    val showParameterPanel: Boolean = false,
    val isSample: Boolean = false,
    val galleryVisible: Boolean = false,
    val galleryProgress: Float = 0f,
    val pipModeWhenEditing: Boolean = true,
    val autoRunEnabled: Boolean = false,
    val previewHasChanges: Boolean = false
)

internal data class EditorWorkspaceActions(
    val onSplitChanged: (Float) -> Unit,
    val onSplitLabelChanged: (Boolean) -> Unit,
    val onPortraitDraggingChanged: (Boolean) -> Unit,
    val onDraftPreviewRatio: (String) -> Unit,
    val onCommitPreviewRatio: () -> Unit,
    val onSavePreviewRatio: (String) -> Unit,
    val onToggleAutoRun: () -> Unit = {},
    val onRunNow: () -> Unit = {}
)

internal data class EditorWorkspaceSlots(
    val workBar: @Composable () -> Unit,
    val landscapeBar: @Composable () -> Unit,
    val controlBar: @Composable () -> Unit,
    val console: @Composable (Modifier) -> Unit,
    val previewActions: @Composable (Modifier) -> Unit,
    val editor: @Composable (Modifier) -> Unit,
    val completions: @Composable (Modifier) -> Unit,
    val accessory: @Composable (Modifier) -> Unit,
    val preview: @Composable (Modifier) -> Unit,
    val pipPreview: (@Composable (Modifier) -> Unit)? = null,
    val parameterPanel: @Composable (Modifier) -> Unit = {},
    val gallery: @Composable (Modifier) -> Unit = {},
    val galleryPreview: @Composable (Modifier) -> Unit = {},
    val previewChrome: @Composable (Modifier) -> Unit = {},
    val landscapeGalleryBar: @Composable () -> Unit = {},
)

@Composable
internal fun EditorWorkspaceLayout(
    state: EditorWorkspaceState,
    actions: EditorWorkspaceActions,
    slots: EditorWorkspaceSlots,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    var portraitBodySize by remember(state.isLandscape, density) { mutableStateOf(IntSize.Zero) }
    var landscapeWorkspaceSize by remember(state.isLandscape, density) { mutableStateOf(IntSize.Zero) }
    val currentLandscapePreviewFraction = rememberUpdatedState(state.landscapePreviewFraction)
    val currentPreviewRatio = rememberUpdatedState(state.previewRatioSelection)
    val currentRatioChange = rememberUpdatedState(actions.onSavePreviewRatio)
    fun text(source: String, vararg args: Any?) = textTranslator(source, args)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
    ) {
        val containerMaxWidthDp = maxWidth.value
        val containerMaxHeightDp = maxHeight.value
        val hasVisibleCompletions = state.hasVisibleCompletions
        val chromeHeight = (if (state.editorFocused) 0.dp else 108.dp) +
            (if (state.showResizeHandles && !state.editorFocused) 28.dp else 0.dp) +
            (if (state.showConsole) 176.dp else 0.dp) +
            (if (state.editorFocused && state.showEditorAccessoryBar) 60.dp else 8.dp) +
            (if (hasVisibleCompletions) 48.dp else 0.dp)
        val availableForEditing = (maxHeight - chromeHeight).coerceAtLeast(0.dp)

        // In portrait mode, editing ("そのまま" & "縮小" removed -> "隠す" only by default)
        // always hides the top preview viewport and shows the floating PiP window when enabled.
        val isPortraitEditing = !state.isLandscape &&
            (state.editorFocused || state.keyboardVisible) &&
            !state.galleryVisible

        val useFloatingPipInPortrait = isPortraitEditing && state.pipModeWhenEditing

        val previewHeightLimit = availableForEditing * 0.65f
        var portraitPreviewHeightLimit by remember(state.isLandscape, density) {
            mutableStateOf(previewHeightLimit)
        }
        SideEffect {
            if (!state.isLandscape && !state.galleryVisible && !isPortraitEditing) {
                portraitPreviewHeightLimit = previewHeightLimit
            }
        }
        val availablePreviewWidth = (maxWidth - 24.dp).value

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.background,
                            if (state.themeMode == AppThemeMode.DARK ||
                                state.themeMode == AppThemeMode.SUMI ||
                                state.themeMode == AppThemeMode.CUSTOM
                            ) colors.background else colors.surface
                        )
                    )
                )
        ) {
            AnimatedVisibility(
                visible = !state.isLandscape && !state.editorFocused,
                enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)
            ) {
                slots.workBar()
            }

            if (state.isLandscape) {
                val galleryProgress = state.galleryProgress.coerceIn(0f, 1f)
                val galleryPresent = state.galleryVisible
                Box(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .onSizeChanged { size ->
                            if (!galleryPresent) landscapeWorkspaceSize = size
                        }
                ) {
                    val workspaceSize = if (galleryPresent && landscapeWorkspaceSize != IntSize.Zero) {
                        with(density) {
                            Modifier
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .requiredSize(landscapeWorkspaceSize.width.toDp(), landscapeWorkspaceSize.height.toDp())
                        }
                    } else Modifier.fillMaxSize()

                    Row(
                        modifier = workspaceSize
                            .then(if (galleryPresent) Modifier.clearAndSetSemantics {} else Modifier)
                            .focusProperties { if (galleryPresent) canFocus = false }
                            .onPreviewKeyEvent { galleryPresent }
                            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        val editorPane: @Composable RowScope.() -> Unit = {
                            Column(
                                modifier = Modifier
                                    .weight(1f - state.animatedLandscapePreviewFraction)
                                    .fillMaxHeight()
                                    .clipToBounds()
                            ) {
                                AnimatedVisibility(
                                    visible = !state.editorFocused,
                                    enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
                                    exit = fadeOut(tween(140)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)
                                ) {
                                    Box(Modifier.galleryChrome(galleryProgress, GalleryPart.TITLE)) { slots.landscapeBar() }
                                }

                                AnimatedVisibility(
                                    visible = state.showConsole,
                                    enter = fadeIn(tween(140)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                                    exit = fadeOut(tween(110)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
                                ) {
                                    slots.console(Modifier.galleryChrome(galleryProgress, GalleryPart.CONTROLS).padding(bottom = 6.dp))
                                }

                                AnimatedVisibility(
                                    visible = state.previewActionsExpanded,
                                    enter = fadeIn(tween(140)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                                    exit = fadeOut(tween(110)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
                                ) {
                                    slots.previewActions(Modifier.galleryChrome(galleryProgress, GalleryPart.CONTROLS).padding(bottom = 6.dp))
                                }

                                Crossfade(
                                    targetState = state.showParameterPanel,
                                    animationSpec = tween(180),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .galleryEditorReveal(galleryProgress, true, state.landscapeEditorOnLeft),
                                    label = "landscapeEditorParameterCrossfade"
                                ) { isParameter ->
                                    if (isParameter) {
                                        slots.parameterPanel(Modifier.fillMaxSize())
                                    } else {
                                        slots.editor(Modifier.fillMaxSize())
                                    }
                                }

                                slots.completions(Modifier.galleryChrome(galleryProgress, GalleryPart.CONTROLS).padding(top = 6.dp))

                                AnimatedVisibility(
                                    visible = state.editorFocused && state.showEditorAccessoryBar,
                                    enter = fadeIn(tween(120)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                                    exit = fadeOut(tween(90)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
                                ) {
                                    slots.accessory(Modifier.galleryChrome(galleryProgress, GalleryPart.CONTROLS).padding(top = 6.dp))
                                }
                            }
                        }

                        val dividerHandle: @Composable RowScope.() -> Unit = {
                            LandscapeSplitDivider(
                                showResizeHandles = state.showResizeHandles,
                                landscapeEditorOnLeft = state.landscapeEditorOnLeft,
                                landscapePreviewFraction = state.landscapePreviewFraction,
                                currentFraction = currentLandscapePreviewFraction.value,
                                showLandscapeSplitLabel = state.showLandscapeSplitLabel,
                                colors = colors,
                                hapticFeedback = hapticFeedback,
                                onSplitChanged = { next -> actions.onSplitChanged(next) },
                                onLabelVisibilityChanged = { actions.onSplitLabelChanged(it) },
                                textTranslator = textTranslator,
                                modifier = Modifier.galleryChrome(galleryProgress, GalleryPart.SECONDARY)
                            )
                        }

                        val previewPane: @Composable RowScope.() -> Unit = {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(state.animatedLandscapePreviewFraction)
                                    .fillMaxHeight()
                            ) {
                                val previewSize = fitPreviewSize(maxWidth.value, maxHeight.value, state.workPreviewRatio)
                                slots.preview(
                                    Modifier
                                        .size(previewSize.width.dp, previewSize.height.dp)
                                        .align(Alignment.Center)
                                )
                            }
                        }

                        if (state.landscapeEditorOnLeft) {
                            editorPane()
                            dividerHandle()
                            previewPane()
                        } else {
                            previewPane()
                            dividerHandle()
                            editorPane()
                        }
                    }
                    if (galleryPresent) {
                        Box(Modifier.matchParentSize().blockGalleryInput())
                        Column(
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = galleryLayerAlpha(galleryProgress) }
                                .background(colors.background)
                                .then(if (galleryProgress < 1f) Modifier.clearAndSetSemantics {} else Modifier)
                        ) {
                            slots.landscapeGalleryBar()
                            slots.gallery(Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                    slots.galleryPreview(Modifier.matchParentSize())
                    slots.previewChrome(Modifier.matchParentSize())
                    if (galleryPresent && galleryProgress < 1f) Box(Modifier.matchParentSize().blockGalleryInput())
                }
            } else {
                val galleryProgress = state.galleryProgress.coerceIn(0f, 1f)
                val galleryPresent = state.galleryVisible
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .onSizeChanged { size ->
                            if (!galleryPresent) portraitBodySize = size
                        }
                ) {
                    val editorSize = if (galleryPresent && portraitBodySize != IntSize.Zero) {
                        with(density) {
                            Modifier
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .requiredSize(portraitBodySize.width.toDp(), portraitBodySize.height.toDp())
                        }
                    } else {
                        Modifier.fillMaxSize()
                    }
                    Column(
                        editorSize
                            .then(if (galleryPresent) Modifier.clearAndSetSemantics {} else Modifier)
                            .focusProperties { if (galleryPresent) canFocus = false }
                            .onPreviewKeyEvent { galleryPresent }
                    ) {
                        // Top portrait preview is always hidden when editing (compact & keep removed)
                        PortraitPreviewViewport(
                            availableWidth = availablePreviewWidth.dp,
                            heightLimit = if (state.galleryVisible) portraitPreviewHeightLimit else previewHeightLimit,
                            ratio = state.workPreviewRatio,
                            hidden = isPortraitEditing,
                            compact = false
                        ) { previewModifier ->
                            if (!useFloatingPipInPortrait) {
                                slots.preview(previewModifier)
                            } else {
                                Box(previewModifier)
                            }
                        }

                        Column(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                            if (state.showResizeHandles && !state.editorFocused) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp)
                                        .galleryChrome(galleryProgress, GalleryPart.SECONDARY)
                                        .pointerInput(state.activeWorkId) {
                                            var accumulated = 0f
                                            var ratioIndex = PREVIEW_ASPECT_RATIOS
                                                .indexOf(currentPreviewRatio.value)
                                                .coerceAtLeast(0)
                                            val threshold = 38.dp.toPx()
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    accumulated = 0f
                                                    ratioIndex = PREVIEW_ASPECT_RATIOS
                                                        .indexOf(currentPreviewRatio.value)
                                                        .coerceAtLeast(0)
                                                    actions.onPortraitDraggingChanged(true)
                                                },
                                                onDragEnd = {
                                                    actions.onPortraitDraggingChanged(false)
                                                    actions.onCommitPreviewRatio()
                                                },
                                                onDragCancel = {
                                                    actions.onPortraitDraggingChanged(false)
                                                    actions.onCommitPreviewRatio()
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    accumulated += dragAmount
                                                    if (kotlin.math.abs(accumulated) >= threshold) {
                                                        val nextIndex = (
                                                            ratioIndex + if (accumulated > 0f) 1 else -1
                                                            ).coerceIn(0, PREVIEW_ASPECT_RATIOS.lastIndex)
                                                        if (nextIndex != ratioIndex) {
                                                            ratioIndex = nextIndex
                                                            val selectedRatio = PREVIEW_ASPECT_RATIOS[ratioIndex]
                                                            actions.onDraftPreviewRatio(selectedRatio)
                                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        }
                                                        accumulated = 0f
                                                    }
                                                }
                                            )
                                        }
                                        .semantics {
                                            contentDescription = text(
                                                "プレビュー比率を変更: %s",
                                                when (state.previewRatioSelection) {
                                                    "device_landscape" -> text("端末・横")
                                                    "device" -> text("端末・縦")
                                                    else -> state.previewRatioSelection
                                                }
                                            )
                                            customActions = PREVIEW_ASPECT_RATIOS.map { ratio ->
                                                CustomAccessibilityAction(
                                                    when (ratio) {
                                                        "device_landscape" -> text("端末・横")
                                                        "device" -> text("端末・縦")
                                                        else -> ratio
                                                    }
                                                ) {
                                                    currentRatioChange.value(ratio)
                                                    true
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .width(if (state.portraitRatioDragging) 82.dp else 48.dp)
                                            .height(if (state.portraitRatioDragging) 24.dp else 2.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (state.portraitRatioDragging) colors.primaryContainer else colors.outlineVariant,
                                        border = if (state.portraitRatioDragging) BorderStroke(1.dp, colors.primary.copy(alpha = 0.6f)) else null,
                                        tonalElevation = if (state.portraitRatioDragging) 2.dp else 0.dp
                                    ) {
                                        if (state.portraitRatioDragging) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = when (state.previewRatioSelection) {
                                                        "device_landscape" -> text("端末・横")
                                                        "device" -> text("端末・縦")
                                                        else -> state.previewRatioSelection
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontFamily = state.codeFontFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.onPrimaryContainer
                                                )
                                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    repeat(2) {
                                                        Box(
                                                            Modifier
                                                                .width(14.dp)
                                                                .height(1.dp)
                                                                .background(colors.onPrimaryContainer, RoundedCornerShape(1.dp))
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Box(Modifier.fillMaxSize())
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = !state.editorFocused,
                                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                                exit = fadeOut(tween(140)) + shrinkVertically(tween(220))
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                    Box(Modifier.galleryChrome(galleryProgress, GalleryPart.CONTROLS)) { slots.controlBar() }
                                }
                            }

                            AnimatedVisibility(
                                visible = state.showConsole,
                                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(110)) + shrinkVertically(tween(160))
                            ) {
                                slots.console(
                                    Modifier
                                        .galleryChrome(galleryProgress, GalleryPart.CONTROLS)
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = 6.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = state.previewActionsExpanded,
                                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(110)) + shrinkVertically(tween(160))
                            ) {
                                slots.previewActions(
                                    Modifier
                                        .galleryChrome(galleryProgress, GalleryPart.CONTROLS)
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = 6.dp)
                                )
                            }

                            val editorBottomPadding by animateDpAsState(
                                targetValue = if (!state.isSample && state.editorFocused && state.showEditorAccessoryBar) 0.dp else 12.dp,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "editorBottomPadding"
                            )

                            Crossfade(
                                targetState = state.showParameterPanel,
                                animationSpec = tween(180),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .galleryEditorReveal(galleryProgress, false, state.landscapeEditorOnLeft)
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = editorBottomPadding),
                                label = "portraitEditorParameterCrossfade"
                            ) { isParameter ->
                                if (isParameter) {
                                    slots.parameterPanel(Modifier.fillMaxSize())
                                } else {
                                    slots.editor(Modifier.fillMaxSize())
                                }
                            }

                            slots.completions(
                                Modifier
                                    .galleryChrome(galleryProgress, GalleryPart.CONTROLS)
                                    .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                            )

                            AnimatedVisibility(
                                visible = state.editorFocused && state.showEditorAccessoryBar,
                                enter = fadeIn(tween(120)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                                exit = fadeOut(tween(90)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
                            ) {
                                slots.accessory(
                                    Modifier
                                        .galleryChrome(galleryProgress, GalleryPart.CONTROLS)
                                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }

                    // Floating PiP Preview Window when the top preview is hidden during portrait editing
                    if (useFloatingPipInPortrait) {
                        val bottomReserved = (if (state.showEditorAccessoryBar && !state.isSample) 54.dp else 12.dp) +
                            (if (hasVisibleCompletions) 44.dp else 0.dp)
                        FloatingPipPreviewHost(
                            aspectRatio = state.workPreviewRatio,
                            containerWidthDp = containerMaxWidthDp,
                            containerHeightDp = (containerMaxHeightDp - bottomReserved.value).coerceAtLeast(160f),
                            autoRunEnabled = state.autoRunEnabled,
                            onToggleAutoRun = actions.onToggleAutoRun,
                            previewHasChanges = state.previewHasChanges,
                            onRunNow = actions.onRunNow,
                            colors = colors,
                            textTranslator = textTranslator,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 6.dp, start = 8.dp, end = 8.dp, bottom = bottomReserved)
                        ) { pipPreviewModifier ->
                            (slots.pipPreview ?: slots.preview)(pipPreviewModifier)
                        }
                    }

                    if (galleryPresent) {
                        Box(Modifier.matchParentSize().blockGalleryInput())
                        slots.gallery(
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = galleryLayerAlpha(galleryProgress) }
                                .background(colors.background)
                                .then(
                                    if (galleryProgress < 1f) Modifier.clearAndSetSemantics {}
                                    else Modifier
                                )
                        )
                    }
                    slots.galleryPreview(Modifier.matchParentSize())
                    slots.previewChrome(Modifier.matchParentSize())
                    if (galleryPresent && galleryProgress < 1f) Box(Modifier.matchParentSize().blockGalleryInput())
                }
            }
        }
    }
}

private fun Modifier.blockGalleryInput(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
