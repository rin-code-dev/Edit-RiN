package com.hikariatelier.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.ConfigurationCompat

@Composable
internal fun EditorAuthoringDialogs(
    models: EditorModels,
    preview: PreviewController,
    activeWork: Work?,
    editorText: String,
    editingValue: TextFieldValue,
    editorFocusRequester: FocusRequester,
    codeFontFamily: FontFamily,
    workSaving: Boolean,
    showSnippets: Boolean,
    onSnippetsChange: (Boolean) -> Unit,
    showReferenceDialog: Boolean,
    onReferenceDialogChange: (Boolean) -> Unit,
    referenceSearchQuery: String,
    showScreenshotScale: Boolean,
    onScreenshotScaleChange: (Boolean) -> Unit,
    onPendingScreenshotScale: (Int) -> Unit,
    showRecordingFormatDialog: Boolean,
    onRecordingFormatDialogChange: (Boolean) -> Unit,
    isRecordingOrCountingDown: Boolean,
    mp4BitrateMbps: Int,
    onMp4BitrateChange: (Int) -> Unit,
    recordingCountdownSeconds: Int,
    onRecordingCountdownChange: (Int) -> Unit,
    onRequestPreviewRecording: (String) -> Unit,
    showColorPickerDialog: Boolean,
    onColorPickerDialogChange: (Boolean) -> Unit,
    colorPickerInitial: Color,
    activeColorTarget: EditorColorTarget?,
    onLastColorPickerColor: (Color) -> Unit,
    showUserGuide: Boolean,
    onUserGuideChange: (Boolean) -> Unit,
    applyEditorChange: (TextFieldValue) -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    val configuration = LocalConfiguration.current
    val sessionViewModel = models.session
    val workManagementViewModel = models.works
    val recordingViewModel = models.recording
    val settingsViewModel = models.settings

    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    if (showSnippets) {
        SnippetDialog(
            text = { uiText(it) },
            onInsert = { snippet ->
                if (!workSaving) {
                    val indent = editingValue.text.substring(0, editingValue.selection.min)
                        .substringAfterLast('\n').takeWhile { it == ' ' || it == '\t' }
                    val insertion = snippet.code.replace("\n", "\n$indent") + "\n$indent"
                    applyEditorChange(insertAtSelection(editingValue, insertion))
                    workManagementViewModel.saveCurrentWork()
                    onSnippetsChange(false)
                    editorFocusRequester.requestFocus()
                }
            },
            onDismiss = { onSnippetsChange(false) }
        )
    }

    if (showReferenceDialog) {
        val effectiveLang = resolveUiLanguage(
            settingsViewModel.appLanguage,
            ConfigurationCompat.getLocales(configuration)[0]?.language ?: "en"
        )
        P5ReferenceDialog(
            initialQuery = referenceSearchQuery,
            codeFontFamily = codeFontFamily,
            language = effectiveLang,
            textTranslator = { uiText(it) },
            onInsert = { snippetCode ->
                if (!workSaving && !sessionViewModel.editorInputLocked) {
                    if (sessionViewModel.sampleReadOnly) {
                        workManagementViewModel.requestSampleCopy()
                        onReferenceDialogChange(false)
                        return@P5ReferenceDialog
                    }
                    val indent = editingValue.text.substring(0, editingValue.selection.min)
                        .substringAfterLast('\n').takeWhile { it == ' ' || it == '\t' }
                    val insertion = snippetCode.replace("\n", "\n$indent")
                    applyEditorChange(insertAtSelection(editingValue, insertion))
                    workManagementViewModel.saveCurrentWork()
                    onReferenceDialogChange(false)
                    editorFocusRequester.requestFocus()
                }
            },
            onDismiss = { onReferenceDialogChange(false) }
        )
    }

    if (preview.screenshotBusy || recordingViewModel.screenshotSaving) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(uiText("画像を書き出し中…"))
                }
            }
        }
    }

    if (showScreenshotScale) {
        ScreenshotScaleDialog(
            text = { uiText(it) },
            onCapture = { scale ->
                onScreenshotScaleChange(false)
                onPendingScreenshotScale(scale)
                preview.requestScreenshot(scale = scale)
            },
            onDismiss = { onScreenshotScaleChange(false) }
        )
    }

    if (showRecordingFormatDialog && !isRecordingOrCountingDown) {
        RecordingOptionsSheet(
            bitrate = mp4BitrateMbps,
            countdown = recordingCountdownSeconds,
            text = ::uiText,
            onBitrate = onMp4BitrateChange,
            onCountdown = onRecordingCountdownChange,
            onDismiss = { onRecordingFormatDialogChange(false) },
            onStart = { format ->
                onRecordingFormatDialogChange(false)
                onRequestPreviewRecording(format)
            }
        )
    }

    if (showColorPickerDialog) {
        EditorColorPickerDialog(
            initialColor = colorPickerInitial,
            target = activeColorTarget,
            onDismiss = { onColorPickerDialogChange(false) },
            onApply = { newColor ->
                onLastColorPickerColor(newColor)
                val target = activeColorTarget
                if (target != null) {
                    applyEditorChange(replaceColorTarget(editingValue, target, newColor))
                } else {
                    applyEditorChange(insertColorAtCursor(editingValue, newColor))
                }
                editorFocusRequester.requestFocus()
            },
            text = ::uiText
        )
    }

    if (showUserGuide) {
        Dialog(
            onDismissRequest = { onUserGuideChange(false) },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            val deviceLanguage = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language ?: "en"
            UserGuideScreen(
                language = resolveUiLanguage(settingsViewModel.appLanguage, deviceLanguage),
                onClose = { onUserGuideChange(false) }
            )
        }
    }

    val tourDue = onboardingDue(
        settingsViewModel.onboardingVersion,
        loadFailed = workManagementViewModel.loadFailure != null,
        hasPendingDraft = workManagementViewModel.pendingDraft != null
    )
    if (tourDue || settingsViewModel.onboardingReplay) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
                dismissOnClickOutside = false)
        ) {
            val deviceLanguage = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language ?: "en"
            OnboardingTour(
                language = resolveUiLanguage(settingsViewModel.appLanguage, deviceLanguage),
                onFinish = { settingsViewModel.completeOnboarding() },
                onSkip = { settingsViewModel.completeOnboarding() },
                onOpenDetailedGuide = {
                    settingsViewModel.completeOnboarding()
                    onUserGuideChange(true)
                },
                allowSkip = settingsViewModel.onboardingReplay
            )
        }
    }

    if (sessionViewModel.showMinifySheet) {
        TweetMinifyShareSheet(
            editorText = editingValue.text,
            targetLimit = sessionViewModel.tweetTargetLimit,
            codeFontFamily = codeFontFamily,
            onTargetLimitChange = { sessionViewModel.tweetTargetLimit = it },
            onApplyToEditor = { minified ->
                applyEditorChange(TextFieldValue(minified))
            },
            onVerifyInPreview = { minified ->
                preview.runSketch(source = minified)
            },
            onDismiss = { sessionViewModel.showMinifySheet = false },
            textTranslator = { s, args -> uiText(s, *args) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TweetMinifyShareSheet(
    editorText: String,
    targetLimit: Int,
    codeFontFamily: FontFamily,
    onTargetLimitChange: (Int) -> Unit,
    onApplyToEditor: (String) -> Unit,
    onVerifyInPreview: (String) -> Unit,
    onDismiss: () -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    fun uiText(source: String, vararg args: Any?) = textTranslator(source, args)
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = MaterialTheme.colorScheme

    var embedHashtagInCall by remember { mutableStateOf(true) }
    var preferNewlines by remember { mutableStateOf(true) }

    val golfOptions = remember(embedHashtagInCall, preferNewlines) {
        TweetGolfOptions(
            embedHashtagInCall = embedHashtagInCall,
            preferNewlines = preferNewlines,
            aggressiveGolf = true
        )
    }

    val minifiedCode = remember(editorText, golfOptions) {
        minifyJavaScriptForTweet(editorText, golfOptions)
    }

    val originalCount = editorText.length
    val minifiedCount = minifiedCode.length
    val savedCount = (originalCount - minifiedCount).coerceAtLeast(0)
    val savedPercent = if (originalCount > 0) ((savedCount * 100f) / originalCount).toInt() else 0
    val isOver = minifiedCount > targetLimit
    val statusColor = if (isOver) colors.error else Color(0xFF10B981)
    val progressFraction = (minifiedCount.toFloat() / targetLimit.toFloat()).coerceIn(0f, 1f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Row: Big Counter + 140/280 Toggle (Never wraps)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$minifiedCount",
                        fontFamily = codeFontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "/ $targetLimit",
                        fontFamily = codeFontFamily,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "(-$savedPercent%)",
                        fontFamily = codeFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Fixed single-line 140 / 280 switch
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.dp, colors.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(140, 280).forEach { limit ->
                        val selected = targetLimit == limit
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) colors.primary else Color.Transparent)
                                .clickable { onTargetLimitChange(limit) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$limit",
                                fontFamily = codeFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Thin Progress Bar + Compact Mode Pills
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = statusColor,
                trackColor = colors.outlineVariant.copy(alpha = 0.3f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pillShape = RoundedCornerShape(8.dp)
                listOf(
                    (embedHashtagInCall to "noStroke`#...`") to { embedHashtagInCall = !embedHashtagInCall },
                    (preferNewlines to "改行優先") to { preferNewlines = !preferNewlines }
                ).forEach { (pair, onClick) ->
                    val (active, label) = pair
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(pillShape)
                            .background(if (active) colors.primary.copy(alpha = 0.14f) else colors.surfaceVariant.copy(alpha = 0.4f))
                            .border(
                                1.dp,
                                if (active) colors.primary.copy(alpha = 0.6f) else colors.outlineVariant.copy(alpha = 0.5f),
                                pillShape
                            )
                            .clickable { onClick() }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = codeFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) colors.primary else colors.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Code Output Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, colors.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = minifiedCode,
                    fontFamily = codeFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp,
                    color = colors.onSurface
                )
            }

            // Actions: Share (Primary) + Apply / Preview (Secondary Row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val clip = android.content.ClipData.newPlainText("p5.js tweet sketch", minifiedCode)
                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                            ?.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, uiText("コピーしました"), android.widget.Toast.LENGTH_SHORT).show()

                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, minifiedCode)
                        }
                        context.startActivity(android.content.Intent.createChooser(sendIntent, uiText("共有")))
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(painter = painterResource(R.drawable.ic_share_card), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(uiText("コピー & 共有"), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }

                OutlinedButton(
                    onClick = {
                        onApplyToEditor(minifiedCode)
                        onDismiss()
                    },
                    modifier = Modifier.weight(0.9f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(uiText("本文に反映"), fontSize = 12.sp, maxLines = 1, softWrap = false)
                }

                OutlinedButton(
                    onClick = {
                        onVerifyInPreview(minifiedCode)
                    },
                    modifier = Modifier.weight(0.75f),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(uiText("試す"), fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
