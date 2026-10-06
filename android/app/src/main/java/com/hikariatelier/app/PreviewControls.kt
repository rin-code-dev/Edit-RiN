package com.hikariatelier.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
internal fun PreviewOverlayButton(
    iconRes: Int,
    description: String,
    colors: ColorScheme,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.requiredSize(40.dp),
        shape = RoundedCornerShape(11.dp),
        color = if (active) {
            colors.errorContainer.copy(alpha = 0.96f)
        } else {
            colors.surface.copy(alpha = 0.9f)
        },
        contentColor = if (active) colors.onErrorContainer else colors.onSurface,
        border = BorderStroke(
            1.dp,
            if (active) colors.error else colors.outlineVariant
        ),
        shadowElevation = 3.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
internal fun PreviewActionsToggleButton(
    isRecordingOrCountingDown: Boolean,
    previewActionsExpanded: Boolean,
    isLandscape: Boolean,
    colors: ColorScheme,
    onClick: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    val borderColor = when {
        isRecordingOrCountingDown -> colors.error
        previewActionsExpanded -> colors.primary
        else -> colors.outlineVariant
    }
    val contentColor = when {
        isRecordingOrCountingDown -> colors.error
        previewActionsExpanded -> colors.primary
        else -> colors.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .height(if (isLandscape) 34.dp else 36.dp)
            .widthIn(min = if (isLandscape) 34.dp else 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(
                    if (isRecordingOrCountingDown) R.drawable.ic_stop else R.drawable.ic_camera
                ),
                contentDescription = uiText("プレビュー操作"),
                tint = contentColor,
                modifier = Modifier.size(if (isLandscape) 15.dp else 16.dp)
            )
            Spacer(Modifier.width(3.dp))
            Icon(
                painter = painterResource(
                    if (previewActionsExpanded) R.drawable.ic_close else R.drawable.ic_chevron_down
                ),
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(if (isLandscape) 11.dp else 12.dp)
            )
        }
    }
}

@Composable
internal fun PreviewActionsTray(
    isRecordingOrCountingDown: Boolean,
    isRecordingSaving: Boolean,
    isPreviewRecording: Boolean,
    pendingRecordingFormat: String?,
    colors: ColorScheme,
    onOpenParameters: () -> Unit = {},
    onScreenshot: () -> Unit,
    onShareCard: () -> Unit,
    onRecordToggle: () -> Unit,
    onFullscreen: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            @Composable
            fun ActionChip(
                iconRes: Int,
                label: String,
                active: Boolean = false,
                destructive: Boolean = false,
                onClick: () -> Unit
            ) {
                val chipBg = when {
                    destructive -> colors.errorContainer.copy(alpha = 0.85f)
                    active -> colors.primaryContainer.copy(alpha = 0.85f)
                    else -> colors.surfaceVariant.copy(alpha = 0.5f)
                }
                val chipContent = when {
                    destructive -> colors.onErrorContainer
                    active -> colors.onPrimaryContainer
                    else -> colors.onSurface
                }
                Surface(
                    onClick = onClick,
                    shape = RoundedCornerShape(9.dp),
                    color = chipBg,
                    border = BorderStroke(
                        1.dp,
                        when {
                            destructive -> colors.error
                            active -> colors.primary
                            else -> colors.outlineVariant.copy(alpha = 0.5f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = label,
                            tint = chipContent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = chipContent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // View controls
            ActionChip(
                iconRes = R.drawable.ic_fullscreen,
                label = uiText("全画面"),
                onClick = onFullscreen
            )

            // Export group: everything that produces a file or a card
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .width(1.dp)
                    .height(22.dp)
                    .background(colors.outlineVariant)
            )
            Text(
                text = uiText("書き出し"),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            ActionChip(
                iconRes = R.drawable.ic_camera,
                label = uiText("スクショ"),
                onClick = onScreenshot
            )
            ActionChip(
                iconRes = R.drawable.ic_share_card,
                label = uiText("シェアカード"),
                onClick = onShareCard
            )
            ActionChip(
                iconRes = if (isRecordingOrCountingDown) R.drawable.ic_stop else R.drawable.ic_record,
                label = when {
                    pendingRecordingFormat != null -> uiText("録画中止")
                    isPreviewRecording -> uiText("録画停止")
                    else -> uiText("録画")
                },
                active = isRecordingOrCountingDown,
                destructive = isRecordingOrCountingDown,
                onClick = onRecordToggle
            )
        }
    }
}

@Composable
internal fun RunStatusControls(
    isError: Boolean,
    isPaused: Boolean,
    colors: ColorScheme,
    height: Dp,
    buttonSize: Dp,
    iconSize: Dp,
    onTogglePause: () -> Unit,
    onReload: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier,
    hasPendingChanges: Boolean = false
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    Row(
        modifier = modifier
            .height(height)
            .border(
                width = 1.dp,
                color = if (isError) colors.error else colors.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(start = 8.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when {
                        hasPendingChanges -> colors.tertiary
                        isError -> colors.error
                        isPaused -> colors.outline
                        else -> colors.primary
                    }
                )
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = when {
                hasPendingChanges -> uiText("変更未反映")
                isError -> "ERR"
                isPaused -> "PAUSE"
                else -> "RUN"
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hasPendingChanges) colors.tertiary else if (isError) colors.error else colors.onSurfaceVariant,
            modifier = if (hasPendingChanges) Modifier.clickable(onClick = onReload) else Modifier,
            letterSpacing = 0.6.sp
        )
        IconButton(
            onClick = onTogglePause,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                painter = painterResource(if (isPaused) R.drawable.ic_play else R.drawable.ic_pause),
                contentDescription = uiText(if (isPaused) "再生" else "一時停止"),
                tint = colors.onSurface,
                modifier = Modifier.size(iconSize)
            )
        }
        IconButton(
            onClick = onReload,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_reload),
                contentDescription = uiText(if (hasPendingChanges) "変更を実行" else "再読み込み"),
                tint = colors.onSurface,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
internal fun ConsoleButton(
    hasError: Boolean,
    entryCount: Int,
    colors: ColorScheme,
    height: Dp,
    minWidth: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    Box(
        modifier = modifier
            .height(height)
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = if (hasError) colors.error else colors.outlineVariant,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_log),
                contentDescription = uiText("ログ"),
                tint = if (hasError) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.size(iconSize)
            )
            if (entryCount > 0) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (entryCount > 99) "99+" else entryCount.toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (hasError) colors.error else colors.outline
                )
            }
        }
    }
}

