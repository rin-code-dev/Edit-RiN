package com.hikariatelier.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.ConfigurationCompat
import java.io.File
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns one Activity's WebView. No ViewModel retains this controller or the Activity. */
internal class PreviewController(
    private val activity: ComponentActivity,
    val assetStorage: AssetStorage,
    private val models: EditorModels
) {
    val session = PreviewSession()
    private val transfer = RecordingTransfer(File(activity.cacheDir, "recording-transfer"))
    private val screenshotTransfer = RecordingTransfer(File(activity.cacheDir, "screenshot-transfer"))
    var generation by mutableIntStateOf(0)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var loadingThumbnail by mutableStateOf<android.graphics.Bitmap?>(null)
        private set
    private var runStartedAt = 0L
    private var prepared = false
    private var preparationJob: Job? = null
    var webView: WebView? = null
        private set
    var isError by mutableStateOf(false)
    var isPaused by mutableStateOf(false)
    @Volatile private var disposed = false
    @Volatile private var captureForShareCard = false
    var screenshotBusy by mutableStateOf(false)
        private set

    private fun text(source: String): String {
        val language = ConfigurationCompat.getLocales(activity.resources.configuration)[0]?.language ?: "en"
        return translateUi(source, resolveUiLanguage(models.settings.appLanguage, language))
    }

    private fun onMain(action: () -> Unit) {
        activity.runOnUiThread { if (!disposed) action() }
    }

    private fun onMain(token: String, action: () -> Unit) {
        onMain { if (session.isCurrent(token)) action() }
    }

    private fun append(level: ConsoleLevel, message: String, line: Int? = null, file: String? = null) {
        models.console.append(level, message, line, file, session.workId)
        if (level == ConsoleLevel.ERROR) {
            isLoading = false
            loadingThumbnail = null
            isError = true
            models.console.showConsole = true
        }
    }

    fun evaluate(script: String) { if (!disposed) webView?.evaluateJavascript(script, null) }

    fun requestScreenshot(forShareCard: Boolean = false, scale: Int = 1) {
        if (disposed || webView == null || screenshotBusy || recording.screenshotSaving) return
        if (!session.isCurrent(session.assets.token)) {
            Toast.makeText(activity, text("プレビューの準備ができてから再度お試しください"), Toast.LENGTH_LONG).show()
            return
        }
        if (scale !in listOf(1, 2, 4)) return
        if (scale > 1 && (recording.isPreviewRecording || recording.isRecordingSaving || recording.pendingRecordingFormat != null)) {
            Toast.makeText(activity, text("録画を停止してから書き出してください"), Toast.LENGTH_LONG).show()
            return
        }
        screenshotBusy = true
        captureForShareCard = forShareCard
        evaluate("if (typeof window.__editKiroCaptureScreenshot === 'function') { window.__editKiroCaptureScreenshot($scale); } " +
            "else { window.Android?.onScreenshotError('${session.assets.token}', 'プレビューの準備ができてから再度お試しください'); }")
    }

    fun runSketch(
        source: String = models.session.editorValueState.value.text,
        supportingFiles: Map<String, String> = currentWork()?.files.orEmpty(),
        sourceAssets: Map<String, ProjectAsset> = currentWork()?.assets.orEmpty()
    ) {
        if (disposed) return
        val recording = models.recording
        if (recording.isPreviewRecording || recording.isRecordingSaving || recording.pendingRecordingFormat != null) {
            Toast.makeText(activity, text("録画を停止してから再実行してください"), Toast.LENGTH_SHORT).show()
            return
        }
        // A new page invalidates any unfinished capture owned by the previous run.
        // A transfer can begin before its recording-status callback reaches the UI thread.
        transfer.abort()
        screenshotTransfer.abort()
        screenshotBusy = false
        captureForShareCard = false
        preparationJob?.cancel()
        val work = currentWork()
        val input = capturePreviewRun(work, source, supportingFiles, sourceAssets,
            models.session.fileDrafts.toMap(), work?.let { models.works.parameterValues(it) }.orEmpty())
        val previousId = session.workId
        session.request(input.token)
        beginLoading(previousId, input.token)
        isPaused = false
        isError = false
        models.console.clear()
        preparationJob = activity.lifecycleScope.launch {
            try {
                val preparedRun = withContext(Dispatchers.Default) { preparePreviewRun(input) }
                ensureActive()
                if (disposed || !session.publish(preparedRun)) return@launch
                prepared = true
                webView?.loadUrl(previewUrl(input.token)) ?: run { generation++ }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (!disposed && session.isRequested(input.token)) append(ConsoleLevel.ERROR, text("プレビューを準備できませんでした"))
            }
        }
    }

    private fun beginLoading(previousId: String?, token: String) {
        isLoading = true
        loadingThumbnail = null
        runStartedAt = SystemClock.elapsedRealtime()
        if (previousId != null) activity.lifecycleScope.launch {
            val thumbnail = withContext(Dispatchers.IO) {
                runCatching { android.graphics.BitmapFactory.decodeFile(workPreviewFile(activity.cacheDir, previousId).path) }.getOrNull()
            }
            if (!disposed && isLoading && session.isRequested(token)) loadingThumbnail = thumbnail
        }
    }

    private fun currentWork() = models.session.worksState.value.find {
        it.id == models.session.activeWorkIdState.value
    }

    // Recording survives Activity recreation; the WebView itself does not.
    private val recording get() = models.recording

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun createHost(context: Context, clearEditorFocus: () -> Unit): PreviewWebViewHost {
        check(!disposed) { "Preview has been disposed" }
        val preview = webView?.also {
            (it.parent as? ViewGroup)?.removeView(it)
            it.onResume()
            it.post { if (!disposed) it.onResume() }
        } ?: WebView(context).also { view ->
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.allowFileAccess = false
            view.settings.allowContentAccess = false
            view.setBackgroundColor(android.graphics.Color.BLACK)
            view.addJavascriptInterface(Bridge(), "Android")
            view.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                    message?.let {
                        val content = it.message().orEmpty()
                        if (!content.contains("Ignored attempt to cancel a touchmove event", ignoreCase = true)) {
                            val level = when (it.messageLevel()) {
                                ConsoleMessage.MessageLevel.ERROR -> ConsoleLevel.ERROR
                                ConsoleMessage.MessageLevel.WARNING -> ConsoleLevel.WARNING
                                else -> ConsoleLevel.LOG
                            }
                            val sourceId = it.sourceId().orEmpty()
                            val token = previewTokenFromSource(sourceId) ?: return@let
                            val location = if (sourceId.contains("sketch.js?run=")) {
                                previewSourceLocation(session.sourceFiles, it.lineNumber())
                            } else null
                            onMain(token) { append(level, content, location?.line, location?.file) }
                        }
                    }
                    return true
                }
            }
            view.webViewClient = AssetWebClient(activity.assets, assetStorage, onRendererCrash = { crashed ->
                onMain {
                    if (webView !== view) return@onMain
                    webView = null
                    transfer.abort()
                    screenshotTransfer.abort()
                    recording.onPreviewDestroyed()
                    screenshotBusy = false
                    captureForShareCard = false
                    val message = if (crashed) {
                        text("描画プロセスが異常終了しました（メモリまたはGPU負荷が高すぎる可能性があります）")
                    } else text("描画プロセスがメモリ不足等により終了されました")
                    append(ConsoleLevel.ERROR, message)
                }
            }) { session.assets }
            webView = view
            // Read the current session, rather than capturing a work from the first composition.
            if (prepared && session.isCurrent(session.assets.token)) {
                view.loadUrl(previewUrl(session.assets.token))
            } else if (preparationJob?.isActive != true) runSketch()
        }
        preview.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                clearEditorFocus()
                view.requestFocus()
            }
            false
        }
        return PreviewWebViewHost(context, preview)
    }

    fun pause() { webView?.onPause() }
    fun resume() { if (!disposed) webView?.onResume() }

    fun close() {
        if (disposed) return
        disposed = true
        preparationJob?.cancel()
        session.invalidate()
        captureForShareCard = false
        transfer.abort()
        screenshotTransfer.abort()
        recording.onPreviewDestroyed()
        webView?.let {
            it.stopLoading()
            it.removeJavascriptInterface("Android")
            it.loadUrl("about:blank")
            it.clearHistory()
            it.removeAllViews()
            it.destroy()
        }
        webView = null
    }

    /** Called on the WebView bridge thread; UI changes are dispatched and guarded by close(). */
    private inner class Bridge {
        @JavascriptInterface fun onPreviewReady(token: String) {
            onMain(token) {
                if (!isError) {
                    isLoading = false
                    loadingThumbnail = null
                    if (BuildConfig.DEBUG) Log.d("EditRiNPreview", "Preview ready in ${SystemClock.elapsedRealtime() - runStartedAt} ms")
                }
            }
        }
        @JavascriptInterface fun getSketchCode(token: String): String = session.snapshotFor(token)?.sketchCode.orEmpty()
        @JavascriptInterface fun getP5Version(token: String): String = session.snapshotFor(token)?.p5Version.orEmpty()
        @JavascriptInterface fun isP5SoundEnabled(token: String): Boolean = session.snapshotFor(token)?.soundEnabled == true
        @JavascriptInterface fun getWorkLibraries(token: String): String = session.snapshotFor(token)?.libraries ?: "{}"
        @JavascriptInterface fun getWorkParameters(token: String): String = session.snapshotFor(token)?.parameters ?: "{}"
        @JavascriptInterface fun getWorkShaders(token: String): String = session.snapshotFor(token)?.shaders ?: "{}"

        @JavascriptInterface fun onError(token: String, message: String) {
            onMain(token) { Log.e("P5JS", message); append(ConsoleLevel.ERROR, message) }
        }
        @JavascriptInterface fun onRuntimeError(token: String, message: String, line: Int) {
            onMain(token) {
                val location = previewSourceLocation(session.sourceFiles, line)
                Log.e("P5JS", "$message ($line)")
                append(ConsoleLevel.ERROR, message, location?.line, location?.file)
            }
        }
        @JavascriptInterface fun onStatusChanged(token: String, status: String) {
            onMain(token) {
                if (status == "実行中") isPaused = false
                if (status == "一時停止中") isPaused = true
            }
        }
        @JavascriptInterface fun onScreenshotReady(token: String, dataUrl: String) = screenshotReady(token, dataUrl, 0, 0)
        @JavascriptInterface fun onScreenshotExportReady(token: String, dataUrl: String, width: Int, height: Int) =
            screenshotReady(token, dataUrl, width, height)
        private fun currentScreenshot(owner: String, token: String) =
            !disposed && session.isCurrent(owner) && token.startsWith("$owner:png-")
        @JavascriptInterface fun beginScreenshotTransfer(owner: String, token: String): Boolean =
            currentScreenshot(owner, token) && screenshotTransfer.begin(token)
        @JavascriptInterface fun appendScreenshotChunk(owner: String, token: String, encoded: String): Boolean =
            currentScreenshot(owner, token) && screenshotTransfer.append(token, encoded)
        @JavascriptInterface fun abortScreenshotTransfer(owner: String, token: String) {
            if (currentScreenshot(owner, token)) screenshotTransfer.abort(token)
        }
        @JavascriptInterface fun finishScreenshotTransfer(owner: String, token: String, width: Int, height: Int) {
            if (!currentScreenshot(owner, token)) return
            val file = screenshotTransfer.finish(token)
            activity.runOnUiThread {
                if (!currentScreenshot(owner, token)) file?.delete() else {
                    val forCard = captureForShareCard
                    captureForShareCard = false
                    screenshotBusy = false
                    recording.saveScreenshotFile(file, forCard, width, height)
                }
            }
        }
        @JavascriptInterface fun onScreenshotError(token: String, message: String) {
            onMain(token) {
                screenshotBusy = false
                captureForShareCard = false
                Toast.makeText(activity, text(message), Toast.LENGTH_LONG).show()
            }
        }
        private fun screenshotReady(token: String, dataUrl: String, width: Int, height: Int) {
            onMain(token) {
                val forCard = captureForShareCard
                captureForShareCard = false
                screenshotBusy = false
                recording.saveScreenshot(dataUrl, forCard, width, height)
            }
        }
        @JavascriptInterface fun onRecordingStatusChanged(token: String, value: Boolean) {
            onMain(token) { recording.isPreviewRecording = value }
        }
        @JavascriptInterface fun beginRecordingTransfer(owner: String, token: String): Boolean =
            !disposed && session.isCurrent(owner) && transfer.begin(token)
        @JavascriptInterface fun appendRecordingChunk(owner: String, token: String, encoded: String): Boolean =
            !disposed && session.isCurrent(owner) && transfer.append(token, encoded)
        @JavascriptInterface fun abortRecordingTransfer(owner: String, token: String) {
            if (!disposed && session.isCurrent(owner)) transfer.abort(token)
            onMain(owner) { recording.abortTransfer() }
        }
        @JavascriptInterface fun onRecordingSaving(token: String) {
            onMain(token) {
                if (!recording.isRecordingSaving) {
                    recording.recordingElapsedMillis = (SystemClock.elapsedRealtime() - recording.recordingStartedAt)
                        .coerceIn(0L, recording.recordingLimitMillis)
                }
                recording.isRecordingSaving = true
            }
        }
        @JavascriptInterface fun finishRecordingTransfer(owner: String, token: String, mimeType: String) {
            if (disposed || !session.isCurrent(owner)) return
            val file = transfer.finish(token)
            // Do not strand a file if the Activity is destroyed before its queued callback executes.
            activity.runOnUiThread {
                if (disposed || !session.isCurrent(owner)) file?.delete() else recording.saveTransferredRecording(file, mimeType)
            }
        }
        @JavascriptInterface fun onCaptureError(token: String, message: String) {
            onMain(token) {
                Toast.makeText(activity, text(message), Toast.LENGTH_LONG).show()
            }
        }
    }
}
