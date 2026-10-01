package com.hikariatelier.app

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.core.os.ConfigurationCompat

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.launch

@Composable
internal fun EditorWorkDialogs(models: EditorModels, pendingRevision: WorkRevision?, onRevisionChange: (WorkRevision?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val wideWorkPanels = configuration.screenWidthDp >= 640 && configuration.fontScale <= 1.25f
    val colors = MaterialTheme.colorScheme
    val sessionViewModel = models.session
    val workManagementViewModel = models.works
    val snapshotViewModel = models.snapshots
    val settingsViewModel = models.settings
    val works by sessionViewModel.worksState
    val activeWorkId by sessionViewModel.activeWorkIdState
    val activeWork = works.find { it.id == activeWorkId } ?: works.firstOrNull()
    val editorText = sessionViewModel.editorValueState.value.text
    val codeFontFamily = settingsViewModel.customFontFamily ?: FontFamily.Monospace
    val assetBusy = sessionViewModel.assetBusy
    val selectedFolderUri = workManagementViewModel.selectedFolderUri
    var showSnapshotSheet by workManagementViewModel::showSnapshotSheet
    var showHistoryDialog by workManagementViewModel::showHistoryDialog
    var showAspectRatioDialog by workManagementViewModel::showAspectRatioDialog
    var showRuntimeDialog by workManagementViewModel::showRuntimeDialog
    var showAddDialog by workManagementViewModel::showAddDialog
    var showRenameDialog by workManagementViewModel::showRenameDialog
    var showDeleteDialog by workManagementViewModel::showDeleteDialog
    val editingTagsWork = workManagementViewModel.editingTagsWork
    val currentSnapshots = snapshotViewModel.currentSnapshots
    val snapshotsLoading = snapshotViewModel.loading
    val snapshotBusy = sessionViewModel.snapshotOperationWorkId != null
    val previewRatioSelection = workManagementViewModel.previewRatio(activeWork)
    val devicePreviewRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.coerceAtLeast(1)
    fun uiText(source: String, vararg arguments: Any?): String {
        val language = ConfigurationCompat.getLocales(configuration)[0]?.language ?: "en"
        val translated = translateUi(source, resolveUiLanguage(settingsViewModel.appLanguage, language))
        return if (arguments.isEmpty()) translated else String.format(java.util.Locale.ROOT, translated, *arguments)
    }
    val snapshotError = snapshotViewModel.error?.let { uiText(it) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) workManagementViewModel.chooseFolder(uri)
    }
    WorkRecoveryDialogs(workManagementViewModel, { uiText(it) }) { folderLauncher.launch(selectedFolderUri) }
    if (workManagementViewModel.showTemplateManager) UserTemplateManagerSheet(
        templates = workManagementViewModel.userTemplates, currentWorkTitle = activeWork?.title,
        busy = assetBusy || snapshotBusy, loadFailed = workManagementViewModel.templateLoadFailed,
        text = { uiText(it) }, onRetry = { workManagementViewModel.retryUserTemplates() },
        onRename = { id, title -> workManagementViewModel.renameUserTemplate(id, title) },
        onUpdate = { workManagementViewModel.updateUserTemplate(it) },
        onDelete = { workManagementViewModel.deleteUserTemplate(it) },
        onDismiss = { workManagementViewModel.showTemplateManager = false }
    )
    if (showSnapshotSheet) {
        val currentContent = activeWork?.let {
            currentSnapshotContent(workManagementViewModel.workForSnapshot(it), editorText, sessionViewModel.fileDrafts)
        } ?: SnapshotContent(editorText, emptyMap(), emptyMap())
        WorkSnapshotSheet(
            title = uiText("スナップショット"),
            subtitle = activeWork?.title ?: uiText("作品未選択"),
            snapshots = currentSnapshots,
            current = currentContent,
            loading = snapshotsLoading,
            busy = snapshotBusy || assetBusy,
            error = snapshotError,
            codeFontFamily = codeFontFamily,
            colors = colors,
            isLandscape = isLandscape,
            dismissEnabled = !sessionViewModel.snapshotRestoring,
            onRetry = { snapshotViewModel.load() },
            onCreateSnapshot = { snapshotViewModel.create() },
            onRestoreSnapshot = { snapshotViewModel.restore(it) },
            onDeleteSnapshot = { snapshotViewModel.delete(it) },
            onDismiss = { if (!sessionViewModel.snapshotRestoring) showSnapshotSheet = false },
            textTranslator = { s, args -> uiText(s, *args) },
            windowSetup = { KeepLandscapeDialogImmersive() }
        )
    }

    if (showHistoryDialog) {
        WorkHistoryDialog(
            revisions = activeWork?.revisions.orEmpty(),
            colors = colors,
            codeFontFamily = codeFontFamily,
            onSelectRevision = { onRevisionChange(it) },
            onDismiss = { showHistoryDialog = false },
            textTranslator = { s, args -> uiText(s, *args) }
        )
    }

    pendingRevision?.let { revision ->
        WorkRevisionDiffDialog(
            revision = revision,
            editorText = editorText,
            codeFontFamily = codeFontFamily,
            onRestore = { workManagementViewModel.restoreRevision(revision) },
            onDismiss = { onRevisionChange(null) },
            textTranslator = { s, args -> uiText(s, *args) }
        )
    }

    WorkRuntimeDialog(
        visible = showRuntimeDialog,
        initialP5Version = normalizedP5Version(activeWork?.p5Version),
        initialSoundEnabled = activeWork?.p5SoundEnabled == true,
        initialLibraries = activeWork?.libraries.orEmpty(),
        uiText = { uiText(it) },
        onSave = { version, soundEnabled, libraries ->
            workManagementViewModel.saveRuntime(version, soundEnabled, libraries)
        },
        onDismiss = { showRuntimeDialog = false }
    )

    AspectRatioDialog(
        visible = showAspectRatioDialog,
        wideWorkPanels = wideWorkPanels,
        isLandscape = isLandscape,
        devicePreviewRatio = devicePreviewRatio,
        previewRatioSelection = previewRatioSelection,
        codeFontFamily = codeFontFamily,
        uiText = { uiText(it) },
        onSelectRatio = { workManagementViewModel.savePreviewRatio(it) },
        onDismiss = { showAspectRatioDialog = false }
    )



    if (selectedFolderUri == null && workManagementViewModel.loadFailure == null && workManagementViewModel.pendingDraft == null) {
        FolderSelectionPromptDialog(
            colors = colors,
            onChooseFolder = { folderLauncher.launch(null) },
            textTranslator = { s, args -> uiText(s, *args) },
            windowSetup = { KeepLandscapeDialogImmersive() }
        )
    }

    val missingOfficialSamples = remember(works) {
        val existingIds = works.map { it.id }.toSet()
        val existingTitles = works.map { it.title.lowercase() }.toSet()
        workManagementViewModel.officialSamples.filter { sample ->
            sample.id !in existingIds && sample.title.lowercase() !in existingTitles
        }
    }
    var showSampleUpdateDialog by workManagementViewModel::showSamplePrompt

    if (showSampleUpdateDialog && workManagementViewModel.loadFailure == null && workManagementViewModel.pendingDraft == null &&
        missingOfficialSamples.isNotEmpty() && selectedFolderUri != null &&
        settingsViewModel.samplePromptVersion < BuildConfig.VERSION_CODE) {
        val sampleNames = missingOfficialSamples.joinToString(", ") { it.title }
        SampleUpdatePromptDialog(
            sampleNames = sampleNames,
            onAddSamples = {
                workManagementViewModel.addSamples(missingOfficialSamples, fromPrompt = true)
            },
            onDismiss = {
                workManagementViewModel.dismissSamplePrompt()
            },
            textTranslator = { s, args -> uiText(s, *args) },
            windowSetup = { KeepLandscapeDialogImmersive() }
        )
    }

    if (showAddDialog) {
        AddWorkDialog(
            worksCount = works.size,
            userTemplates = workManagementViewModel.userTemplates,
            busy = assetBusy || snapshotBusy,
            templateLoadFailed = workManagementViewModel.templateLoadFailed,
            onRetryTemplates = { workManagementViewModel.retryUserTemplates() },
            onDeleteTemplate = { workManagementViewModel.deleteUserTemplate(it) },
            onManageTemplates = { showAddDialog = false; workManagementViewModel.showTemplateManager = true },
            onCreateFromTemplate = { title, id ->
                workManagementViewModel.createWorkFromUserTemplate(title.trim().ifBlank { uiText("新しい作品") }, id)
            },
            workTemplates = workTemplates,
            wideWorkPanels = wideWorkPanels,
            colors = colors,
            codeFontFamily = codeFontFamily,
            configuration = configuration,
            onDismiss = { showAddDialog = false },
            onCreate = { title, ratio, sizingMode, template ->
                workManagementViewModel.createWork(title.trim().ifBlank { uiText("新しい作品") }, ratio, sizingMode, template)
            },
            textTranslator = { s, args -> uiText(s, *args) },
            windowSetup = { KeepLandscapeDialogImmersive() }
        )
    }

    if (workManagementViewModel.showSaveTemplateDialog && activeWork != null) {
        SaveUserTemplateDialog(
            initialTitle = activeWork.title,
            busy = assetBusy || snapshotBusy,
            loadFailed = workManagementViewModel.templateLoadFailed,
            text = { uiText(it) },
            onRetry = { workManagementViewModel.retryUserTemplates() },
            onSave = { workManagementViewModel.saveUserTemplate(it) },
            onDismiss = { workManagementViewModel.showSaveTemplateDialog = false }
        )
    }

    if (showRenameDialog && activeWork != null) {
        RenameWorkDialog(
            initialTitle = activeWork.title,
            colors = colors,
            onDismiss = { showRenameDialog = false },
            onConfirmRename = { title -> workManagementViewModel.renameWork(activeWork.id, title) },
            textTranslator = { s, args -> uiText(s, *args) },
            windowSetup = { KeepLandscapeDialogImmersive() }
        )
    }

    editingTagsWork?.let { targetWork ->
        WorkTagsDialog(
            workTitle = targetWork.title,
            currentTags = targetWork.tags.toList(),
            allKnownTags = remember(works) { works.flatMap { it.tags }.distinct().sorted() },
            text = ::uiText,
            onAddTag = { tag -> workManagementViewModel.addTag(targetWork.id, tag) },
            onRemoveTag = { tag -> workManagementViewModel.removeTag(targetWork.id, tag) },
            onDismiss = { workManagementViewModel.editingTagsWorkId = null }
        )
    }

    if (showDeleteDialog) {
        DeleteWorkDialog(
            workTitle = activeWork?.title,
            colors = colors,
            onDismiss = { showDeleteDialog = false },
            onConfirmDelete = { workManagementViewModel.deleteCurrentWork() },
            textTranslator = { s, args -> uiText(s, *args) },
            windowSetup = { KeepLandscapeDialogImmersive() }
        )
    }}
