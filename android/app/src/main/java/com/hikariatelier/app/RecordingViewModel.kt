package com.hikariatelier.app

import android.graphics.Bitmap
import android.os.SystemClock
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
    var isRecordingSaving by mutableStateOf(false)
    var pendingRecordingFormat by mutableStateOf<String?>(null)
    var recordingCountdownRemaining by mutableIntStateOf(0)

    fun startSelectedRecording(
        format: String,
        mp4BitrateMbps: Int,
        evaluateJavascript: (String) -> Unit
    ) {
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
        if (savingJob?.isActive == true) return
        isPreviewRecording = false
        isRecordingSaving = true
        val elapsed = recordingElapsedMillis
        savingJob = viewModelScope.launch {
            try {
                val media = completeRecordingSave({ isRecordingSaving = it }) {
                    withContext(Dispatchers.IO) {
                        if (file == null) return@withContext null
                        val mime = mimeType.substringBefore(';').lowercase()
                        val extension = when (mime) {
                            "image/gif" -> "gif"
                            "video/mp4" -> "mp4"
                            "video/webm" -> "webm"
                            else -> return@withContext null
                        }
                        val name = "EditRiN_${java.util.UUID.randomUUID()}.$extension"
                        val thumbnail = recordingThumbnail(file, mime)
                        val uri = mediaRepository.save(mimeType = mime, displayName = name,
                            video = mime != "image/gif", sourceFile = file)
                        uri?.let { SavedPreviewMedia(it, mime, name, file.length(), elapsed, thumbnail) }
                    }
                }
                savedPreviewMedia = media
                if (media == null) notices.send("録画を保存できませんでした")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notices.send("録画を保存できませんでした") }
            finally {
                isRecordingSaving = false
                withContext(NonCancellable + Dispatchers.IO) { file?.delete() }
            }
        }
    }

    fun abortTransfer() {
        isPreviewRecording = false
        if (savingJob?.isActive != true) isRecordingSaving = false
    }

    /** Saving and decoding use application storage and survive Activity recreation. */
    fun saveScreenshot(dataUrl: String, forShareCard: Boolean) {
        viewModelScope.launch {
            try {
                if (forShareCard) {
                    val bitmap = withContext(Dispatchers.IO) {
                        val bytes = android.util.Base64.decode(dataUrl.substringAfter(',', dataUrl), android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    if (bitmap == null) notices.send("スクリーンショットを保存できませんでした")
                    else shareCardArtwork = bitmap
                } else {
                    val uri = withContext(Dispatchers.IO) {
                        mediaRepository.save(dataUrl = dataUrl, mimeType = "image/png",
                            displayName = "EditRiN_${System.currentTimeMillis()}.png", video = false)
                    }
                    notices.send(if (uri != null) "スクリーンショットをPictures/EditRiNへ保存しました"
                        else "スクリーンショットを保存できませんでした")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notices.send("スクリーンショットを保存できませんでした") }
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
