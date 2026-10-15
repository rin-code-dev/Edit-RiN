package com.hikariatelier.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
    val minifiedCode = remember(editorText) { minifyJavaScriptForTweet(editorText) }
    val originalCount = editorText.length
    val minifiedCount = minifiedCode.length
    val reducedCount = originalCount - minifiedCount
    val isOver = minifiedCount > targetLimit
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiText("#つぶやきProcessing Minify & シェア"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = targetLimit == 140,
                        onClick = { onTargetLimitChange(140) },
                        label = { Text(uiText("140字"), style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = targetLimit == 280,
                        onClick = { onTargetLimitChange(280) },
                        label = { Text(uiText("280字"), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isOver) colors.errorContainer else Color(0xFF2E7D32).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isOver) {
                            uiText("%s字超過 (%s / %s 字)", minifiedCount - targetLimit, minifiedCount, targetLimit)
                        } else {
                            uiText("制限内 (%s / %s 字)", minifiedCount, targetLimit)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isOver) colors.onErrorContainer else Color(0xFF2E7D32),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = uiText("元コード: %s字", originalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        text = uiText("削減: -%s字", reducedCount),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = minifiedCode,
                    fontFamily = codeFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.onSurface
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val shareText = "$minifiedCode\n\n#つぶやきProcessing #EditRiN"
                        val clip = android.content.ClipData.newPlainText("p5.js sketch", shareText)
                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                            ?.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, uiText("クリップボードにコピーしました"), android.widget.Toast.LENGTH_SHORT).show()

                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(android.content.Intent.createChooser(sendIntent, uiText("つぶやきProcessingを共有")))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(painter = painterResource(R.drawable.ic_share_card), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(uiText("コピーして共有"), fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onApplyToEditor(minifiedCode)
                            android.widget.Toast.makeText(context, uiText("エディター本文を圧縮コードで上書きしました"), android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(uiText("エディター本文を上書き"), style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            onVerifyInPreview(minifiedCode)
                            android.widget.Toast.makeText(context, uiText("プレビューを実行しました"), android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(uiText("プレビューで確認"), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