@Composable
internal fun ParameterButton(
    parameterCount: Int,
    colors: ColorScheme,
    height: Dp,
    minWidth: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)
    val hasParameters = parameterCount > 0
    val borderColor = if (hasParameters) colors.primary.copy(alpha = 0.9f) else colors.outlineVariant
    val backgroundColor = if (hasParameters) colors.primaryContainer.copy(alpha = 0.22f) else Color.Transparent
    val contentColor = if (hasParameters) colors.primary else colors.onSurfaceVariant

    Box(
        modifier = modifier
            .height(height)
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tune),
                contentDescription = uiText("パラメータ"),
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
            if (hasParameters) {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = if (parameterCount > 99) "99+" else parameterCount.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
            }
        }
    }
}

@Composable
internal fun SaveRestoreControls(
    hasUnsavedChanges: Boolean,
    colors: ColorScheme,
    height: Dp,
    buttonSize: Dp,
    iconSize: Dp,
    onSnapshot: (() -> Unit)? = null,
    onRestore: () -> Unit,
    onSave: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    Row(
        modifier = modifier
            .height(height)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onSnapshot != null) {
            TooltipIconButton(
                label = uiText("スナップショット"),
                onClick = onSnapshot,
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_snapshot),
                    contentDescription = uiText("スナップショット"),
                    tint = colors.onSurface,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        TooltipIconButton(
            label = uiText("保存済み状態に戻す"),
            onClick = onRestore,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_restore),
                contentDescription = uiText("保存済み状態に戻す"),
                tint = colors.onSurface,
                modifier = Modifier.size(iconSize)
            )
        }
        TooltipIconButton(
            label = uiText("作品の全ファイルを保存"),
            onClick = onSave,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_save),
                contentDescription = uiText("作品の全ファイルを保存"),
                tint = if (hasUnsavedChanges) colors.primary else colors.onSurface,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
