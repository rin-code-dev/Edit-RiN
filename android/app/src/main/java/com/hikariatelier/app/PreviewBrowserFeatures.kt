package com.hikariatelier.app

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.ArrayDeque

internal fun previewDevicePermissions(resources: Collection<String>): List<String> = resources.mapNotNull {
    when (it) {
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
        else -> null
    }
}.distinct()

/** Browser requests stay scoped to the active local sketch, not an external iframe. */
internal class PreviewBrowserFeatures(
    private val activity: ComponentActivity,
    private val currentOwner: () -> String?,
    private val currentView: () -> WebView?,
    private val downloads: SketchDownloadsViewModel,
    private val report: (String) -> Unit
) {
    private data class MediaPermission(val request: PermissionRequest, val owner: String)
    private data class FileSelection(val callback: ValueCallback<Array<Uri>>, val owner: String)
    private var closed = false
    private var mediaRequest: MediaPermission? = null
    private var permissionDialogRunning = false
    private var fileSelection: FileSelection? = null
    private var fileDialogRunning = false
    private var customDialog: Dialog? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private val pendingDownloads = ArrayDeque<PendingSketchDownload>()
    private var selectedDownload: PendingSketchDownload? = null
    private var storagePermissionOwner: String? = null

    private val permissionLauncher = activity.activityResultRegistry.register(
        "preview-media-permissions", ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionDialogRunning = false
        val pending = mediaRequest.also { mediaRequest = null } ?: return@register
        if (closed || currentOwner() != pending.owner) pending.request.deny()
        else grantAvailableResources(pending.request)
    }

    private val fileLauncher = activity.activityResultRegistry.register(
        "preview-file-selection", ActivityResultContracts.StartActivityForResult()
    ) { result ->
        fileDialogRunning = false
        val pending = fileSelection.also { fileSelection = null } ?: return@register
        val uris = if (!closed && currentOwner() == pending.owner) {
            // A file provider must not smuggle a private file:// path through a picker result.
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
                ?.filter { it.scheme == "content" }?.toTypedArray()?.takeIf { it.isNotEmpty() }
        } else null
        pending.callback.onReceiveValue(uris)
    }

    private val downloadLauncher = activity.activityResultRegistry.register(
        "preview-download-destination", ActivityResultContracts.StartActivityForResult()
    ) { result ->
        downloads.finishDestinationSelection()
        val pending = selectedDownload.also { selectedDownload = null }
        if (pending != null) {
            val uri = result.data?.data.takeIf {
                result.resultCode == Activity.RESULT_OK && !closed && currentOwner() == pending.metadata.owner && it?.scheme == "content"
            }
            if (uri != null) downloads.save(pending, uri) else downloads.discard(pending)
        }
        drainLegacyDownloads()
    }

    private val storagePermissionLauncher = activity.activityResultRegistry.register(
        "preview-download-storage-permission", ActivityResultContracts.RequestPermission()
    ) {
        downloads.finishLegacyPermissionRequest()
        val requestedOwner = storagePermissionOwner.also { storagePermissionOwner = null }
        if (closed) return@register
        if (requestedOwner != null && requestedOwner != currentOwner()) {
            val iterator = pendingDownloads.iterator()
            while (iterator.hasNext()) {
                val pending = iterator.next()
                if (pending.metadata.owner == requestedOwner) { iterator.remove(); downloads.discard(pending) }
            }
        }
        // Rotation may deliver the previous Activity's result here. Only this
        // page's queued files survive; the actual OS permission is checked again.
        drainLegacyDownloads()
    }

    private fun trusted(view: WebView?, origin: Uri? = null): Boolean {
        val owner = currentOwner() ?: return false
        val url = view?.url?.let(Uri::parse) ?: return false
        if (closed || view !== currentView() || url.scheme != "https" ||
            url.host != "appassets.androidplatform.net" || !url.path.orEmpty().startsWith("/project/$owner/")) return false
        return origin == null || (origin.scheme == "https" && origin.host == url.host && origin.port == url.port)
    }

    fun requestMedia(request: PermissionRequest) {
        if (!trusted(currentView(), request.origin) || request.resources.isEmpty() ||
            previewDevicePermissions(request.resources.toList()).isEmpty() || permissionDialogRunning) {
            request.deny(); return
        }
        val needed = previewDevicePermissions(request.resources.toList()).filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) { grantAvailableResources(request); return }
        mediaRequest = MediaPermission(request, checkNotNull(currentOwner()))
        permissionDialogRunning = true
        try { permissionLauncher.launch(needed.toTypedArray()) }
        catch (_: Exception) {
            permissionDialogRunning = false
            mediaRequest = null
            request.deny()
        }
    }

    private fun grantAvailableResources(request: PermissionRequest) {
        val resources = request.resources.filter { resource ->
            val permission = previewDevicePermissions(listOf(resource)).singleOrNull()
            permission != null && ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (resources.isEmpty()) request.deny() else request.grant(resources)
    }

    fun cancelMedia(request: PermissionRequest) {
        if (mediaRequest?.request === request) mediaRequest = null
    }

    fun chooseFiles(view: WebView?, callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams?): Boolean {
        if (!trusted(view) || params == null || fileDialogRunning) {
            callback.onReceiveValue(null); return true
        }
        fileSelection = FileSelection(callback, checkNotNull(currentOwner()))
        fileDialogRunning = true
        try {
            val intent = params.createIntent().apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            fileLauncher.launch(intent)
        } catch (_: Exception) {
            fileSelection = null
            fileDialogRunning = false
            callback.onReceiveValue(null)
            report("ファイルを選択できませんでした")
        }
        return true
    }

    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (closed || customDialog != null) { callback.onCustomViewHidden(); return }
        customCallback = callback
        val container = FrameLayout(activity).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            (view.parent as? ViewGroup)?.removeView(view)
            addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        customDialog = dialog
        dialog.setContentView(container)
        dialog.setOnDismissListener {
            container.removeAllViews()
            customDialog = null
            val pending = customCallback.also { customCallback = null }
            pending?.onCustomViewHidden()
        }
        dialog.show()
        dialog.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, container).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    fun hideFullscreen() { customDialog?.dismiss() }

    fun openExternal(uri: Uri) {
        if (closed || uri.scheme !in setOf("https", "http")) return
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
        catch (_: Exception) { report("ブラウザーを開けませんでした") }
    }

    fun saveFile(download: PendingSketchDownload) {
        if (closed || currentOwner() != download.metadata.owner) { downloads.discard(download); return }
        if (!downloads.reserve(download)) return
        downloads.save(download)
    }

    private fun legacyStorageGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun drainLegacyDownloads() {
        if (closed) return
        val owner = currentOwner()
        val iterator = pendingDownloads.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (pending.metadata.owner != owner) { iterator.remove(); downloads.discard(pending) }
        }
        if (pendingDownloads.isEmpty()) return
        if (legacyStorageGranted()) {
            while (pendingDownloads.isNotEmpty()) downloads.save(pendingDownloads.removeFirst())
            return
        }
        if (downloads.legacyPermissionRequestInFlight || downloads.destinationSelectionInFlight) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && downloads.beginLegacyPermissionRequest()) {
            storagePermissionOwner = pendingDownloads.first.metadata.owner
            try { storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
            catch (_: Exception) {
                storagePermissionOwner = null
                downloads.finishLegacyPermissionRequest()
                chooseNextDownload()
            }
        } else chooseNextDownload()
    }

    private fun chooseNextDownload() {
        if (closed || selectedDownload != null || pendingDownloads.isEmpty() || !downloads.beginDestinationSelection()) return
        val pending = pendingDownloads.removeFirst()
        selectedDownload = pending
        try {
            downloadLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = pending.metadata.mime
                putExtra(Intent.EXTRA_TITLE, pending.metadata.name)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            })
        } catch (_: Exception) {
            downloads.finishDestinationSelection()
            selectedDownload = null
            downloads.discard(pending)
            chooseNextDownload()
        }
    }

    fun invalidatePage() {
        mediaRequest?.request?.deny()
        mediaRequest = null
        fileSelection?.callback?.onReceiveValue(null)
        fileSelection = null
        hideFullscreen()
        pendingDownloads.forEach(downloads::discard)
        pendingDownloads.clear()
        selectedDownload?.let(downloads::discard)
        selectedDownload = null
    }

    fun close() {
        closed = true
        invalidatePage()
        // Keep OS-dialog flags during recreation so a restored old result can
        // never be applied to a newly queued file. Started writer jobs stay in VM.
        if (!activity.isChangingConfigurations) {
            downloads.finishDestinationSelection()
            downloads.finishLegacyPermissionRequest()
        }
        permissionLauncher.unregister()
        fileLauncher.unregister()
        downloadLauncher.unregister()
        storagePermissionLauncher.unregister()
    }
}
