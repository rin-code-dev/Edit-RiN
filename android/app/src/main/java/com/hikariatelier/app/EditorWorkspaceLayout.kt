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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
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
    val galleryProgressProvider: () -> Float = { galleryProgress },
    val gallerySettled: Boolean = galleryProgress >= 1f,
    val pipModeWhenEditing: Boolean = true,
    val autoRunEnabled: Boolean = false,
    val previewHasChanges: Boolean = false,
    val pipSizePreset: PipSizePreset = PipSizePreset.SMALL,
    val pipTranslucent: Boolean = false,
    val pipMinimized: Boolean = true,
    val pipSnapCorner: PipCorner = PipCorner.TOP_END,
    val pipAutoGhosted: Boolean = false,
    val blockModeFullscreen: Boolean = false,
    val parameterFullscreen: Boolean = false
)

internal data class EditorWorkspaceActions(
    val onSplitChanged: (Float) -> Unit,
    val onSplitLabelChanged: (Boolean) -> Unit,
    val onPortraitDraggingChanged: (Boolean) -> Unit,
    val onDraftPreviewRatio: (String) -> Unit,
    val onCommitPreviewRatio: () -> Unit,
    val onSavePreviewRatio: (String) -> Unit,
    val onToggleAutoRun: () -> Unit = {},
    val onRunNow: () -> Unit = {},
    val onPipSizePresetChanged: (PipSizePreset) -> Unit = {},
    val onPipTranslucentChanged: (Boolean) -> Unit = {},
    val onPipMinimizedChanged: (Boolean) -> Unit = {},
    val onPipSnapCornerChanged: (PipCorner) -> Unit = {},
    val onPipCanvasTap: ((LayoutCoordinates, Offset) -> Unit)? = null,
    val onExpandPipFullscreen: (() -> Unit)? = null
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
    val landscapeRail: (@Composable (Modifier) -> Unit)? = null,
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    // The editor owns a WebView. Moving between split/full/portrait layouts must
    // move its composition, rather than dispose it and create a new renderer.
    val currentEditor = rememberUpdatedState(slots.editor)
    val editorContent = remember { movableContentOf<Modifier> { currentEditor.value(it) } }
    var portraitBodySize by remember(state.isLandscape, density) { mutableStateOf(IntSize.Zero) }
    var landscapeWorkspaceSize by remember(state.isLandscape, density) { mutableStateOf(IntSize.Zero) }
    var rootSize by remember(density) { mutableStateOf(IntSize.Zero) }
    var landscapeInnerSize by remember(state.isLandscape, density) { mutableStateOf(IntSize.Zero) }
    var previewPaneSize by remember(state.isLandscape, density) { mutableStateOf(IntSize.Zero) }
    val configuration = LocalConfiguration.current
    val currentLandscapePreviewFraction = rememberUpdatedState(state.landscapePreviewFraction)
    val currentPreviewRatio = rememberUpdatedState(state.previewRatioSelection)
    val currentRatioChange = rememberUpdatedState(actions.onSavePreviewRatio)
    fun text(source: String, vararg args: Any?) = textTranslator(source, args)

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .imePadding()
            .onSizeChanged { if (it != rootSize) rootSize = it }
    ) {
        val maxWidth = if (rootSize.width > 0) with(density) { rootSize.width.toDp() } else configuration.screenWidthDp.dp
        val maxHeight = if (rootSize.height > 0) with(density) { rootSize.height.toDp() } else configuration.screenHeightDp.dp
        val containerMaxWidthDp = maxWidth.value
        val containerMaxHeightDp = maxHeight.value
        val hasVisibleCompletions = state.hasVisibleCompletions
        val chromeHeight = (if (state.editorFocused) 0.dp else 108.dp) +
            (if (state.showResizeHandles && !state.editorFocused) 18.dp else 0.dp) +
            (if (state.showConsole) 176.dp else 0.dp) +
            (if (state.editorFocused && state.showEditorAccessoryBar) 60.dp else 8.dp) +
            (if (hasVisibleCompletions) 48.dp else 0.dp)
        val availableForEditing = (maxHeight - chromeHeight).coerceAtLeast(0.dp)

        // In portrait mode, editing ("そのまま" & "縮小" removed -> "隠す" only by default)
        // always hides the top preview viewport and shows the floating PiP window when enabled.
        val isPortraitFullscreenOverlay = state.blockModeFullscreen || state.parameterFullscreen
        val isPortraitEditing = !state.isLandscape &&
            (state.editorFocused || state.keyboardVisible || isPortraitFullscreenOverlay) &&
            !state.galleryVisible

        val useFloatingPipInPortrait = isPortraitEditing && (state.pipModeWhenEditing || isPortraitFullscreenOverlay)

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
                visible = !state.isLandscape && !state.editorFocused && !isPortraitFullscreenOverlay,
                enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)
            ) {
                slots.workBar()
            }

            if (state.isLandscape) {
                val galleryProgressProvider = state.galleryProgressProvider
                val gallerySettled = state.gallerySettled
                val galleryPresent = state.galleryVisible
                val useFloatingPipInLandscape = (state.blockModeFullscreen || state.parameterFullscreen) && !galleryPresent
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

                    Box(
                        modifier = workspaceSize
                            .then(if (galleryPresent) Modifier.clearAndSetSemantics {} else Modifier)
                            .focusProperties { if (galleryPresent) canFocus = false }
                            .onPreviewKeyEvent { galleryPresent }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .onSizeChanged { size ->
                                if (!galleryPresent && size != landscapeInnerSize) landscapeInnerSize = size
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val innerMaxWidth = if (landscapeInnerSize.width > 0) with(density) { landscapeInnerSize.width.toDp() }
                            else (maxWidth - 20.dp).coerceAtLeast(0.dp)
                        val innerMaxHeight = if (landscapeInnerSize.height > 0) with(density) { landscapeInnerSize.height.toDp() }
                            else (maxHeight - 20.dp).coerceAtLeast(0.dp)
                        val dividerWidth = if (state.showResizeHandles) 18.dp else 8.dp
                        val isSquareWork = kotlin.math.abs(state.workPreviewRatio - 1f) < 0.01f
                        val previewPaneWidth = ((innerMaxWidth - dividerWidth) * state.animatedLandscapePreviewFraction)
                            .coerceAtLeast(0.dp)
                        val alignedRowHeight = if (
                            !useFloatingPipInLandscape &&
                            !state.keyboardVisible &&
                            isSquareWork &&
                            state.animatedLandscapePreviewFraction >= 0.49f &&
                            previewPaneWidth in 220.dp..innerMaxHeight
                        ) {
                            previewPaneWidth
                        } else {
                            innerMaxHeight
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(alignedRowHeight),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val hasLandscapeRail = slots.landscapeRail != null
                            val railPane: @Composable () -> Unit = {
                                slots.landscapeRail?.invoke(
                                    Modifier.galleryChrome(galleryProgressProvider, GalleryPart.CONTROLS)
                                )
                            }
                            val editorColumn: @Composable (Modifier) -> Unit = { editorModifier ->
                                Column(
                                    modifier = editorModifier
                                        .fillMaxHeight()
                                        .clipToBounds()
                                ) {
                                    if (!hasLandscapeRail) {
                                        AnimatedVisibility(
                                            visible = !state.editorFocused && !useFloatingPipInLandscape,
                                            enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = FastOutSlowInEasing), expandFrom = Alignment.Top),
                                            exit = fadeOut(tween(140)) + shrinkVertically(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top)
                                        ) {
                                            Box(Modifier.galleryChrome(galleryProgressProvider, GalleryPart.TITLE)) { slots.landscapeBar() }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = state.showConsole,
                                        enter = fadeIn(tween(140)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                                        exit = fadeOut(tween(110)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
                                    ) {
                                        slots.console(Modifier.galleryChrome(galleryProgressProvider, GalleryPart.CONTROLS).padding(bottom = 4.dp))
                                    }

                                    AnimatedVisibility(
                                        visible = state.previewActionsExpanded,
                                        enter = fadeIn(tween(140)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                                        exit = fadeOut(tween(110)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
                                    ) {
                                        slots.previewActions(Modifier.galleryChrome(galleryProgressProvider, GalleryPart.CONTROLS).padding(bottom = 4.dp))
                                    }

                                    EditorAndParameterContainer(
                                        showParameterPanel = state.showParameterPanel,
                                        editorContent = editorContent,
                                        parameterPanel = slots.parameterPanel,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .galleryEditorReveal(galleryProgressProvider, true, state.landscapeEditorOnLeft)
                                    )

                                    slots.completions(Modifier.galleryChrome(galleryProgressProvider, GalleryPart.CONTROLS).padding(top = 4.dp))

                                    AnimatedVisibility(
                                        visible = state.editorFocused && state.showEditorAccessoryBar,
                                        enter = fadeIn(tween(120)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                                        exit = fadeOut(tween(90)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
                                    ) {
                                        slots.accessory(Modifier.galleryChrome(galleryProgressProvider, GalleryPart.CONTROLS).padding(top = 4.dp))
                                    }
                                }
                            }

                            val editorSidePane: @Composable RowScope.() -> Unit = {
                                Row(
                                    modifier = Modifier
                                        .weight(if (useFloatingPipInLandscape) 1f else (1f - state.animatedLandscapePreviewFraction))
                                        .fillMaxHeight(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasLandscapeRail && state.landscapeEditorOnLeft) {
                                        railPane()
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    editorColumn(Modifier.weight(1f))
                                    if (hasLandscapeRail && !state.landscapeEditorOnLeft) {
                                        Spacer(Modifier.width(8.dp))
                                        railPane()
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
                                    modifier = Modifier.galleryChrome(galleryProgressProvider, GalleryPart.SECONDARY)
                                )
                            }

                            val previewPane: @Composable RowScope.() -> Unit = {
                                Box(
                                    modifier = Modifier
                                        .weight(state.animatedLandscapePreviewFraction)
                                        .fillMaxHeight()
                                        .onSizeChanged { size ->
                                            if (!galleryPresent && size != previewPaneSize) previewPaneSize = size
                                        }
                                ) {
                                    val paneWidthDp = if (previewPaneSize.width > 0) with(density) { previewPaneSize.width.toDp() } else previewPaneWidth
                                    val paneHeightDp = if (previewPaneSize.height > 0) with(density) { previewPaneSize.height.toDp() } else alignedRowHeight
                                    val previewSize = fitPreviewSize(paneWidthDp.value, paneHeightDp.value, state.workPreviewRatio)
                                    slots.preview(
                                        Modifier
                                            .size(previewSize.width.dp, previewSize.height.dp)
                                            .align(Alignment.Center)
                                    )
                                }
                            }

                            if (useFloatingPipInLandscape) {
                                editorSidePane()
                            } else if (state.landscapeEditorOnLeft) {
                                editorSidePane()
                                dividerHandle()
                                previewPane()
                            } else {
                                previewPane()
                                dividerHandle()
                                editorSidePane()
                            }
                        }
                    }
                    if (useFloatingPipInLandscape) {
                        val topReserved = if (state.blockModeFullscreen) 50.dp else 6.dp
                        val bottomReserved = if (state.parameterFullscreen) 12.dp else 72.dp
                        FloatingPipPreviewHost(
                            aspectRatio = state.workPreviewRatio,
                            containerWidthDp = containerMaxWidthDp,
                            containerHeightDp = (containerMaxHeightDp - topReserved.value - bottomReserved.value).coerceAtLeast(160f),
                            autoRunEnabled = state.autoRunEnabled,
                            onToggleAutoRun = actions.onToggleAutoRun,
                            previewHasChanges = state.previewHasChanges,
                            onRunNow = actions.onRunNow,
                            colors = colors,
                            textTranslator = textTranslator,
                            sizePreset = state.pipSizePreset,
                            onSizePresetChange = actions.onPipSizePresetChanged,
                            translucent = state.pipTranslucent,
                            onTranslucentChange = actions.onPipTranslucentChanged,
                            minimized = state.pipMinimized,
                            onMinimizedChange = actions.onPipMinimizedChanged,
                            snapCorner = state.pipSnapCorner,
                            onSnapCornerChange = actions.onPipSnapCornerChanged,
                            isGhosted = if (state.parameterFullscreen) false else state.pipAutoGhosted,
                            onTapCanvas = actions.onPipCanvasTap,
                            onExpandFullscreen = actions.onExpandPipFullscreen,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topReserved, start = 16.dp, end = 16.dp, bottom = bottomReserved)
                        ) { pipPreviewModifier ->
                            (slots.pipPreview ?: slots.preview)(pipPreviewModifier)
                        }
                    }
                    if (galleryPresent) {
                        Box(Modifier.matchParentSize().blockGalleryInput())
                        Column(
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = galleryLayerAlpha(galleryProgressProvider()) }
                                .background(colors.background)
                                .then(if (!gallerySettled) Modifier.clearAndSetSemantics {} else Modifier)
                        ) {
                            slots.landscapeGalleryBar()
                            slots.gallery(Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                    slots.galleryPreview(Modifier.matchParentSize())
                    slots.previewChrome(Modifier.matchParentSize())
                    if (galleryPresent && !gallerySettled) Box(Modifier.matchParentSize().blockGalleryInput())
                }
            } else {
                val galleryProgressProvider = state.galleryProgressProvider
                val gallerySettled = state.gallerySettled
                val galleryPresent = state.galleryVisible
                val currentDensity = LocalDensity.current
                val currentView = LocalView.current
                val navVisibleBottomDp = with(currentDensity) { WindowInsets.navigationBars.getBottom(currentDensity).toDp() }
                val cornerTargetDp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val rootInsets = currentView.rootWindowInsets
                    val leftRadius = rootInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
                    val rightRadius = rootInsets?.getRoundedCorner(android.view.RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
                    val maxRadiusPx = maxOf(leftRadius, rightRadius)
                    if (maxRadiusPx > 0) {
                        (with(currentDensity) { maxRadiusPx.toDp() } * 0.42f).coerceIn(14.dp, 20.dp)
                    } else {
                        14.dp
                    }
                } else {
                    14.dp
                }
                val extraCornerBottom = if (!state.keyboardVisible) {
                    (cornerTargetDp - navVisibleBottomDp).coerceAtLeast(0.dp)
                } else {
                    0.dp
                }
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
                            if (state.showResizeHandles && !state.editorFocused && !isPortraitFullscreenOverlay) {
                                val resizeHandleHeight by animateDpAsState(
                                    targetValue = if (state.portraitRatioDragging) 26.dp else 18.dp,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "portraitResizeHandleHeight"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(resizeHandleHeight)
                                        .galleryChrome(galleryProgressProvider, GalleryPart.SECONDARY)
                                        .pointerInput(state.activeWorkId) {
                                            var accumulated = 0f
                                            var ratioOptions = previewAspectRatioOptions(currentPreviewRatio.value)
                                            var ratioIndex = ratioOptions
                                                .indexOf(currentPreviewRatio.value)
                                                .coerceAtLeast(0)
                                            val threshold = 38.dp.toPx()
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    accumulated = 0f
                                                    ratioOptions = previewAspectRatioOptions(currentPreviewRatio.value)
                                                    ratioIndex = ratioOptions
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
                                                            ).coerceIn(0, ratioOptions.lastIndex)
                                                        if (nextIndex != ratioIndex) {
                                                            ratioIndex = nextIndex
                                                            val selectedRatio = ratioOptions[ratioIndex]
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
                                            customActions = previewAspectRatioOptions(state.previewRatioSelection).map { ratio ->
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
                                visible = !state.editorFocused && !isPortraitFullscreenOverlay,
                                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                                exit = fadeOut(tween(140)) + shrinkVertically(tween(220))
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                                    Box(Modifier.galleryChrome(galleryProgressProvider, GalleryPart.CONTROLS)) { slots.controlBar() }
                                }
                            }

                            AnimatedVisibility(
                                visible = state.showConsole,
                                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(110)) + shrinkVertically(tween(160))
                            ) {
                                slots.console(
                                    Modifier
                                        .galleryChrome(galleryProgressProvider, GalleryPart.CONTROLS)
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = 6.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = state.previewActionsExpanded && !state.editorFocused && !isPortraitFullscreenOverlay,
                                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(110)) + shrinkVertically(tween(160))
                            ) {
                                slots.previewActions(
                                    Modifier
                                        .galleryChrome(galleryProgressProvider, GalleryPart.CONTROLS)
                                        .padding(horizontal = 12.dp)
                                        .padding(bottom = 6.dp)
                                )
                            }

                            val baseEditorBottomPadding = if (isPortraitFullscreenOverlay) {
                                10.dp
                            } else if (!state.isSample && state.editorFocused && state.showEditorAccessoryBar) {
                                0.dp
                            } else {
                                12.dp
                            }
                            val editorBottomPadding by animateDpAsState(
                                targetValue = maxOf(baseEditorBottomPadding, extraCornerBottom),
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "editorBottomPadding"
                            )

                            EditorAndParameterContainer(
                                showParameterPanel = state.showParameterPanel,
                                editorContent = editorContent,
                                parameterPanel = slots.parameterPanel,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .galleryEditorReveal(galleryProgressProvider, false, state.landscapeEditorOnLeft)
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = editorBottomPadding)
                            )

                            slots.completions(
                                Modifier
                                    .galleryChrome(galleryProgressProvider, GalleryPart.CONTROLS)
                                    .padding(start = 12.dp, end = 12.dp, top = 6.dp)
                            )

                            AnimatedVisibility(
                                visible = state.editorFocused && state.showEditorAccessoryBar,
                                enter = fadeIn(tween(120)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
                                exit = fadeOut(tween(90)) + shrinkVertically(spring(stiffness = Spring.StiffnessMedium))
                            ) {
                                slots.accessory(
                                    Modifier
                                        .galleryChrome(galleryProgressProvider, GalleryPart.CONTROLS)
                                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)
                                )
                            }
                        }
                    }

                    // Floating PiP Preview Window when the top preview is hidden during portrait editing
                    if (useFloatingPipInPortrait) {
                        val topReserved = if (state.blockModeFullscreen) 36.dp else 6.dp
                        val bottomReserved = (if (state.parameterFullscreen) 12.dp else if (state.blockModeFullscreen) 72.dp else if (state.showEditorAccessoryBar && !state.isSample) 54.dp else 12.dp) +
                            (if (hasVisibleCompletions && !state.parameterFullscreen) 44.dp else 0.dp) +
                            (if (state.parameterFullscreen || state.blockModeFullscreen) extraCornerBottom else 0.dp)
                        FloatingPipPreviewHost(
                            aspectRatio = state.workPreviewRatio,
                            containerWidthDp = containerMaxWidthDp,
                            containerHeightDp = (containerMaxHeightDp - topReserved.value - bottomReserved.value).coerceAtLeast(160f),
                            autoRunEnabled = state.autoRunEnabled,
                            onToggleAutoRun = actions.onToggleAutoRun,
                            previewHasChanges = state.previewHasChanges,
                            onRunNow = actions.onRunNow,
                            colors = colors,
                            textTranslator = textTranslator,
                            sizePreset = state.pipSizePreset,
                            onSizePresetChange = actions.onPipSizePresetChanged,
                            translucent = state.pipTranslucent,
                            onTranslucentChange = actions.onPipTranslucentChanged,
                            minimized = state.pipMinimized,
                            onMinimizedChange = actions.onPipMinimizedChanged,
                            snapCorner = state.pipSnapCorner,
                            onSnapCornerChange = actions.onPipSnapCornerChanged,
                            isGhosted = if (state.parameterFullscreen) false else state.pipAutoGhosted,
                            onTapCanvas = actions.onPipCanvasTap,
                            onExpandFullscreen = actions.onExpandPipFullscreen,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topReserved, start = 8.dp, end = 8.dp, bottom = bottomReserved)
                        ) { pipPreviewModifier ->
                            (slots.pipPreview ?: slots.preview)(pipPreviewModifier)
                        }
                    }

                    if (galleryPresent) {
                        Box(Modifier.matchParentSize().blockGalleryInput())
                        slots.gallery(
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = galleryLayerAlpha(galleryProgressProvider()) }
                                .background(colors.background)
                                .then(
                                    if (!gallerySettled) Modifier.clearAndSetSemantics {}
                                    else Modifier
                                )
                        )
                    }
                    slots.galleryPreview(Modifier.matchParentSize())
                    slots.previewChrome(Modifier.matchParentSize())
                    if (galleryPresent && !gallerySettled) Box(Modifier.matchParentSize().blockGalleryInput())
                }
            }
        }
    }
}

@Composable
private fun EditorAndParameterContainer(
    showParameterPanel: Boolean,
    editorContent: @Composable (Modifier) -> Unit,
    parameterPanel: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (showParameterPanel) 0f else 1f }
                .then(if (showParameterPanel) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier)
                .focusProperties { if (showParameterPanel) canFocus = false }
                .onPreviewKeyEvent { showParameterPanel }
                .then(if (showParameterPanel) Modifier.blockGalleryInput() else Modifier)
        ) {
            editorContent(Modifier.fillMaxSize())
        }
        AnimatedVisibility(
            visible = showParameterPanel,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(100)),
            modifier = Modifier.fillMaxSize()
        ) {
            parameterPanel(Modifier.fillMaxSize())
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
