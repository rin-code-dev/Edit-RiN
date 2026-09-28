package com.hikariatelier.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaScannerConnection
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File

/** Uses only Application Context so recording saves can finish across recreation. */
internal class PreviewMediaRepository(context: Context) {
    private val context = context.applicationContext
    fun save(
        dataUrl: String = "",
        mimeType: String,
        displayName: String,
        video: Boolean,
        sourceFile: File? = null
    ): Uri? = runCatching {
        fun copyTo(output: java.io.OutputStream) {
            if (sourceFile != null) sourceFile.inputStream().use { it.copyTo(output, 64 * 1024) }
            else output.write(Base64.decode(dataUrl.substringAfter(',', dataUrl), Base64.DEFAULT))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                    if (video) "Movies/EditRiN" else "Pictures/EditRiN"
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
        } else {
            val parent = context.getExternalFilesDir(
                if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            ) ?: error("保存先を利用できません")
            val directory = File(parent, "EditRiN").apply { mkdirs() }
            val file = File(directory, displayName)
            file.outputStream().use { copyTo(it) }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(mimeType),
                null
            )
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
    }.getOrNull()

}
