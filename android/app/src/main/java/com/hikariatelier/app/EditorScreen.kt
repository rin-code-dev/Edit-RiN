package com.hikariatelier.app

import android.app.Activity
import android.graphics.Bitmap
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.core.os.ConfigurationCompat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreen(
    models: EditorModels,
    preview: PreviewController,
    mediaActions: PreviewMediaActions,
    onOpenExternalUrl: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context as ComponentActivity
    val lifecycle = activity.lifecycle
    val lifecycleScope = activity.lifecycleScope
    val applicationContext = context.applicationContext
    val cacheDir = context.cacheDir
    val assets = context.assets
    val assetStorage = preview.assetStorage
    val sessionViewModel = models.session
    val updateViewModel = models.update
    val searchReplaceViewModel = models.search
    val consoleViewModel = models.console
    val settingsViewModel = models.settings
    val recordingViewModel = models.recording
    val workManagementViewModel = models.works
    val snapshotViewModel = models.snapshots
    val themeMode = settingsViewModel.themeMode
    val customFontFamily = settingsViewModel.customFontFamily
    val importedFontName = settingsViewModel.importedFontName
    val fontImportBusy = settingsViewModel.fontImportBusy
    val appLanguage = settingsViewModel.appLanguage
    val sampleLanguage = resolveUiLanguage(appLanguage,
        ConfigurationCompat.getLocales(configuration)[0]?.language ?: "en")
    LaunchedEffect(sampleLanguage, workManagementViewModel.officialSamples) {
        workManagementViewModel.updateSampleLanguage(sampleLanguage)
    }
    val codeFontFamily = customFontFamily ?: FontFamily.Monospace
    val fontFeatures = if (settingsViewModel.fontLigatures)
        "'liga' 1, 'clig' 1, 'calt' 1" else "'liga' 0, 'clig' 0, 'calt' 0"
    fun uiText(source: String, vararg arguments: Any?): String {
        val deviceLanguage = ConfigurationCompat.getLocales(configuration)[0]?.language ?: "en"
        val translated = translateUi(source, resolveUiLanguage(settingsViewModel.appLanguage, deviceLanguage))
        return if (arguments.isEmpty()) translated else String.format(java.util.Locale.ROOT, translated, *arguments)
    }
    val focusManager =
        LocalFocusManager.current

    val view =
        LocalView.current

    val isLandscape =
        configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

    val wideWorkPanels = configuration.screenWidthDp >= 640 &&
        configuration.fontScale <= 1.25f

    val colors =
        MaterialTheme.colorScheme

    val hapticFeedback =
        LocalHapticFeedback.current

    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) settingsViewModel.importFont(applicationContext, uri)
    }

    var showStatusBar by settingsViewModel::showStatusBar

    var landscapeUseCutout by settingsViewModel::landscapeUseCutout
    // Own cutout avoidance in Compose, including when system bars are hidden.
    val appInsets = WindowInsets.statusBars.union(WindowInsets.navigationBars).union(
        if (isLandscape && landscapeUseCutout) WindowInsets(0, 0, 0, 0)
        else WindowInsets.displayCutout
    )

    var manualRotation by settingsViewModel::manualRotation

    EditorWindowEffects(isLandscape, manualRotation, showStatusBar, settingsViewModel.showNavigationBar)

    val selectedFolderUri = workManagementViewModel.selectedFolderUri

    val works by sessionViewModel.worksState
    val activeWorkId by sessionViewModel.activeWorkIdState
    var editorValue by sessionViewModel.editorValueState
    val editorText =
        editorValue.text

    val activeWork =
        works.find {
            it.id == activeWorkId
        } ?: works.firstOrNull()

    // Read observable drafts in composition and pass an immutable source snapshot to the worker.
    val parameterSources = projectSearchSources(activeWorkId, editorText,
        activeWork?.files.orEmpty(), sessionViewModel.fileDrafts)
    val liveParameterCount by key(activeWorkId) {
        produceState(initialValue = 0, key1 = parameterSources) {
            if (parameterSources.values.sumOf { it.length } >= 8_000) kotlinx.coroutines.delay(120)
            value = withContext(Dispatchers.Default) { workParameters(parameterSources).size }
        }
    }

    var isError by preview::isError

    var isPaused by preview::isPaused

    var editorFocused by remember {
        mutableStateOf(false)
    }

    var showSettings by rememberSaveable {
        mutableStateOf(false)
    }

    var showSnippets by rememberSaveable(activeWorkId) { mutableStateOf(false) }
    var showReferenceDialog by rememberSaveable(activeWorkId) { mutableStateOf(false) }
    var referenceSearchQuery by rememberSaveable(activeWorkId) { mutableStateOf("") }
    var showScreenshotScale by rememberSaveable { mutableStateOf(false) }
    var pendingScreenshotScale by rememberSaveable { mutableIntStateOf(1) }
    val screenshotPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) preview.requestScreenshot(scale = pendingScreenshotScale)
        else Toast.makeText(context, uiText("画像を保存するにはストレージへのアクセスを許可してください"), Toast.LENGTH_LONG).show()
    }

    var showUserGuide by rememberSaveable {
        mutableStateOf(false)
    }

    var showAddDialog by workManagementViewModel::showAddDialog
    var showDeleteDialog by workManagementViewModel::showDeleteDialog
    var showRenameDialog by workManagementViewModel::showRenameDialog
    var workMenuExpanded by workManagementViewModel::workMenuExpanded

    val workGalleryState = rememberWorkGalleryState()
    val galleryTarget = workMenuExpanded
    val galleryProgress by animateFloatAsState(
        targetValue = if (galleryTarget) 1f else 0f,
        animationSpec = tween(GALLERY_MOTION_DURATION_MS, easing = androidx.compose.animation.core.LinearEasing),
        label = "inlineWorkGallery"
    )
    val galleryPresent = galleryTarget || galleryProgress > 0f
    val galleryHandoffAlpha by animateFloatAsState(
        if (galleryPresent) 1f else 0f,
        animationSpec = tween(if (galleryPresent) 0 else 120), label = "galleryPreviewHandoff")
    val keyboardController = LocalSoftwareKeyboardController.current
    val galleryDensity = LocalDensity.current
    var galleryPreviewBitmap by remember(isLandscape, galleryDensity) { mutableStateOf<Bitmap?>(null) }
    var galleryPreviewBounds by remember(isLandscape, galleryDensity) { mutableStateOf<Rect?>(null) }
    var galleryThumbnailBounds by remember(isLandscape, galleryDensity) { mutableStateOf<Rect?>(null) }
    var galleryPreviewOwner by remember(isLandscape) { mutableStateOf<String?>(null) }
    var galleryPreviewToken by remember(isLandscape) { mutableStateOf<String?>(null) }
    var gallerySelectionPrepared by remember(isLandscape) { mutableStateOf(false) }

    var showConsole by consoleViewModel::showConsole
    var showSearchDialog by searchReplaceViewModel::showSearchDialog

    var showAssets by rememberSaveable { mutableStateOf(false) }
    var assetBusy by sessionViewModel::assetBusy
    var assetTargetId by rememberSaveable { mutableStateOf<String?>(null) }

    var showP5Import by workManagementViewModel::showP5Import
    var p5Username by settingsViewModel::p5Username
    val p5Sketches by workManagementViewModel::p5Sketches
    val p5Busy by workManagementViewModel::p5Busy
    var p5Error by workManagementViewModel::p5Error

    var showProjectFilesDialog by workManagementViewModel::showProjectFilesDialog
    var showHistoryDialog by workManagementViewModel::showHistoryDialog
    var showSnapshotSheet by workManagementViewModel::showSnapshotSheet

    var showColorPickerDialog by remember {
        mutableStateOf(false)
    }
    var activeColorTarget by remember {
        mutableStateOf<EditorColorTarget?>(null)
    }
    var colorPickerInitial by remember {
        mutableStateOf(Color(0xFFE91E63))
    }
    var lastColorPickerColor by remember {
        mutableStateOf(Color(0xFFE91E63))
    }

    val snapshotBusy = sessionViewModel.snapshotOperationWorkId != null
    LaunchedEffect(activeWorkId) { snapshotViewModel.migrateLegacy() }
    LaunchedEffect(showSnapshotSheet, activeWorkId, sessionViewModel.snapshotOperationWorkId) {
        if (showSnapshotSheet && !snapshotBusy) snapshotViewModel.load()
    }

    var showAspectRatioDialog by workManagementViewModel::showAspectRatioDialog
    var showRuntimeDialog by workManagementViewModel::showRuntimeDialog
    var showParameterSheet by remember { mutableStateOf(false) }


    val previewRatioSelection = workManagementViewModel.previewRatio(activeWork)
    val devicePreviewRatio = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp
    ) {
        configuration.screenWidthDp.toFloat() /
            configuration.screenHeightDp.coerceAtLeast(1).toFloat()
    }

    var portraitRatioDragging by remember {
        mutableStateOf(false)
    }

    var landscapePreviewFraction by settingsViewModel::landscapePreviewFraction

    var showLandscapeSplitLabel by remember {
        mutableStateOf(false)
    }


    var showExpandedPreview by rememberSaveable {
        mutableStateOf(false)
    }
    var expandedCanvasSwapped by rememberSaveable { mutableStateOf(false) }

    var isPreviewRecording by recordingViewModel::isPreviewRecording
    var showRecordingFormatDialog by remember {
        mutableStateOf(false)
    }
    var recordingFormatLabel by recordingViewModel::recordingFormatLabel
    var recordingLimitMillis by recordingViewModel::recordingLimitMillis
    var recordingElapsedMillis by recordingViewModel::recordingElapsedMillis
    var isRecordingSaving by recordingViewModel::isRecordingSaving
    var mp4BitrateMbps by settingsViewModel::mp4BitrateMbps
    var recordingCountdownSeconds by settingsViewModel::recordingCountdownSeconds
    var pendingRecordingFormat by recordingViewModel::pendingRecordingFormat
    var recordingCountdownRemaining by recordingViewModel::recordingCountdownRemaining
    val isRecordingOrCountingDown = isPreviewRecording || isRecordingSaving || pendingRecordingFormat != null

    fun startSelectedRecording(format: String) {
        recordingViewModel.startSelectedRecording(format, mp4BitrateMbps) { js ->
            preview.webView?.evaluateJavascript(js, null)
        }
    }

    fun requestPreviewRecording(format: String) {
        recordingViewModel.requestPreviewRecording(format, recordingCountdownSeconds, mp4BitrateMbps) { js ->
            preview.webView?.evaluateJavascript(js, null)
        }
    }

    fun cancelRecordingCountdown() {
        recordingViewModel.cancelRecordingCountdown()
    }

    EditorRecordingEffects(recordingViewModel, ::startSelectedRecording)

    var previewActionsExpanded by remember {
        mutableStateOf(false)
    }

    fun stopPreviewRecording() {
        preview.webView?.evaluateJavascript("window.__editKiroStopRecording?.()", null)
    }

    fun togglePreviewRecording() {
        if (pendingRecordingFormat != null) {
            cancelRecordingCountdown()
        } else if (isRecordingSaving) {
            // Wait for the current recording to finish saving.
        } else if (isPreviewRecording) {
            stopPreviewRecording()
        } else {
            showRecordingFormatDialog = true
        }
        previewActionsExpanded = false
    }

    fun togglePreviewPlayback() {
        isPaused = !isPaused
        preview.webView?.evaluateJavascript(
            if (isPaused) "pauseSketch()" else "resumeSketch()", null
        )
    }

    fun showScreenshotOptions() {
        showScreenshotScale = true
        previewActionsExpanded = false
    }



    fun openPreviewParameters() {
        previewActionsExpanded = false
        showExpandedPreview = false
        showParameterSheet = true
    }

    LaunchedEffect(showExpandedPreview) {
        previewActionsExpanded = false
        if (!showExpandedPreview && expandedCanvasSwapped) {
            preview.webView?.evaluateJavascript("window.__editKiroSetCanvasSwapped?.(false)", null)
            expandedCanvasSwapped = false
        }
    }

    var normalPreviewSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    var showAuxiliaryFileEditor by workManagementViewModel::showAuxiliaryFileEditor

    var auxiliaryFileName by workManagementViewModel::auxiliaryFileName

    var originalAuxiliaryFileName by workManagementViewModel::originalAuxiliaryFileName

    var auxiliaryFileContent by workManagementViewModel::auxiliaryFileContent

    val consoleEntries = consoleViewModel.entries

    val editorFocusRequester =
        remember {
            FocusRequester()
        }


    val hasUnsavedChanges by remember(activeWorkId) {
        derivedStateOf {
            val selected = sessionViewModel.activeWorkIdState.value
            val saved = sessionViewModel.worksState.value.firstOrNull { it.id == selected }
            sessionViewModel.editorValueState.value.text != sessionViewModel.lastSavedTextState.value ||
                saved?.files?.any { (name, code) -> sessionViewModel.fileDrafts["$selected/$name"]?.let { it != code } == true } == true ||
                workManagementViewModel.hasPendingMetadata(selected)
        }
    }

    var selectedEditorFile by remember(activeWorkId, sessionViewModel.auxiliaryEditorGeneration) {
        sessionViewModel.editorFileState(activeWorkId)
    }
    val editingFile = selectedEditorFile.takeIf { it in activeWork?.files.orEmpty() } ?: "sketch.js"
    val editingJavaScript = isJavaScriptProjectFile(editingFile)
    val editingKey = "$activeWorkId/$editingFile"
    val editingState = if (editingFile == "sketch.js") sessionViewModel.editorValueState else
        remember(editingKey, sessionViewModel.auxiliaryEditorGeneration) {
            val state = sessionViewModel.fileEditorValues.getOrPut(editingKey) {
                mutableStateOf(TextFieldValue(sessionViewModel.fileDrafts[editingKey]
                    ?: activeWork?.files?.get(editingFile).orEmpty()))
            }
            object : androidx.compose.runtime.MutableState<TextFieldValue> {
                override var value: TextFieldValue
                    get() = state.value
                    set(next) {
                        if (next.text != state.value.text) sessionViewModel.fileDrafts[editingKey] = next.text
                        state.value = next
                    }
                override fun component1() = value
                override fun component2(): (TextFieldValue) -> Unit = { value = it }
            }
        }
    var editingValue by editingState
    val editingText = editingValue.text
    val inlineSearchVisible = showSearchDialog && !searchReplaceViewModel.searchWholeWork && !galleryPresent
    var searchHighlightResult by remember(editingKey) { mutableStateOf<Pair<String, List<IntRange>>?>(null) }
    val previewHasChanges by remember(preview, activeWorkId) {
        derivedStateOf { !previewRunMatches(preview.runInput,
            sessionViewModel.worksState.value.firstOrNull { it.id == sessionViewModel.activeWorkIdState.value },
            sessionViewModel.editorValueState.value.text, sessionViewModel.fileDrafts) }
    }
    val undoStack = if (editingFile == "sketch.js") sessionViewModel.undoStack else
        remember(editingKey, sessionViewModel.auxiliaryEditorGeneration) {
            sessionViewModel.fileUndoStacks.getOrPut(editingKey) { mutableStateListOf() }
        }
    val redoStack = if (editingFile == "sketch.js") sessionViewModel.redoStack else
        remember(editingKey, sessionViewModel.auxiliaryEditorGeneration) {
            sessionViewModel.fileRedoStacks.getOrPut(editingKey) { mutableStateListOf() }
        }
    var pendingRevision by remember { mutableStateOf<WorkRevision?>(null) }
    var consoleExpanded by consoleViewModel::consoleExpanded
    val workSaving = workManagementViewModel.workSaving
    var previewAsset by remember { mutableStateOf<Pair<String, ProjectAsset>?>(null) }
    if (workSaving && workManagementViewModel.showBlockingProgress) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(uiText("保存中"))
                }
            }
        }
    }


    val currentWordOrSelection = {
        if (!editingValue.selection.collapsed) {
            val selected = editingValue.text.substring(editingValue.selection.min, editingValue.selection.max).trim()
            if (selected.isNotEmpty() && selected.length <= 30 && !selected.contains('\n')) selected else ""
        } else {
            val cursor = editingValue.selection.start.coerceIn(0, editingValue.text.length)
            var start = cursor
            while (start > 0 && (editingValue.text[start - 1].isLetterOrDigit() || editingValue.text[start - 1] == '_' || editingValue.text[start - 1] == '$')) start--
            var end = cursor
            while (end < editingValue.text.length && (editingValue.text[end].isLetterOrDigit() || editingValue.text[end] == '_' || editingValue.text[end] == '$')) end++
            val word = editingValue.text.substring(start, end).trim()
            if (word.length in 2..30) word else ""
        }
    }

    fun applyEditorChange(
        nextValue: TextFieldValue
    ) {
        if (sessionViewModel.editorInputLocked) return
        if (sessionViewModel.sampleReadOnly) { workManagementViewModel.requestSampleCopy(); return }
        sessionViewModel.applyChange(editingValue, nextValue, undoStack, redoStack)
        editingValue = nextValue
    }

    val formatScope = rememberCoroutineScope()
    val currentEditingKey by rememberUpdatedState(editingKey)
    var formatBusy by remember { mutableStateOf(false) }

    fun formatEditingFile() {
        if (!editingJavaScript) {
            Toast.makeText(context, uiText("JavaScriptファイルを選択してください"), Toast.LENGTH_LONG).show()
            return
        }
        if (formatBusy || sessionViewModel.editorInputLocked) return
        if (sessionViewModel.sampleReadOnly) { workManagementViewModel.requestSampleCopy(); return }
        val original = editingValue
        val originalKey = editingKey
        formatBusy = true
        formatScope.launch {
            try {
                val (result, selection) = withContext(Dispatchers.Default) {
                    val result = formatJavaScriptDetailed(original.text)
                    val selection = if (result is FormatResult.Success) TextRange(
                        formattedJavaScriptOffset(original.text, result.code, original.selection.start),
                        formattedJavaScriptOffset(original.text, result.code, original.selection.end)
                    ) else original.selection
                    result to selection
                }
                // Do not apply a stale result after typing, cursor movement or work/file switching.
                if (currentEditingKey != originalKey || editingValue != original ||
                    sessionViewModel.editorInputLocked || sessionViewModel.sampleReadOnly) return@launch
                when (result) {
                    is FormatResult.Success -> {
                        if (result.code == original.text) {
                            Toast.makeText(context, uiText("既に整形されています"), Toast.LENGTH_SHORT).show()
                        } else {
                            applyEditorChange(TextFieldValue(result.code, selection))
                            Toast.makeText(context, uiText("コードを整形しました"), Toast.LENGTH_SHORT).show()
                        }
                    }
                    is FormatResult.UnbalancedBrackets ->
                        Toast.makeText(context, uiText("括弧の対応を確認してください"), Toast.LENGTH_SHORT).show()
                    is FormatResult.UnfinishedString ->
                        Toast.makeText(context, uiText("文字列の閉じクォートを確認してください"), Toast.LENGTH_SHORT).show()
                }
                editorFocusRequester.requestFocus()
            } finally {
                formatBusy = false
            }
        }
    }

    fun undoEditorChange() {
        if (sessionViewModel.editorInputLocked) return
        if (sessionViewModel.sampleReadOnly) { workManagementViewModel.requestSampleCopy(); return }
        val restored = sessionViewModel.undo(editingValue, undoStack, redoStack) ?: return
        editingValue = restored
        editorFocusRequester.requestFocus()
    }

    fun redoEditorChange() {
        if (sessionViewModel.editorInputLocked) return
        if (sessionViewModel.sampleReadOnly) { workManagementViewModel.requestSampleCopy(); return }
        val restored = sessionViewModel.redo(editingValue, undoStack, redoStack) ?: return
        editingValue = restored
        editorFocusRequester.requestFocus()
    }

    BackHandler(enabled = editorFocused && !galleryPresent && !showSettings && !showUserGuide) {
        focusManager.clearFocus(force = true)
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showUserGuide) { showUserGuide = false }

    var autoRun by settingsViewModel::autoRun

    var hideEditingPreview by settingsViewModel::hideEditingPreview

    val keyboardInsets = WindowInsets.ime
    val keyboardDensity = LocalDensity.current
    val keyboardVisible by remember(keyboardInsets, keyboardDensity) {
        derivedStateOf { keyboardInsets.getBottom(keyboardDensity) > 0 }
    }
    var keyboardWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible) {
        if (keyboardWasVisible && !keyboardVisible && hideEditingPreview && !isLandscape) {
            focusManager.clearFocus(force = true)
        }
        keyboardWasVisible = keyboardVisible
    }

    var compactPreview by settingsViewModel::compactPreview

    var showResizeHandles by settingsViewModel::showResizeHandles

    var preserveExpandedPreview by settingsViewModel::preserveExpandedPreview

    var editorFontSize by settingsViewModel::editorFontSize

    var autoIndent by settingsViewModel::autoIndent

    var showLineNumbers by settingsViewModel::showLineNumbers

    var editorWordWrap by settingsViewModel::editorWordWrap

    var landscapeEditorOnLeft by settingsViewModel::landscapeEditorOnLeft

    var showEditorAccessoryBar by settingsViewModel::showEditorAccessoryBar

    var codeCompletion by settingsViewModel::codeCompletion

    var showAccessoryNavigation by settingsViewModel::showAccessoryNavigation

    var showAccessorySymbols by settingsViewModel::showAccessorySymbols

    var compactAccessoryKeys by settingsViewModel::compactAccessoryKeys

    var draftRecovery by settingsViewModel::draftRecovery

    LaunchedEffect(
        editorText,
        activeWorkId,
        sessionViewModel.fileDrafts.hashCode(),
        draftRecovery
    ) {

        if (
            !draftRecovery ||
            activeWorkId.isBlank()
        ) {
            return@LaunchedEffect
        }

        kotlinx.coroutines.delay(
            700L
        )

        workManagementViewModel.saveDraft()
    }

    var navigationSequence by remember { mutableIntStateOf(0) }
    var navigationTarget by remember { mutableStateOf<EditorNavigationTarget?>(null) }
    var navigationRequestsFocus by remember { mutableStateOf(true) }

    fun navigateToSource(file: String, line: Int, selection: TextRange? = null) {
        if (line <= 0 || (file != "sketch.js" && file !in activeWork?.files.orEmpty())) return
        val state = if (file == "sketch.js") sessionViewModel.editorValueState else {
            val key = "$activeWorkId/$file"
            sessionViewModel.fileEditorValues.getOrPut(key) {
                mutableStateOf(TextFieldValue(sessionViewModel.fileDrafts[key]
                    ?: activeWork?.files?.get(file).orEmpty()))
            }
        }
        val offset = sourceLineOffset(state.value.text, line) ?: return
        state.value = state.value.copy(selection = selection?.takeIf {
            it.min >= 0 && it.max <= state.value.text.length
        } ?: TextRange(offset))
        selectedEditorFile = file
        navigationTarget = EditorNavigationTarget(file, line, state.value.selection)
        navigationRequestsFocus = true
        navigationSequence++
        showConsole = false
        consoleExpanded = false
    }

    fun jumpToLine(line: Int) = navigateToSource(editingFile, line)

    fun updateActiveWorkPreviewRatio(value: String, persist: Boolean = true) {
        if (persist) workManagementViewModel.savePreviewRatio(value)
        else workManagementViewModel.draftPreviewRatio(value)
    }


    fun runSketch() = preview.runSketch()

    LaunchedEffect(sessionViewModel.assetPreviewRevision) {
        if (sessionViewModel.assetPreviewRevision > 0) runSketch()
    }

    fun restoreCurrentWork() { workManagementViewModel.requestRestoreCurrentWork() }
    fun saveCurrentWork() { workManagementViewModel.saveCurrentWork() }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) workManagementViewModel.chooseFolder(uri)
    }
    val importJsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) workManagementViewModel.importJs(uri)
    }

    fun changeAssets(workId: String, updated: Map<String, ProjectAsset>) {
        workManagementViewModel.changeAssets(workId, updated)
    }
    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val targetId = assetTargetId
        assetTargetId = null
        if (targetId != null && uris.isNotEmpty()) workManagementViewModel.addAssets(targetId, uris, preview.session.assets)
    }

    val exportJsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/javascript")) { uri ->
        if (uri != null) workManagementViewModel.exportJs(uri)
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) workManagementViewModel.exportBackup(uri)
    }
    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) workManagementViewModel.importBackup(uri)
    }
    val exportWorkZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) workManagementViewModel.exportWorkZip(uri)
    }
    val importWorkZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) workManagementViewModel.importWorkZip(uri)
    }
    var pendingGalleryExportIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var pendingGalleryExportFolder by rememberSaveable { mutableStateOf<String?>(null) }
    val exportGalleryZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val ids = pendingGalleryExportIds.toSet()
        if (uri != null && ids.isNotEmpty()) {
            workManagementViewModel.exportGalleryWorks(uri, ids, pendingGalleryExportFolder)
        }
        pendingGalleryExportIds = emptyList()
        pendingGalleryExportFolder = null
    }
    val deletionSnackbar = remember { SnackbarHostState() }
    val deletionUndoToken = workManagementViewModel.deletionUndoToken
    LaunchedEffect(deletionUndoToken) {
        val token = deletionUndoToken ?: return@LaunchedEffect
        while (workManagementViewModel.deletionUndoToken == token) {
            val result = deletionSnackbar.showSnackbar(uiText("作品を削除しました"),
                actionLabel = uiText("元に戻す"), withDismissAction = true, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) {
                workManagementViewModel.undoGalleryDeletion(token)?.join()
                // Persistence failure keeps the same batch available for another attempt.
            } else {
                workManagementViewModel.dismissGalleryDeletion(token)
                break
            }
        }
    }
    val currentWorkEventHandler = rememberUpdatedState<(WorkEvent) -> Unit> { event ->
        if (event.haptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        event.message?.let { Toast.makeText(context, uiText(it), Toast.LENGTH_LONG).show() }
        if (event.closeAuxiliary) showAuxiliaryFileEditor = false
        if (event.closeHistory) { pendingRevision = null; showHistoryDialog = false }
        if (event.openSettings) showSettings = true
        if (event.openFolder) folderLauncher.launch(selectedFolderUri)
        if (event.forceRun || (event.rerun && autoRun)) runSketch()
    }
    LaunchedEffect(workManagementViewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            workManagementViewModel.events.collect { currentWorkEventHandler.value(it) }
        }
    }
    LaunchedEffect(activeWorkId) { RecentWorks.touch(context, activeWorkId) }
    val autoSaveOnLeave by settingsViewModel::autoSaveOnLeave
    val currentAutoSaveOnLeave = rememberUpdatedState(autoSaveOnLeave)
    val currentHasUnsavedChanges = rememberUpdatedState(hasUnsavedChanges)
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && currentAutoSaveOnLeave.value &&
                currentHasUnsavedChanges.value && !sessionViewModel.sampleReadOnly) {
                workManagementViewModel.saveCurrentWork(blockUi = false)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(snapshotViewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotViewModel.notices.events.collect { notice ->
                if (notice.haptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(context, uiText(notice.text), Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(settingsViewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            settingsViewModel.notices.events.collect { notice ->
                Toast.makeText(context, uiText(notice.text, *notice.arguments.toTypedArray()), Toast.LENGTH_LONG).show()
            }
        }
    }
    LaunchedEffect(recordingViewModel) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            recordingViewModel.notices.events.collect { notice ->
                Toast.makeText(context, uiText(notice.text, *notice.arguments.toTypedArray()), Toast.LENGTH_LONG).show()
            }
        }
    }

    val workPreviewRatio = previewAspectRatioValue(previewRatioSelection, devicePreviewRatio)


    val animatedLandscapePreviewFraction by animateFloatAsState(
        targetValue = landscapePreviewFraction,
        animationSpec = tween(GALLERY_MOTION_DURATION_MS, easing = androidx.compose.animation.core.LinearEasing),
        label = "landscapePreviewSplit"
    )

    @Composable
    fun WorkSelector(modifier: Modifier = Modifier) {
        WorkSelectorChip(
            activeWorkTitle = activeWork?.title,
            hasUnsavedChanges = hasUnsavedChanges,
            isLandscape = isLandscape,
            manualRotation = manualRotation,
            colors = colors,
            textTranslator = { s, args -> uiText(s, *args) },
            onClick = { workMenuExpanded = true },
            saveLabel = when {
                workManagementViewModel.operationState == WorkOperationState.CONFLICT -> uiText("保存内容の競合")
                workManagementViewModel.operationState == WorkOperationState.FAILED -> uiText("保存失敗")
                workSaving -> uiText("処理中")
                hasUnsavedChanges -> uiText("未保存")
                workManagementViewModel.operationState == WorkOperationState.SAVED -> uiText("保存済み")
                else -> null
            },
            saveFailed = workManagementViewModel.operationState in setOf(WorkOperationState.CONFLICT, WorkOperationState.FAILED),
            onRetry = if (!workSaving && workManagementViewModel.operationState in setOf(WorkOperationState.CONFLICT, WorkOperationState.FAILED)) ({
                if (workManagementViewModel.operationState == WorkOperationState.CONFLICT) workManagementViewModel.showConflictDialog = true
                else saveCurrentWork()
            }) else null,
            modifier = modifier
        )

    }

    var initialGallerySectionSet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sessionViewModel.initialized) {
        if (sessionViewModel.initialized && !initialGallerySectionSet) {
            if (sessionViewModel.sampleReadOnly) workGalleryState.changeFolder(SAMPLE_FOLDER)
            initialGallerySectionSet = true
        }
    }

    fun closeWorkGallery() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        if (gallerySelectionPrepared && galleryPreviewOwner != activeWorkId) {
            // A failed save leaves the gallery open; backing out must show the retained work.
            galleryPreviewBitmap = null
            galleryThumbnailBounds = null
            gallerySelectionPrepared = false
        }
        workMenuExpanded = false
    }

    // Capture the renderer's owner, which can differ from the selected work when auto-run is off.
    LaunchedEffect(workMenuExpanded, isLandscape) {
        if (!workMenuExpanded) return@LaunchedEffect
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        navigationTarget = null
        navigationSequence++
        gallerySelectionPrepared = false
        galleryPreviewBitmap = null
        galleryThumbnailBounds = null
        galleryPreviewOwner = null
        galleryPreviewToken = null
        val webView = preview.webView ?: return@LaunchedEffect
        val owner = preview.session.workId ?: return@LaunchedEffect
        val token = preview.session.assets.token
        if (isError || webView.url != previewUrl(preview.session.assets)) return@LaunchedEffect
        galleryPreviewOwner = owner
        galleryPreviewToken = token
        webView.clearFocus()
        galleryPreviewBitmap = preview.images.peek(owner)
        preview.images.load(owner)?.let {
            if (!gallerySelectionPrepared && galleryPreviewOwner == owner && galleryPreviewToken == token) {
                galleryPreviewBitmap = it
            }
        }
        val capturedInput = preview.runInput?.takeIf { it.token == token }
        captureWorkPreview(webView) { encoded ->
            lifecycleScope.launch {
                if (preview.session.workId != owner || preview.session.assets.token != token) return@launch
                val fingerprint = withContext(Dispatchers.Default) { capturedInput?.let(::thumbnailFingerprint) }
                if (preview.session.workId != owner || preview.session.assets.token != token) return@launch
                val bitmap = preview.images.storeEncoded(owner, encoded, fingerprint)
                if (bitmap != null) {
                    if (galleryPreviewToken == token && workMenuExpanded) galleryPreviewBitmap = bitmap
                    workManagementViewModel.notifyPreviewUpdated(owner)
                }
            }
        }
    }

    DisposableEffect(galleryPresent, preview.generation) {
        val webView = preview.webView
        val accessibility = webView?.importantForAccessibility
        val focusable = webView?.isFocusable
        val touchFocusable = webView?.isFocusableInTouchMode
        if (galleryPresent) {
            webView?.clearFocus()
            webView?.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            webView?.isFocusable = false
        }
        onDispose {
            if (galleryPresent && webView != null && preview.webView === webView) {
                webView.importantForAccessibility = accessibility!!
                webView.isFocusable = focusable!!
                webView.isFocusableInTouchMode = touchFocusable!!
            }
        }
    }
    LaunchedEffect(galleryPresent, galleryHandoffAlpha) {
        if (!galleryPresent && galleryHandoffAlpha == 0f) galleryPreviewBitmap = null
    }
    LaunchedEffect(galleryPresent) {
        if (!galleryPresent) {
            // Preserve the filtered thumbnail's position until the closing motion finishes.
            workGalleryState.closeSearch()
            workGalleryState.finishSelection()
        }
    }
    BackHandler(enabled = galleryTarget && !keyboardVisible && !showSettings && !showUserGuide) {
        closeWorkGallery()
    }

    val liftPreviewChrome = !showExpandedPreview && galleryPreviewBounds?.usableGalleryBounds() == true &&
        (galleryPresent || galleryHandoffAlpha > 0f)
    val galleryMorphActive = galleryPresent && galleryProgress > 0f &&
        galleryProgress < 1f && galleryPreviewBitmap?.isRecycled == false &&
        galleryPreviewBounds?.usableGalleryBounds() == true &&
        galleryThumbnailBounds?.usableGalleryBounds() == true

    fun jumpToEditingWork() {
        if (assetBusy) return
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        workGalleryState.changeFolder(if (activeWork?.isSample == true) SAMPLE_FOLDER else activeWork?.folderName?.takeIf { it.isNotEmpty() })
        workGalleryState.jumpToWork(activeWorkId)
    }
    fun toggleAllGallerySelection() {
        if (assetBusy) return
        val allIds = works.filter { !it.isSample && workGalleryState.inFolder(it) &&
            (workGalleryState.selectedTag == null || it.tags.any { tag -> tag.equals(workGalleryState.selectedTag, true) }) &&
            (workGalleryState.query.isBlank() || it.title.contains(workGalleryState.query.trim(), true) ||
                it.tags.any { tag -> tag.contains(workGalleryState.query.trim().removePrefix("#"), true) }) }.map { it.id }.toSet()
        workGalleryState.selectedIds = if (workGalleryState.selectedIds == allIds) emptySet() else allIds
    }

    @Composable
    fun GalleryActions(modifier: Modifier = Modifier, buttonSize: androidx.compose.ui.unit.Dp = 38.dp) {
        WorkGalleryActions(state = workGalleryState, onDismiss = ::closeWorkGallery,
            text = { uiText(it) }, sort = settingsViewModel.workSort,
            onSort = { settingsViewModel.workSort = it }, modifier = modifier, buttonSize = buttonSize,
            busy = assetBusy, canDeleteSelection = workGalleryState.selectedIds.size < works.size,
            onMoveSelection = { workManagementViewModel.galleryMoveIds = workGalleryState.selectedIds },
            onTagSelection = {
                focusManager.clearFocus(force = true); keyboardController?.hide()
                workManagementViewModel.galleryTagIds = workGalleryState.selectedIds
            },
            onDeleteSelection = {
                focusManager.clearFocus(force = true); keyboardController?.hide()
                workManagementViewModel.galleryDeleteIds = workGalleryState.selectedIds
            },
            onExportSelection = {
                focusManager.clearFocus(force = true); keyboardController?.hide()
                pendingGalleryExportIds = workGalleryState.selectedIds.toList()
                pendingGalleryExportFolder = selectedFolderUri?.toString()
                exportGalleryZip.launch("Edit-RiN-works.zip")
            })
    }

    @Composable
    fun InlineGallery(modifier: Modifier) {
        InlineWorkGallery(
            works = works, activeId = activeWorkId, unsaved = hasUnsavedChanges,
            sort = settingsViewModel.workSort, cacheDir = cacheDir,
            previewRevision = workManagementViewModel.previewRevision,
            updatedPreviewId = workManagementViewModel.updatedPreviewId,
            previewRevisions = workManagementViewModel.previewRevisions,
            images = preview.images,
            onThumbnailScrolling = {
                workManagementViewModel.thumbnailScrolling = it
                preview.performance.galleryScrolling(it)
            },
            onThumbnailPriority = workManagementViewModel::prioritizeThumbnails,
            text = { uiText(it) },
            onOpen = { work, openMenu ->
                if (workSaving || (!assetBusy && sessionViewModel.snapshotOperationWorkId == null)) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    workManagementViewModel.selectWork(work.id, openMenu)
                }
            },
            onAdd = { workManagementViewModel.newWorkFolder = workGalleryState.selectedFolder?.takeIf { it in workManagementViewModel.galleryFolders }.orEmpty(); closeWorkGallery(); showAddDialog = true },
            state = workGalleryState,
            modifier = modifier.semantics { paneTitle = uiText("作品") },
            onTogglePin = { workManagementViewModel.togglePin(it.id) },
            onEditTags = { workManagementViewModel.editingTagsWorkId = it.id },
            onDeleteGlobalTag = { workManagementViewModel.deleteGlobalTag(it) },
            busy = assetBusy,
            navigationWhileBusy = workSaving,
            onRename = { workManagementViewModel.galleryRenameWorkId = it.id },
            onDuplicate = { if (it.isSample) workManagementViewModel.requestSampleCopy(it.id) else workManagementViewModel.duplicateGalleryWork(it.id) },
            onDelete = { workManagementViewModel.galleryDeleteIds = setOf(it.id) },
            onPrepareOpen = { work, bitmap, bounds ->
                if (workSaving || (!assetBusy && sessionViewModel.snapshotOperationWorkId == null)) {
                    // Pin the tapped card before selection replaces the live renderer.
                    gallerySelectionPrepared = true
                    galleryPreviewOwner = work.id
                    galleryPreviewToken = null
                    galleryPreviewBitmap = bitmap
                    galleryThumbnailBounds = bounds
                }
            },
            onActiveThumbnailBounds = { if (galleryTarget && !gallerySelectionPrepared) galleryThumbnailBounds = it },
            morphWorkId = galleryPreviewOwner,
            suppressMorphThumbnail = galleryMorphActive,
            morphProgress = galleryProgress,
            folders = workManagementViewModel.galleryFolders,
            tabs = workManagementViewModel.galleryTabs,
            onManageFolders = { workManagementViewModel.showGalleryFolders = true },
            onMove = { workManagementViewModel.galleryMoveIds = setOf(it.id) },
            onReorderTabs = { workManagementViewModel.reorderGalleryTabs(it) }
        )
    }

    fun editUserWork(action: () -> Unit) {
        if (sessionViewModel.sampleReadOnly) workManagementViewModel.requestSampleCopy() else action()
    }

    @Composable
    fun WorkActions() {
        WorkActionsMenu(
            activeWork = activeWork,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            canDelete = activeWork?.isSample != true && works.size > 1,
            hasUnsavedChanges = hasUnsavedChanges,
            isLandscape = isLandscape,
            manualRotation = manualRotation,
            wideWorkPanels = wideWorkPanels,
            galleryProgress = galleryProgress,
            colors = colors,
            viewModel = workManagementViewModel,
            onRotate = {
                val activity = view.context as Activity
                activity.requestedOrientation = if (isLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            },
            onOpenUserGuide = { showUserGuide = true },
            onOpenSettings = {
                showSettings = true
                workManagementViewModel.saveCurrentWork(blockUi = false)
            },
            onUndo = { undoEditorChange() },
            onRedo = { redoEditorChange() },
            onSearch = { editUserWork { searchReplaceViewModel.openSearch(editingValue) } },
            onFormat = {
                formatEditingFile()
            },
            onSnippets = {
                if (editingJavaScript) showSnippets = true
                else Toast.makeText(context, uiText("JavaScriptファイルを選択してください"), Toast.LENGTH_LONG).show()
            },
            onReference = {
                referenceSearchQuery = currentWordOrSelection()
                showReferenceDialog = true
            },
            onSnapshot = { editUserWork { showSnapshotSheet = true } },
            onHistory = { editUserWork { showHistoryDialog = true } },
            onAspectRatio = { editUserWork { showAspectRatioDialog = true } },
            onTogglePin = { editUserWork { activeWork?.let { workManagementViewModel.togglePin(it.id) } } },
            onEditTags = { editUserWork { workManagementViewModel.editingTagsWorkId = activeWork?.id } },
            onRename = { editUserWork { showRenameDialog = true } },
            onDuplicate = { editUserWork { workManagementViewModel.duplicateWork() } },
            assetBusy = assetBusy,
            onExportZip = { exportWorkZip.launch("Edit-RiN-work.zip") },
            onImportZip = { importWorkZip.launch(arrayOf("application/zip", "application/octet-stream")) },
            onImportJs = {
                importJsLauncher.launch(
                    arrayOf(
                        "application/javascript",
                        "text/javascript",
                        "text/plain",
                        "application/octet-stream"
                    )
                )
            },
            onExportJs = {
                exportJsLauncher.launch(
                    safeJsFileName(activeWork?.title ?: "sketch")
                )
            },
            onDelete = { editUserWork { showDeleteDialog = true } },
            onOpenProjectFiles = { editUserWork { showProjectFilesDialog = true } },
            onOpenAssets = { editUserWork { focusManager.clearFocus(force = true); showAssets = true } },
            onOpenRuntime = { editUserWork { showRuntimeDialog = true } },
            textTranslator = { s, args -> uiText(s, *args) },
            windowSetup = { KeepLandscapeDialogImmersive() }
        )
    }

    @Composable
    fun WorkBar() {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).padding(end = 8.dp)) {
                Box(Modifier.galleryChrome(galleryProgress, GalleryPart.TITLE)
                    .blockGalleryInput(galleryPresent)) {
                    WorkSelector()
                }
                if (galleryPresent) {
                    WorkSelectorChip(
                        activeWorkTitle = if (workGalleryState.selecting) uiText("すべて選択 / 解除") else activeWork?.title,
                        hasUnsavedChanges = false,
                        isLandscape = false, manualRotation = manualRotation, colors = colors,
                        textTranslator = { s, args -> uiText(s, *args) },
                        onClick = { if (workGalleryState.selecting) toggleAllGallerySelection() else jumpToEditingWork() },
                        labelText = if (workGalleryState.selecting)
                            "${workGalleryState.selectedIds.size} · ${uiText("選択中")}"
                            else uiText("作品") + " · " + works.size,
                        modifier = Modifier.graphicsLayer { alpha = 1f - galleryExposure(galleryProgress, GalleryPart.TITLE) }
                            .blockGalleryInput(galleryProgress < 1f)
                    )
                }
            }
            // Four fixed slots let the glyphs change without moving the toolbar.
            Box(Modifier.width(164.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.blockGalleryInput(galleryPresent)) {
                    WorkActions()
                }
                if (galleryPresent) {
                    GalleryActions(Modifier.graphicsLayer { alpha = galleryLayerAlpha(galleryProgress) }
                        .blockGalleryInput(galleryProgress < 1f))
                }
            }
        }
    }

    @Composable
    fun ParameterButton(
        modifier: Modifier = Modifier
    ) {
        com.hikariatelier.app.ParameterButton(
            parameterCount = liveParameterCount,
            colors = colors,
            height = if (isLandscape) 34.dp else 36.dp,
            minWidth = if (isLandscape) 34.dp else 36.dp,
            iconSize = if (isLandscape) 15.dp else 16.dp,
            onClick = ::openPreviewParameters,
            textTranslator = { s, args -> uiText(s, *args) },
            modifier = modifier
        )
    }

    @Composable
    fun PreviewActionsToggleButton(
        modifier: Modifier = Modifier
    ) {
        com.hikariatelier.app.PreviewActionsToggleButton(
            isRecordingOrCountingDown = isRecordingOrCountingDown,
            previewActionsExpanded = previewActionsExpanded,
            isLandscape = isLandscape,
            colors = colors,
            onClick = { previewActionsExpanded = !previewActionsExpanded },
            textTranslator = { s, args -> uiText(s, *args) },
            modifier = modifier
        )
    }

    @Composable
    fun PreviewActionsTray(
        modifier: Modifier = Modifier
    ) {
        com.hikariatelier.app.PreviewActionsTray(
            isRecordingOrCountingDown = isRecordingOrCountingDown,
            isRecordingSaving = isRecordingSaving,
            isPreviewRecording = isPreviewRecording,
            pendingRecordingFormat = pendingRecordingFormat,
            colors = colors,
            onOpenParameters = ::openPreviewParameters,
            onScreenshot = ::showScreenshotOptions,
            onRecordToggle = ::togglePreviewRecording,
            onFullscreen = {
                previewActionsExpanded = false
                focusManager.clearFocus(force = true)
                showExpandedPreview = true
            },
            textTranslator = { s, args -> uiText(s, *args) },
            modifier = modifier
        )
    }

    @Composable
    fun LandscapeGalleryBar() {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (workGalleryState.searching && !workGalleryState.selecting) {
                WorkGallerySearch(workGalleryState, { uiText(it) }, galleryProgress >= 1f,
                    Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(enabled = !assetBusy) {
                        if (workGalleryState.selecting) toggleAllGallerySelection() else jumpToEditingWork()
                    },
                    verticalArrangement = Arrangement.Center) {
                    Text(if (workGalleryState.selecting)
                        "${workGalleryState.selectedIds.size} · ${uiText("選択中")}"
                        else "${uiText("作品")} · ${works.size}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (workGalleryState.selecting) uiText("すべて選択 / 解除") else activeWork?.title.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            GalleryActions(buttonSize = 48.dp)
        }
    }

    @Composable
    fun LandscapeUnifiedBar() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            WorkSelector(
                modifier = Modifier.widthIn(min = 90.dp, max = 180.dp)
            )

            RunStatusControls(
                isError = isError,
                isPaused = isPaused,
                hasPendingChanges = previewHasChanges && !preview.isLoading,
                colors = colors,
                height = 34.dp,
                buttonSize = 32.dp,
                iconSize = 17.dp,
                onTogglePause = ::togglePreviewPlayback,
                onReload = { runSketch() },
                textTranslator = { s, args -> uiText(s, *args) }
            )

            ConsoleButton(
                hasError = consoleEntries.any { it.level == ConsoleLevel.ERROR },
                entryCount = consoleEntries.size,
                colors = colors,
                height = 34.dp,
                minWidth = 38.dp,
                iconSize = 16.dp,
                onClick = { showConsole = !showConsole },
                textTranslator = { s, args -> uiText(s, *args) }
            )

            ParameterButton()

            PreviewActionsToggleButton()

            SaveRestoreControls(
                hasUnsavedChanges = hasUnsavedChanges,
                colors = colors,
                height = 34.dp,
                buttonSize = 32.dp,
                iconSize = 18.dp,
                onSnapshot = { editUserWork { showSnapshotSheet = true } },
                onRestore = { restoreCurrentWork() },
                onSave = { saveCurrentWork() },
                textTranslator = { s, args -> uiText(s, *args) }
            )

            WorkActions()
        }
    }

    @Composable
    fun BoxWithConstraintsScope.PreviewChrome(fullscreen: Boolean = false, showExpandControl: Boolean = true) {
                PreviewOverlayControls(
                    fullscreen = fullscreen,
                    showExpandControl = showExpandControl,
                    isLandscape = isLandscape,
                    isRecordingOrCountingDown = isRecordingOrCountingDown,
                    recordingCountdownRemaining = recordingCountdownRemaining,
                    pendingRecordingFormat = pendingRecordingFormat,
                    isPreviewRecording = isPreviewRecording,
                    isRecordingSaving = isRecordingSaving,
                    recordingFormatLabel = recordingFormatLabel,
                    recordingElapsedMillis = recordingElapsedMillis,
                    recordingLimitMillis = recordingLimitMillis,
                    previewActionsExpanded = previewActionsExpanded,
                    onTogglePreviewActions = { previewActionsExpanded = !previewActionsExpanded },
                    onClosePreviewActions = { previewActionsExpanded = false },
                    onRotate = {
                        if (isRecordingOrCountingDown) {
                            Toast.makeText(context,
                                uiText("録画を停止してから描画の向きを変更してください"),
                                Toast.LENGTH_SHORT).show()
                        } else {
                            val activity = view.context as Activity
                            activity.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                        }
                        previewActionsExpanded = false
                    },
                    onScreenshot = ::showScreenshotOptions,
                    onOpenParameters = ::openPreviewParameters,
                    onToggleRecording = ::togglePreviewRecording,
                    onCancelCountdown = { cancelRecordingCountdown() },
                    onToggleFullscreen = {
                        previewActionsExpanded = false
                        if (showExpandControl) {
                            focusManager.clearFocus(force = true)
                            showExpandedPreview = true
                        } else {
                            if (isRecordingOrCountingDown) {
                                Toast.makeText(
                                    context,
                                    uiText("録画を停止してから全画面表示を閉じてください"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                showExpandedPreview = false
                            }
                        }
                    },
                    onStopRecording = ::stopPreviewRecording,
                    colors = colors,
                    textTranslator = { s, args -> uiText(s, *args) }
                )
    }

    @Composable
    fun PreviewArea(
        modifier: Modifier,
        showExpandControl: Boolean = true,
        logicalSize: IntSize? = null,
        fullscreen: Boolean = false
    ) {
        PreviewSurface(
            preview = preview, isError = isError, fullscreen = fullscreen,
            logicalSize = logicalSize, modifier = modifier,
            galleryProgress = if (fullscreen) 0f else galleryProgress,
            galleryMorphActive = !fullscreen && galleryMorphActive,
            chromeLifted = !fullscreen && liftPreviewChrome,
            onClearEditorFocus = { focusManager.clearFocus(force = true) }
        ) { PreviewChrome(fullscreen, showExpandControl) }
    }

    @Composable
    fun MainPreviewArea(
        modifier: Modifier
    ) {
        if (showExpandedPreview) {
            Box(modifier = modifier)
        } else {
            PreviewArea(
                modifier = modifier.onGloballyPositioned { coordinates ->
                    if (!galleryPresent || gallerySelectionPrepared || !galleryTarget) galleryPreviewBounds = coordinates.boundsInRoot()
                }.onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) {
                        normalPreviewSize = size
                    }
                }
            )
        }
    }

    @Composable
    fun ControlBar() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 36.dp else 46.dp)
                .padding(vertical = if (isLandscape) 2.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RunStatusControls(
                isError = isError,
                isPaused = isPaused,
                hasPendingChanges = previewHasChanges && !preview.isLoading,
                colors = colors,
                height = if (isLandscape) 32.dp else 36.dp,
                buttonSize = if (isLandscape) 26.dp else 28.dp,
                iconSize = if (isLandscape) 15.dp else 16.dp,
                onTogglePause = ::togglePreviewPlayback,
                onReload = { runSketch() },
                textTranslator = { s, args -> uiText(s, *args) }
            )

            Spacer(Modifier.width(4.dp))

            ConsoleButton(
                hasError = consoleEntries.any { it.level == ConsoleLevel.ERROR },
                entryCount = consoleEntries.size,
                colors = colors,
                height = if (isLandscape) 32.dp else 36.dp,
                minWidth = if (isLandscape) 32.dp else 36.dp,
                iconSize = if (isLandscape) 15.dp else 16.dp,
                onClick = { showConsole = !showConsole },
                textTranslator = { s, args -> uiText(s, *args) }
            )

            Spacer(Modifier.width(4.dp))

            ParameterButton()

            Spacer(Modifier.width(4.dp))

            PreviewActionsToggleButton()

            Spacer(Modifier.weight(1f))

            SaveRestoreControls(
                hasUnsavedChanges = hasUnsavedChanges,
                colors = colors,
                height = if (isLandscape) 32.dp else 36.dp,
                buttonSize = if (isLandscape) 26.dp else 28.dp,
                iconSize = if (isLandscape) 16.dp else 17.dp,
                onSnapshot = { editUserWork { showSnapshotSheet = true } },
                onRestore = { restoreCurrentWork() },
                onSave = { saveCurrentWork() },
                textTranslator = { s, args -> uiText(s, *args) }
            )
        }
    }

    @Composable
    fun ConsolePanel(modifier: Modifier = Modifier) {
        com.hikariatelier.app.ConsolePanel(
            viewModel = consoleViewModel,
            activeWorkId = activeWorkId,
            activeWorkFiles = activeWork?.files.orEmpty(),
            onNavigateToSource = { file, line -> navigateToSource(file, line) },
            codeFontFamily = codeFontFamily,
            textTranslator = { s, args -> uiText(s, *args) },
            modifier = modifier
        )
    }

    @Composable
    fun EditorAccessoryBar(
        modifier: Modifier = Modifier
    ) {
        if (sessionViewModel.sampleReadOnly) return
        com.hikariatelier.app.EditorAccessoryBar(
            editingValue = editingValue,
            compactAccessoryKeys = compactAccessoryKeys,
            colors = colors,
            codeFontFamily = codeFontFamily,
            undoAvailable = undoStack.isNotEmpty(),
            redoAvailable = redoStack.isNotEmpty(),
            onUndo = { undoEditorChange() },
            onRedo = { redoEditorChange() },
            onSearch = { editUserWork { searchReplaceViewModel.openSearch(editingValue) } },
            onFormat = {
                formatEditingFile()
            },
            onSnippets = {
                if (editingJavaScript) showSnippets = true
                else Toast.makeText(context, uiText("JavaScriptファイルを選択してください"), Toast.LENGTH_LONG).show()
            },
            onReference = {
                referenceSearchQuery = currentWordOrSelection()
                showReferenceDialog = true
            },
            onApplyEdit = { value ->
                applyEditorChange(value)
                editorFocusRequester.requestFocus()
            },
            lastColorPickerColor = lastColorPickerColor,
            onOpenColorPicker = { target, color ->
                if (editingJavaScript) {
                    activeColorTarget = target
                    colorPickerInitial = color
                    showColorPickerDialog = true
                } else Toast.makeText(context, uiText("JavaScriptファイルを選択してください"), Toast.LENGTH_LONG).show()
            },
            showAccessoryNavigation = showAccessoryNavigation,
            showAccessorySymbols = showAccessorySymbols,
            textTranslator = { s, args -> uiText(s, *args) },
            modifier = modifier
        )
    }

    val projectCompletionSymbols = rememberProjectCompletionSymbols(
        activeWorkId, editingFile, editingText, editorText, activeWork?.files.orEmpty(),
        sessionViewModel, codeCompletion && editingJavaScript
    )
    val editorSuggestions = remember(editingValue.text, editingValue.selection, editorFocused,
        codeCompletion, editingFile, projectCompletionSymbols) {
        if (!editorFocused || !codeCompletion || !editingJavaScript) emptyList()
        else projectCompletions(editingValue, projectCompletionSymbols, editingFile)
    }

    @Composable
    fun CompletionBar(
        modifier: Modifier = Modifier
    ) {
        com.hikariatelier.app.CompletionBar(
            suggestions = editorSuggestions,
            editingValue = editingValue,
            editingText = editingText,
            visible = editorFocused && codeCompletion && editingJavaScript && !inlineSearchVisible,
            codeFontFamily = codeFontFamily,
            colors = colors,
            textTranslator = { s, args -> uiText(s, *args) },
            onApplyCompletion = { value ->
                applyEditorChange(value)
                editorFocusRequester.requestFocus()
            },
            modifier = modifier
        )
    }

    @Composable
    fun EditorArea(
        modifier: Modifier
    ) {
        com.hikariatelier.app.EditorArea(
            activeWork = activeWork,
            activeWorkId = activeWorkId,
            selectedEditorFile = selectedEditorFile,
            onSelectEditorFile = { selectedEditorFile = it },
            onOpenSnapshotSheet = { showSnapshotSheet = true },
            editingFile = editingFile,
            editingKey = editingKey,
            editingText = editingText,
            editingValue = editingValue,
            onUpdateEditingValue = { if (!sessionViewModel.editorInputLocked && !galleryPresent &&
                (!sessionViewModel.sampleReadOnly || it.text == editingValue.text)) editingValue = it },
            onApplyEditorChange = { if (!galleryPresent) applyEditorChange(it) },
            editorFocused = editorFocused,
            onFocusChange = { editorFocused = it },
            editorFocusRequester = editorFocusRequester,
            focusManager = focusManager,
            consoleEntries = consoleEntries,
            sessionViewModel = sessionViewModel,
            errorsCurrent = !previewHasChanges,
            previewHasChanges = previewHasChanges && !preview.isLoading,
            onRunChanges = { runSketch() },
            searchMatches = searchHighlightResult?.takeIf {
                showSearchDialog && !searchReplaceViewModel.searchWholeWork && it.first == editingText
            }?.second.orEmpty(),
            inlineSearch = {
                if (!galleryPresent && !sessionViewModel.editorInputLocked) {
                    SearchReplaceBar(
                        viewModel = searchReplaceViewModel,
                        editingKey = editingKey,
                        editingValue = editingValue,
                        onApplyChange = { applyEditorChange(it) },
                        onJumpToLine = { jumpToLine(it) },
                        editorFocusRequester = editorFocusRequester,
                        codeFontFamily = codeFontFamily,
                        textTranslator = { s, args -> uiText(s, *args) },
                        onSearchMatchesChanged = { source, matches -> searchHighlightResult = source to matches },
                        onRevealMatch = { range ->
                            navigationRequestsFocus = false
                            navigationTarget = EditorNavigationTarget(editingFile,
                                1 + editingState.value.text.take(range.min).count { it == '\n' }, range)
                            navigationSequence++
                        }
                    )
                }
            },
            codeFontFamily = codeFontFamily,
            editorFontSize = editorFontSize,
            onEditorFontSizeChange = { editorFontSize = it },
            showErrorBanner = !showConsole,
            onJumpToSource = { file, line -> navigateToSource(file, line) },
            editorWordWrap = editorWordWrap,
            autoIndent = autoIndent,
            showLineNumbers = showLineNumbers,
            fontFeatures = fontFeatures,
            navigationSequence = navigationSequence,
            navigationTarget = navigationTarget.takeUnless { galleryPresent },
            navigationRequestsFocus = navigationRequestsFocus,
            onClearNavigationTarget = { navigationTarget = null },
            readOnly = sessionViewModel.editorInputLocked || galleryPresent || sessionViewModel.sampleReadOnly,
            onCopySample = { workManagementViewModel.requestSampleCopy() },
            colors = colors,
            textTranslator = { s, args -> uiText(s, *args) },
            modifier = modifier
        )
    }

    val selectedFolderName by produceState(
        initialValue = if (selectedFolderUri == null) uiText("端末内") else uiText("選択済みフォルダー"),
        selectedFolderUri,
        appLanguage
    ) {
        value = if (selectedFolderUri == null) uiText("端末内")
            else workManagementViewModel.folderName(selectedFolderUri) ?: uiText("選択済みフォルダー")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(deletionSnackbar, Modifier.imePadding()) },
        containerColor =
            colors.background,

        contentWindowInsets =
            appInsets
    ) { innerPadding ->

        if (showSettings) {

            SettingsScreen(
                state = settingsViewModel.state,
                onSettingsChange = { settingsViewModel.update(it) },
                font = FontSettingsUiState(customFontFamily, importedFontName, fontImportBusy),
                actions = SettingsScreenActions(
                    onBack = { showSettings = false },
                    onChooseFolder = { workManagementViewModel.saveCurrentWork(WorkEvent(openFolder = true)) },
                    onImportOfficialSamples = {
                        showSettings = false
                        workGalleryState.changeFolder(SAMPLE_FOLDER)
                        workGalleryState.closeSearch(); workGalleryState.finishSelection()
                        workMenuExpanded = true
                    },
                    onImportFont = { fontPicker.launch(arrayOf("*/*")) },
                    onResetFont = { settingsViewModel.resetFont() },
                    onImportP5 = { workManagementViewModel.resetP5Results(); showP5Import = true },
                    onImportJs = { importJsLauncher.launch(arrayOf("application/javascript", "text/javascript", "text/plain", "application/octet-stream")) },
                    onExportJs = { exportJsLauncher.launch(safeJsFileName(activeWork?.title ?: "sketch")) },
                    onExportBackup = { exportBackupLauncher.launch("Edit-RiN-backup.zip") },
                    onImportBackup = { importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    openExternalUrl = { onOpenExternalUrl(it) }
                ),
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
                folderName = selectedFolderName,
                assets = assets,
                updateViewModel = updateViewModel,
                textTranslator = { text, arguments -> uiText(text, *arguments) }
            )

        } else {

            EditorWorkspaceLayout(
                state = EditorWorkspaceState(
                    isLandscape = isLandscape,
                    editorFocused = (editorFocused || inlineSearchVisible) && !galleryPresent,
                    keyboardVisible = keyboardVisible && !galleryPresent,
                    showResizeHandles = showResizeHandles,
                    showConsole = showConsole,
                    showEditorAccessoryBar = showEditorAccessoryBar && !inlineSearchVisible,
                    compactPreview = compactPreview,
                    hideEditingPreview = hideEditingPreview,
                    themeMode = themeMode,
                    workPreviewRatio = workPreviewRatio,
                    animatedLandscapePreviewFraction = animatedLandscapePreviewFraction,
                    landscapeEditorOnLeft = landscapeEditorOnLeft,
                    previewActionsExpanded = previewActionsExpanded,
                    landscapePreviewFraction = landscapePreviewFraction,
                    showLandscapeSplitLabel = showLandscapeSplitLabel,
                    activeWorkId = activeWorkId,
                    previewRatioSelection = previewRatioSelection,
                    portraitRatioDragging = portraitRatioDragging,
                    codeFontFamily = codeFontFamily,
                    hasVisibleCompletions = editorSuggestions.isNotEmpty() && !inlineSearchVisible,
                    galleryVisible = galleryPresent,
                    galleryProgress = galleryProgress
                ),
                actions = EditorWorkspaceActions(
                    onSplitChanged = { landscapePreviewFraction = it },
                    onSplitLabelChanged = { showLandscapeSplitLabel = it },
                    onPortraitDraggingChanged = { portraitRatioDragging = it },
                    onDraftPreviewRatio = { updateActiveWorkPreviewRatio(it, persist = false) },
                    onCommitPreviewRatio = { workManagementViewModel.commitPreviewRatio() },
                    onSavePreviewRatio = { updateActiveWorkPreviewRatio(it) }
                ),
                slots = EditorWorkspaceSlots(
                    workBar = { WorkBar() }, landscapeBar = { LandscapeUnifiedBar() },
                    controlBar = { ControlBar() }, console = { ConsolePanel(it) },
                    previewActions = { PreviewActionsTray(it) }, editor = { EditorArea(it) },
                    completions = { CompletionBar(it) }, accessory = { EditorAccessoryBar(it) },
                    preview = { MainPreviewArea(it) },
                    gallery = { InlineGallery(it) },
                    landscapeGalleryBar = { LandscapeGalleryBar() },
                    previewChrome = { modifier ->
                        WorkGalleryPreviewChrome(galleryPreviewBounds, modifier, visible = liftPreviewChrome) {
                            BoxWithConstraints(Modifier.fillMaxSize().galleryChrome(galleryProgress, GalleryPart.SECONDARY)) {
                                PreviewChrome()
                            }
                        }
                    },
                    galleryPreview = { modifier ->
                        WorkGalleryPreview(
                            bitmap = galleryPreviewBitmap,
                            start = galleryPreviewBounds, end = galleryThumbnailBounds,
                            progress = galleryProgress, modifier = modifier,
                            visible = (galleryPresent && galleryProgress < 1f) || (!galleryPresent && galleryHandoffAlpha > 0f),
                            opacity = if (galleryPresent) 1f else galleryHandoffAlpha
                        )
                    }
                ),
                textTranslator = { source, args -> uiText(source, *args) },
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
            )
    }

    }

    GalleryWorkDialogs(workManagementViewModel, works, assetBusy, { uiText(it) },
        windowSetup = { KeepLandscapeDialogImmersive() })

    if (showExpandedPreview) {
        Dialog(
            onDismissRequest = {
                if (!isRecordingOrCountingDown) showExpandedPreview = false
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            KeepLandscapeDialogImmersive(forceFullscreen = true)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                PreviewArea(
                    modifier = Modifier
                        .fillMaxSize(),
                    showExpandControl = false,
                    logicalSize = (if (expandedCanvasSwapped)
                        IntSize(normalPreviewSize.height, normalPreviewSize.width)
                    else normalPreviewSize).takeIf {
                        preserveExpandedPreview
                    },
                    fullscreen = true
                )
            }
        }
    }

    if (showSnippets) {
        SnippetDialog(text = { uiText(it) }, onInsert = { snippet ->
            if (!workSaving) {
                val indent = editingValue.text.substring(0, editingValue.selection.min)
                    .substringAfterLast('\n').takeWhile { it == ' ' || it == '\t' }
                val insertion = snippet.code.replace("\n", "\n$indent") + "\n$indent"
                applyEditorChange(insertAtSelection(editingValue, insertion))
                workManagementViewModel.saveCurrentWork()
                showSnippets = false
                editorFocusRequester.requestFocus()
            }
        }, onDismiss = { showSnippets = false })
    }
    if (showReferenceDialog) {
        val effectiveLang = resolveUiLanguage(appLanguage, ConfigurationCompat.getLocales(configuration)[0]?.language ?: "en")
        P5ReferenceDialog(
            initialQuery = referenceSearchQuery,
            codeFontFamily = codeFontFamily,
            language = effectiveLang,
            textTranslator = { uiText(it) },
            onInsert = { snippetCode ->
                if (!workSaving && !sessionViewModel.editorInputLocked) {
                    if (sessionViewModel.sampleReadOnly) {
                        workManagementViewModel.requestSampleCopy()
                        showReferenceDialog = false
                        return@P5ReferenceDialog
                    }
                    val indent = editingValue.text.substring(0, editingValue.selection.min)
                        .substringAfterLast('\n').takeWhile { it == ' ' || it == '\t' }
                    val insertion = snippetCode.replace("\n", "\n$indent")
                    applyEditorChange(insertAtSelection(editingValue, insertion))
                    workManagementViewModel.saveCurrentWork()
                    showReferenceDialog = false
                    editorFocusRequester.requestFocus()
                }
            },
            onDismiss = { showReferenceDialog = false }
        )
    }
    if (preview.screenshotBusy || recordingViewModel.screenshotSaving) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
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
        ScreenshotScaleDialog(text = { uiText(it) }, onCapture = { scale ->
            showScreenshotScale = false
            pendingScreenshotScale = scale
            preview.requestScreenshot(scale = scale)
        }, onDismiss = { showScreenshotScale = false })
    }

    if (showParameterSheet) {
        LiveParameterSheet(activeWork, editorText, sessionViewModel, workManagementViewModel, preview,
            ParameterSheetLayout(isLandscape, landscapeEditorOnLeft, 1f - animatedLandscapePreviewFraction, showStatusBar),
            { source, args -> uiText(source, *args) },
            onDismiss = { showParameterSheet = false })
    }

    if (showRecordingFormatDialog && !isRecordingOrCountingDown) {
        RecordingOptionsSheet(
            bitrate = mp4BitrateMbps, countdown = recordingCountdownSeconds,
            text = ::uiText,
            onBitrate = {
                mp4BitrateMbps = it

            },
            onCountdown = {
                recordingCountdownSeconds = it

            },
            onDismiss = { showRecordingFormatDialog = false },
            onStart = { format ->
                showRecordingFormatDialog = false
                requestPreviewRecording(format)
            }
        )
    }

    PreviewMediaSheets(models, preview, mediaActions, editorValue)

    EditorWorkDialogs(models, pendingRevision) { pendingRevision = it }

    if (assetBusy && !showAssets && !workSaving && !sessionViewModel.snapshotRestoring) {
        AlertDialog(onDismissRequest = {}, title = { Text(uiText("ファイルを処理中…")) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) }, confirmButton = {})
    }

    if (showP5Import) {
        P5AccountImportDialog(
            username = p5Username,
            sketches = p5Sketches,
            busy = p5Busy,
            error = p5Error?.let { uiText(it) },
            text = { uiText(it) },
            onUsernameChange = { value ->
                p5Username = value.take(64)
                workManagementViewModel.resetP5Results()
            },
            onLoad = { workManagementViewModel.loadP5Account() },
            onImport = { workManagementViewModel.importFromP5(it, preview.session.assets) },
            onDismiss = { showP5Import = false }
        )
    }

    if (showAssets) {
        AssetManagerDialog(
            assets = activeWork?.assets.orEmpty(), busy = assetBusy, text = ::uiText,
            onAdd = {
                assetTargetId = activeWorkId
                assetPicker.launch(arrayOf("*/*"))
            },
            onRename = { request -> workManagementViewModel.renameAsset(request) },
            onDelete = { name -> activeWork?.let { changeAssets(it.id, it.assets.toMap() - name) } },
            onClose = { showAssets = false },
            onPreview = { name, asset -> previewAsset = name to asset },
            onInsert = { name, asset ->
                if (editingJavaScript) {
                    applyEditorChange(insertAtSelection(editingValue, assetLoaderCode(name, asset)))
                    showAssets = false
                } else {
                    Toast.makeText(context, uiText("JavaScriptファイルを選択してください"), Toast.LENGTH_LONG).show()
                }
            },
            referenceSources = projectSearchSources(activeWorkId, editorText, activeWork?.files.orEmpty(), sessionViewModel.fileDrafts)
        )
    }
    previewAsset?.let { (name, asset) ->
        AssetPreviewDialog(name, asset, assetStorage.file(asset), ::uiText) { previewAsset = null }
    }

    if (showProjectFilesDialog) {
        ProjectFilesDialog(
            activeWorkFiles = activeWork?.files.orEmpty(),
            colors = colors,
            codeFontFamily = codeFontFamily,
            onSelectFile = { name, content ->
                originalAuxiliaryFileName = name
                auxiliaryFileName = name
                auxiliaryFileContent = content
                showProjectFilesDialog = false
                selectedEditorFile = name
            },
            onAddNewFile = {
                originalAuxiliaryFileName = null
                auxiliaryFileName = "library.js"
                auxiliaryFileContent = ""
                showProjectFilesDialog = false
                showAuxiliaryFileEditor = true
            },
            onEditFile = { name, content ->
                originalAuxiliaryFileName = name
                auxiliaryFileName = name
                auxiliaryFileContent = sessionViewModel.fileDrafts["$activeWorkId/$name"] ?: content
                showProjectFilesDialog = false
                showAuxiliaryFileEditor = true
            },
            onDismiss = { showProjectFilesDialog = false },
            textTranslator = { s, args -> uiText(s, *args) }
        )
    }

    if (showAuxiliaryFileEditor) {
        AuxiliaryFileEditorDialog(
            originalFileName = originalAuxiliaryFileName,
            initialFileName = auxiliaryFileName,
            initialContent = auxiliaryFileContent,
            onContentChange = { auxiliaryFileContent = it },
            fontFeatures = fontFeatures,
            codeFontFamily = codeFontFamily,
            colors = colors,
            busy = workSaving || assetBusy,
            existingFileNames = activeWork?.files.orEmpty().keys,
            onSave = { normalizedName, content ->
                val updated = activeWork?.files?.toMutableMap() ?: return@AuxiliaryFileEditorDialog
                originalAuxiliaryFileName?.takeIf { it != normalizedName }?.let(updated::remove)
                updated[normalizedName] = content
                workManagementViewModel.saveAuxiliaryFiles(updated)
            },
            onDelete = { existingName ->
                val updated = activeWork?.files?.toMutableMap() ?: return@AuxiliaryFileEditorDialog
                updated.remove(existingName)
                workManagementViewModel.saveAuxiliaryFiles(updated)
            },
            onDismiss = { showAuxiliaryFileEditor = false },
            textTranslator = { s, args -> uiText(s, *args) }
        )
    }

    SearchReplaceDialog(
        viewModel = searchReplaceViewModel,
        activeWorkId = activeWorkId,
        activeWorkFiles = activeWork?.files.orEmpty(),
        editorText = editorText,
        fileDrafts = sessionViewModel.fileDrafts,
        editingValue = editingValue,
        onApplyChange = { applyEditorChange(it) },
        onNavigateToSource = { file, line, range -> navigateToSource(file, line, range) },
        onJumpToLine = { jumpToLine(it) },
        editorFocusRequester = editorFocusRequester,
        codeFontFamily = codeFontFamily,
        textTranslator = { s, args -> uiText(s, *args) }
    )

    if (showColorPickerDialog) {
        EditorColorPickerDialog(
            initialColor = colorPickerInitial,
            target = activeColorTarget,
            onDismiss = {
                showColorPickerDialog = false
            },
            onApply = { newColor ->
                lastColorPickerColor = newColor
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
            onDismissRequest = { showUserGuide = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            val deviceLanguage = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]?.language ?: "en"
            UserGuideScreen(
                language = resolveUiLanguage(appLanguage, deviceLanguage),
                onClose = { showUserGuide = false }
            )
        }
    }
}

/** Alpha leaves controls interactive; disable their input and accessibility while covered. */
private fun Modifier.blockGalleryInput(blocked: Boolean): Modifier =
    then(if (blocked) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier)
        .focusProperties { if (blocked) canFocus = false }
        .onPreviewKeyEvent { blocked }
        .pointerInput(blocked) {
            if (blocked) awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
