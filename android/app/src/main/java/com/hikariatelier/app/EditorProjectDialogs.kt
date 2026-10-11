package com.hikariatelier.app

import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue

@Composable
internal fun EditorProjectDialogs(
    models: EditorModels,
    preview: PreviewController,
    activeWork: Work?,
    activeWorkId: String,
    editorText: String,
    editingValue: TextFieldValue,
    editingJavaScript: Boolean,
    codeFontFamily: FontFamily,
    fontFeatures: String,
    colors: ColorScheme,
    workSaving: Boolean,
    assetPicker: ActivityResultLauncher<Array<String>>,
    showP5Import: Boolean,
    onP5ImportChange: (Boolean) -> Unit,
    showAssets: Boolean,
    onAssetsChange: (Boolean) -> Unit,
    onSetAssetTargetId: (String?) -> Unit,
    showProjectFilesDialog: Boolean,
    onProjectFilesDialogChange: (Boolean) -> Unit,
    showAuxiliaryFileEditor: Boolean,
    onAuxiliaryFileEditorChange: (Boolean) -> Unit,
    auxiliaryFileName: String,
    onAuxiliaryFileNameChange: (String) -> Unit,
    originalAuxiliaryFileName: String?,
    onOriginalAuxiliaryFileNameChange: (String?) -> Unit,
    auxiliaryFileContent: String,
    onAuxiliaryFileContentChange: (String) -> Unit,
    onSelectEditorFile: (String) -> Unit,
    applyEditorChange: (TextFieldValue) -> Unit,
    changeAssets: (String, Map<String, WorkAsset>) -> Unit,
    textTranslator: (String, Array<out Any?>) -> String
) {
    val context = LocalContext.current
    val sessionViewModel = models.session
    val workManagementViewModel = models.works
    val settingsViewModel = models.settings
    val assetStorage = workManagementViewModel.assetStorage
    val assetBusy = sessionViewModel.assetBusy
    var p5Username by settingsViewModel::p5Username
    val p5Sketches = workManagementViewModel.p5Sketches
    val p5Busy = workManagementViewModel.p5Busy
    val p5Error = workManagementViewModel.p5Error
    var previewAsset by remember { mutableStateOf<Pair<String, WorkAsset>?>(null) }

    fun uiText(source: String, vararg arguments: Any?): String = textTranslator(source, arguments)

    if (assetBusy && !showAssets && !workSaving && !sessionViewModel.snapshotRestoring) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(uiText("ファイルを処理中…")) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {}
        )
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
            onDismiss = { onP5ImportChange(false) }
        )
    }

    if (showAssets) {
        AssetManagerDialog(
            assets = activeWork?.assets.orEmpty(),
            busy = assetBusy,
            text = ::uiText,
            onAdd = {
                onSetAssetTargetId(activeWorkId)
                assetPicker.launch(arrayOf("*/*"))
            },
            onRename = { request -> workManagementViewModel.renameAsset(request) },
            onDelete = { name -> activeWork?.let { changeAssets(it.id, it.assets.toMap() - name) } },
            onClose = { onAssetsChange(false) },
            onPreview = { name, asset -> previewAsset = name to asset },
            onInsert = { name, asset ->
                if (editingJavaScript) {
                    applyEditorChange(insertAtSelection(editingValue, assetLoaderCode(name, asset)))
                    onAssetsChange(false)
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
                onOriginalAuxiliaryFileNameChange(name)
                onAuxiliaryFileNameChange(name)
                onAuxiliaryFileContentChange(content)
                onProjectFilesDialogChange(false)
                onSelectEditorFile(name)
            },
            onAddNewFile = {
                onOriginalAuxiliaryFileNameChange(null)
                onAuxiliaryFileNameChange("library.js")
                onAuxiliaryFileContentChange("")
                onProjectFilesDialogChange(false)
                onAuxiliaryFileEditorChange(true)
            },
            onEditFile = { name, content ->
                onOriginalAuxiliaryFileNameChange(name)
                onAuxiliaryFileNameChange(name)
                onAuxiliaryFileContentChange(sessionViewModel.fileDrafts["$activeWorkId/$name"] ?: content)
                onProjectFilesDialogChange(false)
                onAuxiliaryFileEditorChange(true)
            },
            onDismiss = { onProjectFilesDialogChange(false) },
            textTranslator = { s, args -> uiText(s, *args) }
        )
    }

    if (showAuxiliaryFileEditor) {
        AuxiliaryFileEditorDialog(
            originalFileName = originalAuxiliaryFileName,
            initialFileName = auxiliaryFileName,
            initialContent = auxiliaryFileContent,
            onContentChange = onAuxiliaryFileContentChange,
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
            onDismiss = { onAuxiliaryFileEditorChange(false) },
            textTranslator = { s, args -> uiText(s, *args) }
        )
    }
}
