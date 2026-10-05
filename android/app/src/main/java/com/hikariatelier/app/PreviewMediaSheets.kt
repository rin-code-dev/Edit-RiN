package com.hikariatelier.app

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.core.os.ConfigurationCompat

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun PreviewMediaSheets(
    models: EditorModels,
    preview: PreviewController,
    mediaActions: PreviewMediaActions,
    editorValue: TextFieldValue
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleScope = (context as ComponentActivity).lifecycleScope
    val cacheDir = context.cacheDir
    val session = models.session
    val works by session.worksState
    val activeWorkId by session.activeWorkIdState
    val activeWork = works.find { it.id == activeWorkId } ?: works.firstOrNull()
    val editorText = editorValue.text
    var shareCardArtwork by models.recording::shareCardArtwork
    var savedPreviewMedia by models.recording::savedPreviewMedia
    var shareCardAuthor by models.settings::shareCardAuthor
    val xShareText = models.settings.xShareText
    val pendingRecording = models.recording.pendingRecording
    var destinationRecordingName by rememberSaveable { mutableStateOf<String?>(null) }
    val recordingDestination = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(pendingRecording?.mimeType ?: "video/mp4")
    ) { uri ->
        val expectedName = destinationRecordingName
        destinationRecordingName = null
        if (uri != null) models.recording.retryPendingRecording(uri, expectedName)
    }
    fun uiText(source: String, vararg arguments: Any?): String {
        val language = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language ?: "en"
        val translated = translateUi(source, resolveUiLanguage(models.settings.appLanguage, language))
        return if (arguments.isEmpty()) translated else String.format(java.util.Locale.ROOT, translated, *arguments)
    }
    if (pendingRecording != null && models.recording.recordingRecoveryVisible) {
        RecordingRecoverySheet(
            pending = pendingRecording,
            saving = models.recording.isRecordingSaving,
            text = { uiText(it) },
            onRetry = { models.recording.retryPendingRecording() },
            onChooseDestination = {
                destinationRecordingName = pendingRecording.displayName
                runCatching { recordingDestination.launch(pendingRecording.displayName) }.onFailure {
                    destinationRecordingName = null
                    Toast.makeText(context, uiText("保存先を開けませんでした"), Toast.LENGTH_LONG).show()
                }
            },
            onDiscard = { models.recording.discardPendingRecording() }
        )
    }
    shareCardArtwork?.let { artwork ->
        val fullCode = if (preview.session.sketchCode.isNotBlank()) {
            preview.session.sketchCode
        } else {
            editorText
        }
        val hasAssets = activeWork?.assets?.isNotEmpty() == true
        val initialRange = if (!editorValue.selection.collapsed) {
            val startChar = editorValue.selection.min
            val endChar = editorValue.selection.max
            val sLine = editorText.take(startChar).count { it == '\n' } + 1
            val eLine = editorText.take(endChar).count { it == '\n' } + 1
            sLine to eLine
        } else null

        ShareCardSheet(
            artwork = artwork,
            workTitle = activeWork?.title ?: "",
            fullCode = fullCode,
            hasAssets = hasAssets,
            initialAuthor = shareCardAuthor,
            onAuthorChange = { newAuthor ->
                shareCardAuthor = newAuthor

            },
            initialSelectedRange = initialRange,
            text = ::uiText,
            onSave = { cardBmp ->
                lifecycleScope.launch {
                    val name = "EditRiN_Card_${System.currentTimeMillis()}.png"
                    val tempFile = File(cacheDir, name)
                    val uri = withContext(Dispatchers.IO) {
                        try {
                            tempFile.outputStream().use { out ->
                                cardBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                            }
                            mediaActions.savePreviewMedia(
                                mimeType = "image/png",
                                displayName = name,
                                video = false,
                                sourceFile = tempFile
                            )
                        } finally {
                            tempFile.delete()
                        }
                    }
                    shareCardArtwork = null
                    if (uri != null) {
                        Toast.makeText(
                            context,
                            uiText("シェアカードをPictures/EditRiNへ保存しました"),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            uiText("シェアカードを保存できませんでした"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onShare = { cardBmp, xOnly ->
                lifecycleScope.launch {
                    val name = "EditRiN_Card_${System.currentTimeMillis()}.png"
                    val tempFile = File(cacheDir, name)
                    val media = withContext(Dispatchers.IO) {
                        try {
                            tempFile.outputStream().use { out ->
                                cardBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                            }
                            val uri = mediaActions.savePreviewMedia(
                                mimeType = "image/png",
                                displayName = name,
                                video = false,
                                sourceFile = tempFile
                            )
                            uri?.let {
                                SavedPreviewMedia(
                                    uri = it,
                                    mimeType = "image/png",
                                    displayName = name,
                                    sizeBytes = tempFile.length(),
                                    thumbnail = cardBmp
                                )
                            }
                        } finally {
                            tempFile.delete()
                        }
                    }
                    shareCardArtwork = null
                    if (media != null) {
                        mediaActions.sharePreviewMedia(media, xOnly, xShareText)
                    } else {
                        Toast.makeText(
                            context,
                            uiText("シェアカードを保存できませんでした"),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = { shareCardArtwork = null }
        )
    }

    savedPreviewMedia?.let { media ->
        SavedRecordingSheet(
            name = media.displayName, mimeType = media.mimeType,
            sizeBytes = media.sizeBytes, durationMillis = media.durationMillis,
            thumbnail = media.thumbnail, text = ::uiText,
            onOpen = { mediaActions.openPreviewMedia(media) },
            onShare = { mediaActions.sharePreviewMedia(media, false) },
            onX = { mediaActions.sharePreviewMedia(media, true, xShareText) },
            onDismiss = { savedPreviewMedia = null }
        )
    }

}
