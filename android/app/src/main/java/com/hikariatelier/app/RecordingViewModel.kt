package com.hikariatelier.app

import android.graphics.Bitmap
import android.os.SystemClock
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import java.io.File
import androidx.lifecycle.ViewModel

internal class RecordingViewModel(private val mediaRepository: PreviewMediaRepository) : ViewModel() {
    var recordingFormatLabel by mutableStateOf("")
    var recordingStartedAt by mutableLongStateOf(0L)
    var recordingLimitMillis by mutableLongStateOf(0L)
    var recordingElapsedMillis by mutableLongStateOf(0L)
    var savedPreviewMedia by mutableStateOf<SavedPreviewMedia?>(null)
    var shareCardArtwork by mutableStateOf<Bitmap?>(null)
    var isPreviewRecording by mutableStateOf(false)
    val notices = UiNotices()
    private var savingJob: Job? = null
    private val recoveryStore = RecordingRecoveryStore(mediaRepository.recordingRecoveryDirectory)
    var pendingRecording by mutableStateOf<PendingRecording?>(null)
        private set
    var recordingRecoveryVisible by mutableStateOf(false)
        private set
    private var recoveryReady = false
    private val recoveryJob = viewModelScope.launch {
        try {
            pendingRecording = withContext(Dispatchers.IO) { recoveryStore.restoreAndClean() }
            recordingRecoveryVisible = pendingRecording != null
        } finally { recoveryReady = true }
    }
    var isRecordingSaving by mutableStateOf(false)
    var pendingRecordingFormat by mutableStateOf<String?>(null)
    var recordingCountdownRemaining by mutableIntStateOf(0)

    fun startSelectedRecording(
        format: String,
        mp4BitrateMbps: Int,
        evaluateJavascript: (String) -> Unit
    ) {
        if (!canStartRecording()) return
        savedPreviewMedia = null
        recordingFormatLabel = format.uppercase()
        recordingLimitMillis = if (format == "gif") 15_000L else 60_000L
        recordingStartedAt = SystemClock.elapsedRealtime()
        recordingElapsedMillis = 0L
        recordingCountdownRemaining = 0
        val bitrate = mp4BitrateMbps * 1_000_000
        evaluateJavascript("window.__editKiroStartRecording?.('$format', $bitrate)")
    }

    fun requestPreviewRecording(
        format: String,
        countdownSeconds: Int,
        mp4BitrateMbps: Int,
        evaluateJavascript: (String) -> Unit
    ) {
        if (!canStartRecording()) return
        recordingFormatLabel = format.uppercase()
        recordingLimitMillis = if (format == "gif") 15_000L else 60_000L
        if (countdownSeconds == 0) {
            startSelectedRecording(format, mp4BitrateMbps, evaluateJavascript)
        } else {
            pendingRecordingFormat = format
            recordingCountdownRemaining = countdownSeconds
        }
    }

    fun saveTransferredRecording(file: File?, mimeType: String) {
        if (savingJob?.isActive == true || pendingRecording != null) {
            viewModelScope.launch(Dispatchers.IO) { file?.delete() }
            return
        }
        isPreviewRecording = false
        isRecordingSaving = true
        val elapsed = recordingElapsedMillis
        savingJob = viewModelScope.launch {
            try {
                recoveryJob.join()
                if (pendingRecording != null) {
                    withContext(Dispatchers.IO) { file?.delete() }
                    notices.send("保存待ちの録画を保存または破棄してください")
                    return@launch
                }
                val mime = mimeType.substringBefore(';').lowercase()
                val extension = recordingExtension(mime)
                if (file == null || extension == null || !file.isFile || file.length() == 0L) {
                    withContext(Dispatchers.IO) { file?.delete() }
                    notices.send("録画を保存できませんでした")
                    return@launch
                }
                val name = "EditRiN_${java.util.UUID.randomUUID()}.$extension"
                val pending = withContext(Dispatchers.IO) {
                    runCatching { recoveryStore.retain(file, mime, name, elapsed) }
                        .getOrElse { PendingRecording(file, mime, name, elapsed) }
                }
                finishRecordingSave(pending)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notices.send("録画を保存できませんでした") }
            finally { isRecordingSaving = false }
        }
    }

    private fun canStartRecording(): Boolean {
        if (!recoveryReady) { notices.send("録画の準備中です"); return false }
        if (pendingRecording != null) { notices.send("保存待ちの録画を保存または破棄してください"); return false }
        return !isRecordingSaving
    }

    fun retryPendingRecording(destination: Uri? = null, expectedName: String? = null) {
        val pending = pendingRecording
        if (pending == null || (expectedName != null && pending.displayName != expectedName) ||
            savingJob?.isActive == true) {
            if (destination != null) viewModelScope.launch(Dispatchers.IO) { mediaRepository.deleteCreatedDocument(destination) }
            return
        }
        isRecordingSaving = true
        savingJob = viewModelScope.launch {
            try { finishRecordingSave(pending, destination) }
            finally { isRecordingSaving = false }
        }
    }

