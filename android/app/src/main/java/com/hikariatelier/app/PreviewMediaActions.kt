package com.hikariatelier.app

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Platform share/open actions scoped to the current Activity. */
internal class PreviewMediaActions(
    private val activity: ComponentActivity,
    private val text: (String, Array<out Any?>) -> String
) {
    private val mediaRepository = PreviewMediaRepository(activity.applicationContext)
    private val cacheDir get() = activity.cacheDir
    private val contentResolver get() = activity.contentResolver
    private val lifecycleScope get() = activity.lifecycleScope
    private fun startActivity(intent: Intent) = activity.startActivity(intent)
    private fun uiText(source: String, vararg arguments: Any?) = text(source, arguments)
    fun savePreviewMedia(dataUrl: String = "", mimeType: String, displayName: String,
                                 video: Boolean, sourceFile: File? = null): Uri? =
        mediaRepository.save(dataUrl, mimeType, displayName, video, sourceFile)

    fun openPreviewMedia(media: SavedPreviewMedia) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(media.uri, media.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, media.displayName, media.uri)
        }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(activity, uiText("録画を開けませんでした"), Toast.LENGTH_SHORT).show()
            }
    }

    private var recordingShareBusy = false

    fun sharePreviewMedia(media: SavedPreviewMedia, xOnly: Boolean, xText: String = "") {
        fun sendIntent(uri: Uri, packageName: String? = null) = Intent(Intent.ACTION_SEND).apply {
            type = media.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, media.displayName)
            if (xOnly) {
                // Give this attachment an identity in Intent.data as well as EXTRA_STREAM.
                // Some receiving activities reuse intents based on data, not extras.
                setDataAndType(uri, media.mimeType)
                if (xText.isNotBlank()) putExtra(Intent.EXTRA_TEXT, xText)
            }
            clipData = ClipData(
                media.displayName, arrayOf(media.mimeType), ClipData.Item(uri)
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (packageName != null) setPackage(packageName)
        }

        if (!xOnly) {
            runCatching {
                startActivity(Intent.createChooser(sendIntent(media.uri), uiText("共有")))
            }.onFailure {
                Toast.makeText(activity, uiText("共有できませんでした"), Toast.LENGTH_SHORT).show()
            }
            return
        }
        if (recordingShareBusy) return
        recordingShareBusy = true
        Toast.makeText(activity, uiText("共有の準備中"), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            var attachment: File? = null
            var handedOff = false
            try {
                val uri = withContext(Dispatchers.IO) {
                    val file = createRecordingShareFile(
                        File(cacheDir, "recording-shares"), media.mimeType, media.sizeBytes
                    ) { contentResolver.openInputStream(media.uri) }
                    attachment = file
                    FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
                }
                try {
                    startActivity(sendIntent(uri, "com.twitter.android"))
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(activity,
                        uiText("Xアプリを開けないため共有先を選択してください"), Toast.LENGTH_SHORT).show()
                    startActivity(Intent.createChooser(sendIntent(uri), uiText("共有")))
                }
                handedOff = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val errorMsg = if (media.mimeType.startsWith("image/")) {
                    uiText("画像を共有できませんでした。空き容量とファイルを確認してください")
                } else {
                    uiText("録画を共有できませんでした。空き容量と録画ファイルを確認してください")
                }
                Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show()
            } finally {
                if (!handedOff) attachment?.delete()
                recordingShareBusy = false
            }
        }
    }

}
