package com.hikariatelier.app

import android.app.Activity
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
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
    val codeFontFamily = customFontFamily ?: FontFamily.Monospace
    val fontFeatures = if (settingsViewModel.fontLigatures)
        "'liga' 1, 'clig' 1, 'calt' 1" else "'liga' 0, 'clig' 0, 'calt' 0"
    fun uiText(source: String, vararg arguments: Any?): String {
        val deviceLanguage = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language ?: "en"
        val translated = translateUi(source, resolveUiLanguage(settingsViewModel.appLanguage, deviceLanguage))
        return if (arguments.isEmpty()) translated else String.format(java.util.Locale.ROOT, translated, *arguments)
    }
    val focusManager =
        LocalFocusManager.current

    val configuration =
        LocalConfiguration.current

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

    EditorWindowEffects(isLandscape, manualRotation, showStatusBar)

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

    var isError by preview::isError

    var isPaused by preview::isPaused

    var editorFocused by remember {
        mutableStateOf(false)
    }

    var showSettings by rememberSaveable {
        mutableStateOf(false)
    }

    var showUserGuide by rememberSaveable {
        mutableStateOf(false)
    }

    var showAddDialog by workManagementViewModel::showAddDialog
    var showDeleteDialog by workManagementViewModel::showDeleteDialog
    var showRenameDialog by workManagementViewModel::showRenameDialog
    val editingTagsWork = workManagementViewModel.editingTagsWork
    var workMenuExpanded by workManagementViewModel::workMenuExpanded
    var workSettingsMenuExpanded by workManagementViewModel::workSettingsMenuExpanded
    var workActionsMenuExpanded by workManagementViewModel::workActionsMenuExpanded

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

    val currentSnapshots = snapshotViewModel.currentSnapshots
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
    var recordingStartedAt by recordingViewModel::recordingStartedAt
    var recordingLimitMillis by recordingViewModel::recordingLimitMillis
    var recordingElapsedMillis by recordingViewModel::recordingElapsedMillis
    var savedPreviewMedia by recordingViewModel::savedPreviewMedia
    var shareCardArtwork by recordingViewModel::shareCardArtwork
    var shareCardAuthor by settingsViewModel::shareCardAuthor
    var isRecordingSaving by recordingViewModel::isRecordingSaving
    var mp4BitrateMbps by settingsViewModel::mp4BitrateMbps
    var xShareText by settingsViewModel::xShareText
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

    LaunchedEffect(pendingRecordingFormat) {
        val format = pendingRecordingFormat ?: return@LaunchedEffect
        while (recordingCountdownRemaining > 0) {
            delay(1000)
            recordingCountdownRemaining--
        }
        if (pendingRecordingFormat == format) {
            pendingRecordingFormat = null
            startSelectedRecording(format)
        }
    }

    LaunchedEffect(isPreviewRecording, isRecordingSaving, recordingStartedAt) {
        while (isPreviewRecording && !isRecordingSaving) {
            recordingElapsedMillis = (SystemClock.elapsedRealtime() - recordingStartedAt)
                .coerceIn(0L, recordingLimitMillis)
            delay(200)
        }
    }

    var previewActionsExpanded by remember {
        mutableStateOf(false)
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

    var searchQuery by searchReplaceViewModel::searchQuery
    var replacementText by searchReplaceViewModel::replacementText
    var searchWholeWork by searchReplaceViewModel::searchWholeWork
    var searchMatchCase by searchReplaceViewModel::searchMatchCase
    var goToLineText by searchReplaceViewModel::goToLineText
    val consoleEntries = consoleViewModel.entries

    val editorFocusRequester =
        remember {
            FocusRequester()
        }

    val lastSavedText by sessionViewModel.lastSavedTextState

    val hasUnsavedChanges =
        editorText != lastSavedText || sessionViewModel.fileDrafts.keys.any { it.startsWith("$activeWorkId/") } ||
            workManagementViewModel.hasPendingMetadata(activeWorkId)

    var selectedEditorFile by rememberSaveable(activeWorkId) { mutableStateOf("sketch.js") }
    val editingFile = selectedEditorFile.takeIf { it in activeWork?.files.orEmpty() } ?: "sketch.js"
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
    val undoStack = if (editingFile == "sketch.js") sessionViewModel.undoStack else
        sessionViewModel.fileUndoStacks.getOrPut(editingKey) { mutableStateListOf() }
    val redoStack = if (editingFile == "sketch.js") sessionViewModel.redoStack else
        sessionViewModel.fileRedoStacks.getOrPut(editingKey) { mutableStateListOf() }
    var pendingRevision by remember { mutableStateOf<WorkRevision?>(null) }
    var consoleHeight by consoleViewModel::consoleHeight
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


    fun applyEditorChange(
        nextValue: TextFieldValue
    ) {
        sessionViewModel.applyChange(editingValue, nextValue, undoStack, redoStack)
        editingValue = nextValue
    }

    fun undoEditorChange() {
        val restored = sessionViewModel.undo(editingValue, undoStack, redoStack) ?: return
        editingValue = restored
        editorFocusRequester.requestFocus()
    }

    fun redoEditorChange() {
        val restored = sessionViewModel.redo(editingValue, undoStack, redoStack) ?: return
        editingValue = restored
        editorFocusRequester.requestFocus()
    }

    BackHandler(enabled = editorFocused && !showSettings && !showUserGuide) { focusManager.clearFocus(force = true) }

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
        sessionViewModel.fileDrafts.toMap(),
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
    var navigationTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }

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
        navigationTarget = file to line
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

    fun restoreCurrentWork() { workManagementViewModel.restoreCurrentWork() }
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
    val currentWorkEventHandler = rememberUpdatedState<(WorkEvent) -> Unit> { event ->
        if (event.failure) isError = true
        else if (event.clearError || event.rerun || event.forceRun) isError = false
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
        animationSpec = tween(
            durationMillis = 260,
            easing = FastOutSlowInEasing
        ),
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
            modifier = modifier
        )

        if (workMenuExpanded) {
            var sort by settingsViewModel::workSort
            val previewRevision = workManagementViewModel.previewRevision
            val updatedPreviewId = workManagementViewModel.updatedPreviewId
            LaunchedEffect(Unit) {
                val view = preview.webView
                val workId = preview.session.workId
                val token = preview.session.assets.token
                if (view != null && workId != null && !isError && view.url == previewUrl(token)) {
                    captureWorkPreview(view) { encoded ->
                        if (preview.session.workId == workId && preview.session.assets.token == token) {
                            lifecycleScope.launch {
                                if (storeWorkPreview(workPreviewFile(cacheDir, workId), encoded)) {
                                    workManagementViewModel.notifyPreviewUpdated(workId)
                                }
                            }
                        }
                    }
                }
            }
            WorkGallerySheet(
                visible = true,
                works = works,
                activeWorkId = activeWorkId,
                hasUnsavedChanges = hasUnsavedChanges,
                initialSort = sort,
                cacheDir = cacheDir,
                previewRevision = previewRevision,
                updatedPreviewId = updatedPreviewId,
                assetBusy = assetBusy,
                onSortChange = { sort = it },
                onSelectWork = { work, openMenu -> workManagementViewModel.selectWork(work.id, openMenu) },
                onAddWork = { workMenuExpanded = false; showAddDialog = true },
                onDismiss = { workMenuExpanded = false },
                onTogglePin = { target -> workManagementViewModel.togglePin(target.id) },
                onEditTags = { target -> workManagementViewModel.editingTagsWorkId = target.id },
                onDeleteGlobalTag = { tag -> workManagementViewModel.deleteGlobalTag(tag) },
                textTranslator = { s, args -> uiText(s, *args) },
                windowSetup = { KeepLandscapeDialogImmersive(enabled = isLandscape) }
            )
        }
    }

    @Composable
    fun WorkActions() {
        WorkActionsMenu(
            activeWork = activeWork,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            canDelete = works.size > 1,
            hasUnsavedChanges = hasUnsavedChanges,
            isLandscape = isLandscape,
            manualRotation = manualRotation,
            wideWorkPanels = wideWorkPanels,
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
            onSearch = { showSearchDialog = true },
            onFormat = {
                val formatted = formatJavaScript(editingText)
                if (formatted != editingText) {
                    applyEditorChange(TextFieldValue(formatted, TextRange(0)))
                }
                editorFocusRequester.requestFocus()
            },
            onSnapshot = { showSnapshotSheet = true },
            onHistory = { showHistoryDialog = true },
            onAspectRatio = { showAspectRatioDialog = true },
            onTogglePin = { activeWork?.let { workManagementViewModel.togglePin(it.id) } },
            onEditTags = { workManagementViewModel.editingTagsWorkId = activeWork?.id },
            onRename = { showRenameDialog = true },
            onDuplicate = { workManagementViewModel.duplicateWork() },
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
            onDelete = { showDeleteDialog = true },
            onOpenProjectFiles = { showProjectFilesDialog = true },
            onOpenAssets = { focusManager.clearFocus(force = true); showAssets = true },
            onOpenRuntime = { showRuntimeDialog = true },
            textTranslator = { s, args -> uiText(s, *args) },
            windowSetup = { KeepLandscapeDialogImmersive(enabled = isLandscape) }
        )
    }

    @Composable
    fun WorkBar() {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal =
                            12.dp,

                        vertical =
                            2.dp
                    ),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
            ) {
                WorkSelector()
            }

            WorkActions()
        }
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
            onOpenParameters = {
                previewActionsExpanded = false
                showExpandedPreview = false
                showParameterSheet = true
            },
            onScreenshot = {
                preview.requestScreenshot()
                previewActionsExpanded = false
            },
            onShareCard = {
                preview.requestScreenshot(forShareCard = true)
                previewActionsExpanded = false
            },
            onRecordToggle = {
                if (pendingRecordingFormat != null) {
                    cancelRecordingCountdown()
                } else if (isRecordingSaving) {
                    // wait
                } else if (isPreviewRecording) {
                    preview.webView?.evaluateJavascript(
                        "window.__editKiroStopRecording?.()",
                        null
                    )
                } else {
                    showRecordingFormatDialog = true
                }
                previewActionsExpanded = false
            },
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
                colors = colors,
                height = 34.dp,
                buttonSize = 32.dp,
                iconSize = 17.dp,
                onTogglePause = {
                    isPaused = !isPaused
                    preview.webView?.evaluateJavascript(
                        if (isPaused) "pauseSketch()" else "resumeSketch()", null
                    )
                },
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

            PreviewActionsToggleButton()

            SaveRestoreControls(
                hasUnsavedChanges = hasUnsavedChanges,
                colors = colors,
                height = 34.dp,
                buttonSize = 32.dp,
                iconSize = 18.dp,
                onRestore = { restoreCurrentWork() },
                onSave = { saveCurrentWork() },
                textTranslator = { s, args -> uiText(s, *args) }
            )

            WorkActions()
        }
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
            onClearEditorFocus = { focusManager.clearFocus(force = true) }
        ) {
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
                    onScreenshot = {
                        preview.requestScreenshot()
                        previewActionsExpanded = false
                    },
                    onShareCard = {
                        preview.requestScreenshot(forShareCard = true)
                        previewActionsExpanded = false
                    },
                    onOpenParameters = {
                        previewActionsExpanded = false
                        showExpandedPreview = false
                        showParameterSheet = true
                    },
                    onToggleRecording = {
                        if (pendingRecordingFormat != null) {
                            cancelRecordingCountdown()
                        } else if (isRecordingSaving) {
                            // Wait for the current recording to finish saving.
                        } else if (isPreviewRecording) {
                            preview.webView?.evaluateJavascript(
                                "window.__editKiroStopRecording?.()",
                                null
                            )
                        } else {
                            showRecordingFormatDialog = true
                        }
                        previewActionsExpanded = false
                    },
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
                    onStopRecording = {
                        preview.webView?.evaluateJavascript("window.__editKiroStopRecording?.()", null)
                    },
                    colors = colors,
                    textTranslator = { s, args -> uiText(s, *args) }
                )
        }
    }

    @Composable
    fun MainPreviewArea(
        modifier: Modifier
    ) {
        if (showExpandedPreview) {
            Box(modifier = modifier)
        } else {
            PreviewArea(
                modifier = modifier.onSizeChanged { size ->
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
                .height(if (isLandscape) 40.dp else 50.dp)
                .padding(vertical = if (isLandscape) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RunStatusControls(
                isError = isError,
                isPaused = isPaused,
                colors = colors,
                height = if (isLandscape) 34.dp else 38.dp,
                buttonSize = 34.dp,
                iconSize = 19.dp,
                onTogglePause = {
                    isPaused = !isPaused
                    preview.webView?.evaluateJavascript(
                        if (isPaused) "pauseSketch()" else "resumeSketch()", null
                    )
                },
                onReload = { runSketch() },
                textTranslator = { s, args -> uiText(s, *args) }
            )

            Spacer(Modifier.width(6.dp))

            ConsoleButton(
                hasError = consoleEntries.any { it.level == ConsoleLevel.ERROR },
                entryCount = consoleEntries.size,
                colors = colors,
                height = if (isLandscape) 34.dp else 38.dp,
                minWidth = 44.dp,
                iconSize = 17.dp,
                onClick = { showConsole = !showConsole },
                textTranslator = { s, args -> uiText(s, *args) }
            )

            Spacer(Modifier.width(6.dp))

            PreviewActionsToggleButton()

            Spacer(Modifier.weight(1f))

            SaveRestoreControls(
                hasUnsavedChanges = hasUnsavedChanges,
                colors = colors,
                height = if (isLandscape) 34.dp else 38.dp,
                buttonSize = 34.dp,
                iconSize = 20.dp,
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
        com.hikariatelier.app.EditorAccessoryBar(
            editingValue = editingValue,
            compactAccessoryKeys = compactAccessoryKeys,
            colors = colors,
            codeFontFamily = codeFontFamily,
            undoAvailable = undoStack.isNotEmpty(),
            redoAvailable = redoStack.isNotEmpty(),
            onUndo = { undoEditorChange() },
            onRedo = { redoEditorChange() },
            onSearch = { showSearchDialog = true },
            onFormat = {
                val formatted = formatJavaScript(editingText)
                if (formatted != editingText) {
                    applyEditorChange(TextFieldValue(formatted, TextRange(0)))
                    editorFocusRequester.requestFocus()
                }
            },
            onApplyEdit = { value ->
                applyEditorChange(value)
                editorFocusRequester.requestFocus()
            },
            lastColorPickerColor = lastColorPickerColor,
            onOpenColorPicker = { target, color ->
                activeColorTarget = target
                colorPickerInitial = color
                showColorPickerDialog = true
            },
            showAccessoryNavigation = showAccessoryNavigation,
            showAccessorySymbols = showAccessorySymbols,
            textTranslator = { s, args -> uiText(s, *args) },
            modifier = modifier
        )
    }

    val completionSources = remember(activeWorkId, editingFile, editingText, editorText,
        activeWork?.files, sessionViewModel.auxiliaryEditorGeneration, codeCompletion) {
        if (!codeCompletion) emptyMap()
        else projectSearchSources(activeWorkId, editorText, activeWork?.files.orEmpty(),
            sessionViewModel.fileDrafts).toMutableMap().apply { put(editingFile, editingText) }
    }
    val projectCompletionSnapshot by produceState<Pair<String, List<ProjectSymbol>>>(
        "" to emptyList(), activeWorkId, completionSources
    ) {
        if (completionSources.values.sumOf { it.length } >= 8_000) delay(120)
        value = activeWorkId to withContext(Dispatchers.Default) { projectSymbols(completionSources) }
    }
    val projectCompletionSymbols = projectCompletionSnapshot.second.takeIf {
        projectCompletionSnapshot.first == activeWorkId
    }.orEmpty()
    val editorSuggestions = remember(editingValue.text, editingValue.selection, editorFocused,
        codeCompletion, editingFile, projectCompletionSymbols) {
        if (!editorFocused || !codeCompletion) emptyList()
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
            visible = editorFocused && codeCompletion,
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
            onUpdateEditingValue = { editingValue = it },
            onApplyEditorChange = { applyEditorChange(it) },
            editorFocused = editorFocused,
            onFocusChange = { editorFocused = it },
            editorFocusRequester = editorFocusRequester,
            focusManager = focusManager,
            consoleEntries = consoleEntries,
            sessionViewModel = sessionViewModel,
            codeFontFamily = codeFontFamily,
            editorFontSize = editorFontSize,
            editorWordWrap = editorWordWrap,
            autoIndent = autoIndent,
            showLineNumbers = showLineNumbers,
            fontFeatures = fontFeatures,
            navigationSequence = navigationSequence,
            navigationTarget = navigationTarget,
            onClearNavigationTarget = { navigationTarget = null },
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
                        val ids = works.map { it.id }.toSet()
                        val titles = works.map { it.title.lowercase() }.toSet()
                        val missing = workManagementViewModel.officialSamples.filter { it.id !in ids && it.title.lowercase() !in titles }
                        if (missing.isEmpty()) Toast.makeText(context,
                            uiText("すべての公式サンプル作品は既に追加されています"), Toast.LENGTH_SHORT).show()
                        else workManagementViewModel.addSamples(missing)
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
                statusBarForcedHidden = isLandscape,
                assets = assets,
                updateViewModel = updateViewModel,
                textTranslator = { text, arguments -> uiText(text, *arguments) }
            )

        } else {

            EditorWorkspaceLayout(
                state = EditorWorkspaceState(
                    isLandscape = isLandscape,
                    editorFocused = editorFocused,
                    keyboardVisible = keyboardVisible,
                    showResizeHandles = showResizeHandles,
                    showConsole = showConsole,
                    showEditorAccessoryBar = showEditorAccessoryBar,
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
                    hasVisibleCompletions = editorSuggestions.isNotEmpty()
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
                    preview = { MainPreviewArea(it) }
                ),
                textTranslator = { source, args -> uiText(source, *args) },
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
            )
    }

    }

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
            KeepLandscapeDialogImmersive(enabled = true)
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

    if (showParameterSheet) {
        LiveParameterSheet(activeWork, editorText, sessionViewModel, workManagementViewModel, preview,
            ParameterSheetLayout(isLandscape, landscapeEditorOnLeft, 1f - animatedLandscapePreviewFraction, showStatusBar),
            { source, args -> uiText(source, *args) }, onDismiss = { showParameterSheet = false })
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
            onRename = { old, new ->
                activeWork?.let { work ->
                    val updated = work.assets.toMutableMap()
                    updated.remove(old)?.let { updated[new] = it }
                    changeAssets(work.id, updated)
                }
            },
            onDelete = { name -> activeWork?.let { changeAssets(it.id, it.assets.toMap() - name) } },
            onClose = { showAssets = false },
            onPreview = { name, asset -> previewAsset = name to asset },
            onInsert = { name, asset ->
                applyEditorChange(insertAtSelection(editingValue, assetLoaderCode(name, asset)))
                showAssets = false
            }
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
