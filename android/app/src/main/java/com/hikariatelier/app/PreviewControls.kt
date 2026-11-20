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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

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
    onShareCard: () -> Unit = {},
    onRecordToggle: () -> Unit,
    onFullscreen: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String,
    modifier: Modifier = Modifier,
    showHud: Boolean = false,
    onToggleHud: (() -> Unit)? = null,
    onStepFrame: (() -> Unit)? = null
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
                    active -> colors.primary.copy(alpha = 0.14f)
                    else -> colors.surface
                }
                val chipContent = when {
                    destructive -> colors.onErrorContainer
                    active -> colors.primary
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
                            else -> colors.outlineVariant
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
            if (onStepFrame != null) {
                ActionChip(
                    iconRes = R.drawable.ic_play,
                    label = uiText("1コマ送り"),
                    onClick = onStepFrame
                )
            }
            if (onToggleHud != null) {
                ActionChip(
                    iconRes = R.drawable.ic_terminal,
                    label = "FPS HUD",
                    active = showHud,
                    onClick = onToggleHud
                )
            }

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
    hasPendingChanges: Boolean = false,
    onStepFrame: (() -> Unit)? = null
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    val haptic = LocalHapticFeedback.current
    val dotColor by animateColorAsState(
        when {
            hasPendingChanges -> colors.tertiary
            isError -> colors.error
            isPaused -> colors.outline
            else -> colors.primary
        },
        tween(200),
        label = "runBarStatusDot"
    )

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
                .background(dotColor)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = when {
                hasPendingChanges -> uiText("未反映")
                isError -> "ERR"
                isPaused -> "PAUSE"
                else -> "RUN"
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hasPendingChanges) colors.tertiary else if (isError) colors.error else colors.onSurfaceVariant,
            modifier = if (hasPendingChanges) Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClickLabel = uiText("変更を実行"), onClick = onReload)
                .padding(horizontal = 3.dp, vertical = 1.dp)
                .semantics { contentDescription = uiText("変更未反映") }
                else Modifier,
            maxLines = 1,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePause()
                })
                .semantics { contentDescription = uiText(if (isPaused) "再生" else "一時停止") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(if (isPaused) R.drawable.ic_play else R.drawable.ic_pause),
                contentDescription = null,
                tint = colors.onSurface,
                modifier = Modifier.size(iconSize)
            )
        }
        if (isPaused && onStepFrame != null) {
            Box(
                modifier = Modifier
                    .height(buttonSize)
                    .widthIn(min = buttonSize)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onStepFrame()
                    })
                    .semantics { contentDescription = uiText("1コマ送り") }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+1",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
            }
        }
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onReload()
                })
                .semantics { contentDescription = uiText(if (hasPendingChanges) "変更を実行" else "再読み込み") },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_reload),
                contentDescription = null,
                tint = if (hasPendingChanges) colors.tertiary else colors.onSurface,
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
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)
    val hasParameters = parameterCount > 0
    val borderColor = if (active) colors.primary else if (hasParameters) colors.primary.copy(alpha = 0.9f) else colors.outlineVariant
    val backgroundColor = if (active) colors.primary.copy(alpha = 0.18f) else if (hasParameters) colors.primaryContainer.copy(alpha = 0.22f) else Color.Transparent
    val contentColor = if (active || hasParameters) colors.primary else colors.onSurfaceVariant

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

    var historyExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val controlShape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier.height(height)
            .border(1.dp, colors.outlineVariant, controlShape)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TooltipIconButton(
                label = uiText("履歴"),
                onClick = { historyExpanded = true },
                shape = UNIT_COMPACT_CONTROL_SHAPE,
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = uiText("履歴"),
                    tint = colors.onSurface,
                    modifier = Modifier.size(iconSize)
                )
            }
            DropdownMenu(
                expanded = historyExpanded,
                onDismissRequest = { historyExpanded = false }
            ) {
                if (onSnapshot != null) {
                    DropdownMenuItem(
                        text = { Text(uiText("スナップショット")) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_snapshot), contentDescription = null) },
                        onClick = { historyExpanded = false; onSnapshot() }
                    )
                }
                DropdownMenuItem(
                    text = { Text(uiText("保存済み状態に戻す")) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_restore), contentDescription = null) },
                    onClick = { historyExpanded = false; onRestore() }
                )
            }
        }
        Box(contentAlignment = Alignment.Center) {
            TooltipIconButton(
                label = uiText("作品の全ファイルを保存"),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSave()
                },
                shape = UNIT_COMPACT_CONTROL_SHAPE,
                modifier = Modifier.size(buttonSize)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_save),
                    contentDescription = uiText("作品の全ファイルを保存"),
                    tint = if (hasUnsavedChanges) colors.primary else colors.onSurface,
                    modifier = Modifier.size(iconSize)
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = hasUnsavedChanges,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = scaleIn(tween(160)) + fadeIn(tween(160)),
                exit = scaleOut(tween(160)) + fadeOut(tween(160))
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp, end = 3.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.primary)
                )
            }
        }
    }
}

