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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
    val compactPreview: Boolean,
    val hideEditingPreview: Boolean,
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
)

internal data class EditorWorkspaceActions(
    val onSplitChanged: (Float) -> Unit,
    val onSplitLabelChanged: (Boolean) -> Unit,
    val onPortraitDraggingChanged: (Boolean) -> Unit,
    val onDraftPreviewRatio: (String) -> Unit,
    val onCommitPreviewRatio: () -> Unit,
    val onSavePreviewRatio: (String) -> Unit
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
    val preview: @Composable (Modifier) -> Unit
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
    val currentLandscapePreviewFraction = rememberUpdatedState(state.landscapePreviewFraction)
    val currentPreviewRatio = rememberUpdatedState(state.previewRatioSelection)
    val currentRatioChange = rememberUpdatedState(actions.onSavePreviewRatio)
    fun text(source: String, vararg args: Any?) = textTranslator(source, args)
    BoxWithConstraints(
        modifier
            .fillMaxSize().background(colors.background)
            .imePadding()
    ) {
        // Reserve editing space even for tall works and when the keyboard is open.
        val hasVisibleCompletions = state.hasVisibleCompletions
        val chromeHeight = (if (state.editorFocused) 0.dp else 108.dp) +
            (if (state.showResizeHandles && !state.editorFocused) 28.dp else 0.dp) +
            (if (state.showConsole) 176.dp else 0.dp) +
            (if (state.editorFocused && state.showEditorAccessoryBar) 60.dp else 8.dp) +
            (if (hasVisibleCompletions) 48.dp else 0.dp)
        val availableForEditing = (maxHeight - chromeHeight).coerceAtLeast(0.dp)
        // Editing uses a shallow, full-width viewport, without changing the saved work ratio.
        val useWideEditingPreview = (state.editorFocused && state.compactPreview) || (state.keyboardVisible && !state.hideEditingPreview)
        val previewHeightLimit = if (state.keyboardVisible) {
            availableForEditing * 0.35f
        } else if (state.editorFocused && state.compactPreview) {
            availableForEditing * 0.5f
        } else {
            availableForEditing * 0.65f
        }
        val availablePreviewWidth = (maxWidth - 24.dp).value
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.background,
                                // Match the inset background in explicit dark mode.
                                // Keep the existing light / Material You gradient.
                                if (state.themeMode == AppThemeMode.DARK ||
                                    state.themeMode == AppThemeMode.CUSTOM) colors.background
                                else colors.surface
                            )
                        )
                    )
        ) {

            AnimatedVisibility(
                visible =
                    !state.isLandscape &&
                            !state.editorFocused,

                enter =
                    fadeIn(
                        tween(180)
                    ) +
                            expandVertically(
                                animationSpec =
                                    tween(
                                        220,
                                        easing =
                                            FastOutSlowInEasing
                                    ),

                                expandFrom =
                                    Alignment.Top
                            ),

                exit =
                    fadeOut(
                        tween(140)
                    ) +
                            shrinkVertically(
                                animationSpec =
                                    tween(
                                        220,
                                        easing =
                                            FastOutSlowInEasing
                                    ),

                                shrinkTowards =
                                    Alignment.Top
                            )
            ) {

                slots.workBar()
            }

            if (state.isLandscape) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start =
                                    12.dp,

                                end =
                                    12.dp,

                                top =
                                    8.dp,

                                bottom =
                                    8.dp
                            ),

                    horizontalArrangement = Arrangement.Start
                ) {
                    val editorPane: @Composable RowScope.() -> Unit = {
                        Column(
                            modifier = Modifier
                                .weight(1f - state.animatedLandscapePreviewFraction)
                                .fillMaxHeight()
                        ) {
                            AnimatedVisibility(
                                visible = !state.editorFocused,
                                enter = fadeIn(tween(180)) +
                                        expandVertically(
                                            animationSpec = tween(
                                                220,
                                                easing = FastOutSlowInEasing
                                            ),
                                            expandFrom = Alignment.Top
                                        ),
                                exit = fadeOut(tween(140)) +
                                        shrinkVertically(
                                            animationSpec = tween(
                                                220,
                                                easing = FastOutSlowInEasing
                                            ),
                                            shrinkTowards = Alignment.Top
                                        )
                            ) {
                                slots.landscapeBar()
                            }

                            AnimatedVisibility(
                                visible = state.showConsole,
                                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(110)) + shrinkVertically(tween(160))
                            ) {
                                slots.console(
                                    Modifier.padding(bottom = 6.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = state.previewActionsExpanded,
                                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                                exit = fadeOut(tween(110)) + shrinkVertically(tween(160))
                            ) {
                                slots.previewActions(
                                    Modifier.padding(bottom = 6.dp)
                                )
                            }

                            slots.editor(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )

                            slots.completions(
                                Modifier.padding(top = 6.dp)
                            )

                            AnimatedVisibility(
                                visible = state.editorFocused && state.showEditorAccessoryBar,
                                enter = fadeIn(tween(120)) + expandVertically(tween(160)),
                                exit = fadeOut(tween(90)) + shrinkVertically(tween(130))
                            ) {
                                slots.accessory(
                                    Modifier.padding(top = 6.dp)
                                )
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
                            onSplitChanged = { next ->
                                actions.onSplitChanged(next)

                            },
                            onLabelVisibilityChanged = { actions.onSplitLabelChanged(it) },
                            textTranslator = textTranslator
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

            } else {

                PortraitPreviewViewport(
                    availableWidth = availablePreviewWidth.dp,
                    heightLimit = previewHeightLimit,
                    ratio = state.workPreviewRatio,
                    hidden = state.hideEditingPreview && (state.editorFocused || state.keyboardVisible),
                    compact = useWideEditingPreview
                ) { previewModifier -> slots.preview(previewModifier) }

                if (state.showResizeHandles && !state.editorFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
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
                                            hapticFeedback.performHapticFeedback(
                                                HapticFeedbackType.LongPress
                                            )
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
                                    "device" -> text("端末")
                                    "device_landscape" -> text("端末・横")
                                    else -> state.previewRatioSelection
                                }
                            )
                            customActions = PREVIEW_ASPECT_RATIOS.map { ratio ->
                                CustomAccessibilityAction(
                                    when (ratio) {
                                        "device" -> text("端末")
                                        "device_landscape" -> text("端末・横")
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
                        color = if (state.portraitRatioDragging) {
                            colors.primaryContainer
                        } else {
                            colors.outlineVariant
                        },
                        border = if (state.portraitRatioDragging) {
                            BorderStroke(
                                1.dp,
                                colors.primary.copy(alpha = 0.6f)
                            )
                        } else null,
                        tonalElevation = if (state.portraitRatioDragging) 2.dp else 0.dp
                    ) {
                        if (state.portraitRatioDragging) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = when (state.previewRatioSelection) {
                                    "device" -> text("端末")
                                    "device_landscape" -> text("端末・横")
                                    else -> state.previewRatioSelection
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = state.codeFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.portraitRatioDragging) {
                                    colors.onPrimaryContainer
                                } else {
                                    colors.onSurfaceVariant
                                }
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                repeat(2) {
                                    Box(
                                        Modifier
                                            .width(14.dp)
                                            .height(1.dp)
                                            .background(
                                                if (state.portraitRatioDragging) {
                                                    colors.onPrimaryContainer
                                                } else {
                                                    colors.onSurfaceVariant
                                                },
                                                RoundedCornerShape(1.dp)
                                            )
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
                    visible =
                        !state.editorFocused,

                    enter =
                        fadeIn(
                            tween(180)
                        ) +
                                expandVertically(
                                    tween(220)
                                ),

                    exit =
                        fadeOut(
                            tween(140)
                        ) +
                                shrinkVertically(
                                    tween(220)
                                )
                ) {

                    Box(
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    12.dp
                            )
                    ) {

                        slots.controlBar()
                    }
                }

                AnimatedVisibility(
                    visible =
                        state.showConsole,
                    enter =
                        fadeIn(
                            tween(140)
                        ) +
                            expandVertically(
                                tween(180)
                            ),
                    exit =
                        fadeOut(
                            tween(110)
                        ) +
                            shrinkVertically(
                                tween(160)
                            )
                ) {

                    slots.console(
                        Modifier
                                .padding(
                                    horizontal =
                                        12.dp
                                )
                                .padding(
                                    bottom =
                                        6.dp
                                )
                    )
                }

                AnimatedVisibility(
                    visible =
                        state.previewActionsExpanded,
                    enter =
                        fadeIn(
                            tween(140)
                        ) +
                            expandVertically(
                                tween(180)
                            ),
                    exit =
                        fadeOut(
                            tween(110)
                        ) +
                            shrinkVertically(
                                tween(160)
                            )
                ) {

                    slots.previewActions(
                        Modifier
                                .padding(
                                    horizontal =
                                        12.dp
                                )
                                .padding(
                                    bottom =
                                        6.dp
                                )
                    )
                }

                slots.editor(
                    Modifier
                            .weight(
                                1f
                            )
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    12.dp
                            )
                            .padding(
                                bottom =
                                    if (state.editorFocused && state.showEditorAccessoryBar) 0.dp else 8.dp
                            )
                )

                slots.completions(
                    Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = 6.dp
                    )
                )

                AnimatedVisibility(
                    visible = state.editorFocused && state.showEditorAccessoryBar,
                    enter = fadeIn(tween(120)) + expandVertically(tween(160)),
                    exit = fadeOut(tween(90)) + shrinkVertically(tween(130))
                ) {
                    slots.accessory(
                        Modifier.padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 6.dp,
                            bottom = 2.dp
                        )
                    )
                }
            }
        }
    }
}