    private suspend fun finishRecordingSave(pending: PendingRecording, destination: Uri? = null) {
        // State owns the source before I/O begins, so all failure paths still offer recovery.
        pendingRecording = pending
        val media = try {
            withContext(Dispatchers.IO) {
                val thumbnail = recordingThumbnail(pending.file, pending.mimeType)
                val uri = if (destination != null) mediaRepository.saveRecordingToDocument(pending, destination)
                else mediaRepository.save(mimeType = pending.mimeType, displayName = pending.displayName,
                    video = pending.mimeType != "image/gif", sourceFile = pending.file)
                uri?.let { SavedPreviewMedia(it, pending.mimeType, pending.displayName,
                    pending.sizeBytes, pending.durationMillis, thumbnail) }
            }
        } catch (cancelled: CancellationException) {
            if (destination != null) withContext(NonCancellable + Dispatchers.IO) { mediaRepository.deleteCreatedDocument(destination) }
            throw cancelled
        } catch (_: Exception) {
            if (destination != null) withContext(Dispatchers.IO) { mediaRepository.deleteCreatedDocument(destination) }
            null
        }
        if (media == null) {
            recordingRecoveryVisible = true
            notices.send("録画を保存できませんでした")
        } else {
            savedPreviewMedia = media
            pendingRecording = null
            recordingRecoveryVisible = false
            withContext(NonCancellable + Dispatchers.IO) { runCatching { recoveryStore.complete(pending) } }
        }
    }

    fun discardPendingRecording() {
        val pending = pendingRecording ?: return
        if (savingJob?.isActive == true) return
        isRecordingSaving = true
        savingJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { recoveryStore.discard(pending) }
                pendingRecording = null
                recordingRecoveryVisible = false
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notices.send("録画を破棄できませんでした") }
            finally { isRecordingSaving = false }
        }
    }

    fun abortTransfer() {
        isPreviewRecording = false
        if (savingJob?.isActive != true) isRecordingSaving = false
    }

    /** Saving and decoding use application storage and survive Activity recreation. */
    var screenshotSaving by mutableStateOf(false)
        private set

    fun saveScreenshot(dataUrl: String, forShareCard: Boolean, width: Int = 0, height: Int = 0) =
        saveScreenshotSource(dataUrl, null, forShareCard, width, height)

    fun saveScreenshotFile(file: File?, forShareCard: Boolean, width: Int, height: Int) {
        if (file == null) { notices.send("スクリーンショットを保存できませんでした"); return }
        saveScreenshotSource("", file, forShareCard, width, height)
    }

    private fun saveScreenshotSource(dataUrl: String, sourceFile: File?, forShareCard: Boolean, width: Int, height: Int) {
        if (screenshotSaving) {
            viewModelScope.launch(Dispatchers.IO) { sourceFile?.delete() }
            return
        }
        screenshotSaving = true
        viewModelScope.launch {
            try {
                if (forShareCard) {
                    val bitmap = withContext(Dispatchers.IO) {
                        if (sourceFile != null) android.graphics.BitmapFactory.decodeFile(sourceFile.path)
                        else {
                            val bytes = android.util.Base64.decode(dataUrl.substringAfter(',', dataUrl), android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }
                    }
                    if (bitmap == null) notices.send("スクリーンショットを保存できませんでした")
                    else shareCardArtwork = bitmap
                } else {
                    val media = withContext(Dispatchers.IO) {
                        val name = "EditRiN_${System.currentTimeMillis()}.png"
                        val uri = mediaRepository.save(dataUrl = dataUrl, sourceFile = sourceFile, mimeType = "image/png",
                            displayName = name, video = false, directoryName = if (width > 0) "Edit-RiN" else "EditRiN")
                        uri?.let {
                            val bytes = if (sourceFile == null) runCatching { android.util.Base64.decode(
                                dataUrl.substringAfter(',', dataUrl), android.util.Base64.DEFAULT) }.getOrNull() else null
                            val thumbnail = runCatching {
                                if (sourceFile != null) recordingThumbnail(sourceFile, "image/png")
                                else bytes?.let(::imageThumbnail)
                            }.getOrNull()
                            val size = runCatching { sourceFile?.length() ?: bytes?.size?.toLong() ?: 0L }.getOrDefault(0L)
                            SavedPreviewMedia(it, "image/png", name, size,
                                thumbnail = thumbnail)
                        }
                    }
                    if (media == null) notices.send("スクリーンショットを保存できませんでした")
                    else savedPreviewMedia = media
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notices.send("スクリーンショットを保存できませんでした") }
            finally {
                screenshotSaving = false
                withContext(NonCancellable + Dispatchers.IO) { sourceFile?.delete() }
            }
        }
    }

    fun onPreviewDestroyed() {
        cancelRecordingCountdown()
        abortTransfer()
    }

    fun cancelRecordingCountdown() {
        pendingRecordingFormat = null
        recordingCountdownRemaining = 0
    }
}

/** The saving flag is released on success, exception, and cancellation. */
internal suspend fun <T> completeRecordingSave(setSaving: (Boolean) -> Unit, save: suspend () -> T): T {
    setSaving(true)
    return try { save() } finally { setSaving(false) }
}