@Composable
private fun PreviewHudOverlay(
    hudMetricsProvider: () -> PreviewHudMetrics?,
    isPaused: Boolean,
    colors: ColorScheme,
    modifier: Modifier = Modifier
) {
    val hudMetrics = hudMetricsProvider() ?: return
    val sizePart = if (hudMetrics.width > 0 && hudMetrics.height > 0) {
        " · ${hudMetrics.width}×${hudMetrics.height}"
    } else ""
    val fpsPart = if (hudMetrics.paused || isPaused) "PAUSE" else "${hudMetrics.fps.roundToInt()} fps"
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (hudMetrics.paused || isPaused) colors.tertiary else Color(0xFF4ADE80))
            )
            Text(
                text = "$fpsPart · #${hudMetrics.frameCount}$sizePart",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LiveRecordingStatus(
    format: String,
    elapsedProvider: () -> Long,
    limitMillis: Long,
    saving: Boolean,
    text: (String) -> String,
    modifier: Modifier = Modifier,
    onStop: () -> Unit
) {
    val elapsed = elapsedProvider()
    RecordingStatus(
        format = format,
        elapsed = elapsed,
        remaining = (limitMillis - elapsed).coerceAtLeast(0),
        saving = saving,
        text = text,
        modifier = modifier,
        onStop = onStop
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BoxScope.PreviewOverlayControls(
    fullscreen: Boolean,
    showExpandControl: Boolean,
    isLandscape: Boolean,
    isRecordingOrCountingDown: Boolean,
    recordingCountdownRemaining: Int,
    pendingRecordingFormat: String?,
    isPreviewRecording: Boolean,
    isRecordingSaving: Boolean,
    recordingFormatLabel: String,
    recordingElapsedMillis: Long = 0L,
    recordingElapsedMillisProvider: () -> Long = { recordingElapsedMillis },
    recordingLimitMillis: Long,
    previewActionsExpanded: Boolean,
    onTogglePreviewActions: () -> Unit,
    onClosePreviewActions: () -> Unit,
    onRotate: () -> Unit,
    onScreenshot: () -> Unit,
    onShareCard: () -> Unit = {},
    onOpenParameters: () -> Unit,
    onToggleRecording: () -> Unit,
    onCancelCountdown: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onStopRecording: () -> Unit,
    colors: ColorScheme,
    textTranslator: (String, Array<out Any?>) -> String,
    showHud: Boolean = false,
    hudMetrics: PreviewHudMetrics? = null,
    hudMetricsProvider: () -> PreviewHudMetrics? = { hudMetrics },
    isPaused: Boolean = false,
    onToggleHud: (() -> Unit)? = null,
    onStepFrame: (() -> Unit)? = null,
    onTogglePause: (() -> Unit)? = null
) {
    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    if (showHud) {
        val hudModifier = Modifier
            .align(Alignment.TopStart)
            .then(
                if (fullscreen) Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                ) else Modifier
            )
            .padding(10.dp)
        PreviewHudOverlay(
            hudMetricsProvider = hudMetricsProvider,
            isPaused = isPaused,
            colors = colors,
            modifier = hudModifier
        )
    }

    @Composable
    fun PreviewActionButtonItems() {
        if (fullscreen && onTogglePause != null) {
            PreviewOverlayButton(
                iconRes = if (isPaused) R.drawable.ic_play else R.drawable.ic_pause,
                description = uiText(if (isPaused) "再生" else "一時停止"),
                colors = colors,
                active = isPaused,
                onClick = onTogglePause
            )
        }
        if (fullscreen && isPaused && onStepFrame != null) {
            Surface(
                onClick = onStepFrame,
                modifier = Modifier.requiredSize(40.dp),
                shape = RoundedCornerShape(11.dp),
                color = colors.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, colors.primary)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.semantics { contentDescription = uiText("1コマ送り") }
                ) {
                    Text(
                        text = "+1",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary
                    )
                }
            }
        }
        if (fullscreen && onToggleHud != null) {
            PreviewOverlayButton(
                iconRes = R.drawable.ic_terminal,
                description = "FPS HUD",
                colors = colors,
                active = showHud,
                onClick = onToggleHud
            )
        }
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

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val configuration = LocalConfiguration.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    if (fullscreen) {
        Box(
            Modifier
                .matchParentSize()
                .onSizeChanged { if (it != containerSize) containerSize = it }
        )
    }
    val containerWidthDp = if (containerSize.width > 0) with(density) { containerSize.width.toDp() }
        else configuration.screenWidthDp.dp
    val containerHeightDp = if (containerSize.height > 0) with(density) { containerSize.height.toDp() }
        else configuration.screenHeightDp.dp
    val availableWidth = containerWidthDp - if (fullscreen) with(density) {
        (WindowInsets.safeDrawing.getLeft(density, direction) +
            WindowInsets.safeDrawing.getRight(density, direction)).toDp()
    } else 0.dp
    val actionButtonCount =
        5 +
        (if (fullscreen) 1 else 0) +
        (if (fullscreen && onTogglePause != null) 1 else 0) +
        (if (fullscreen && isPaused && onStepFrame != null) 1 else 0) +
        (if (fullscreen && onToggleHud != null) 1 else 0)
    val buttonsWidth = 40.dp * actionButtonCount + 7.dp * (actionButtonCount - 1).coerceAtLeast(0)
    val requiredInlineWidth = buttonsWidth + 68.dp +
        (if (showHud) 190.dp else 0.dp) +
        (if (isPreviewRecording || isRecordingSaving) 148.dp else 0.dp)
    val usePopup = availableWidth < requiredInlineWidth || containerHeightDp < 60.dp

    if (recordingCountdownRemaining > 0) {
        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(22.dp),
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            border = BorderStroke(1.dp, colors.outlineVariant)
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
                TextButton(onClick = onCancelCountdown, shape = UNIT_CONTROL_SHAPE) {
                    Text(uiText("キャンセル"), color = colors.primary)
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
                LiveRecordingStatus(
                    format = recordingFormatLabel,
                    elapsedProvider = recordingElapsedMillisProvider,
                    limitMillis = recordingLimitMillis,
                    saving = isRecordingSaving,
                    text = ::uiText,
                    modifier = Modifier.weight(1f, fill = false),
                    onStop = onStopRecording
                )
            }
            AnimatedVisibility(
                visible = previewActionsExpanded && !usePopup,
                modifier = Modifier.weight(1f, fill = false),
                enter = fadeIn(tween(130)) + expandHorizontally(
                    animationSpec = tween(210, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.End
                ),
                exit = fadeOut(tween(100)) + shrinkHorizontally(
                    animationSpec = tween(170, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.End
                )
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    PreviewActionButtonItems()
                }
            }

            if (previewActionsExpanded && usePopup) {
                val popupMaxWidth = (availableWidth - 20.dp).coerceIn(180.dp, 340.dp)
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
                        modifier = Modifier.widthIn(max = popupMaxWidth)
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            maxItemsInEachRow = 5
                        ) {
                            PreviewActionButtonItems()
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
            LiveRecordingStatus(
                format = recordingFormatLabel,
                elapsedProvider = recordingElapsedMillisProvider,
                limitMillis = recordingLimitMillis,
                saving = isRecordingSaving,
                text = ::uiText,
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
                .width(18.dp)
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
                            val index = LANDSCAPE_PREVIEW_SPLITS.indices.minByOrNull { kotlin.math.abs(LANDSCAPE_PREVIEW_SPLITS[it] - landscapePreviewFraction) } ?: 1
                            val next = LANDSCAPE_PREVIEW_SPLITS[(index + 1).coerceIn(0, 2)]
                            onSplitChanged(next)
                            true
                        },
                        CustomAccessibilityAction(textTranslator("エディターを広げる", emptyArray())) {
                            val index = LANDSCAPE_PREVIEW_SPLITS.indices.minByOrNull { kotlin.math.abs(LANDSCAPE_PREVIEW_SPLITS[it] - landscapePreviewFraction) } ?: 1
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
