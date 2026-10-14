package com.hikariatelier.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
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
}