internal fun BoxWithConstraintsScope.PreviewOverlayControls(
    fullscreen: Boolean,
    showExpandControl: Boolean,
    isLandscape: Boolean,
    isRecordingOrCountingDown: Boolean,
    recordingCountdownRemaining: Int,
    pendingRecordingFormat: String?,
    isPreviewRecording: Boolean,
    isRecordingSaving: Boolean,
    recordingFormatLabel: String,
    recordingElapsedMillis: Long,
    recordingLimitMillis: Long,
    previewActionsExpanded: Boolean,
    onTogglePreviewActions: () -> Unit,
    onClosePreviewActions: () -> Unit,
    onRotate: () -> Unit,
    onScreenshot: () -> Unit,
    onShareCard: () -> Unit,
    onOpenParameters: () -> Unit,
    onToggleRecording: () -> Unit,
    onCancelCountdown: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onStopRecording: () -> Unit,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    @Composable
    fun PreviewActionButtons() {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (fullscreen) {
                PreviewOverlayButton(
                    iconRes = R.drawable.ic_rotate,
                    description = uiText("画面を回転"),
                    colors = colors,
                    onClick = onRotate
                )
            }
            PreviewOverlayButton(
                iconRes = R.drawable.ic_camera,
                description = uiText("プレビューをスクリーンショット"),
                colors = colors,
                onClick = onScreenshot
            )

            PreviewOverlayButton(
                iconRes = R.drawable.ic_share_card,
                description = uiText("シェアカードを作成"),
                colors = colors,
                onClick = onShareCard
            )

            PreviewOverlayButton(
                iconRes = R.drawable.ic_tune,
                description = uiText("パラメータ"),
                colors = colors,
                onClick = onOpenParameters
            )

            PreviewOverlayButton(
                iconRes = if (isRecordingOrCountingDown) {
                    R.drawable.ic_stop
                } else {
                    R.drawable.ic_record
                },
                description = when {
                    pendingRecordingFormat != null -> uiText("録画カウントダウンを中止")
                    isPreviewRecording -> uiText("録画を停止")
                    else -> uiText("プレビューを録画")
                },
                colors = colors,
                active = isRecordingOrCountingDown,
                onClick = onToggleRecording
            )

            if (showExpandControl) {
                PreviewOverlayButton(
                    iconRes = R.drawable.ic_fullscreen,
                    description = uiText("プレビューを全画面表示"),
                    colors = colors,
                    onClick = onToggleFullscreen
                )
            } else {
                PreviewOverlayButton(
                    iconRes = R.drawable.ic_close,
                    description = uiText("全画面表示を閉じる"),
                    colors = colors,
                    onClick = onToggleFullscreen
                )
            }
        }
    }

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val availableWidth = maxWidth - if (fullscreen) with(density) {
        (WindowInsets.safeDrawing.getLeft(density, direction) +
            WindowInsets.safeDrawing.getRight(density, direction)).toDp()
    } else 0.dp
    val requiredInlineWidth = (if (fullscreen) 345.dp else 298.dp) +
        (if (isPreviewRecording || isRecordingSaving) 120.dp else 0.dp)
    val usePopup = availableWidth < requiredInlineWidth || maxHeight < 60.dp

    if (recordingCountdownRemaining > 0) {
        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(22.dp),
            color = Color.Black.copy(alpha = 0.82f),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = recordingCountdownRemaining.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onCancelCountdown) {
                    Text(uiText("キャンセル"), color = Color.White)
                }
            }
        }
    }

    if (fullscreen) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top +
                            WindowInsetsSides.Horizontal
                    )
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (isPreviewRecording || isRecordingSaving) {
                RecordingStatus(
                    format = recordingFormatLabel, elapsed = recordingElapsedMillis,
                    remaining = (recordingLimitMillis - recordingElapsedMillis).coerceAtLeast(0),
                    saving = isRecordingSaving, text = ::uiText,
                    modifier = Modifier.weight(1f, fill = false),
                    onStop = onStopRecording
                )
            }
            AnimatedVisibility(
                visible = previewActionsExpanded && !usePopup,
                enter = fadeIn(tween(130)) + expandHorizontally(
                    animationSpec = tween(210, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.End
                ),
                exit = fadeOut(tween(100)) + shrinkHorizontally(
                    animationSpec = tween(170, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.End
                )
            ) {
                PreviewActionButtons()
            }

            if (previewActionsExpanded && usePopup) {
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = IntOffset(0, with(density) { 48.dp.roundToPx() }),
                    onDismissRequest = onClosePreviewActions,
                    properties = PopupProperties(focusable = true)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.outlineVariant),
                        shadowElevation = 6.dp,
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
                            PreviewActionButtons()
                        }
                    }
                }
            }

            PreviewOverlayButton(
                iconRes = R.drawable.ic_more_horizontal,
                description = if (previewActionsExpanded) uiText("プレビュー操作を閉じる") else uiText("プレビュー操作を開く"),
                colors = colors,
                active = isRecordingOrCountingDown,
                onClick = onTogglePreviewActions
            )
        }
    } else if (isPreviewRecording || isRecordingSaving) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            RecordingStatus(
                format = recordingFormatLabel, elapsed = recordingElapsedMillis,
                remaining = (recordingLimitMillis - recordingElapsedMillis).coerceAtLeast(0),
                saving = isRecordingSaving, text = ::uiText,
                onStop = onStopRecording
            )
        }
    }
}

