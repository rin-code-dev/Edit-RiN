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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    val liveParameterCount by produceState(initialValue = 0, activeWorkId) {
        snapshotFlow {
            val work = works.find { it.id == activeWorkId } ?: works.firstOrNull()
            val workId = work?.id ?: activeWorkId
            val currentText = sessionViewModel.editorValueState.value.text
            val files = work?.files.orEmpty()
            val auxDrafts = files.keys.associateWith { name -> sessionViewModel.fileDrafts["$workId/$name"] }
            Triple(currentText, files, auxDrafts)
        }.collectLatest { (mainText, files, auxDrafts) ->
            val totalLen = mainText.length + files.entries.sumOf { (name, saved) -> (auxDrafts[name] ?: saved).length }
            delay(if (totalLen >= 8_000) 120L else 60L)
            value = withContext(Dispatchers.Default) {
                val sources = buildMap {
                    put("sketch.js", mainText)
                    files.toSortedMap().forEach { (name, saved) ->
                        if (name != "sketch.js") put(name, auxDrafts[name] ?: saved)
                    }
                }
                workParameters(sources).size
            }
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
    var showOutlineDialog by rememberSaveable(activeWorkId) { mutableStateOf(false) }
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
    val galleryTargetState = rememberUpdatedState(galleryTarget)
    val galleryProgressState = animateFloatAsState(
        targetValue = if (galleryTarget) 1f else 0f,
        animationSpec = tween(GALLERY_MOTION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "inlineWorkGallery"
    )
    val galleryPresent by remember {
        derivedStateOf { galleryTargetState.value || galleryProgressState.value > 0f }
    }
    val gallerySettled by remember {
        derivedStateOf { galleryProgressState.value >= 1f }
    }
    val galleryAnimating by remember {
        derivedStateOf { galleryProgressState.value > 0f && galleryProgressState.value < 1f }
    }
    val galleryProgressProvider = remember(galleryProgressState) {
        { galleryProgressState.value.coerceIn(0f, 1f) }
    }
    val galleryHandoffAlphaState = animateFloatAsState(
        if (galleryPresent) 1f else 0f,
        animationSpec = tween(if (galleryPresent) 0 else 120),
        label = "galleryPreviewHandoff"
    )
    val galleryHandoffActive by remember {
        derivedStateOf { galleryHandoffAlphaState.value > 0f }
    }
    val galleryPresentState = rememberUpdatedState(galleryPresent)
    val galleryHandoffOpacityProvider = remember(galleryHandoffAlphaState) {
        { if (galleryPresentState.value) 1f else galleryHandoffAlphaState.value }
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val galleryDensity = LocalDensity.current
    var galleryPreviewBitmap by remember(isLandscape, galleryDensity) { mutableStateOf<Bitmap?>(null) }
    val latestPreviewBounds = remember(isLandscape, galleryDensity) { arrayOfNulls<Rect>(1) }
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
    var parameterFullscreen by rememberSaveable { mutableStateOf(false) }
    fun setParameterFullscreenMode(enabled: Boolean) {
        parameterFullscreen = enabled
        if (enabled) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            sessionViewModel.pipMinimized = false
        }
    }
    LaunchedEffect(showParameterSheet, isLandscape) {
        if (showParameterSheet && !isLandscape) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            sessionViewModel.pipMinimized = false
        }
    }


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

    fun requestPreviewRecording(format: String, durationSeconds: Int = 0, frameSync: Boolean = false) {
        recordingViewModel.requestPreviewRecording(
            format = format,
            countdownSeconds = recordingCountdownSeconds,
            mp4BitrateMbps = mp4BitrateMbps,
            evaluateJavascript = { js -> preview.webView?.evaluateJavascript(js, null) },
            durationSeconds = durationSeconds,
            frameSync = frameSync
        )
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

    fun sharePreviewCard() {
        preview.requestScreenshot(forShareCard = true)
        previewActionsExpanded = false
    }



    fun openPreviewParameters() {
        previewActionsExpanded = false
        showExpandedPreview = false
        showParameterSheet = !showParameterSheet
        if (showParameterSheet && !isLandscape) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            sessionViewModel.pipMinimized = false
        }
        if (!showParameterSheet) parameterFullscreen = false
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
    val hasConsoleError by remember(consoleEntries) {
        derivedStateOf { consoleEntries.any { it.level == ConsoleLevel.ERROR } }
    }

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
                sessionViewModel.blockEditorDrafts.containsKey(selected) ||
                sessionViewModel.blockEditorHasPendingInput ||
                workManagementViewModel.hasPendingMetadata(selected)
        }
    }

    var selectedEditorFile by remember(activeWorkId, sessionViewModel.auxiliaryEditorGeneration) {
        sessionViewModel.editorFileState(activeWorkId)
    }
    val editingFile = selectedEditorFile.takeIf { it in activeWork?.files.orEmpty() } ?: "sketch.js"
    val editingJavaScript = isJavaScriptProjectFile(editingFile)
    val isBlockModeActive = editingFile == "sketch.js" && sessionViewModel.blockModeState(activeWorkId).value
    val isBlockModeFullscreen = isBlockModeActive && !sessionViewModel.blockSplitView && !galleryPresent
    LaunchedEffect(isBlockModeActive) {
        if (isBlockModeActive) previewActionsExpanded = false
    }
    LaunchedEffect(isBlockModeFullscreen) {
        if (isBlockModeFullscreen) previewActionsExpanded = false
    }
    LaunchedEffect(editorFocused, isLandscape) {
        if (!isLandscape && editorFocused) previewActionsExpanded = false
    }
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
    if (workSaving && workManagementViewModel.showBlockingProgress) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Surface(
                shape = UNIT_SHAPE,
                color = colors.surface,
                contentColor = colors.onSurface,
                border = BorderStroke(UNIT_BORDER_WIDTH, colors.outlineVariant),
                tonalElevation = 0.dp
            ) {
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

    val editorToolScope = rememberCoroutineScope()
    val currentGalleryPresent by rememberUpdatedState(galleryPresent)
    val currentEditingFile by rememberUpdatedState(editingFile)
    val actionCoordinator = remember(sessionViewModel) {
        EditorActionCoordinator(
            session = sessionViewModel,
            scope = editorToolScope,
            folderUriProvider = { workManagementViewModel.selectedFolderUri },
            onSampleCopyRequested = { workManagementViewModel.requestSampleCopy() },
            onFlushFailure = {
                Toast.makeText(context, uiText("ブロックの編集を確定できませんでした。もう一度お試しください。"), Toast.LENGTH_SHORT).show()
            }
        )
    }
    val dialogCoordinator = remember { WorkDialogCoordinator() }
    LaunchedEffect(activeWorkId, sessionViewModel.blockEditorActivationGeneration) {
        dialogCoordinator.invalidateForWorkChange(activeWorkId, sessionViewModel.blockEditorActivationGeneration)
    }

    suspend fun flushOwnedBlockEditor(owner: String, activation: Long, requireEditable: Boolean = false): Boolean =
        sessionViewModel.flushBlockEditor() && sessionViewModel.canApplyBlockAction(owner, activation, requireEditable = requireEditable)

    fun openCodeTool(action: () -> Unit) {
        actionCoordinator.launchAction<Unit>(
            kind = ActionKind.SWITCH_BLOCK_MODE,
            requireEditable = false,
            requireFlush = true
        ) { ctx ->
            if (currentGalleryPresent) return@launchAction CoordinatorResult.Cancelled("Gallery present")
            sessionViewModel.blockModeState(ctx.workId).value = false
            // Give the text field a frame to attach before a code-only tool requests its focus.
            withFrameNanos { }
            withFrameNanos { }
            if (sessionViewModel.canApplyBlockAction(ctx.workId, ctx.activationGeneration, requireEditable = false) && !currentGalleryPresent) {
                action()
            }
            CoordinatorResult.Success(Unit)
        }
    }

    fun applyEditorChange(
        nextValue: TextFieldValue
    ) {
        if (sessionViewModel.editorInputLocked) return
        if (sessionViewModel.sampleReadOnly) { workManagementViewModel.requestSampleCopy(); return }
        val measurements = preview.performance.measurements
        val started = if (measurements.enabled) System.nanoTime() else 0L
        try {
            sessionViewModel.applyChange(editingValue, nextValue, undoStack, redoStack)
            editingValue = nextValue
        } finally {
            if (measurements.enabled) measurements.record(
                if (nextValue.text.length >= 100_000) PerformanceOperation.EDIT_CHANGE_APPLY_LARGE
                else PerformanceOperation.EDIT_CHANGE_APPLY,
                System.nanoTime() - started
            )
        }
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
        val requestedFile = editingFile
        actionCoordinator.launchAction<Unit>(
            kind = ActionKind.UNDO,
            requireEditable = true,
            requireFlush = editingFile == "sketch.js" && sessionViewModel.blockModeState(activeWorkId).value
        ) { ctx ->
            if (currentGalleryPresent) return@launchAction CoordinatorResult.Cancelled("Gallery present")
            if (currentEditingFile != requestedFile) return@launchAction CoordinatorResult.Superseded
            if (editingFile == "sketch.js" && sessionViewModel.blockModeState(ctx.workId).value) {
                sessionViewModel.undo(sessionViewModel.editorValueState.value)?.let {
                    sessionViewModel.editorValueState.value = it
                }
            } else {
                val restored = sessionViewModel.undo(editingValue, undoStack, redoStack)
                if (restored != null) {
                    editingValue = restored
                    editorFocusRequester.requestFocus()
                }
            }
            CoordinatorResult.Success(Unit)
        }
    }

    fun redoEditorChange() {
        val requestedFile = editingFile
        actionCoordinator.launchAction<Unit>(
            kind = ActionKind.REDO,
            requireEditable = true,
            requireFlush = editingFile == "sketch.js" && sessionViewModel.blockModeState(activeWorkId).value
        ) { ctx ->
            if (currentGalleryPresent) return@launchAction CoordinatorResult.Cancelled("Gallery present")
            if (currentEditingFile != requestedFile) return@launchAction CoordinatorResult.Superseded
            if (editingFile == "sketch.js" && sessionViewModel.blockModeState(ctx.workId).value) {
                sessionViewModel.redo(sessionViewModel.editorValueState.value)?.let {
                    sessionViewModel.editorValueState.value = it
                }
            } else {
                val restored = sessionViewModel.redo(editingValue, undoStack, redoStack)
                if (restored != null) {
                    editingValue = restored
                    editorFocusRequester.requestFocus()
                }
            }
            CoordinatorResult.Success(Unit)
        }
    }

    BackHandler(enabled = showParameterSheet && (!isLandscape || parameterFullscreen) && !galleryPresent && !showSettings && !showUserGuide) {
        if (!isLandscape) {
            showParameterSheet = false
            parameterFullscreen = false
        } else {
            parameterFullscreen = false
        }
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
        if (keyboardWasVisible && !keyboardVisible && !isLandscape) {
            focusManager.clearFocus(force = true)
        }
        keyboardWasVisible = keyboardVisible
    }

    var compactPreview by settingsViewModel::compactPreview
    SideEffect {
        if (sessionViewModel.pipModeEnabled != compactPreview) {
            sessionViewModel.pipModeEnabled = compactPreview
        }
    }

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

    LaunchedEffect(activeWorkId, draftRecovery) {
        if (!draftRecovery || activeWorkId.isBlank()) return@LaunchedEffect
        snapshotFlow {
            Triple(
                sessionViewModel.editorValueState.value.text,
                sessionViewModel.blockEditorInputGeneration,
                sessionViewModel.fileDrafts.toMap() to sessionViewModel.blockEditorDrafts.toMap()
            )
        }.collectLatest {
            kotlinx.coroutines.delay(700L)
            workManagementViewModel.saveDraft()
        }
    }

    var navigationSequence by remember { mutableIntStateOf(0) }
    var navigationTarget by remember { mutableStateOf<EditorNavigationTarget?>(null) }
    var navigationRequestsFocus by remember { mutableStateOf(true) }

    fun applySourceNavigation(file: String, line: Int, selection: TextRange? = null) {
        if (line <= 0 || (file != "sketch.js" && file !in activeWork?.files.orEmpty())) return
        if (file == "sketch.js") sessionViewModel.blockModeState(activeWorkId).value = false
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

    fun navigateToSource(file: String, line: Int, selection: TextRange? = null) {
        if (sessionViewModel.blockModeState(activeWorkId).value && sessionViewModel.blockEditorFlusher != null) {
            val owner = activeWorkId
            val activation = sessionViewModel.blockEditorActivationGeneration
            editorToolScope.launch {
                if (flushOwnedBlockEditor(owner, activation) && !currentGalleryPresent) {
                    applySourceNavigation(file, line, selection)
                }
            }
        } else applySourceNavigation(file, line, selection)
    }

    fun jumpToLine(line: Int) = navigateToSource(editingFile, line)

    fun updateActiveWorkPreviewRatio(value: String, persist: Boolean = true) {
        if (persist) workManagementViewModel.savePreviewRatio(value)
        else workManagementViewModel.draftPreviewRatio(value)
    }


    fun runSketch(
        awaitStage: PreviewStage? = null,
        awaitTransition: (suspend () -> Unit)? = null,
        onResult: ((PreviewExecutionResult) -> Unit)? = null
    ): kotlinx.coroutines.Job {
        return actionCoordinator.launchAction<PreviewExecutionResult>(
            kind = ActionKind.RUN_SKETCH,
            requireEditable = false,
            requireFlush = true,
            onResult = { res ->
                when (res) {
                    is CoordinatorResult.Success -> onResult?.invoke(res.value)
                    is CoordinatorResult.Rejected -> onResult?.invoke(PreviewExecutionResult.Rejected(res.reason))
                    is CoordinatorResult.Cancelled -> onResult?.invoke(PreviewExecutionResult.Cancelled(null, null))
                    is CoordinatorResult.Superseded -> onResult?.invoke(PreviewExecutionResult.Superseded(null, null))
                    is CoordinatorResult.Failed -> onResult?.invoke(PreviewExecutionResult.Failed(null, null, res.message, res.cause))
                }
            }
        ) { ctx ->
            val result = preview.runSketch(awaitTransition = awaitTransition)
            if (awaitStage != null && result is PreviewExecutionResult.Accepted) {
                val stageResult = preview.executionCoordinator.awaitStage(result.token, awaitStage)
                CoordinatorResult.Success(stageResult)
            } else {
                CoordinatorResult.Success(result)
            }
        }
    }

    fun openFullPreviewFromEditor() {
        previewActionsExpanded = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        actionCoordinator.launchAction<Unit>(
            kind = ActionKind.RUN_FULL_PREVIEW,
            requireEditable = false,
            requireFlush = true
        ) { ctx ->
            if (currentGalleryPresent) return@launchAction CoordinatorResult.Cancelled("Gallery present")
            if (previewHasChanges && !preview.isLoading) preview.runSketch()
            showExpandedPreview = true
            CoordinatorResult.Success(Unit)
        }
    }

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
    val exportHtmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        if (uri != null) workManagementViewModel.exportHtml(uri)
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
        if (event.forceRun || (event.rerun && autoRun)) {
            val awaitClose: (suspend () -> Unit)? = if (!workMenuExpanded && galleryProgressState.value > 0f) {
                {
                    withTimeoutOrNull(1000L) {
                        androidx.compose.runtime.snapshotFlow { galleryProgressState.value == 0f || workMenuExpanded }.first { it }
                    }
                }
            } else null
            runSketch(awaitTransition = awaitClose)
        } else if (event.rerun) {
            preview.stopSketch()
        }
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
            if (event == Lifecycle.Event.ON_PAUSE && currentAutoSaveOnLeave.value &&
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

    fun openWorkGallery() {
        latestPreviewBounds[0]?.let { galleryPreviewBounds = it }
        val owner = activeWorkId
        val activation = sessionViewModel.blockEditorActivationGeneration
        if (sessionViewModel.blockEditorFlusher == null || !sessionViewModel.blockModeState(owner).value) {
            workMenuExpanded = true
        } else {
            editorToolScope.launch {
                if (flushOwnedBlockEditor(owner, activation)) workMenuExpanded = true
            }
        }
    }

    fun openNewWorkStudio(
        folder: String = "",
        category: WorkTemplateCategory = WorkTemplateCategory.BASIC
    ) {
        val owner = activeWorkId
        val activation = sessionViewModel.blockEditorActivationGeneration
        if (sessionViewModel.blockEditorFlusher == null || !sessionViewModel.blockModeState(owner).value) {
            workManagementViewModel.openNewWorkDialog(folder, category)
        } else {
            editorToolScope.launch {
                if (flushOwnedBlockEditor(owner, activation)) {
                    workManagementViewModel.openNewWorkDialog(folder, category)
                }
            }
        }
    }

    val currentSaveLabel = when {
        workManagementViewModel.operationState == WorkOperationState.CONFLICT -> uiText("保存内容の競合")
        workManagementViewModel.operationState == WorkOperationState.FAILED -> uiText("保存失敗")
        workSaving -> uiText("処理中")
        hasUnsavedChanges -> uiText("未保存")
        workManagementViewModel.operationState == WorkOperationState.SAVED -> uiText("保存済み")
        else -> null
    }
    val currentSaveFailed = workManagementViewModel.operationState in setOf(WorkOperationState.CONFLICT, WorkOperationState.FAILED)
    val currentSaveRetry: (() -> Unit)? = if (!workSaving && currentSaveFailed) ({
        if (workManagementViewModel.operationState == WorkOperationState.CONFLICT) workManagementViewModel.showConflictDialog = true
        else saveCurrentWork()
    }) else null

    @Composable
    fun WorkSelector(modifier: Modifier = Modifier) {
        WorkSelectorChip(
            activeWorkTitle = activeWork?.title,
            hasUnsavedChanges = hasUnsavedChanges,
            isLandscape = isLandscape,
            manualRotation = manualRotation,
            colors = colors,
            textTranslator = { s, args -> uiText(s, *args) },
            onClick = ::openWorkGallery,
            saveLabel = currentSaveLabel,
            saveFailed = currentSaveFailed,
            onRetry = currentSaveRetry,
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

    LaunchedEffect(workManagementViewModel, lifecycle, preview) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                combine(
                    snapshotFlow {
                        workMenuExpanded && !workManagementViewModel.showAddDialog &&
                            !workManagementViewModel.thumbnailScrolling &&
                            !workManagementViewModel.workSaving && !sessionViewModel.assetBusy &&
                            sessionViewModel.snapshotOperationWorkId == null &&
                            !recordingViewModel.isPreviewRecording && !recordingViewModel.isRecordingSaving &&
                            recordingViewModel.pendingRecordingFormat == null &&
                            !preview.screenshotBusy && !preview.isLoading
                    },
                    preview.performance.measurements.loadChanges
                ) { idle, load -> idle && !load.heavyLoad && !load.jankActive }
                    .collect { workManagementViewModel.setPrefetchAllowed(it) }
            } finally {
                workManagementViewModel.setPrefetchAllowed(false)
            }
        }
    }

    // Capture the renderer's owner, which can differ from the selected work when auto-run is off.
    LaunchedEffect(workMenuExpanded, isLandscape) {
        if (!workMenuExpanded) return@LaunchedEffect
        latestPreviewBounds[0]?.let { galleryPreviewBounds = it }
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

    LaunchedEffect(showSettings) {
        if (showSettings) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }
    val previewObscured = galleryPresent || showSettings
    DisposableEffect(previewObscured, preview.generation) {
        val webView = preview.webView
        val accessibility = webView?.importantForAccessibility
        val focusable = webView?.isFocusable
        val touchFocusable = webView?.isFocusableInTouchMode
        if (previewObscured) {
            webView?.clearFocus()
            webView?.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            webView?.isFocusable = false
        }
        onDispose {
            if (previewObscured && webView != null && preview.webView === webView) {
                webView.importantForAccessibility = accessibility!!
                webView.isFocusable = focusable!!
                webView.isFocusableInTouchMode = touchFocusable!!
            }
        }
    }
    val previewSuspended = ((galleryPresent && gallerySettled) || showSettings) && !isRecordingOrCountingDown
    DisposableEffect(previewSuspended, preview.generation) {
        val webView = preview.webView
        if (previewSuspended) webView?.onPause()
        onDispose {
            if (previewSuspended && webView != null && preview.webView === webView &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                webView.onResume()
            }
        }
    }
    LaunchedEffect(galleryPresent, galleryHandoffActive) {
        if (!galleryPresent && !galleryHandoffActive) galleryPreviewBitmap = null
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
        (galleryPresent || galleryHandoffActive)
    val galleryMorphActive = galleryPresent && galleryAnimating &&
        galleryPreviewBitmap?.isRecycled == false &&
        galleryPreviewBounds?.usableGalleryBounds() == true &&
        galleryThumbnailBounds?.usableGalleryBounds() == true

    fun jumpToEditingWork() {
        if (assetBusy) return
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        workGalleryState.changeFolder(if (activeWork?.isSample == true) SAMPLE_FOLDER else activeWork?.folderName?.takeIf { it.isNotEmpty() })
        workGalleryState.jumpToWork(activeWorkId)
    }
    val workDatabase = remember(applicationContext) { WorkDatabase.getInstance(applicationContext) }
    LaunchedEffect(works) {
        withContext(Dispatchers.IO) {
            runCatching { workDatabase.syncAllWorks(works) }
        }
    }

    var pendingGalleryCodeJump by remember { mutableStateOf<Pair<GalleryCodeMatch, String>?>(null) }
    LaunchedEffect(activeWorkId, activeWork?.bodyLoaded, galleryPresent, pendingGalleryCodeJump) {
        val pending = pendingGalleryCodeJump ?: return@LaunchedEffect
        val (match, query) = pending
        val work = activeWork
        if (!galleryPresent && activeWorkId == match.workId && work != null && work.bodyLoaded) {
            pendingGalleryCodeJump = null
            val sources = work.files.mapValues { (name, code) ->
                sessionViewModel.fileDrafts["${work.id}/$name"] ?: code
            } + ("sketch.js" to sessionViewModel.editorValueState.value.text)
            val resolved = findFirstCodeMatchInSources(work.id, sources, query) ?: match
            navigateToSource(resolved.file, resolved.line)
        }
    }

    fun toggleAllGallerySelection() {
        if (assetBusy) return
        val trimmedQuery = workGalleryState.query.trim()
        formatScope.launch {
            val ftsMatchedIds = if (trimmedQuery.isEmpty()) emptySet()
            else withContext(Dispatchers.IO) {
                runCatching { workDatabase.searchWorkIds(trimmedQuery) }.getOrDefault(emptySet())
            }
            val allIds = works.filter {
                !it.isSample && workGalleryState.matchesScope(it) &&
                    (workGalleryState.selectedTag == null || it.tags.any { tag -> tag.equals(workGalleryState.selectedTag, true) }) &&
                    matchesGalleryQuery(it, trimmedQuery, ftsMatchedIds)
            }.map { it.id }.toSet()
            workGalleryState.selectedIds = if (workGalleryState.selectedIds == allIds) emptySet() else allIds
        }
    }

    @Composable
    fun GalleryActions(
        modifier: Modifier = Modifier,
        buttonSize: androidx.compose.ui.unit.Dp = 38.dp,
        showManageTags: Boolean = true
    ) {
        WorkGalleryActions(state = workGalleryState, onDismiss = ::closeWorkGallery,
            text = { uiText(it) }, sort = settingsViewModel.workSort,
            onSort = { settingsViewModel.workSort = it }, modifier = modifier, buttonSize = buttonSize,
            busy = assetBusy, canDeleteSelection = workGalleryState.selectedIds.size < works.size,
            showManageTags = showManageTags,
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
    fun LandscapeGalleryBar(visibleCount: Int) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (workGalleryState.searching && !workGalleryState.selecting) {
                WorkGallerySearch(
                    state = workGalleryState,
                    text = { uiText(it) },
                    interactive = gallerySettled,
                    modifier = Modifier.weight(1f),
                    compact = true
                )
            } else {
                Row(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 36.dp)
                        .clip(UNIT_CONTROL_SHAPE)
                        .clickable(enabled = !assetBusy) {
                            if (workGalleryState.selecting) toggleAllGallerySelection() else jumpToEditingWork()
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (workGalleryState.selecting)
                            "${workGalleryState.selectedIds.size} · ${uiText("選択中")}"
                        else galleryFolderLabel(workGalleryState.selectedFolder) { uiText(it) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!workGalleryState.selecting) {
                        Text(
                            visibleCount.toString(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.outline,
                            maxLines = 1
                        )
                    }
                    val selectedTag = workGalleryState.selectedTag
                    if (!workGalleryState.selecting && selectedTag != null) {
                        Surface(
                            onClick = { workGalleryState.selectedTag = null },
                            shape = RoundedCornerShape(8.dp),
                            color = colors.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                "#$selectedTag ×",
                                Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    val subtitle = if (workGalleryState.selecting) {
                        uiText("すべて選択 / 解除")
                    } else {
                        activeWork?.title?.takeIf { it.isNotEmpty() }?.let { "· $it" }.orEmpty()
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
            GalleryActions(buttonSize = 36.dp, showManageTags = false)
        }
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
            onAdd = {
                openNewWorkStudio(
                    folder = workGalleryState.selectedFolder?.takeIf { it in workManagementViewModel.galleryFolders }.orEmpty(),
                    category = WorkTemplateCategory.BASIC
                )
            },
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
            morphProgress = if (gallerySettled) 1f else 0f,
            folders = workManagementViewModel.galleryFolders,
            tabs = workManagementViewModel.galleryTabs,
            onManageFolders = { workManagementViewModel.showGalleryFolders = true },
            onMove = { workManagementViewModel.galleryMoveIds = setOf(it.id) },
            onReorderTabs = { workManagementViewModel.reorderGalleryTabs(it) },
            landscapeHeader = { visibleCount -> LandscapeGalleryBar(visibleCount) },
            onOpenCodeMatch = { work, match, query ->
                if (workSaving || (!assetBusy && sessionViewModel.snapshotOperationWorkId == null)) {
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    pendingGalleryCodeJump = match to query
                    workManagementViewModel.selectWork(work.id, false)
                }
            },
            onResetThumbnail = { work ->
                preview.resetManualThumbnail(work.id) {
                    Toast.makeText(context, uiText("自動サムネイルに戻しました"), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    fun editUserWork(action: () -> Unit) {
        if (sessionViewModel.sampleReadOnly) workManagementViewModel.requestSampleCopy() else action()
    }

    fun openAppSettings() {
        val owner = activeWorkId
        val activation = sessionViewModel.blockEditorActivationGeneration
        if (sessionViewModel.blockEditorFlusher == null || !sessionViewModel.blockModeState(owner).value) {
            workManagementViewModel.saveCurrentWork(blockUi = false)
            showSettings = true
        } else {
            editorToolScope.launch {
                if (flushOwnedBlockEditor(owner, activation)) {
                    workManagementViewModel.saveCurrentWork(blockUi = false)?.join()
                    if (sessionViewModel.canApplyBlockAction(owner, activation, requireEditable = false)) showSettings = true
                }
            }
        }
    }

    @Composable
    fun WorkActions(showButtons: Boolean = true) {
        WorkActionsMenu(
            activeWork = activeWork,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            canDelete = activeWork?.isSample != true && works.size > 1,
            hasUnsavedChanges = hasUnsavedChanges,
            isLandscape = isLandscape,
            manualRotation = manualRotation,
            wideWorkPanels = wideWorkPanels,
            galleryProgressProvider = galleryProgressProvider,
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
            onOpenSettings = ::openAppSettings,
            onUndo = { undoEditorChange() },
            onRedo = { redoEditorChange() },
            onSearch = {
                if (editingFile == "sketch.js" && sessionViewModel.blockModeState(activeWorkId).value) {
                    sessionViewModel.blockEditorSearch?.invoke()
                } else openCodeTool { editUserWork { searchReplaceViewModel.openSearch(editingValue) } }
            },
            onFormat = {
                openCodeTool { formatEditingFile() }
            },
            onSnippets = {
                openCodeTool {
                    if (editingJavaScript) showSnippets = true
                    else Toast.makeText(context, uiText("JavaScriptファイルを選択してください"), Toast.LENGTH_LONG).show()
                }
            },
            onReference = {
                openCodeTool {
                    referenceSearchQuery = currentWordOrSelection()
                    showReferenceDialog = true
                }
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
            onExportHtml = {
                exportHtmlLauncher.launch(
                    safeHtmlFileName(activeWork?.title ?: "sketch")
                )
            },
            onDelete = { editUserWork { showDeleteDialog = true } },
            onOpenWorkSettings = { editUserWork { sessionViewModel.showInspectorSheet = true } },
            tweetModeEnabled = sessionViewModel.tweetModeEnabled,
            onToggleTweetMode = { sessionViewModel.tweetModeEnabled = !sessionViewModel.tweetModeEnabled },
            onOpenMinify = { openCodeTool { sessionViewModel.showMinifySheet = true } },
            onCreateNewWork = {
                openNewWorkStudio(
                    folder = activeWork?.folderName.orEmpty(),
                    category = if (isBlockModeActive) WorkTemplateCategory.BLOCKS else WorkTemplateCategory.BASIC
                )
            },
            textTranslator = { s, args -> uiText(s, *args) },
            windowSetup = { KeepLandscapeDialogImmersive() },
            showButtons = showButtons
        )
    }

    @Composable
    fun WorkBar() {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).padding(end = 8.dp)) {
                Box(Modifier.galleryChrome(galleryProgressProvider, GalleryPart.TITLE)
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
                        modifier = Modifier.graphicsLayer { alpha = 1f - galleryExposure(galleryProgressProvider(), GalleryPart.TITLE) }
                            .blockGalleryInput(!gallerySettled)
                    )
                }
            }
            // Four fixed slots in normal mode; expand to five slots during multi-select.
            Box(Modifier.width(if (galleryPresent && workGalleryState.selecting) 210.dp else 164.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.blockGalleryInput(galleryPresent)) {
                    WorkActions()
                }
                if (galleryPresent) {
                    GalleryActions(Modifier.graphicsLayer { alpha = galleryLayerAlpha(galleryProgressProvider()) }
                        .blockGalleryInput(!gallerySettled))
                }
            }
        }
    }

    @Composable
    fun ParameterButton(
        modifier: Modifier = Modifier,
        borderless: Boolean = false
    ) {
        com.hikariatelier.app.ParameterButton(
            parameterCount = liveParameterCount,
            colors = colors,
            height = if (isLandscape) 34.dp else 36.dp,
            minWidth = if (isLandscape) 34.dp else 36.dp,
            iconSize = if (isLandscape) 15.dp else 16.dp,
            onClick = ::openPreviewParameters,
            textTranslator = { s, args -> uiText(s, *args) },
            modifier = modifier,
            active = showParameterSheet,
            borderless = borderless
        )
    }

    @Composable
    fun PreviewActionsToggleButton(
        modifier: Modifier = Modifier,
        borderless: Boolean = false
    ) {
        com.hikariatelier.app.PreviewActionsToggleButton(
            isRecordingOrCountingDown = isRecordingOrCountingDown,
            previewActionsExpanded = previewActionsExpanded,
            isLandscape = isLandscape,
            colors = colors,
            onClick = { previewActionsExpanded = !previewActionsExpanded },
            textTranslator = { s, args -> uiText(s, *args) },
            modifier = modifier,
            borderless = borderless
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
            onShareCard = ::sharePreviewCard,
            onRecordToggle = ::togglePreviewRecording,
            onFullscreen = {
                previewActionsExpanded = false
                focusManager.clearFocus(force = true)
                showExpandedPreview = true
            },
            textTranslator = { s, args -> uiText(s, *args) },
            modifier = modifier,
            showHud = preview.showHud,
            onToggleHud = { preview.setHudEnabled(!preview.showHud) },
            onStepFrame = { preview.stepFrame() }
        )
    }

    @Composable
    fun LandscapeRail(modifier: Modifier = Modifier) {
        var historyExpanded by remember { mutableStateOf(false) }
        var settingsExpanded by remember { mutableStateOf(false) }
        val hasPendingChanges = previewHasChanges && !preview.isLoading
        val consoleHasError = hasConsoleError
        val statusDotColor = when {
            hasPendingChanges -> colors.tertiary
            isError -> colors.error
            isPaused -> colors.outline
            else -> colors.primary
        }
        val itemCount = 5 + 3 + (if (manualRotation) 1 else 0)
        LandscapeToolRail(
            itemCount = itemCount,
            modifier = modifier,
            top = {
                RailButton(
                    iconRes = if (isPaused) R.drawable.ic_play else R.drawable.ic_pause,
                    label = uiText(if (isPaused) "再生（長押しで1コマ送り）" else "一時停止（長押しで1コマ送り）"),
                    onClick = ::togglePreviewPlayback,
                    onLongClick = { preview.stepFrame() },
                    dotColor = statusDotColor,
                    outlineColor = if (isError) colors.error else null
                )
                RailButton(
                    iconRes = R.drawable.ic_reload,
                    label = uiText(if (hasPendingChanges) "変更を実行" else "再読み込み"),
                    onClick = { runSketch() },
                    tint = if (hasPendingChanges) colors.tertiary else null,
                    dotColor = if (hasPendingChanges) colors.tertiary else null
                )
                RailButton(
                    iconRes = R.drawable.ic_log,
                    label = uiText("ログ"),
                    onClick = { showConsole = !showConsole },
                    active = showConsole,
                    tint = if (consoleHasError) colors.error else null,
                    outlineColor = if (consoleHasError) colors.error else null,
                    badge = if (consoleEntries.isNotEmpty())
                        (if (consoleEntries.size > 99) "99+" else consoleEntries.size.toString())
                    else null
                )
                RailButton(
                    iconRes = R.drawable.ic_tune,
                    label = uiText("パラメータ"),
                    onClick = ::openPreviewParameters,
                    active = showParameterSheet,
                    tint = if (liveParameterCount > 0 || showParameterSheet) colors.primary else null,
                    badge = if (liveParameterCount > 0)
                        (if (liveParameterCount > 99) "99+" else liveParameterCount.toString())
                    else null
                )
                RailButton(
                    iconRes = if (isRecordingOrCountingDown) R.drawable.ic_stop else R.drawable.ic_camera,
                    label = uiText("プレビュー操作"),
                    onClick = { previewActionsExpanded = !previewActionsExpanded },
                    active = previewActionsExpanded,
                    tint = if (isRecordingOrCountingDown) colors.error else null,
                    outlineColor = if (isRecordingOrCountingDown) colors.error else null
                )
            },
            bottom = {
                Box {
                    RailButton(
                        iconRes = R.drawable.ic_save,
                        label = uiText("保存（長押しで履歴）"),
                        onClick = { saveCurrentWork() },
                        onLongClick = { historyExpanded = true },
                        tint = if (hasUnsavedChanges) colors.primary else null,
                        dotColor = if (hasUnsavedChanges) colors.primary else null
                    )
                    UnitDropdownMenu(
                        expanded = historyExpanded,
                        onDismissRequest = { historyExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(uiText("スナップショット")) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_snapshot), contentDescription = null) },
                            onClick = {
                                historyExpanded = false
                                editUserWork { showSnapshotSheet = true }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(uiText("保存済み状態に戻す")) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_restore), contentDescription = null) },
                            onClick = {
                                historyExpanded = false
                                restoreCurrentWork()
                            }
                        )
                    }
                }
                if (manualRotation) {
                    RailButton(
                        iconRes = R.drawable.ic_rotate,
                        label = uiText("画面を回転"),
                        onClick = {
                            val activity = view.context as Activity
                            activity.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                        }
                    )
                }
                Box {
                    RailButton(
                        iconRes = R.drawable.ic_settings,
                        label = uiText("設定"),
                        onClick = { settingsExpanded = true }
                    )
                    UnitDropdownMenu(
                        expanded = settingsExpanded,
                        onDismissRequest = { settingsExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(uiText("作品設定")) },
                            enabled = activeWork != null,
                            leadingIcon = { Icon(painterResource(R.drawable.ic_folder_code), contentDescription = null) },
                            onClick = {
                                settingsExpanded = false
                                workManagementViewModel.workActionsMenuExpanded = false
                                editUserWork { sessionViewModel.showInspectorSheet = true }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(uiText("アプリ設定")) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                            onClick = {
                                settingsExpanded = false
                                openAppSettings()
                            }
                        )
                    }
                }
                RailButton(
                    iconRes = R.drawable.ic_more_vertical,
                    label = uiText("作品メニュー"),
                    onClick = { workManagementViewModel.workActionsMenuExpanded = true }
                )
            }
        )
        WorkActions(showButtons = false)
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
                modifier = Modifier.widthIn(max = 120.dp)
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
                textTranslator = { s, args -> uiText(s, *args) },
                onStepFrame = { preview.stepFrame() }
            )

            ConsoleButton(
                hasError = hasConsoleError,
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
    fun BoxScope.PreviewChrome(fullscreen: Boolean = false, showExpandControl: Boolean = true) {
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
                    recordingElapsedMillisProvider = { recordingViewModel.recordingElapsedMillis },
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
                    onShareCard = ::sharePreviewCard,
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
                    textTranslator = { s, args -> uiText(s, *args) },
                    showHud = preview.showHud,
                    hudMetricsProvider = { preview.hudMetrics },
                    isPaused = isPaused,
                    onToggleHud = { preview.setHudEnabled(!preview.showHud) },
                    onStepFrame = { preview.stepFrame() },
                    onTogglePause = ::togglePreviewPlayback
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
            galleryProgressProvider = if (fullscreen) ({ 0f }) else galleryProgressProvider,
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
                    val bounds = coordinates.boundsInRoot()
                    latestPreviewBounds[0] = bounds
                    if ((galleryTarget && !gallerySelectionPrepared) ||
                        (galleryPresent && galleryPreviewBounds == null)) {
                        val current = galleryPreviewBounds
                        if (current == null ||
                            kotlin.math.abs(current.left - bounds.left) >= 1f ||
                            kotlin.math.abs(current.top - bounds.top) >= 1f ||
                            kotlin.math.abs(current.right - bounds.right) >= 1f ||
                            kotlin.math.abs(current.bottom - bounds.bottom) >= 1f
                        ) {
                            galleryPreviewBounds = bounds
                        }
                    }
                }.onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0 && normalPreviewSize != size) {
                        normalPreviewSize = size
                    }
                }
            )
        }
    }

    @Composable
    fun PipPreviewArea(
        modifier: Modifier
    ) {
        if (showExpandedPreview) {
            Box(modifier = modifier)
        } else {
            PreviewSurface(
                preview = preview,
                isError = isError,
                fullscreen = true,
                logicalSize = normalPreviewSize.takeIf { it.width > 0 && it.height > 0 },
                modifier = modifier,
                galleryProgress = 0f,
                galleryMorphActive = false,
                chromeLifted = true,
                clearFocusOnTouch = false,
                onClearEditorFocus = {}
            ) {}
        }
    }

    @Composable
    fun ControlBar() {
        val barHeight = if (isLandscape) 32.dp else 36.dp
        val unitBorderColor = if (isError || hasConsoleError) {
            colors.error.copy(alpha = 0.85f)
        } else {
            colors.outlineVariant
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isLandscape) {
                        Modifier.height(36.dp).padding(vertical = 2.dp)
                    } else {
                        Modifier.padding(top = if (showResizeHandles) 0.dp else 4.dp, bottom = 6.dp)
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = UNIT_CONTROL_SHAPE,
                color = colors.surface,
                border = BorderStroke(UNIT_BORDER_WIDTH, unitBorderColor),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.height(barHeight).padding(end = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RunStatusControls(
                        isError = isError,
                        isPaused = isPaused,
                        hasPendingChanges = previewHasChanges && !preview.isLoading,
                        colors = colors,
                        height = barHeight,
                        buttonSize = if (isLandscape) 26.dp else 28.dp,
                        iconSize = if (isLandscape) 15.dp else 16.dp,
                        onTogglePause = ::togglePreviewPlayback,
                        onReload = { runSketch() },
                        textTranslator = { s, args -> uiText(s, *args) },
                        onStepFrame = { preview.stepFrame() },
                        borderless = true
                    )

                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .width(1.dp)
                            .height(16.dp)
                            .background(colors.outlineVariant.copy(alpha = UNIT_HAIRLINE_ALPHA))
                    )

                    ConsoleButton(
                        hasError = hasConsoleError,
                        entryCount = consoleEntries.size,
                        colors = colors,
                        height = barHeight,
                        minWidth = if (isLandscape) 32.dp else 34.dp,
                        iconSize = if (isLandscape) 15.dp else 16.dp,
                        onClick = { showConsole = !showConsole },
                        textTranslator = { s, args -> uiText(s, *args) },
                        borderless = true
                    )

                    ParameterButton(borderless = true)

                    PreviewActionsToggleButton(borderless = true)
                }
            }

            Spacer(Modifier.weight(1f))

            SaveRestoreControls(
                hasUnsavedChanges = hasUnsavedChanges,
                colors = colors,
                height = barHeight,
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
            modifier = modifier,
            onEvaluateExpression = { preview.evaluateConsoleExpression(it) }
        )
    }

    @Composable
    fun EditorAccessoryBar(
        modifier: Modifier = Modifier
    ) {
        if (sessionViewModel.sampleReadOnly) return
        if (editingFile == "sketch.js" && sessionViewModel.blockModeState(activeWorkId).value) return
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
            onOutline = { showOutlineDialog = true },
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
            modifier = modifier,
            onLiveScrub = {
                if (!sessionViewModel.autoRunEnabled) {
                    sessionViewModel.autoRunEnabled = true
                }
            }
        )
    }

    val projectCompletionSymbols = rememberProjectCompletionSymbols(
        activeWorkId, editingFile, editingText, editorText, activeWork?.files.orEmpty(),
        sessionViewModel, codeCompletion && editingJavaScript
    )
    val completionSupported = editingJavaScript || projectTextSyntax(editingFile) == ProjectTextSyntax.SHADER
    val editorSuggestions = remember(editingValue.text, editingValue.selection, editorFocused,
        codeCompletion, editingFile, completionSupported, projectCompletionSymbols) {
        if (!editorFocused || !codeCompletion || !completionSupported) emptyList()
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
            visible = editorFocused && codeCompletion && completionSupported && !inlineSearchVisible,
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
            onOpenProjectFiles = { editUserWork { showProjectFilesDialog = true } },
            watchValues = if (settingsViewModel.inlineWatchEnabled) preview.watchValues else emptyMap(),
            onSwitchRuntimeVersion = { nextVersion ->
                editUserWork {
                    activeWork?.let { current ->
                        workManagementViewModel.saveRuntime(nextVersion, current.p5SoundEnabled, current.libraries)
                    }
                }
            },
            colors = colors,
            textTranslator = { s, args -> uiText(s, *args) },
            devicePreviewRatio = devicePreviewRatio,
            blockLanguage = sampleLanguage,
            onOpenBlockCode = { start, end ->
                val currentSource = sessionViewModel.editorValueState.value.text
                val first = start.coerceIn(0, currentSource.length)
                val last = end.coerceIn(first, currentSource.length)
                navigateToSource("sketch.js", 1 + currentSource.take(first).count { it == '\n' }, TextRange(first, last))
            },
            onOpenBlockRuntime = { editUserWork { showRuntimeDialog = true } },
            onBlockUndo = {
                if (!sessionViewModel.editorInputLocked && !sessionViewModel.sampleReadOnly && !galleryPresent) {
                    sessionViewModel.undo(sessionViewModel.editorValueState.value)?.let {
                        sessionViewModel.editorValueState.value = it
                    }
                }
            },
            onBlockRedo = {
                if (!sessionViewModel.editorInputLocked && !sessionViewModel.sampleReadOnly && !galleryPresent) {
                    sessionViewModel.redo(sessionViewModel.editorValueState.value)?.let {
                        sessionViewModel.editorValueState.value = it
                    }
                }
            },
            onOpenFullPreview = { openFullPreviewFromEditor() },
            onOpenBlockExamples = {
                openNewWorkStudio(
                    folder = activeWork?.folderName.orEmpty(),
                    category = WorkTemplateCategory.BLOCKS
                )
            },
            documentLeadingContent = if (isLandscape) ({
                CompactWorkTitleButton(
                    title = activeWork?.title,
                    hasUnsavedChanges = hasUnsavedChanges,
                    saveLabel = currentSaveLabel,
                    saveFailed = currentSaveFailed,
                    onClick = ::openWorkGallery,
                    onRetry = currentSaveRetry,
                    textTranslator = { s, args -> uiText(s, *args) }
                )
            }) else null,
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

        Box(Modifier.fillMaxSize()) {

            EditorWorkspaceLayout(
                state = EditorWorkspaceState(
                    isLandscape = isLandscape,
                    editorFocused = (editorFocused || inlineSearchVisible) && !galleryPresent,
                    keyboardVisible = keyboardVisible && !galleryPresent,
                    showResizeHandles = showResizeHandles,
                    showConsole = showConsole,
                    showEditorAccessoryBar = showEditorAccessoryBar && !inlineSearchVisible && !sessionViewModel.sampleReadOnly,
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
                    showParameterPanel = showParameterSheet,
                    isSample = sessionViewModel.sampleReadOnly || activeWork?.isSample == true,
                    galleryVisible = galleryPresent,
                    galleryProgress = 0f,
                    galleryProgressProvider = galleryProgressProvider,
                    gallerySettled = gallerySettled,
                    pipModeWhenEditing = compactPreview && !showExpandedPreview,
                    autoRunEnabled = sessionViewModel.autoRunEnabled,
                    previewHasChanges = previewHasChanges && !preview.isLoading,
                    pipSizePreset = sessionViewModel.pipSizePreset,
                    pipTranslucent = sessionViewModel.pipTranslucent,
                    pipMinimized = sessionViewModel.pipMinimized,
                    pipSnapCorner = sessionViewModel.pipSnapCorner,
                    pipAutoGhosted = sessionViewModel.pipAutoGhosted,
                    blockModeFullscreen = isBlockModeFullscreen,
                    parameterFullscreen = showParameterSheet && (!isLandscape || parameterFullscreen) && !galleryPresent
                ),
                actions = EditorWorkspaceActions(
                    onSplitChanged = { landscapePreviewFraction = it },
                    onSplitLabelChanged = { showLandscapeSplitLabel = it },
                    onPortraitDraggingChanged = { portraitRatioDragging = it },
                    onDraftPreviewRatio = { updateActiveWorkPreviewRatio(it, persist = false) },
                    onCommitPreviewRatio = { workManagementViewModel.commitPreviewRatio() },
                    onSavePreviewRatio = { updateActiveWorkPreviewRatio(it) },
                    onToggleAutoRun = { sessionViewModel.autoRunEnabled = !sessionViewModel.autoRunEnabled },
                    onRunNow = { runSketch() },
                    onPipSizePresetChanged = { sessionViewModel.pipSizePreset = it },
                    onPipTranslucentChanged = { sessionViewModel.pipTranslucent = it },
                    onPipMinimizedChanged = { sessionViewModel.pipMinimized = it },
                    onPipSnapCornerChanged = { sessionViewModel.pipSnapCorner = it },
                    onPipCanvasTap = { coords, offset ->
                        sessionViewModel.pipCanvasTapHandler?.invoke(coords, offset)
                    },
                    onExpandPipFullscreen = { openFullPreviewFromEditor() }
                ),
                slots = EditorWorkspaceSlots(
                    workBar = { WorkBar() }, landscapeBar = { LandscapeUnifiedBar() },
                    controlBar = { ControlBar() }, console = { ConsolePanel(it) },
                    previewActions = { PreviewActionsTray(it) }, editor = { EditorArea(it) },
                    pipPreview = { PipPreviewArea(it) },
                    parameterPanel = { modifier ->
                        LiveParameterPanel(
                            work = activeWork,
                            editorText = editorText,
                            sessionViewModel = sessionViewModel,
                            workManagementViewModel = workManagementViewModel,
                            preview = preview,
                            textTranslator = { source, args -> uiText(source, *args) },
                            onDismiss = {
                                showParameterSheet = false
                                parameterFullscreen = false
                            },
                            onJumpToParameter = { paramName ->
                                val currentSources = activeWork?.files.orEmpty().mapValues { (name, code) ->
                                    sessionViewModel.fileDrafts["${activeWork?.id}/$name"] ?: code
                                } + ("sketch.js" to editorText)
                                findParameterDeclarationLine(currentSources, paramName)?.let { (file, line) ->
                                    navigateToSource(file, line)
                                    showParameterSheet = false
                                    parameterFullscreen = false
                                }
                            },
                            modifier = modifier,
                            isFullscreen = !isLandscape || parameterFullscreen,
                            onToggleFullscreen = if (isLandscape) ({ setParameterFullscreenMode(!parameterFullscreen) }) else null
                        )
                    },
                    completions = { CompletionBar(it) }, accessory = { EditorAccessoryBar(it) },
                    preview = { MainPreviewArea(it) },
                    gallery = { InlineGallery(it) },
                    landscapeGalleryBar = {},
                    landscapeRail = { LandscapeRail(it) },
                    previewChrome = { modifier ->
                        WorkGalleryPreviewChrome(galleryPreviewBounds, modifier, visible = liftPreviewChrome) {
                            Box(Modifier.fillMaxSize().galleryChrome(galleryProgressProvider, GalleryPart.SECONDARY)) {
                                PreviewChrome()
                            }
                        }
                    },
                    galleryPreview = { modifier ->
                        WorkGalleryPreview(
                            bitmap = galleryPreviewBitmap,
                            start = galleryPreviewBounds, end = galleryThumbnailBounds,
                            progressProvider = galleryProgressProvider, modifier = modifier,
                            visible = (galleryPresent && !gallerySettled) || (!galleryPresent && galleryHandoffActive),
                            opacityProvider = galleryHandoffOpacityProvider
                        )
                    }
                ),
                textTranslator = { source, args -> uiText(source, *args) },
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .graphicsLayer { alpha = if (showSettings) 0f else 1f }
                    .then(if (showSettings) Modifier.clearAndSetSemantics { hideFromAccessibility() } else Modifier)
                    .focusProperties { if (showSettings) canFocus = false }
                    .onPreviewKeyEvent { showSettings }
                    .blockGalleryInput(showSettings)
            )

            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(110)),
                modifier = Modifier.fillMaxSize()
            ) {
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
                            val owner = activeWorkId
                            val activation = sessionViewModel.blockEditorActivationGeneration
                            if (sessionViewModel.blockEditorFlusher == null || !sessionViewModel.blockModeState(owner).value) {
                                workMenuExpanded = true
                            } else {
                                editorToolScope.launch {
                                    if (flushOwnedBlockEditor(owner, activation)) workMenuExpanded = true
                                }
                            }
                        },
                        onImportFont = { fontPicker.launch(arrayOf("*/*")) },
                        onResetFont = { settingsViewModel.resetFont() },
                        onImportP5 = { workManagementViewModel.resetP5Results(); showP5Import = true },
                        onImportJs = { importJsLauncher.launch(arrayOf("application/javascript", "text/javascript", "text/plain", "application/octet-stream")) },
                        onExportJs = { exportJsLauncher.launch(safeJsFileName(activeWork?.title ?: "sketch")) },
                        onExportHtml = { exportHtmlLauncher.launch(safeHtmlFileName(activeWork?.title ?: "sketch")) },
                        onShowOnboarding = {
                            showSettings = false
                            settingsViewModel.replayOnboarding()
                        },
                        onShowPracticeTour = {
                            showSettings = false
                            settingsViewModel.replayOnboarding()
                        },
                        onExportBackup = { exportBackupLauncher.launch("Edit-RiN-backup.zip") },
                        onImportBackup = { importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                        openExternalUrl = { onOpenExternalUrl(it) },
                        onTryGuideCode = { title, code ->
                            showSettings = false
                            workManagementViewModel.createWorkWithCode(title, code)
                        }
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                    folderName = selectedFolderName,
                    assets = assets,
                    updateViewModel = updateViewModel,
                    textTranslator = { text, arguments -> uiText(text, *arguments) }
                )
            }
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

    if (showOutlineDialog) {
        CodeOutlineDialog(
            file = editingFile,
            source = editingText,
            codeFontFamily = codeFontFamily,
            text = ::uiText,
            onJump = { line -> navigateToSource(editingFile, line) },
            onDismiss = { showOutlineDialog = false }
        )
    }

    EditorAuthoringDialogs(
        models = models,
        preview = preview,
        activeWork = activeWork,
        editorText = editorText,
        editingValue = editingValue,
        editorFocusRequester = editorFocusRequester,
        codeFontFamily = codeFontFamily,
        workSaving = workSaving,
        showSnippets = showSnippets,
        onSnippetsChange = { showSnippets = it },
        showReferenceDialog = showReferenceDialog,
        onReferenceDialogChange = { showReferenceDialog = it },
        referenceSearchQuery = referenceSearchQuery,
        showScreenshotScale = showScreenshotScale,
        onScreenshotScaleChange = { showScreenshotScale = it },
        onPendingScreenshotScale = { pendingScreenshotScale = it },
        showRecordingFormatDialog = showRecordingFormatDialog,
        onRecordingFormatDialogChange = { showRecordingFormatDialog = it },
        isRecordingOrCountingDown = isRecordingOrCountingDown,
        mp4BitrateMbps = mp4BitrateMbps,
        onMp4BitrateChange = { mp4BitrateMbps = it },
        recordingCountdownSeconds = recordingCountdownSeconds,
        onRecordingCountdownChange = { recordingCountdownSeconds = it },
        onRequestPreviewRecording = ::requestPreviewRecording,
        showColorPickerDialog = showColorPickerDialog,
        onColorPickerDialogChange = { showColorPickerDialog = it },
        colorPickerInitial = colorPickerInitial,
        activeColorTarget = activeColorTarget,
        onLastColorPickerColor = { lastColorPickerColor = it },
        showUserGuide = showUserGuide,
        onUserGuideChange = { showUserGuide = it },
        applyEditorChange = ::applyEditorChange,
        textTranslator = { s, args -> uiText(s, *args) }
    )

    PreviewMediaSheets(models, preview, mediaActions, editorValue)

    EditorWorkDialogs(
        models = models,
        pendingRevision = pendingRevision,
        dialogCoordinator = dialogCoordinator,
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
        onImportZip = { importWorkZip.launch(arrayOf("application/zip", "application/octet-stream")) },
        onImportP5 = { workManagementViewModel.resetP5Results(); showP5Import = true }
    ) { pendingRevision = it }

    EditorProjectDialogs(
        models = models,
        preview = preview,
        activeWork = activeWork,
        activeWorkId = activeWorkId,
        editorText = editorText,
        editingValue = editingValue,
        editingJavaScript = editingJavaScript,
        codeFontFamily = codeFontFamily,
        fontFeatures = fontFeatures,
        colors = colors,
        workSaving = workSaving,
        assetPicker = assetPicker,
        showP5Import = showP5Import,
        onP5ImportChange = { showP5Import = it },
        showAssets = showAssets,
        onAssetsChange = { showAssets = it },
        onSetAssetTargetId = { assetTargetId = it },
        showProjectFilesDialog = showProjectFilesDialog,
        onProjectFilesDialogChange = { showProjectFilesDialog = it },
        showAuxiliaryFileEditor = showAuxiliaryFileEditor,
        onAuxiliaryFileEditorChange = { showAuxiliaryFileEditor = it },
        auxiliaryFileName = auxiliaryFileName,
        onAuxiliaryFileNameChange = { auxiliaryFileName = it },
        originalAuxiliaryFileName = originalAuxiliaryFileName,
        onOriginalAuxiliaryFileNameChange = { originalAuxiliaryFileName = it },
        auxiliaryFileContent = auxiliaryFileContent,
        onAuxiliaryFileContentChange = { auxiliaryFileContent = it },
        onSelectEditorFile = { selectedEditorFile = it },
        applyEditorChange = ::applyEditorChange,
        changeAssets = ::changeAssets,
        textTranslator = { s, args -> uiText(s, *args) }
    )

    if (sessionViewModel.showInspectorSheet) {
        ProjectInspectorSheet(
            work = activeWork,
            autoRunEnabled = sessionViewModel.autoRunEnabled,
            onToggleAutoRun = { sessionViewModel.autoRunEnabled = it },
            onOpenProjectFilesDialog = {
                editUserWork {
                    dialogCoordinator.push(WorkDialogDestination.ProjectFiles(activeWorkId, sessionViewModel.blockEditorActivationGeneration))
                    showProjectFilesDialog = true
                }
            },
            onOpenAssetManagerDialog = {
                editUserWork {
                    focusManager.clearFocus(force = true)
                    dialogCoordinator.push(WorkDialogDestination.AssetManager(activeWorkId, sessionViewModel.blockEditorActivationGeneration))
                    showAssets = true
                }
            },
            onOpenRuntimeDialog = {
                editUserWork {
                    dialogCoordinator.push(WorkDialogDestination.RuntimeSettings(activeWorkId, sessionViewModel.blockEditorActivationGeneration))
                    showRuntimeDialog = true
                }
            },
            onOpenAspectRatioDialog = {
                editUserWork {
                    dialogCoordinator.push(WorkDialogDestination.AspectRatio(activeWorkId, sessionViewModel.blockEditorActivationGeneration))
                    showAspectRatioDialog = true
                }
            },
            onOpenSnapshotSheet = {
                editUserWork {
                    dialogCoordinator.push(WorkDialogDestination.HistoryAndSnapshots(activeWorkId, sessionViewModel.blockEditorActivationGeneration, initialTab = 2))
                    showSnapshotSheet = true
                }
            },
            onOpenHistoryDialog = {
                editUserWork {
                    dialogCoordinator.push(WorkDialogDestination.HistoryAndSnapshots(activeWorkId, sessionViewModel.blockEditorActivationGeneration, initialTab = 1))
                    showHistoryDialog = true
                }
            },
            onOpenTagsDialog = {
                editUserWork {
                    dialogCoordinator.push(WorkDialogDestination.Tags(activeWorkId, sessionViewModel.blockEditorActivationGeneration))
                    workManagementViewModel.editingTagsWorkId = activeWork?.id
                }
            },
            onSelectAspectRatio = { ratio -> updateActiveWorkPreviewRatio(ratio) },
            onDismiss = { sessionViewModel.showInspectorSheet = false },
            codeFontFamily = codeFontFamily,
            textTranslator = { s, args -> uiText(s, *args) },
            currentAspectRatio = workManagementViewModel.previewRatio(activeWork)
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
