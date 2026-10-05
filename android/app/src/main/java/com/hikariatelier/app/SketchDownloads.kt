package com.hikariatelier.app

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class SketchDownloadMetadata(val owner: String, val id: String, val name: String, val mime: String)
internal data class PendingSketchDownload(val metadata: SketchDownloadMetadata, val file: File)
internal data class SketchDownloadResult(val metadata: SketchDownloadMetadata, val saved: Boolean)

/** Download names are leaf names, never paths into the app or another work. */
internal fun sketchDownloadMetadata(owner: String, id: String, filename: String, mime: String): SketchDownloadMetadata? {
    if (owner.isBlank() || owner.length > 80 || id.isBlank() || id.length > 100) return null
    val name = filename.substringAfterLast('/').substringAfterLast('\\')
        .filterNot { it.isISOControl() }.trim().take(180)
        .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "download"
    return SketchDownloadMetadata(owner, id, name, assetMimeType(name, mime.substringBefore(';').trim()))
}

/** Finished downloads belong to a ViewModel so an Activity rotation does not cancel a write. */
internal class SketchDownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val events = MutableSharedFlow<SketchDownloadResult>(extraBufferCapacity = 64)
    val results = events.asSharedFlow()
    private data class OwnedDownload(val download: PendingSketchDownload, val reservation: SketchDownloadQuota.Reservation,
                                     var writing: Boolean = false)
    private val quota = SketchDownloadQuota()
    private val lock = Any()
    private val owned = mutableMapOf<File, OwnedDownload>()
    private val preferences = application.getSharedPreferences("sketch-downloads", Context.MODE_PRIVATE)
    var legacyPermissionRequested = preferences.getBoolean("legacy-storage-requested", false)
        private set
    var legacyPermissionRequestInFlight = false
        private set
    var destinationSelectionInFlight = false
        private set

    fun beginLegacyPermissionRequest(): Boolean {
        if (legacyPermissionRequestInFlight || legacyPermissionRequested) return false
        legacyPermissionRequested = true
        legacyPermissionRequestInFlight = true
        preferences.edit().putBoolean("legacy-storage-requested", true).apply()
        return true
    }

    fun finishLegacyPermissionRequest() { legacyPermissionRequestInFlight = false }

    fun beginDestinationSelection(): Boolean {
        if (destinationSelectionInFlight) return false
        destinationSelectionInFlight = true
        return true
    }

    fun finishDestinationSelection() { destinationSelectionInFlight = false }

    /** Called before a completed temporary file enters an OS-dialog queue. */
    fun reserve(download: PendingSketchDownload): Boolean {
        val reserved = synchronized(lock) {
            owned[download.file]?.let { return@synchronized it.download.metadata == download.metadata }
            val reservation = quota.reserve(download.file.length()) ?: return@synchronized false
            owned[download.file] = OwnedDownload(download, reservation)
            true
        }
        if (!reserved) discard(download)
        return reserved
    }

    /** Cancellation and rejection acknowledge the JavaScript request as well as releasing space. */
    fun discard(download: PendingSketchDownload) {
        val discarded = synchronized(lock) {
            val entry = owned[download.file]
            if (entry?.writing == true || entry != null && entry.download.metadata != download.metadata) return@synchronized false
            owned.remove(download.file)?.reservation?.close()
            true
        }
        if (discarded) {
            download.file.delete()
            viewModelScope.launch { events.emit(SketchDownloadResult(download.metadata, false)) }
        }
    }

    private fun finished(download: PendingSketchDownload) {
        synchronized(lock) { owned.remove(download.file)?.reservation?.close() }
        download.file.delete()
    }

    override fun onCleared() {
        val waiting = synchronized(lock) { owned.values.filter { !it.writing }.map { it.download } }
        waiting.forEach(::discard)
        super.onCleared()
    }

    fun save(download: PendingSketchDownload, destination: Uri? = null) {
        if (!reserve(download)) return
        synchronized(lock) {
            val entry = owned[download.file] ?: return
            if (entry.writing) return
            entry.writing = true
        }
        val job = viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    if (destination != null) {
                        resolver.openOutputStream(destination, "wt")?.use { output ->
                            download.file.inputStream().use { it.copyTo(output, 64 * 1024) }
                        } ?: error("Cannot open download destination")
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        @Suppress("DEPRECATION")
                        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Edit-RiN")
                        val target = copyToUniqueSketchDownload(download.file, directory, download.metadata.name)
                        runCatching {
                            MediaScannerConnection.scanFile(getApplication<Application>(), arrayOf(target.absolutePath),
                                arrayOf(download.metadata.mime), null)
                        }
                    } else {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, download.metadata.name)
                            put(MediaStore.MediaColumns.MIME_TYPE, download.metadata.mime)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Edit-RiN")
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: error("Cannot create download")
                        try {
                            resolver.openOutputStream(uri, "w")?.use { output ->
                                download.file.inputStream().use { it.copyTo(output, 64 * 1024) }
                            } ?: error("Cannot open download")
                            values.clear()
                            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            check(resolver.update(uri, values, null, null) > 0)
                        } catch (error: Exception) {
                            runCatching { resolver.delete(uri, null, null) }
                            throw error
                        }
                    }
                    true
                }.getOrDefault(false)
            }
            events.emit(SketchDownloadResult(download.metadata, saved))
        }
        // Also runs when a scope is cancelled before the coroutine body starts.
        job.invokeOnCompletion { finished(download) }
    }
}