@Composable
internal fun LandscapeSplitDivider(
    showResizeHandles: Boolean,
    landscapeEditorOnLeft: Boolean,
    landscapePreviewFraction: Float,
    currentFraction: Float,
    showLandscapeSplitLabel: Boolean,
    colors: ColorScheme,
    hapticFeedback: HapticFeedback,
    onSplitChanged: (Float) -> Unit,
    onLabelVisibilityChanged: (Boolean) -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier
) {
    if (showResizeHandles) {
        Box(
            modifier = modifier
                .width(24.dp)
                .fillMaxHeight()
                .pointerInput(landscapeEditorOnLeft) {
                    var accumulated = 0f
                    var splitIndex = 1
                    val threshold = 44.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            accumulated = 0f
                            splitIndex = LANDSCAPE_PREVIEW_SPLITS
                                .indices
                                .minByOrNull { index ->
                                    kotlin.math.abs(
                                        LANDSCAPE_PREVIEW_SPLITS[index] - currentFraction
                                    )
                                } ?: 1
                            onLabelVisibilityChanged(true)
                        },
                        onDragEnd = {
                            onLabelVisibilityChanged(false)
                        },
                        onDragCancel = {
                            onLabelVisibilityChanged(false)
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount
                            if (kotlin.math.abs(accumulated) >= threshold) {
                                val step = if (accumulated > 0f) 1 else -1
                                val delta = if (landscapeEditorOnLeft) -step else step
                                val nextIndex = (
                                    splitIndex + delta
                                ).coerceIn(0, LANDSCAPE_PREVIEW_SPLITS.lastIndex)
                                if (nextIndex != splitIndex) {
                                    splitIndex = nextIndex
                                    onSplitChanged(LANDSCAPE_PREVIEW_SPLITS[splitIndex])
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.LongPress
                                    )
                                }
                                accumulated = 0f
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            onSplitChanged(0.5f)
                            hapticFeedback.performHapticFeedback(
                                HapticFeedbackType.LongPress
                            )
                        }
                    )
                }
                .semantics {
                    contentDescription = textTranslator("プレビューとエディターの分割を調整", emptyArray())
                    customActions = listOf(
                        CustomAccessibilityAction(textTranslator("プレビューを広げる", emptyArray())) {
                            val index = LANDSCAPE_PREVIEW_SPLITS.indexOf(landscapePreviewFraction)
                            val next = LANDSCAPE_PREVIEW_SPLITS[(index + 1).coerceIn(0, 2)]
                            onSplitChanged(next)
                            true
                        },
                        CustomAccessibilityAction(textTranslator("エディターを広げる", emptyArray())) {
                            val index = LANDSCAPE_PREVIEW_SPLITS.indexOf(landscapePreviewFraction)
                            val next = LANDSCAPE_PREVIEW_SPLITS[(index - 1).coerceIn(0, 2)]
                            onSplitChanged(next)
                            true
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier
                        .width(if (showLandscapeSplitLabel) 12.dp else 2.dp)
                        .height(if (showLandscapeSplitLabel) 64.dp else 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (showLandscapeSplitLabel) {
                        colors.primaryContainer
                    } else {
                        colors.outlineVariant
                    },
                    border = if (showLandscapeSplitLabel) {
                        BorderStroke(
                            1.dp,
                            colors.primary.copy(alpha = 0.6f)
                        )
                    } else null,
                    tonalElevation = if (showLandscapeSplitLabel) 2.dp else 0.dp
                ) {
                    if (showLandscapeSplitLabel) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(
                                4.dp,
                                Alignment.CenterVertically
                            )
                        ) {
                            repeat(3) {
                                Box(
                                    Modifier
                                        .width(6.dp)
                                        .height(1.dp)
                                        .background(
                                            colors.onPrimaryContainer,
                                            RoundedCornerShape(1.dp)
                                        )
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    } else {
        Spacer(modifier.width(8.dp))
    }
}
