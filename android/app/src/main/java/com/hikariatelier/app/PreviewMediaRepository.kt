package com.hikariatelier.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.media.MediaScannerConnection
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File

/** Uses only Application Context so recording saves can finish across recreation. */
internal class PreviewMediaRepository(context: Context) {
    private val context = context.applicationContext
    val recordingRecoveryDirectory: File get() = File(context.filesDir, "recording-recovery")

    fun deleteCreatedDocument(uri: Uri) {
        runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else context.contentResolver.delete(uri, null, null)
        }
    }

    fun saveRecordingToDocument(pending: PendingRecording, uri: Uri): Uri? = runCatching {
        copyRecordingToDestination(pending.file,
            openOutput = { context.contentResolver.openOutputStream(uri, "wt") },
            deleteFailedDestination = { deleteCreatedDocument(uri) })
        uri
    }.getOrNull()

    fun save(
        dataUrl: String = "",
        mimeType: String,
        displayName: String,
        video: Boolean,
        sourceFile: File? = null,
        directoryName: String = "EditRiN"
    ): Uri? = runCatching {
        require(directoryName in setOf("EditRiN", "Edit-RiN"))
        fun copyTo(output: java.io.OutputStream) {
            if (sourceFile != null) sourceFile.inputStream().use { it.copyTo(output, 64 * 1024) }
            else output.write(Base64.decode(dataUrl.substringAfter(',', dataUrl), Base64.DEFAULT))
        }

        val collection = if (video) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (video) "Movies/$directoryName" else "Pictures/$directoryName"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(collection, values)
            ?: error("保存先を作成できませんでした")
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { copyTo(it) }
                ?: error("保存先を開けませんでした")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            check(context.contentResolver.update(uri, values, null, null) > 0)
        } catch (error: Exception) {
            // Only clean up the pending item created by this capture attempt.
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw error
        }
        uri
    }.getOrNull()
}
