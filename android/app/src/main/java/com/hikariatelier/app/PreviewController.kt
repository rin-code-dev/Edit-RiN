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
import androidx.compose.runtime.setValue
import androidx.core.os.ConfigurationCompat
import java.io.File

/** Owns one Activity's WebView. No ViewModel retains this controller or the Activity. */
internal class PreviewController(
    private val activity: ComponentActivity,
    val assetStorage: AssetStorage,
    private val models: EditorModels
) {
    val session = PreviewSession()
    private val transfer = RecordingTransfer(File(activity.cacheDir, "recording-transfer"))
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

    private fun append(level: ConsoleLevel, message: String, line: Int? = null, file: String? = null) {
        models.console.append(level, message, line, file, session.workId)
        if (level == ConsoleLevel.ERROR) {
            isError = true
            models.console.showConsole = true
        }
    }

    fun evaluate(script: String) { if (!disposed) webView?.evaluateJavascript(script, null) }

    fun requestScreenshot(forShareCard: Boolean = false, scale: Int = 1) {
        if (disposed || webView == null || screenshotBusy || recording.screenshotSaving) return
        if (scale !in listOf(1, 2, 4)) return
        if (scale > 1 && (recording.isPreviewRecording || recording.isRecordingSaving || recording.pendingRecordingFormat != null)) {
            Toast.makeText(activity, text("録画を停止してから書き出してください"), Toast.LENGTH_LONG).show()
            return
        }
        screenshotBusy = true
        captureForShareCard = forShareCard
        evaluate("if (typeof window.__editKiroCaptureScreenshot === 'function') { window.__editKiroCaptureScreenshot($scale); } " +
            "else { window.Android?.onCaptureError('プレビューの準備ができてから再度お試しください'); }")
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
        val token = prepareSnapshot(source, supportingFiles, sourceAssets)
        isPaused = false
        isError = false
        models.console.clear()
        webView?.loadUrl(previewUrl(token))
    }

    private fun prepareSnapshot(source: String, files: Map<String, String>, assets: Map<String, ProjectAsset>): String {
        val work = currentWork()?.let { original ->
            snapshotWork(original).also {
                it.parameterValues.clear()
                it.parameterValues.putAll(models.works.parameterValues(original))
            }
        }
        return session.prepare(work, source, files, assets, models.session.fileDrafts.toMap())
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
                            val location = if (it.sourceId() == "sketch.js") {
                                previewSourceLocation(session.sourceFiles, it.lineNumber())
                            } else null
                            onMain { append(level, content, location?.line, location?.file) }
                        }
                    }
                    return true
                }
            }
            view.webViewClient = AssetWebClient(activity.assets, assetStorage, onRendererCrash = { crashed ->
                onMain {
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
            val token = prepareSnapshot(models.session.editorValueState.value.text,
                currentWork()?.files.orEmpty(), currentWork()?.assets.orEmpty())
            view.loadUrl(previewUrl(token))
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
        captureForShareCard = false
        transfer.abort()
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
        @JavascriptInterface fun getSketchCode(): String = session.sketchCode
        @JavascriptInterface fun getP5Version(): String = session.p5Version
        @JavascriptInterface fun isP5SoundEnabled(): Boolean = session.soundEnabled
        @JavascriptInterface fun getWorkLibraries(): String = session.libraries
        @JavascriptInterface fun getWorkParameters(): String = session.parameters
        @JavascriptInterface fun getWorkShaders(): String = session.shaders

        @JavascriptInterface fun onError(message: String) {
            Log.e("P5JS", message)
            onMain { append(ConsoleLevel.ERROR, message) }
        }
        @JavascriptInterface fun onRuntimeError(message: String, line: Int) {
            Log.e("P5JS", "$message ($line)")
            val location = previewSourceLocation(session.sourceFiles, line)
            onMain { append(ConsoleLevel.ERROR, message, location?.line, location?.file) }
        }
        @JavascriptInterface fun onStatusChanged(status: String) {
            onMain {
                if (status == "実行中") isPaused = false
                if (status == "一時停止中") isPaused = true
            }
        }
        @JavascriptInterface fun onScreenshotReady(dataUrl: String) = screenshotReady(dataUrl, 0, 0)
        @JavascriptInterface fun onScreenshotExportReady(dataUrl: String, width: Int, height: Int) =
            screenshotReady(dataUrl, width, height)
        private fun screenshotReady(dataUrl: String, width: Int, height: Int) {
            val forCard = captureForShareCard
            captureForShareCard = false
            onMain {
                screenshotBusy = false
                recording.saveScreenshot(dataUrl, forCard, width, height)
            }
        }
        @JavascriptInterface fun onRecordingStatusChanged(value: Boolean) {
            onMain { recording.isPreviewRecording = value }
        }
        @JavascriptInterface fun beginRecordingTransfer(token: String): Boolean = !disposed && transfer.begin(token)
        @JavascriptInterface fun appendRecordingChunk(token: String, encoded: String): Boolean =
            !disposed && transfer.append(token, encoded)
        @JavascriptInterface fun abortRecordingTransfer(token: String) {
            transfer.abort(token)
            onMain { recording.abortTransfer() }
        }
        @JavascriptInterface fun onRecordingSaving() {
            onMain {
                if (!recording.isRecordingSaving) {
                    recording.recordingElapsedMillis = (SystemClock.elapsedRealtime() - recording.recordingStartedAt)
                        .coerceIn(0L, recording.recordingLimitMillis)
                }
                recording.isRecordingSaving = true
            }
        }
        @JavascriptInterface fun finishRecordingTransfer(token: String, mimeType: String) {
            val file = transfer.finish(token)
            // Do not strand a file if the Activity is destroyed before its queued callback executes.
            activity.runOnUiThread {
                if (disposed) file?.delete() else recording.saveTransferredRecording(file, mimeType)
            }
        }
        @JavascriptInterface fun onCaptureError(message: String) {
            captureForShareCard = false
            onMain {
                screenshotBusy = false
                Toast.makeText(activity, text(message), Toast.LENGTH_LONG).show()
            }
        }
    }
}
