package com.hikariatelier.app

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import org.json.JSONTokener
import org.json.JSONObject
import kotlin.coroutines.resume

/** One separate, short-lived WebView. No editor state, writable bridge, or permission prompts. */
internal class WorkPreviewWarmup(
    private val activity: ComponentActivity,
    private val storage: AssetStorage,
    private val works: WorkManagementViewModel,
    private val session: EditorSessionViewModel,
    private val mediaBusy: () -> Boolean
) {
    private val preferences = activity.getSharedPreferences("preview-warmup", 0)
    private val epoch = "1:${BuildConfig.VERSION_CODE}:" +
        activity.packageManager.getPackageInfo(activity.packageName, 0).lastUpdateTime

    suspend fun run() {
        if (works.loadFailure != null) return
        // Let the visible editor and its first preview finish opening first.
        delay(2000)
        val folder = works.selectedFolderUri
        val scopeKey = folder?.toString().orEmpty()
        val completedKey = "$scopeKey/completed"
        if (preferences.getString(completedKey, null) == epoch) return
        var retryNeeded = false
        val ids = session.worksState.value.map { it.id }
        for (id in ids) {
            currentCoroutineContext().ensureActive()
            if (folder != works.selectedFolderUri || works.loadFailure != null) return
            val file = workPreviewFile(activity.cacheDir, id)
            val checkpoint = "$scopeKey/${file.name}"
            if (preferences.getString(checkpoint, null) == epoch) continue
            // Wait for save/record operations rather than competing with them.
            while (works.workSaving || session.assetBusy || session.snapshotOperationWorkId != null || mediaBusy()) delay(500)
            try {
                val input = works.thumbnailInput(id, folder)
                if (input == null) { retryNeeded = true; continue }
                val previousModified = withContext(Dispatchers.IO) { file.lastModified() }
                val prepared = withContext(Dispatchers.Default) { preparePreviewRun(input) }
                val encoded = render(prepared)
                if (folder != works.selectedFolderUri) return
                val current = works.thumbnailInput(id, folder)
                if (current == null || current.copy(token = input.token) != input) {
                    retryNeeded = true
                    continue
                }
                if (encoded != null && session.worksState.value.any { it.id == id } &&
                    withContext(Dispatchers.IO) { file.lastModified() == previousModified }) {
                    // A foreground capture made while rendering always takes precedence.
                    if (storeWorkPreview(file, encoded, previousModified)) works.notifyPreviewUpdated(id)
                    else { retryNeeded = true; continue }
                }
                // A broken sketch must not be executed on every return to the app.
                withContext(Dispatchers.IO) { preferences.edit().putString(checkpoint, epoch).commit() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                retryNeeded = true
                // A load failure is retryable on the next launch; other works can still be prepared.
            }
            delay(250)
        }
        if (!retryNeeded && folder == works.selectedFolderUri) withContext(Dispatchers.IO) {
            preferences.edit().putString(completedKey, epoch).commit()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun render(run: PreparedPreviewRun): String? {
        val ready = CompletableDeferred<Unit>()
        val bridge = ThumbnailBridge(run, ready)
        val view = WebView(activity)
        var destroyed = false
        val host = PreviewWebViewHost(activity, view).apply {
            setLogicalSize(480, 480)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isFocusable = false
        }
        // Attach behind the editor so WebView can draw, without taking input or changing selection.
        val parent = activity.findViewById<ViewGroup>(android.R.id.content)
        parent.addView(host, 0, ViewGroup.LayoutParams(1, 1))
        try {
            view.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = true
                blockNetworkLoads = true
            }
            view.isFocusable = false
            view.addJavascriptInterface(bridge, "Android")
            view.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) = request.deny()
                override fun getDefaultVideoPoster() = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            }
            view.webViewClient = AssetWebClient(activity.assets, storage, onRendererCrash = {
                destroyed = true
                ready.completeExceptionally(IllegalStateException("Thumbnail renderer stopped"))
            }) { run.assets }
            view.loadUrl(previewUrl(run.assets))
            return withTimeoutOrNull(8000) {
                ready.await()
                // Allow animated sketches to produce a representative frame.
                delay(700)
                suspendCancellableCoroutine { continuation ->
                    view.evaluateJavascript(THUMBNAIL_CAPTURE_SCRIPT) { result ->
                        val data = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
                        if (continuation.isActive) continuation.resume(data?.takeIf {
                            it.startsWith("data:image/png;base64,")
                        }?.substringAfter(','))
                    }
                }
            }
        } finally {
            parent.removeView(host)
            if (!destroyed) {
                host.removeView(view)
                view.stopLoading()
                view.removeJavascriptInterface("Android")
                view.destroy()
            }
        }
    }
}

internal class ThumbnailBridge(private val run: PreparedPreviewRun, private val ready: CompletableDeferred<Unit>) {
    private fun current(token: String) = token == run.assets.token
    @JavascriptInterface fun onPreviewReady(token: String) { if (current(token)) ready.complete(Unit) }
    @JavascriptInterface fun getSketchCode(token: String) = if (current(token)) run.sketchCode else ""
    @JavascriptInterface fun getP5Version(token: String) = if (current(token)) run.p5Version else ""
    @JavascriptInterface fun isP5SoundEnabled(token: String) = current(token) && run.soundEnabled
    @JavascriptInterface fun getWorkLibraries(token: String) = if (current(token)) run.libraries else "{}"
    @JavascriptInterface fun getWorkParameters(token: String) = if (current(token)) run.parameters else "{}"
    @JavascriptInterface fun getWorkShaders(token: String) = if (current(token)) run.shaders else "{}"
    @JavascriptInterface fun getProjectConfig(token: String) = if (current(token))
        JSONObject(run.projectConfig).put("thumbnailOnly", true).toString() else "{}"
}

// Canvas capture is disposable and never changes the saved source.
private const val THUMBNAIL_CAPTURE_SCRIPT = """
(() => {
 const source = document.querySelector('canvas.p5Canvas') || document.querySelector('canvas');
 if (!source || !source.width || !source.height) return null;
 try {
   const copy = document.createElement('canvas');
   const scale = Math.min(1, 480 / Math.max(source.width, source.height));
   copy.width = Math.max(1, Math.round(source.width * scale));
   copy.height = Math.max(1, Math.round(source.height * scale));
   copy.getContext('2d').drawImage(source, 0, 0, copy.width, copy.height);
   return copy.toDataURL('image/png');
 } catch (_) { return null; }
})()
"""
